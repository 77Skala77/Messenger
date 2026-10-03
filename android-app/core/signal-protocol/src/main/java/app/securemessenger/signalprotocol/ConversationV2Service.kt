package app.securemessenger.signalprotocol

import app.securemessenger.domain.RelayEncryptedBlob
import app.securemessenger.domain.RelayQueueCredentials
import app.securemessenger.domain.TransportClient
import app.securemessenger.signalstore.ConversationV2Store
import app.securemessenger.signalstore.ProtocolTransactionRunner
import app.securemessenger.signalstore.V2Contact
import app.securemessenger.signalstore.V2ContactRequest
import app.securemessenger.signalstore.V2ContactState
import app.securemessenger.signalstore.V2Invitation
import app.securemessenger.signalstore.V2MessageKind
import app.securemessenger.signalstore.V2OutgoingEnvelope
import app.securemessenger.signalstore.V2Route
import app.securemessenger.signalstore.V2StoredMessage
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CancellationException
import org.signal.libsignal.protocol.message.CiphertextMessage

enum class IncomingV2Result {
    /** A text message was stored; a receipt was queued if the sender is an active contact. */
    TEXT_ACCEPTED,

    /** A HELLO answered one of our invitations; a person must accept or decline the request. */
    REQUEST_RECEIVED,

    /** The contact accepted our HELLO. */
    CONTACT_ACCEPTED,
    RECEIPT_APPLIED,

    /** Byte-identical to an envelope that was already accepted. */
    DUPLICATE,

    /** Authenticated but not acted on (unexpected kind, unknown receipt, repeated inner ID). */
    AUTHENTICATED_IGNORED,

    /** No contact, request, or unused invitation has this routing tag. Deleted from the relay (DEC-026). */
    UNROUTABLE,

    /**
     * Malformed, forged, replayed at the protocol level, from a blocked contact, or anything but a
     * HELLO with exactly its invitation's pre-keys. Nothing it caused is kept, and it is deleted
     * from the relay (DEC-026).
     */
    UNAUTHENTICATED,
}

class ConversationV2Exception(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The v2 conversation pipeline (DEC-019): invitations, one-sided pairing with an explicit request,
 * messages, and receipts over libsignal sessions and the untrusted relay.
 *
 * Every protocol step runs under one process-wide lock and inside one storage transaction together
 * with the libsignal store writes it causes (DEC-019 T2). An envelope that is not accepted commits
 * nothing (the transaction is rolled back). The relay copy of an envelope is deleted only once it
 * reached a verdict: after the accepting transaction committed, or after a rejection; a storage
 * failure reaches no verdict and keeps it (DEC-026). Methods block on storage: call them off the
 * main thread.
 *
 * The app's only conversation path. Debug builds only until the Phase 5 independent review
 * (DEC-021); release builds never construct it.
 */
class ConversationV2Service(
    private val sessions: SignalSessions,
    private val invitationKeys: InvitationPreKeyFactory,
    private val store: ConversationV2Store,
    private val transactions: ProtocolTransactionRunner,
    private val transport: TransportClient,
    /** This device's receive queue; contacts only ever learn its ID (DEC-024). */
    private val localReceiveQueue: RelayQueueCredentials,
    private val localDisplayName: () -> String,
    private val random: SecureRandom = SecureRandom(),
    private val now: () -> Long = System::currentTimeMillis,
    private val newLocalId: () -> String = { UUID.randomUUID().toString() },
) {
    // --- Read models for the UI. Blocking; take the same lock so they never see half a step. ---

    fun contacts(): List<V2Contact> = atomically { store.contacts() }

    fun requests(): List<V2ContactRequest> = atomically { store.requests() }

    fun texts(contactId: String): List<V2StoredMessage> = atomically { store.texts(contactId) }

    /** Texts held for a pending request (never receipted before acceptance). */
    fun heldTexts(requestId: String): List<V2StoredMessage> = atomically { store.incomingTexts(requestId) }

    /** The newest unused, unexpired invitation's QR text, or null. */
    fun currentInvitationCode(): String? = atomically { store.latestUsableInvitation(now())?.codeText }

    /** 60-digit safety number for a contact or a pending request, or null if unknown. */
    fun safetyNumber(contactOrRequestId: String): String? = atomically { sessions.safetyNumber(contactOrRequestId) }

    /** Creates and uploads a fresh invitation; returns the text for the QR code. */
    suspend fun createInvitation(): String {
        val invitation = atomically {
            val preKeys = invitationKeys.create()
            val routingTag = RandomId.random(random)
            val createdAt = now()
            val bundle = InvitationBundle(
                preKeys = preKeys,
                receiveQueueId = localReceiveQueue.queueId,
                routingTag = routingTag,
                expiresAtEpochMillis = createdAt + InvitationV2.DEFAULT_LIFETIME_MILLIS,
            )
            val published = InvitationV2.publish(bundle, random)
            V2Invitation(
                invitationId = newLocalId(),
                localRoutingTagHex = routingTag.toHex(),
                signedPreKeyId = preKeys.signedPreKeyId,
                oneTimePreKeyId = preKeys.oneTimePreKeyId,
                kyberPreKeyId = preKeys.kyberPreKeyId,
                mailboxQueueId = published.mailboxQueueId,
                codeText = published.code.encode(),
                sealedBundle = published.sealedBundle,
                createdAtEpochMillis = createdAt,
                expiresAtEpochMillis = bundle.expiresAtEpochMillis,
            ).also(store::saveInvitation)
        }
        uploadInvitation(invitation)
        return invitation.codeText
    }

    /** Re-uploads a stored invitation's sealed bundle, e.g. after the first upload failed. */
    suspend fun retryInvitationUpload(invitationId: String) {
        val invitation = atomically { store.invitation(invitationId) }
            ?: throw ConversationV2Exception("Unknown invitation")
        uploadInvitation(invitation)
    }

    /**
     * Scans a peer's invitation: fetches and verifies the committed bundle, starts the session,
     * stores the contact as awaiting acceptance, and queues HELLO. Returns the new contact ID.
     * Throws [InvitationRejectedException] or [ConversationV2Exception] without changing state.
     */
    suspend fun acceptInvitation(codeText: String, localName: String): String {
        val code = InvitationV2.decodeCode(codeText.trim())
        val candidates = transport.fetchMessages(code.mailbox).map { it.opaqueCiphertext }
        val bundle = InvitationV2.open(code, candidates, now())
        val identity = bundle.preKeys.identityKey
        val contactId = atomically {
            if (identity == sessions.localIdentityKey()) throw ConversationV2Exception("Own invitation")
            if (store.contactWithIdentity(identity.serialize()) != null) throw ConversationV2Exception("Already paired")
            val contactId = newLocalId()
            val localTag = RandomId.random(random)
            sessions.startSession(contactId, bundle.preKeys.toPreKeyBundle())
            val contact = V2Contact(
                contactId = contactId,
                localDisplayName = localName,
                identityKey = identity.serialize(),
                peerQueueId = bundle.receiveQueueId,
                peerRoutingTagHex = bundle.routingTag.toHex(),
                localRoutingTagHex = localTag.toHex(),
                state = V2ContactState.AWAITING_ACCEPTANCE,
                createdAtEpochMillis = now(),
            )
            store.saveContact(contact)
            val displayName = localDisplayName().takeIf(InnerPayloadV2Codec::isAcceptableDisplayName).orEmpty()
            enqueue(contact, InnerPayloadV2.Hello(RandomId.random(random), now(), localReceiveQueue.queueId, localTag, displayName))
            contactId
        }
        sendDue()
        return contactId
    }

    suspend fun acceptRequest(requestId: String, localName: String) {
        atomically {
            val request = store.request(requestId) ?: throw ConversationV2Exception("Unknown request")
            val contact = V2Contact(
                contactId = request.requestId,
                localDisplayName = localName,
                identityKey = request.identityKey,
                peerQueueId = request.peerQueueId,
                peerRoutingTagHex = request.peerRoutingTagHex,
                localRoutingTagHex = request.localRoutingTagHex,
                state = V2ContactState.ACTIVE,
                createdAtEpochMillis = now(),
            )
            store.saveContact(contact)
            store.deleteRequest(requestId)
            enqueue(contact, InnerPayloadV2.Accept(RandomId.random(random), now()))
            // Receipts were withheld while the request was pending.
            store.incomingTexts(requestId).forEach { held -> enqueue(contact, receiptFor(held.innerMessageIdHex)) }
        }
        sendDue()
    }

    /** Declining removes the request, its held messages, and its session and identity. */
    suspend fun declineRequest(requestId: String) {
        atomically {
            store.request(requestId) ?: throw ConversationV2Exception("Unknown request")
            store.deleteRequest(requestId)
            store.deleteMessagesFor(requestId)
            sessions.forget(requestId)
        }
    }

    /** Queues a text for a contact and tries to send it. Returns the local message ID. */
    suspend fun sendText(contactId: String, text: String): String {
        val messageId = atomically {
            val contact = store.contact(contactId) ?: throw ConversationV2Exception("Unknown contact")
            if (contact.state == V2ContactState.KEY_CHANGED) throw ConversationV2Exception("Contact is blocked")
            enqueue(contact, InnerPayloadV2.Text(RandomId.random(random), now(), text), visibleText = text)
        }
        sendDue()
        return messageId
    }

    /**
     * Fetches this device's queue page by page (the relay returns the oldest [RELAY_PAGE_SIZE]),
     * processes every envelope, then sends what is due (receipts too). Every envelope that reaches a
     * verdict is deleted, so a full page means more may be waiting (DEC-026).
     *
     * An envelope that reaches no verdict (a storage failure, or an unexpected failure it caused)
     * stays on the relay and does not stop the others: one bad envelope must never stall the queue.
     * The first such failure is thrown at the end, after sending, so callers still see it (a lost
     * vault key, DEC-023).
     *
     * A rejection can depend on order: a text that follows its HELLO cannot be routed while that
     * HELLO is unprocessed. So once an envelope got no verdict, a later rejected envelope with the
     * same routing tag is not deleted in this sync but tried again in the next one, at most
     * [MAX_DEFERRED_PER_SYNC] of them, so junk cannot pile up behind a failing envelope (audit
     * V2-01, DEC-028).
     */
    suspend fun synchronize(): List<IncomingV2Result> {
        val results = ArrayList<IncomingV2Result>()
        var firstFailure: Throwable? = null
        val unsettledTags = HashSet<String>()
        val deferred = HashSet<String>()
        var pages = 0
        do {
            val page = transport.fetchMessages(localReceiveQueue)
            var decided = 0
            for (blob in page) {
                var tagHex: String? = null
                try {
                    val envelope = decodeOrNull(blob.opaqueCiphertext)
                    tagHex = envelope?.routingTag?.toHex()
                    val mayDefer = tagHex != null && tagHex in unsettledTags &&
                        (blob.messageId in deferred || deferred.size < MAX_DEFERRED_PER_SYNC)
                    val result = decide(blob, envelope, mayDefer)
                    if (result == null) {
                        deferred += blob.messageId
                    } else {
                        results += result
                        decided++
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    tagHex?.let(unsettledTags::add)
                    if (firstFailure == null) firstFailure = failure
                } catch (failure: AssertionError) {
                    // libsignal reports its own unexpected failures this way.
                    tagHex?.let(unsettledTags::add)
                    if (firstFailure == null) firstFailure = failure
                }
            }
            pages++
        } while (page.size >= RELAY_PAGE_SIZE && decided > 0 && pages < MAX_PAGES_PER_SYNC)
        sendDue()
        firstFailure?.let { failure ->
            throw failure as? Exception ?: ConversationV2Exception("An envelope could not be processed", failure)
        }
        return results
    }

    /**
     * Decides one envelope. Only an accepted envelope commits anything; whatever a rejected one
     * caused on the way is rolled back. The relay copy is deleted once there is a verdict, accepted
     * or not: a rejected envelope can never become valid, and keeping it would let anyone who knows
     * the queue ID fill it (DEC-026). A storage failure throws before that and keeps the copy.
     */
    suspend fun processIncoming(blob: RelayEncryptedBlob): IncomingV2Result =
        checkNotNull(decide(blob, decodeOrNull(blob.opaqueCiphertext), mayDefer = false))

    /** [processIncoming]; with [mayDefer], a rejected envelope stays on the relay and null is returned. */
    private suspend fun decide(blob: RelayEncryptedBlob, envelope: EnvelopeV2?, mayDefer: Boolean): IncomingV2Result? {
        val result = if (envelope == null) {
            IncomingV2Result.UNAUTHENTICATED
        } else {
            val digestHex = hexOf(EnvelopeV2Codec.digest(blob.opaqueCiphertext))
            try {
                atomically {
                    if (store.isEnvelopeAccepted(digestHex)) return@atomically IncomingV2Result.DUPLICATE
                    val outcome = when (val route = store.findRoute(envelope.routingTag.toHex())) {
                        null -> IncomingV2Result.UNROUTABLE
                        is V2Route.ToContact -> fromContact(route.contact, envelope)
                        is V2Route.ToRequest -> fromPendingRequest(route.request, envelope)
                        is V2Route.ToInvitation ->
                            // Expired is expired, even before the cleanup ran (second review R3).
                            if (now() > route.invitation.expiresAtEpochMillis + InvitationV2.CLOCK_SKEW_MILLIS) {
                                IncomingV2Result.UNROUTABLE
                            } else {
                                helloForInvitation(route.invitation, envelope)
                            }
                    }
                    if (!outcome.authenticated) throw Rejected(outcome)
                    store.recordAcceptedEnvelope(digestHex, now())
                    outcome
                }
            } catch (rejected: Rejected) {
                rejected.result
            }
        }
        if (mayDefer && !result.authenticated) return null
        transport.deleteMessage(localReceiveQueue, blob.messageId)
        return result
    }

    private fun decodeOrNull(bytes: ByteArray): EnvelopeV2? =
        try {
            EnvelopeV2Codec.decode(bytes)
        } catch (_: MalformedV2Exception) {
            null
        }

    /**
     * Sends due envelopes in order per contact: an envelope waits while an older one to the same
     * contact is unsent, so the relay holds a contact's messages in the order they were made and a
     * text never overtakes its HELLO. Failures are rescheduled with exponential backoff.
     */
    suspend fun sendDue(): Int {
        var sent = 0
        val waiting = HashSet<String>()
        for (outgoing in atomically { store.unsentOutgoing() }) {
            if (outgoing.contactId in waiting) continue
            val notBefore = outgoing.nextAttemptAtEpochMillis
            if (notBefore != null && notBefore > now()) {
                waiting += outgoing.contactId
                continue
            }
            val attempt = outgoing.attemptCount + 1
            try {
                // The relay assigns its own message ID; it is never used as a local key (DEC-020).
                transport.sendMessage(outgoing.peerQueueId, outgoing.envelope, now() + RELAY_TTL_MILLIS)
                atomically { store.recordSent(outgoing.messageId, attempt) }
                sent++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                atomically { store.recordFailed(outgoing.messageId, attempt, now() + retryDelay(attempt)) }
                waiting += outgoing.contactId
            }
        }
        return sent
    }

    /** Deletes expired, unused invitations and their pre-keys. */
    fun expireInvitations(): Int = atomically {
        val expired = store.expiredUnusedInvitations(now())
        expired.forEach { invitation ->
            invitationKeys.delete(invitation.signedPreKeyId, invitation.oneTimePreKeyId, invitation.kyberPreKeyId)
            store.deleteInvitation(invitation.invitationId)
        }
        expired.size
    }

    private suspend fun uploadInvitation(invitation: V2Invitation) {
        transport.sendMessage(invitation.mailboxQueueId, invitation.sealedBundle, invitation.expiresAtEpochMillis)
    }

    private fun fromContact(contact: V2Contact, envelope: EnvelopeV2): IncomingV2Result {
        if (contact.state == V2ContactState.KEY_CHANGED) return IncomingV2Result.UNAUTHENTICATED
        if (!isOwnSessionStart(envelope) { store.invitationUsedBy(contact.contactId) }) return IncomingV2Result.UNAUTHENTICATED
        val payload = when (val decrypted = decrypt(contact.contactId, envelope.ciphertext)) {
            Decrypted.Failed -> return IncomingV2Result.UNAUTHENTICATED
            Decrypted.Unparseable -> return IncomingV2Result.AUTHENTICATED_IGNORED
            is Decrypted.Payload -> decrypted.payload
        }
        return when (payload) {
            is InnerPayloadV2.Text -> {
                // Only the contact's app can encrypt to us, and it sends texts only after its side
                // accepted: a text therefore also completes a pairing we are waiting on.
                if (contact.state == V2ContactState.AWAITING_ACCEPTANCE) {
                    store.setContactState(contact.contactId, V2ContactState.ACTIVE)
                }
                if (store.storeIncomingText(contact.contactId, payload.messageId.toHex(), payload.text, now())) {
                    enqueue(contact, receiptFor(payload.messageId.toHex()))
                    IncomingV2Result.TEXT_ACCEPTED
                } else {
                    IncomingV2Result.AUTHENTICATED_IGNORED
                }
            }
            is InnerPayloadV2.Accept ->
                if (contact.state == V2ContactState.AWAITING_ACCEPTANCE) {
                    store.setContactState(contact.contactId, V2ContactState.ACTIVE)
                    IncomingV2Result.CONTACT_ACCEPTED
                } else {
                    IncomingV2Result.AUTHENTICATED_IGNORED
                }
            is InnerPayloadV2.Receipt ->
                if (store.markDelivered(contact.contactId, payload.originalMessageId.toHex())) {
                    IncomingV2Result.RECEIPT_APPLIED
                } else {
                    IncomingV2Result.AUTHENTICATED_IGNORED
                }
            is InnerPayloadV2.Hello -> IncomingV2Result.AUTHENTICATED_IGNORED
        }
    }

    /**
     * Once a session exists, the only PreKey messages that may still arrive are the peer's first
     * ones, sent before it heard back, which name the pre-keys of the invitation this pairing used.
     * Any other PreKey message would make libsignal start a new session from other pre-keys of ours,
     * spending another invitation; the scanner's side receives no PreKey messages at all (second
     * review R2). [pairingInvitation] is only looked up for a PreKey message.
     */
    private fun isOwnSessionStart(envelope: EnvelopeV2, pairingInvitation: () -> V2Invitation?): Boolean {
        if (envelope.ciphertext.type != CiphertextMessage.PREKEY_TYPE) return true
        val invitation = pairingInvitation() ?: return false
        val header = try {
            SignalSessions.readPreKeyHeader(envelope.ciphertext.bytes)
        } catch (_: SignalSessionException) {
            return false
        }
        return header.oneTimePreKeyId == invitation.oneTimePreKeyId && header.signedPreKeyId == invitation.signedPreKeyId
    }

    /** Texts sent before the request is accepted are held, without receipts. */
    private fun fromPendingRequest(request: V2ContactRequest, envelope: EnvelopeV2): IncomingV2Result {
        if (!isOwnSessionStart(envelope) { store.invitation(request.invitationId) }) return IncomingV2Result.UNAUTHENTICATED
        return when (val decrypted = decrypt(request.requestId, envelope.ciphertext)) {
            Decrypted.Failed -> IncomingV2Result.UNAUTHENTICATED
            Decrypted.Unparseable -> IncomingV2Result.AUTHENTICATED_IGNORED
            is Decrypted.Payload -> {
                val payload = decrypted.payload
                if (payload is InnerPayloadV2.Text &&
                    store.storeIncomingText(request.requestId, payload.messageId.toHex(), payload.text, now())
                ) {
                    IncomingV2Result.TEXT_ACCEPTED
                } else {
                    IncomingV2Result.AUTHENTICATED_IGNORED
                }
            }
        }
    }

    private fun helloForInvitation(invitation: V2Invitation, envelope: EnvelopeV2): IncomingV2Result {
        if (envelope.ciphertext.type != CiphertextMessage.PREKEY_TYPE) return IncomingV2Result.UNAUTHENTICATED
        val header = try {
            SignalSessions.readPreKeyHeader(envelope.ciphertext.bytes)
        } catch (_: SignalSessionException) {
            return IncomingV2Result.UNAUTHENTICATED
        }
        // The HELLO must use exactly this invitation's pre-keys; the tag alone is relay-visible.
        if (header.oneTimePreKeyId != invitation.oneTimePreKeyId || header.signedPreKeyId != invitation.signedPreKeyId) {
            return IncomingV2Result.UNAUTHENTICATED
        }
        val requestId = newLocalId()
        val hello = (decrypt(requestId, envelope.ciphertext) as? Decrypted.Payload)?.payload as? InnerPayloadV2.Hello
        // Only a HELLO opens an invitation, and only with its own Kyber pre-key too (the header names
        // just the EC ones; libsignal deleted the Kyber key it used). Anything else is rejected and
        // rolled back, so the invitation and its keys stay unused: a text that overtook its HELLO, or
        // a HELLO borrowing another invitation's Kyber key, burns nothing (Phase 5 F3, F6).
        if (hello == null || invitationKeys.hasKyberPreKey(invitation.kyberPreKeyId)) {
            return IncomingV2Result.UNAUTHENTICATED
        }
        // The one-time pre-keys are spent now: whatever follows, this invitation is burned. Its
        // signed pre-key goes too: later PreKey messages of this session use the session, not the
        // pre-keys (Phase 5 F8).
        check(store.markInvitationUsed(invitation.invitationId, requestId)) { "Invitation already used" }
        invitationKeys.delete(invitation.signedPreKeyId, invitation.oneTimePreKeyId, invitation.kyberPreKeyId)
        val identity = sessions.identityOf(requestId)
        if (identity == null || store.contactWithIdentity(identity.serialize()) != null) {
            sessions.forget(requestId)
            return IncomingV2Result.AUTHENTICATED_IGNORED
        }
        store.saveRequest(
            V2ContactRequest(
                requestId = requestId,
                invitationId = invitation.invitationId,
                identityKey = identity.serialize(),
                peerQueueId = hello.receiveQueueId,
                peerRoutingTagHex = hello.routingTag.toHex(),
                localRoutingTagHex = invitation.localRoutingTagHex,
                claimedDisplayName = hello.displayName,
                receivedAtEpochMillis = now(),
            ),
        )
        return IncomingV2Result.REQUEST_RECEIVED
    }

    /**
     * Any [SignalSessionException] means "not authenticated". That includes `UNTRUSTED_IDENTITY`:
     * libsignal checks the identity a PreKey message *claims* before authenticating anything, so
     * that result is attacker-controlled and must never change contact state (for example mark a
     * contact as key-changed). libsignal stores nothing when decryption fails.
     */
    private fun decrypt(address: String, ciphertext: SignalCiphertext): Decrypted {
        val plaintext = try {
            sessions.decrypt(address, ciphertext)
        } catch (_: SignalSessionException) {
            return Decrypted.Failed
        }
        return try {
            Decrypted.Payload(InnerPayloadV2Codec.decode(plaintext))
        } catch (_: MalformedV2Exception) {
            // Authenticated but unparseable: the ratchet step is kept and the content dropped, so the
            // same envelope is not retried forever.
            Decrypted.Unparseable
        } finally {
            plaintext.fill(0)
        }
    }

    private fun enqueue(contact: V2Contact, payload: InnerPayloadV2, visibleText: String? = null): String {
        val plaintext = InnerPayloadV2Codec.encode(payload)
        val ciphertext = try {
            sessions.encrypt(contact.contactId, plaintext)
        } finally {
            plaintext.fill(0)
        }
        val messageId = newLocalId()
        store.enqueueOutgoing(
            V2OutgoingEnvelope(
                messageId = messageId,
                contactId = contact.contactId,
                kind = kindOf(payload),
                innerMessageIdHex = payload.messageId.toHex(),
                peerQueueId = contact.peerQueueId,
                envelope = EnvelopeV2Codec.encode(EnvelopeV2(RandomId.fromHex(contact.peerRoutingTagHex), ciphertext)),
                attemptCount = 0,
            ),
            visibleText,
            now(),
        )
        return messageId
    }

    private fun receiptFor(innerMessageIdHex: String) =
        InnerPayloadV2.Receipt(RandomId.random(random), now(), RandomId.fromHex(innerMessageIdHex))

    private fun kindOf(payload: InnerPayloadV2) = when (payload) {
        is InnerPayloadV2.Hello -> V2MessageKind.HELLO
        is InnerPayloadV2.Accept -> V2MessageKind.ACCEPT
        is InnerPayloadV2.Text -> V2MessageKind.TEXT
        is InnerPayloadV2.Receipt -> V2MessageKind.RECEIPT
    }

    private fun retryDelay(attempt: Int): Long =
        (INITIAL_RETRY_DELAY_MILLIS shl (attempt - 1).coerceIn(0, 20)).coerceAtMost(MAX_RETRY_DELAY_MILLIS)

    private fun <T> atomically(block: () -> T): T = PROTOCOL_LOCK.withLock { transactions.inTransaction(block) }

    private val IncomingV2Result.authenticated: Boolean
        get() = this != IncomingV2Result.UNAUTHENTICATED && this != IncomingV2Result.UNROUTABLE

    /** Carries a rejection out of the transaction, which rolls back whatever the step wrote. */
    private class Rejected(val result: IncomingV2Result) : RuntimeException("Envelope rejected: $result")

    private sealed class Decrypted {
        object Failed : Decrypted()
        object Unparseable : Decrypted()
        class Payload(val payload: InnerPayloadV2) : Decrypted()
    }

    companion object {
        /** One lock for the whole process: the UI and the WorkManager worker share it. */
        private val PROTOCOL_LOCK = ReentrantLock()
        private const val RELAY_TTL_MILLIS = 24 * 60 * 60 * 1000L
        private const val INITIAL_RETRY_DELAY_MILLIS = 5_000L
        private const val MAX_RETRY_DELAY_MILLIS = 15 * 60_000L

        /** The relay returns at most this many of the oldest messages per fetch (DEC-026). */
        const val RELAY_PAGE_SIZE = 100

        /** A sync stops after this many pages; the rest waits for the next one. */
        private const val MAX_PAGES_PER_SYNC = 10

        /** At most this many rejected envelopes wait for the next sync behind a failed one (V2-01). */
        private const val MAX_DEFERRED_PER_SYNC = 10

        /**
         * Runs [block] under the process-wide protocol lock, so no protocol step can commit while it
         * runs. For work outside the pipeline that must not interleave with it: the local wipe, and
         * the profile check before a pipeline (and possibly a new libsignal identity) is created.
         * [block] must be blocking code: the lock cannot be held across a suspension point.
         */
        fun <T> exclusively(block: () -> T): T = PROTOCOL_LOCK.withLock(block)
    }
}
