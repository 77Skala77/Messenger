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
import app.securemessenger.crypto.SimplifiedSessionManager
import app.securemessenger.crypto.StandardAead
import app.securemessenger.delivery.DeliverySyncExecutor
import app.securemessenger.delivery.IncomingDeliveryProcessor
import app.securemessenger.delivery.OutgoingAttemptResult
import app.securemessenger.delivery.OutgoingDeliveryProcessor
import app.securemessenger.delivery.ReceiveBinding
import app.securemessenger.storage.AndroidKeystoreVaultCipher
import app.securemessenger.storage.SecureLocalRepository
import app.securemessenger.storage.SecureMessengerDatabase
import app.securemessenger.transport.RelayHttpTransportClient
import app.securemessenger.transport.RelayServerSettings
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

interface DeliverySyncScheduler {
    fun ensurePeriodicSync()
    fun requestRetry()
    fun synchronizeNow()
    fun cancel()
}

class WorkManagerDeliverySyncScheduler(context: Context) : DeliverySyncScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)
    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    override fun ensurePeriodicSync() {
        val work = PeriodicWorkRequestBuilder<RelaySyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_SYNC, ExistingPeriodicWorkPolicy.KEEP, work)
    }

    override fun requestRetry() {
        val work = OneTimeWorkRequestBuilder<RelaySyncWorker>()
            .setConstraints(networkConstraint)
            .setInitialDelay(10, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(RETRY_SYNC, ExistingWorkPolicy.REPLACE, work)
    }

    override fun synchronizeNow() {
        val work = OneTimeWorkRequestBuilder<RelaySyncWorker>()
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(MANUAL_SYNC, ExistingWorkPolicy.REPLACE, work)
    }

    override fun cancel() {
        workManager.cancelUniqueWork(PERIODIC_SYNC)
        workManager.cancelUniqueWork(RETRY_SYNC)
        workManager.cancelUniqueWork(MANUAL_SYNC)
    }

    private companion object {
        const val PERIODIC_SYNC = "relay-periodic-sync"
        const val RETRY_SYNC = "relay-retry-sync"
        const val MANUAL_SYNC = "relay-manual-sync"
    }
}

class LocalQueueIdStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun existingQueueId(): String? = preferences.getString(LOCAL_QUEUE_ID, null)

    fun getOrCreateQueueId(): String =
        existingQueueId() ?: UUID.randomUUID().toString().also {
            preferences.edit().putString(LOCAL_QUEUE_ID, it).apply()
        }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val PREFERENCES = "secure_messenger_queue"
        const val LOCAL_QUEUE_ID = "local_queue_id"
    }
}

/**
 * Executes opaque relay delivery only. It must never log message bodies, keys, nonces, or envelopes.
 */
class RelaySyncWorker(
    applicationContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(applicationContext, workerParameters) {
    override suspend fun doWork(): Result {
        val database = SecureMessengerDatabase.create(applicationContext)
        val sessionsToErase = mutableListOf<app.securemessenger.crypto.EstablishedSession>()
        return try {
            if (database.dao().getProfile() == null) return Result.success()
            val repository = SecureLocalRepository(database.dao(), AndroidKeystoreVaultCipher())
            val transport = RelayHttpTransportClient(RelayServerSettings(applicationContext).currentUrl())
            val sessionManager = SimplifiedSessionManager(StandardAead())
            val executor = DeliverySyncExecutor(
                outgoing = OutgoingDeliveryProcessor(repository, transport),
                incoming = IncomingDeliveryProcessor(repository, transport, sessionManager),
                queueIdProvider = { LocalQueueIdStore(applicationContext).existingQueueId() },
                receiveBindingsProvider = {
                    database.dao().getContactsSnapshot().mapNotNull { contact ->
                        repository.loadSession(contact.contactId)?.also(sessionsToErase::add)?.let {
                            ReceiveBinding(contact.contactId, it)
                        }
                    }
                },
            )
            val result = executor.synchronizeNow()
            if (result.outgoingResults.any { it == OutgoingAttemptResult.RETRY_SCHEDULED }) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        } finally {
            sessionsToErase.forEach { it.rootKey.fill(0) }
            database.close()
        }
    }
}
