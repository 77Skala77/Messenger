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
import java.util.UUID
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
    suspend fun acceptDeliveryReceipt(
        contactId: String,
        session: EstablishedSession,
        message: EncryptedSessionMessage,
        receipt: DeliveryReceipt,
    ): DeliveryReceiptAcceptance
    suspend fun queueOutgoingControlMessage(
        messageId: String,
        contactId: String,
        session: EstablishedSession,
        message: EncryptedSessionMessage,
    )
}

enum class DeliveryReceiptAcceptance {
    STATUS_UPDATED,
    DUPLICATE_SUPPRESSED,
    UNKNOWN_MESSAGE,
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
    DELIVERY_RECEIPT_ACCEPTED,
    DELIVERY_RECEIPT_DUPLICATE_SUPPRESSED,
    DELIVERY_RECEIPT_UNKNOWN_MESSAGE,
}

data class IncomingProcessResult(
    val result: IncomingResult,
    val queuedReceiptMessageId: String? = null,
)

class IncomingDeliveryProcessor(
    private val persistence: DeliveryPersistence,
    private val transport: TransportClient,
    private val sessionManager: SessionManager,
    private val now: () -> Long = System::currentTimeMillis,
    private val messageIdGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun fetchAndProcess(queueId: String, sessions: List<ReceiveBinding>): List<IncomingResult> =
        fetchAndProcessDetailed(queueId, sessions).map { it.result }

    suspend fun fetchAndProcessDetailed(queueId: String, sessions: List<ReceiveBinding>): List<IncomingProcessResult> =
        transport.fetchMessages(queueId).map { processFetchedDetailed(it, sessions) }

    suspend fun processFetched(blob: RelayEncryptedBlob, sessions: List<ReceiveBinding>): IncomingResult {
        return processFetchedDetailed(blob, sessions).result
    }

    suspend fun processFetchedDetailed(
        blob: RelayEncryptedBlob,
        sessions: List<ReceiveBinding>,
    ): IncomingProcessResult {
        val wireMessage = try {
            SessionMessageCodec.decode(blob.opaqueCiphertext)
        } catch (_: Exception) {
            return IncomingProcessResult(IncomingResult.UNAUTHENTICATED)
        }
        sessions.forEach { binding ->
            val authenticatedPlaintext = try {
                sessionManager.decryptStoredMessage(binding.session, wireMessage, outgoing = false)
            } catch (_: Exception) {
                return@forEach
            }
            val payload = try {
                MessagePayloadCodec.decode(authenticatedPlaintext)
            } catch (_: Exception) {
                authenticatedPlaintext.fill(0)
                return IncomingProcessResult(IncomingResult.UNAUTHENTICATED)
            } finally {
                authenticatedPlaintext.fill(0)
            }
            return when (payload) {
                is MessagePayload.Text -> processTextPayload(blob, binding, wireMessage)
                is MessagePayload.Receipt -> processReceiptPayload(blob, binding, wireMessage, payload.value)
            }
        }
        return IncomingProcessResult(IncomingResult.UNAUTHENTICATED)
    }

    private suspend fun processTextPayload(
        blob: RelayEncryptedBlob,
        binding: ReceiveBinding,
        wireMessage: EncryptedSessionMessage,
    ): IncomingProcessResult {
        val accepted = persistence.acceptIncoming(
            messageId = blob.messageId,
            contactId = binding.contactId,
            session = binding.session,
            message = wireMessage,
        )
        val receiptMessageId = if (accepted) {
            runCatching { queueDeliveryReceipt(blob.messageId, binding) }.getOrNull()
        } else {
            null
        }
        // Local durable save or a durable replay marker exists before relay deletion.
        transport.deleteMessage(blob.messageId)
        return IncomingProcessResult(
            result = if (accepted) IncomingResult.ACCEPTED else IncomingResult.AUTHENTICATED_DUPLICATE,
            queuedReceiptMessageId = receiptMessageId,
        )
    }

    private suspend fun processReceiptPayload(
        blob: RelayEncryptedBlob,
        binding: ReceiveBinding,
        wireMessage: EncryptedSessionMessage,
        receipt: DeliveryReceipt,
    ): IncomingProcessResult {
        val result = when (persistence.acceptDeliveryReceipt(binding.contactId, binding.session, wireMessage, receipt)) {
            DeliveryReceiptAcceptance.STATUS_UPDATED -> IncomingResult.DELIVERY_RECEIPT_ACCEPTED
            DeliveryReceiptAcceptance.DUPLICATE_SUPPRESSED -> IncomingResult.DELIVERY_RECEIPT_DUPLICATE_SUPPRESSED
            DeliveryReceiptAcceptance.UNKNOWN_MESSAGE -> IncomingResult.DELIVERY_RECEIPT_UNKNOWN_MESSAGE
        }
        // Receipt replay marker is durable before relay deletion; receipts are never stored as chat text.
        transport.deleteMessage(blob.messageId)
        return IncomingProcessResult(result)
    }

    private suspend fun queueDeliveryReceipt(originalMessageId: String, binding: ReceiveBinding): String {
        val receipt = DeliveryReceipt(
            originalMessageId = originalMessageId,
            conversationId = binding.session.conversationId,
            originalSenderDeviceId = binding.session.remoteDeviceId,
            originalRecipientDeviceId = binding.session.localDeviceId,
            timestampEpochMillis = now(),
        )
        val payload = MessagePayloadCodec.encodeDeliveryReceipt(receipt)
        val encrypted = try {
            sessionManager.encryptMessage(binding.session, payload, now() / TIMESTAMP_BUCKET_MILLIS)
        } finally {
            payload.fill(0)
        }
        val receiptMessageId = messageIdGenerator()
        persistence.queueOutgoingControlMessage(receiptMessageId, binding.contactId, binding.session, encrypted)
        return receiptMessageId
    }

    private companion object {
        const val TIMESTAMP_BUCKET_MILLIS = 60_000L
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
        val incomingDetails = incoming.fetchAndProcessDetailed(queueId, receiveBindingsProvider())
        val receiptOutgoingResults = incomingDetails.mapNotNull { it.queuedReceiptMessageId }
            .map { outgoing.attemptMessage(it) }
        return DeliverySyncResult(
            outgoingResults = outgoingResults,
            incomingResults = incomingDetails.map { it.result },
            receiptOutgoingResults = receiptOutgoingResults,
            queuedDeliveryReceiptCount = incomingDetails.count { it.queuedReceiptMessageId != null },
        )
    }
}

data class DeliverySyncResult(
    val outgoingResults: List<OutgoingAttemptResult>,
    val incomingResults: List<IncomingResult>,
    val receiptOutgoingResults: List<OutgoingAttemptResult> = emptyList(),
    val queuedDeliveryReceiptCount: Int = 0,
)
