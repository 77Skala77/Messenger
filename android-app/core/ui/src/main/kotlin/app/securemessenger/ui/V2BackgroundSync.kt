package app.securemessenger.ui

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.securemessenger.domain.RelayEncryptedBlob
import app.securemessenger.domain.RelayQueueCredentials
import app.securemessenger.domain.TransportClient
import app.securemessenger.signalprotocol.ConversationV2Service
import app.securemessenger.signalprotocol.ConversationV2Wiring
import app.securemessenger.storage.AndroidKeystoreVaultCipher
import app.securemessenger.storage.EncryptedConversationV2Repository
import app.securemessenger.storage.EncryptedSignalStoreRepository
import app.securemessenger.storage.LocalProfileRepository
import app.securemessenger.storage.RoomProtocolTransactionRunner
import app.securemessenger.storage.SecureMessengerDatabase
import app.securemessenger.storage.VaultKeyUnavailableException
import app.securemessenger.storage.VaultRecordRejectedException
import app.securemessenger.transport.RelayHttpTransportClient
import app.securemessenger.transport.RelayServerSettings
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** Builds the v2 pipeline over the app database. Shared by the UI and the background worker. */
internal object V2Pipeline {
    /**
     * Builds the pipeline, or returns null when no profile exists. The profile check and the
     * pipeline creation (which creates this device's libsignal identity if none exists) run under
     * the protocol lock, like [wipeLocalData], so a wipe can never leave a new identity behind.
     * With a lost vault key it throws `VaultKeyUnavailableException` and creates nothing, not even
     * a key (DEC-023). Blocking.
     */
    fun createIfProfileExists(context: Context, database: SecureMessengerDatabase): ConversationV2Service? =
        ConversationV2Service.exclusively {
            LocalProfileRepository(database.dao(), AndroidKeystoreVaultCipher()).readProfileName()
                ?.let { displayName -> create(context, database, displayName) }
        }

    /**
     * Deletes the vault key, then the profile and every conversation and libsignal record (one
     * transaction), then the v2 queue ID. It holds the protocol lock, so a protocol step of the UI
     * or the worker either commits before the wipe (and is wiped) or runs after it on empty state.
     * The key goes first (DEC-023): if deleting it fails, nothing is deleted and the user can try
     * again; once it is gone, the old rows and any copies left in flash cannot be decrypted, even
     * if the row deletion then fails. Needs no key, so it also works on unreadable data. Blocking.
     */
    fun wipeLocalData(context: Context, database: SecureMessengerDatabase) {
        ConversationV2Service.exclusively {
            val vault = AndroidKeystoreVaultCipher()
            vault.deleteKey()
            RoomProtocolTransactionRunner(database).inTransaction {
                LocalProfileRepository(database.dao(), vault).wipeLocalData()
            }
            V2QueueCredentialStore(context.applicationContext, vault).clear()
        }
    }

    private fun create(context: Context, database: SecureMessengerDatabase, displayName: String): ConversationV2Service {
        val application = context.applicationContext
        val dao = database.dao()
        val vault = AndroidKeystoreVaultCipher()
        val signalRecords = EncryptedSignalStoreRepository(dao, vault)
        return ConversationV2Wiring.create(
            localIdentity = signalRecords,
            identities = signalRecords,
            sessions = signalRecords,
            preKeys = signalRecords,
            signedPreKeys = signalRecords,
            kyberPreKeys = signalRecords,
            conversations = EncryptedConversationV2Repository(dao, vault),
            transactions = RoomProtocolTransactionRunner(database),
            transport = CurrentRelayTransport(
                RelayServerSettings(application, application.resources.getBoolean(R.bool.allow_insecure_relay_http)),
            ),
            localReceiveQueue = V2QueueCredentialStore(application, vault).getOrCreate(),
            localDisplayName = { displayName },
        )
    }
}

/**
 * Best-effort background delivery for v2 (DEC-021): Android decides when periodic work runs, so
 * this is not a timely-delivery guarantee. Scheduled only where v2 is available.
 */
class V2SyncScheduler(context: Context) {
    private val application = context.applicationContext
    private val workManager = WorkManager.getInstance(application)
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun ensurePeriodic() {
        if (!V2Availability.isAvailable(application)) return
        val work = PeriodicWorkRequestBuilder<V2SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, work)
    }

    /** One sync soon, e.g. after a send while the app may close. */
    fun requestSoon() {
        if (!V2Availability.isAvailable(application)) return
        val work = OneTimeWorkRequestBuilder<V2SyncWorker>()
            .setConstraints(network)
            .setInitialDelay(10, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(SOON, ExistingWorkPolicy.REPLACE, work)
    }

    fun cancel() {
        workManager.cancelUniqueWork(PERIODIC)
        workManager.cancelUniqueWork(SOON)
    }

    private companion object {
        const val PERIODIC = "v2-periodic-sync"
        const val SOON = "v2-sync-soon"
    }
}

/**
 * Fetches and sends v2 envelopes in the background. It never logs message bodies, keys, envelopes,
 * or queue IDs. The protocol lock inside the pipeline is process-wide, so this worker and the UI
 * never run a protocol step at the same time.
 */
class V2SyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!V2Availability.isAvailable(applicationContext)) return Result.success()
        val database = SecureMessengerDatabase.create(applicationContext)
        return try {
            val service = V2Pipeline.createIfProfileExists(applicationContext, database) ?: return Result.success()
            // Expiry first, so a failing sync cannot keep expired invitations alive.
            service.expireInvitations()
            service.synchronize()
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: VaultKeyUnavailableException) {
            // DEC-023: the stored data can no longer be read. Retrying cannot help, and nothing may
            // run or create a new key; the app explains it the next time it is opened.
            Result.failure()
        } catch (_: VaultRecordRejectedException) {
            // Data not authentic under the current key (a replaced key or a damaged row): same.
            Result.failure()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            database.close()
        }
    }
}

/** Follows relay URL changes made in Settings without rebuilding the pipeline. */
internal class CurrentRelayTransport(private val settings: RelayServerSettings) : TransportClient {
    private var url = ""
    private var client: RelayHttpTransportClient? = null

    @Synchronized
    private fun client(): RelayHttpTransportClient {
        val current = settings.currentUrl()
        return client.takeIf { current == url } ?: RelayHttpTransportClient(current).also {
            client = it
            url = current
        }
    }

    override suspend fun sendMessage(queueId: String, opaqueCiphertext: ByteArray, expiresAtEpochMillis: Long): String =
        client().sendMessage(queueId, opaqueCiphertext, expiresAtEpochMillis)

    override suspend fun fetchMessages(queue: RelayQueueCredentials): List<RelayEncryptedBlob> = client().fetchMessages(queue)

    override suspend fun deleteMessage(queue: RelayQueueCredentials, messageId: String) = client().deleteMessage(queue, messageId)
}
