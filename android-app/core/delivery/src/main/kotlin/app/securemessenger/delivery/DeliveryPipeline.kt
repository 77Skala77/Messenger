package app.securemessenger.delivery

import app.securemessenger.crypto.EncryptedSessionMessage
import app.securemessenger.crypto.EstablishedSession
import app.securemessenger.crypto.SessionManager
import app.securemessenger.crypto.SessionMessageCodec
import app.securemessenger.domain.Contact
import app.securemessenger.domain.ContactDevice
import app.securemessenger.domain.DeviceApprovalStatus
import app.securemessenger.domain.RelayEncryptedBlob
import app.securemessenger.domain.TransportClient
import kotlinx.coroutines.CancellationException

data class StoredOutgoingMessage(
    val messageId: String,
    val contact: Contact,
    val devices: List<ContactDevice>,
    val encryptedMessage: EncryptedSessionMessage,
    val attemptCount: Int,
)

interface DeliveryPersistence {
    suspend fun outgoingMessage(messageId: String): StoredOutgoingMessage?
    suspend fun dueOutgoingMessages(nowEpochMillis: Long): List<StoredOutgoingMessage>
    suspend fun retryableOutgoingMessages(): List<StoredOutgoingMessage>
    suspend fun recordSent(messageId: String, attemptCount: Int)
    suspend fun recordFailed(messageId: String, attemptCount: Int, nextAttemptAtEpochMillis: Long)
    suspend fun acceptIncoming(
        messageId: String,
        contactId: String,
        session: EstablishedSession,
        message: EncryptedSessionMessage,
    ): Boolean
}

data class DeliveryTarget(val queueId: String, val deviceId: String)

/**
 * This boundary exists for future device fan-out. The prototype ciphertext is created for one
 * remote session only, so additional devices must never be silently selected as recipients.
 */
interface DeviceFanOutPlanner {
    fun targetsForSingleDeviceCiphertext(contact: Contact, devices: List<ContactDevice>): List<DeliveryTarget>
}

class PrimarySessionOnlyFanOutPlanner : DeviceFanOutPlanner {
    override fun targetsForSingleDeviceCiphertext(
        contact: Contact,
        devices: List<ContactDevice>,
    ): List<DeliveryTarget> {
        val primary = devices.firstOrNull { it.identity.deviceId == contact.identity.deviceId }
            ?: return emptyList()
        return when (primary.approvalStatus) {
            DeviceApprovalStatus.PENDING_APPROVAL,
            DeviceApprovalStatus.REVOKED,
            DeviceApprovalStatus.KEY_CHANGED,
            -> emptyList()
            DeviceApprovalStatus.PRIMARY_UNVERIFIED,
            DeviceApprovalStatus.VERIFIED,
            -> listOf(DeliveryTarget(primary.queueId, primary.identity.deviceId))
        }
    }
}

data class RetryPolicy(
    val initialDelayMillis: Long = 5_000L,
    val maximumDelayMillis: Long = 15 * 60_000L,
) {
    fun delayAfterFailure(attemptCount: Int): Long {
        var delay = initialDelayMillis
        repeat((attemptCount - 1).coerceAtLeast(0)) {
            delay = (delay * 2).coerceAtMost(maximumDelayMillis)
        }
        return delay
    }
}

enum class OutgoingAttemptResult {
    SENT,
    RETRY_SCHEDULED,
    BLOCKED_BY_DEVICE_POLICY,
    NOT_FOUND,
}

class OutgoingDeliveryProcessor(
    private val persistence: DeliveryPersistence,
    private val transport: TransportClient,
    private val fanOutPlanner: DeviceFanOutPlanner = PrimarySessionOnlyFanOutPlanner(),
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun attemptMessage(messageId: String): OutgoingAttemptResult {
        val stored = persistence.outgoingMessage(messageId) ?: return OutgoingAttemptResult.NOT_FOUND
        val targets = fanOutPlanner.targetsForSingleDeviceCiphertext(stored.contact, stored.devices)
        if (targets.size != 1) return OutgoingAttemptResult.BLOCKED_BY_DEVICE_POLICY
        val attempt = stored.attemptCount + 1
        return try {
            val target = targets.single()
            transport.sendMessage(
                RelayEncryptedBlob(
                    messageId = stored.messageId,
                    queueId = target.queueId,
                    opaqueCiphertext = SessionMessageCodec.encode(stored.encryptedMessage),
                    expiresAtEpochMillis = now() + RELAY_TTL_MILLIS,
                ),
            )
            persistence.recordSent(messageId, attempt)
            OutgoingAttemptResult.SENT
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            persistence.recordFailed(messageId, attempt, now() + retryPolicy.delayAfterFailure(attempt))
            OutgoingAttemptResult.RETRY_SCHEDULED
        }
    }

    suspend fun retryDueMessages(): List<OutgoingAttemptResult> =
        persistence.dueOutgoingMessages(now()).map { attemptMessage(it.messageId) }

    suspend fun retryAllRetryableMessages(): List<OutgoingAttemptResult> =
        persistence.retryableOutgoingMessages().map { attemptMessage(it.messageId) }

    private companion object {
        const val RELAY_TTL_MILLIS = 24 * 60 * 60 * 1000L
    }
}

data class ReceiveBinding(val contactId: String, val session: EstablishedSession)

enum class IncomingResult {
    ACCEPTED,
    AUTHENTICATED_DUPLICATE,
    UNAUTHENTICATED,
}

class IncomingDeliveryProcessor(
    private val persistence: DeliveryPersistence,
    private val transport: TransportClient,
    private val sessionManager: SessionManager,
) {
    suspend fun fetchAndProcess(queueId: String, sessions: List<ReceiveBinding>): List<IncomingResult> =
        transport.fetchMessages(queueId).map { processFetched(it, sessions) }

    suspend fun processFetched(blob: RelayEncryptedBlob, sessions: List<ReceiveBinding>): IncomingResult {
        val wireMessage = try {
            SessionMessageCodec.decode(blob.opaqueCiphertext)
        } catch (_: Exception) {
            return IncomingResult.UNAUTHENTICATED
        }
        sessions.forEach { binding ->
            val authenticatedPlaintext = try {
                sessionManager.decryptStoredMessage(binding.session, wireMessage, outgoing = false)
            } catch (_: Exception) {
                return@forEach
            }
            authenticatedPlaintext.fill(0)
            val accepted = persistence.acceptIncoming(
                messageId = blob.messageId,
                contactId = binding.contactId,
                session = binding.session,
                message = wireMessage,
            )
            // Local durable save or a durable replay marker exists before relay deletion.
            transport.deleteMessage(blob.messageId)
            return if (accepted) IncomingResult.ACCEPTED else IncomingResult.AUTHENTICATED_DUPLICATE
        }
        return IncomingResult.UNAUTHENTICATED
    }
}

/**
 * Platform schedulers invoke this boundary without learning plaintext or cryptographic material.
 * Existing processors preserve authenticate-before-store and durable retry semantics.
 */
class DeliverySyncExecutor(
    private val outgoing: OutgoingDeliveryProcessor,
    private val incoming: IncomingDeliveryProcessor,
    private val queueIdProvider: suspend () -> String?,
    private val receiveBindingsProvider: suspend () -> List<ReceiveBinding>,
) {
    suspend fun synchronizeNow(forceRetry: Boolean = false): DeliverySyncResult {
        val outgoingResults = if (forceRetry) {
            outgoing.retryAllRetryableMessages()
        } else {
            outgoing.retryDueMessages()
        }
        val queueId = queueIdProvider() ?: return DeliverySyncResult(outgoingResults, emptyList())
        val incomingResults = incoming.fetchAndProcess(queueId, receiveBindingsProvider())
        return DeliverySyncResult(outgoingResults, incomingResults)
    }
}

data class DeliverySyncResult(
    val outgoingResults: List<OutgoingAttemptResult>,
    val incomingResults: List<IncomingResult>,
)
