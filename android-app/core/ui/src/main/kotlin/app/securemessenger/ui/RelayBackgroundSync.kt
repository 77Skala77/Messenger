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
import androidx.work.workDataOf
import app.securemessenger.crypto.SimplifiedSessionManager
import app.securemessenger.crypto.StandardAead
import app.securemessenger.delivery.DeliverySyncExecutor
import app.securemessenger.delivery.IncomingDeliveryProcessor
import app.securemessenger.delivery.IncomingResult
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

class WorkManagerDeliverySyncScheduler(
    context: Context,
    private val diagnostics: SafeDeliveryDiagnosticsStore = SafeDeliveryDiagnosticsStore(context.applicationContext),
) : DeliverySyncScheduler {
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
        diagnostics.setWorkSyncStatus(WorkSyncStatus.SCHEDULED)
    }

    override fun requestRetry() {
        val work = OneTimeWorkRequestBuilder<RelaySyncWorker>()
            .setConstraints(networkConstraint)
            .setInitialDelay(10, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(RETRY_SYNC, ExistingWorkPolicy.REPLACE, work)
        diagnostics.setWorkSyncStatus(WorkSyncStatus.RETRY_PENDING)
    }

    override fun synchronizeNow() {
        val work = OneTimeWorkRequestBuilder<RelaySyncWorker>()
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setInputData(workDataOf(RelaySyncWorker.MANUAL_SYNC_INPUT to true))
            .build()
        workManager.enqueueUniqueWork(MANUAL_SYNC, ExistingWorkPolicy.REPLACE, work)
        diagnostics.record(DeliveryTechnicalEvent.MANUAL_SYNC_STARTED)
        diagnostics.setWorkSyncStatus(WorkSyncStatus.SCHEDULED)
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
        val diagnostics = SafeDeliveryDiagnosticsStore(applicationContext)
        val manualSync = inputData.getBoolean(MANUAL_SYNC_INPUT, false)
        diagnostics.setWorkSyncStatus(WorkSyncStatus.RUNNING)
        val database = SecureMessengerDatabase.create(applicationContext)
        val sessionsToErase = mutableListOf<app.securemessenger.crypto.EstablishedSession>()
        return try {
            if (database.dao().getProfile() == null) {
                diagnostics.markSuccessfulSync()
                if (manualSync) diagnostics.record(DeliveryTechnicalEvent.MANUAL_SYNC_FINISHED)
                return Result.success()
            }
            val repository = SecureLocalRepository(database.dao(), AndroidKeystoreVaultCipher())
            val allowInsecureHttp = applicationContext.resources.getBoolean(R.bool.allow_insecure_relay_http)
            val relayUrl = RelayServerSettings(applicationContext, allowInsecureHttp).currentUrl()
            if (relayUrl.isBlank()) {
                diagnostics.setWorkSyncStatus(WorkSyncStatus.RETRY_PENDING)
                if (manualSync) diagnostics.record(DeliveryTechnicalEvent.MANUAL_SYNC_FINISHED)
                return Result.failure()
            }
            val transport = RelayHttpTransportClient(relayUrl)
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
            diagnostics.record(DeliveryTechnicalEvent.INCOMING_FETCH_STARTED)
            val result = executor.synchronizeNow(forceRetry = manualSync)
            if (result.outgoingResults.isNotEmpty()) {
                diagnostics.record(DeliveryTechnicalEvent.OUTGOING_RETRY_STARTED)
            }
            result.outgoingResults.forEach { resultItem ->
                when (resultItem) {
                    OutgoingAttemptResult.SENT -> {
                        diagnostics.record(DeliveryTechnicalEvent.OUTGOING_RETRY_SUCCEEDED)
                        diagnostics.record(DeliveryTechnicalEvent.OUTGOING_SENT)
                    }
                    OutgoingAttemptResult.RETRY_SCHEDULED -> {
                        diagnostics.record(DeliveryTechnicalEvent.OUTGOING_RETRY_FAILED)
                        diagnostics.record(DeliveryTechnicalEvent.OUTGOING_RETRY_SCHEDULED)
                    }
                    OutgoingAttemptResult.BLOCKED_BY_DEVICE_POLICY ->
                        diagnostics.record(DeliveryTechnicalEvent.DEVICE_BLOCKED_SEND)
                    OutgoingAttemptResult.NOT_FOUND -> Unit
                }
            }
            repeat(result.queuedDeliveryReceiptCount) {
                diagnostics.record(DeliveryTechnicalEvent.DELIVERY_RECEIPT_QUEUED)
            }
            result.receiptOutgoingResults.forEach { resultItem ->
                when (resultItem) {
                    OutgoingAttemptResult.SENT -> diagnostics.record(DeliveryTechnicalEvent.DELIVERY_RECEIPT_SENT)
                    OutgoingAttemptResult.RETRY_SCHEDULED ->
                        diagnostics.record(DeliveryTechnicalEvent.OUTGOING_RETRY_SCHEDULED)
                    OutgoingAttemptResult.BLOCKED_BY_DEVICE_POLICY ->
                        diagnostics.record(DeliveryTechnicalEvent.DEVICE_BLOCKED_SEND)
                    OutgoingAttemptResult.NOT_FOUND -> Unit
                }
            }
            result.incomingResults.forEach { resultItem ->
                when (resultItem) {
                    IncomingResult.ACCEPTED -> diagnostics.record(DeliveryTechnicalEvent.INCOMING_ACCEPTED)
                    IncomingResult.AUTHENTICATED_DUPLICATE ->
                        diagnostics.record(DeliveryTechnicalEvent.INCOMING_DUPLICATE_SUPPRESSED)
                    IncomingResult.DELIVERY_RECEIPT_ACCEPTED -> {
                        diagnostics.record(DeliveryTechnicalEvent.DELIVERY_RECEIPT_RECEIVED)
                        diagnostics.record(DeliveryTechnicalEvent.DELIVERY_STATUS_UPDATED)
                    }
                    IncomingResult.DELIVERY_RECEIPT_DUPLICATE_SUPPRESSED ->
                        diagnostics.record(DeliveryTechnicalEvent.DELIVERY_RECEIPT_DUPLICATE_SUPPRESSED)
                    IncomingResult.DELIVERY_RECEIPT_UNKNOWN_MESSAGE ->
                        diagnostics.record(DeliveryTechnicalEvent.DELIVERY_RECEIPT_RECEIVED)
                    IncomingResult.UNAUTHENTICATED -> Unit
                }
            }
            if (manualSync) diagnostics.record(DeliveryTechnicalEvent.MANUAL_SYNC_FINISHED)
            if ((result.outgoingResults + result.receiptOutgoingResults).any { it == OutgoingAttemptResult.RETRY_SCHEDULED }) {
                diagnostics.setWorkSyncStatus(WorkSyncStatus.RETRY_PENDING)
                Result.retry()
            } else {
                diagnostics.markSuccessfulSync()
                Result.success()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            diagnostics.setWorkSyncStatus(WorkSyncStatus.RETRY_PENDING)
            if (manualSync) diagnostics.record(DeliveryTechnicalEvent.MANUAL_SYNC_FINISHED)
            Result.retry()
        } finally {
            sessionsToErase.forEach { it.rootKey.fill(0) }
            database.close()
        }
    }

    companion object {
        const val MANUAL_SYNC_INPUT = "manual_sync"
    }
}
