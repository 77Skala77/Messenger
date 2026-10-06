package app.securemessenger.signalprotocol

import app.securemessenger.domain.RelayEncryptedBlob
import app.securemessenger.domain.RelayQueueCredentials
import app.securemessenger.domain.TransportClient
import app.securemessenger.signalstore.ConversationV2Store
import app.securemessenger.signalstore.ProtocolTransactionRunner
import app.securemessenger.signalstore.V2ChatSummary
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

    /**
     * The texts of a conversation the user is looking at. Its incoming texts are marked seen in the
     * same step that reads them, so a text that arrives a moment later stays unread until it is
     * actually shown (DEC-029). The mark is local only: nothing about reading is sent.
     */
    fun textsMarkingRead(contactId: String): List<V2StoredMessage> = atomically {
        store.markRead(contactId)
        store.texts(contactId)
    }

    /** One row per contact for the chat list: its newest text and its count of unseen texts (DEC-029). */
    fun chatSummaries(): List<V2ChatSummary> = atomically {
        val unread = store.unreadCounts()
        store.contacts().map { contact ->
            V2ChatSummary(contact, store.lastText(contact.contactId), unread[contact.contactId] ?: 0)
        }
    }

    /**
     * How many incoming texts of each contact and request are unseen (DEC-029), without reading any
     * text: compared before and after a background sync to decide what to notify (DEC-031).
     */
    fun unreadCounts(): Map<String, Int> = atomically { store.unreadCounts() }

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
     * Uploads the sealed bundle of the invitation the screen shows (the newest unused, unexpired
     * one) again: the person's "try again" after a failed or unknown upload (audit L2). The bundle
     * is public and sealed, so sending it twice reveals nothing; a scanner keeps only the blob that
     * matches the QR's digest.
     */
    suspend fun retryCurrentInvitationUpload() {
        val invitation = atomically { store.latestUsableInvitation(now()) }
            ?: throw ConversationV2Exception("No usable invitation")
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

    /**
     * Accepts a contact request. [comparedSafetyNumber] is the number the person compared and found
     * matching (audit H1, DEC-030), or null if they accept without comparing. The contact is marked
     * verified only if that number is still the request's number; if it is not, nothing is accepted.
     */
    suspend fun acceptRequest(requestId: String, localName: String, comparedSafetyNumber: String? = null) {
        atomically {
            val request = store.request(requestId) ?: throw ConversationV2Exception("Unknown request")
            val verifiedAt = comparedSafetyNumber?.let { compared ->
                requireSameSafetyNumber(request.requestId, compared)
                now()
            }
            val contact = V2Contact(
                contactId = request.requestId,
                localDisplayName = localName,
                identityKey = request.identityKey,
                peerQueueId = request.peerQueueId,
                peerRoutingTagHex = request.peerRoutingTagHex,
                localRoutingTagHex = request.localRoutingTagHex,
                state = V2ContactState.ACTIVE,
                createdAtEpochMillis = now(),
                verifiedAtEpochMillis = verifiedAt,
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

    /**
     * The person confirmed that [comparedSafetyNumber], the number they saw for this contact, matches
     * the one on the contact's phone (audit H1, DEC-030). Marks the contact verified only if that is
     * still its number and the contact is not blocked; otherwise throws and changes nothing. Allowed
     * while waiting for acceptance: both identity keys, and so the number, are already final.
     */
    fun markVerified(contactId: String, comparedSafetyNumber: String) = atomically {
        val contact = store.contact(contactId) ?: throw ConversationV2Exception("Unknown contact")
        if (contact.state == V2ContactState.KEY_CHANGED) throw ConversationV2Exception("Contact is blocked")
        requireSameSafetyNumber(contactId, comparedSafetyNumber)
        check(store.setContactVerified(contactId, now()))
    }

    /**
     * Forgets envelope digests older than [ACCEPTED_DIGEST_RETENTION_MILLIS] (second review I5). They
     * only spare a decryption for byte-identical re-deliveries, which the relay drops after a day; a
     * replay of a forgotten envelope is still rejected by libsignal and the inner message IDs.
     */
    fun forgetOldEnvelopeDigests(): Int = atomically { store.forgetAcceptedEnvelopesBefore(now() - ACCEPTED_DIGEST_RETENTION_MILLIS) }

    /** Removes the verified mark; the person can set it again after a new comparison. */
    fun clearVerified(contactId: String) = atomically {
        if (!store.setContactVerified(contactId, null)) throw ConversationV2Exception("Unknown contact")
    }

    /**
     * The number the person compared must be the number of the identity stored now: a stale screen,
     * or any other mix-up, must never mark a contact whose number the person did not see.
     */
    private fun requireSameSafetyNumber(contactOrRequestId: String, compared: String) {
        val current = sessions.safetyNumber(contactOrRequestId) ?: throw ConversationV2Exception("Unknown identity")
        val comparedDigits = compared.filter(Char::isDigit)
        if (comparedDigits.isEmpty() || comparedDigits != current.filter(Char::isDigit)) {
            throw ConversationV2Exception("Safety number changed")
        }
    }

    /**
     * Queues a text for a contact and tries to send it. Returns the local message ID. [replyToInnerIdHex]
     * names a text of this chat (either direction) that this one answers (DEC-035); a text this
     * phone does not have is refused, so a reply never points at something the person did not see.
     */
    suspend fun sendText(contactId: String, text: String, replyToInnerIdHex: String? = null): String {
        val messageId = atomically {
            val contact = store.contact(contactId) ?: throw ConversationV2Exception("Unknown contact")
            if (contact.state == V2ContactState.KEY_CHANGED) throw ConversationV2Exception("Contact is blocked")
            val replyTo = replyToInnerIdHex?.let { innerId ->
                if (!store.hasText(contactId, innerId)) throw ConversationV2Exception("Unknown message")
                RandomId.fromHex(innerId)
            }
            enqueue(contact, InnerPayloadV2.Text(RandomId.random(random), now(), text, replyTo), visibleText = text)
        }
        sendDue()
        return messageId
    }

    // --- Local chat management (DEC-035). Nothing here is sent: the contact is never told. ---

    /** Pins a chat to the top of the list (newest pin first), or unpins it. A local mark only. */
    fun setPinned(contactId: String, pinned: Boolean) = atomically {
        if (!store.setContactPinned(contactId, if (pinned) now() else null)) throw ConversationV2Exception("Unknown contact")
    }

    /**
     * Silences a chat's notifications until [mutedUntilEpochMillis] ([MUTED_FOREVER] for good), or
     * lets them through again (null). Its texts still arrive and are still counted.
     */
    fun setMuted(contactId: String, mutedUntilEpochMillis: Long?) = atomically {
        if (!store.setContactMuted(contactId, mutedUntilEpochMillis)) throw ConversationV2Exception("Unknown contact")
    }

    /**
     * The person marked the whole chat as read from the chat list (DEC-035). Like the "seen" mark it
     * sets (DEC-029), it stays on this phone: nothing about reading is ever sent.
     */
    fun markChatRead(contactId: String): Int = atomically { store.markRead(contactId) }

    /**
     * Deletes this chat's texts on this phone. Texts still waiting to be sent stay until they are
     * sent: each one holds a ratchet step that was already committed, and dropping it would leave a
     * gap the contact's phone waits on. The contact, the session and the receipts are untouched.
     */
    fun clearHistory(contactId: String): Int = atomically {
        store.contact(contactId) ?: throw ConversationV2Exception("Unknown contact")
        store.clearTexts(contactId)
    }

    /**
     * Deletes a contact for good (DEC-035): its texts, everything still queued for it, the invitation
     * it answered, its session and its stored identity. Nothing is sent; the contact is not told.
     * Whatever it sends afterwards finds no route and is deleted unread; writing to each other again
     * takes a new invitation, and its identity is then trusted afresh, like any new contact.
     */
    fun deleteContact(contactId: String) = atomically {
        store.contact(contactId) ?: throw ConversationV2Exception("Unknown contact")
        // Its pre-keys went when its HELLO used it; the row still holds the encrypted QR text.
        store.invitationUsedBy(contactId)?.let { store.deleteInvitation(it.invitationId) }
        store.deleteMessagesFor(contactId)
        check(store.deleteContact(contactId)) { "Contact vanished during deletion" }
        sessions.forget(contactId)
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
        var burned: V2Invitation? = null
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
                                helloForInvitation(route.invitation, envelope).also { burned = route.invitation }
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
        // An accepted HELLO used the invitation up (committed): its sealed bundle goes too, so whoever
        // scans the code later is told at once that it no longer works (second review I2).
        if (result.authenticated) burned?.let { withdrawBundle(it) }
        return result
    }

    /**
     * Deletes a used invitation's sealed bundle from its mailbox, best effort: the bundle holds only
     * public keys, and if the relay cannot be reached it expires there with the invitation. Whoever
     * scans the code afterwards finds nothing and is told the invitation no longer works; the
     * invitation itself was single use all along (DEC-026).
     */
    private suspend fun withdrawBundle(invitation: V2Invitation) {
        try {
            val mailbox = InvitationV2.decodeCode(invitation.codeText).mailbox
            transport.fetchMessages(mailbox)
                .filter { it.opaqueCiphertext.contentEquals(invitation.sealedBundle) }
                .forEach { transport.deleteMessage(mailbox, it.messageId) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The verdict stands either way; a later scan then waits in vain, as before.
        }
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
                if (store.storeIncomingText(
                        contact.contactId,
                        payload.messageId.toHex(),
                        payload.text,
                        shownTimeOf(payload.sentAtEpochMillis, now(), store.latestIncomingTextTime(contact.contactId)),
                        payload.replyTo?.toHex(),
                    )
                ) {
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
                    store.storeIncomingText(
                        request.requestId,
                        payload.messageId.toHex(),
                        payload.text,
                        shownTimeOf(payload.sentAtEpochMillis, now(), store.latestIncomingTextTime(request.requestId)),
                        payload.replyTo?.toHex(),
                    )
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
            // The very time the payload carries: the contact's phone sorts this text by it (DEC-034).
            payload.sentAtEpochMillis,
            (payload as? InnerPayloadV2.Text)?.replyTo?.toHex(),
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

        /** Envelope digests are kept this long (second review I5): far beyond the relay's one-day lifetime. */
        const val ACCEPTED_DIGEST_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000

        /** A sync stops after this many pages; the rest waits for the next one. */
        private const val MAX_PAGES_PER_SYNC = 10

        /** At most this many rejected envelopes wait for the next sync behind a failed one (V2-01). */
        private const val MAX_DEFERRED_PER_SYNC = 10

        /** A sender's own time is believed only this far before the moment its text arrived (DEC-034). */
        const val SENDER_TIME_WINDOW_MILLIS = 60_000L

        /** [setMuted] with this silences a chat until it is unmuted. */
        const val MUTED_FOREVER = Long.MAX_VALUE

        /**
         * When an incoming text is shown, and so where it sorts (owner decision, DEC-034): at the time
         * its sender's app put in it, so both phones show a conversation in the same order, if that time
         * lies at most [SENDER_TIME_WINDOW_MILLIS] before its arrival here; otherwise at its arrival.
         * Never before [notBefore], the time of the contact's previous text here: one that came late
         * (and so shows at its arrival) must not end up below a later one that came in time.
         *
         * The time is inside the authenticated payload, so the relay cannot touch it; the contact can
         * only move its own text up to a minute back, never ahead of its arrival or into the future.
         */
        fun shownTimeOf(sentAtEpochMillis: Long, receivedAtEpochMillis: Long, notBefore: Long? = null): Long {
            val believed = sentAtEpochMillis <= receivedAtEpochMillis &&
                receivedAtEpochMillis - sentAtEpochMillis <= SENDER_TIME_WINDOW_MILLIS
            val shown = if (believed) sentAtEpochMillis else receivedAtEpochMillis
            return if (notBefore != null && notBefore > shown) minOf(notBefore, receivedAtEpochMillis) else shown
        }

        /**
         * Runs [block] under the process-wide protocol lock, so no protocol step can commit while it
         * runs. For work outside the pipeline that must not interleave with it: the local wipe, and
         * the profile check before a pipeline (and possibly a new libsignal identity) is created.
         * [block] must be blocking code: the lock cannot be held across a suspension point.
         */
        fun <T> exclusively(block: () -> T): T = PROTOCOL_LOCK.withLock(block)
    }
}
