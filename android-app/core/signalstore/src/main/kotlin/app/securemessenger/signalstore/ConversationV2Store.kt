package app.securemessenger.signalstore

/*
 * Neutral persistence boundary for v2 conversations (DEC-019). No libsignal types: routing tags
 * and message IDs are lowercase hex, keys are opaque bytes. Every method is blocking and is
 * called inside [ProtocolTransactionRunner.inTransaction] together with the libsignal store writes
 * of the same operation.
 *
 * v2 state lives in its own tables; the legacy prototype tables were dropped by the explicit reset
 * (DEC-021). Used by debug builds only until the independent review of Phase 5.
 */

/**
 * Runs [block] atomically: every store write inside it, including libsignal session updates,
 * commits together or not at all (DEC-019 T2). Implementations must run the block on the calling
 * thread, because libsignal calls its stores synchronously on that thread.
 */
interface ProtocolTransactionRunner {
    fun <T> inTransaction(block: () -> T): T
}

enum class V2ContactState {
    /** This device scanned the contact's invitation and sent HELLO; no ACCEPT yet. */
    AWAITING_ACCEPTANCE,

    /** Both sides know each other: this device accepted the request, or received ACCEPT. */
    ACTIVE,

    /** libsignal rejected a changed identity (DEC-018): nothing is sent or accepted until re-pairing. */
    KEY_CHANGED,
}

enum class V2MessageKind { HELLO, ACCEPT, TEXT, RECEIPT }

enum class V2MessageStatus { PENDING, SENT, DELIVERED, RECEIVED, FAILED }

/** An invitation this device published. Its pre-keys are deleted when it expires or is used. */
class V2Invitation(
    val invitationId: String,
    val localRoutingTagHex: String,
    val signedPreKeyId: Int,
    val oneTimePreKeyId: Int,
    val kyberPreKeyId: Int,
    val mailboxQueueId: String,
    /** QR text; it contains the seal key, so implementations encrypt it at rest. */
    val codeText: String,
    /** Public sealed bundle, kept so that a failed upload can be retried. */
    val sealedBundle: ByteArray,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val usedByRequestId: String? = null,
)

/**
 * A HELLO that answered one of this device's invitations and waits for a person to accept it.
 * [requestId] is also the libsignal address name, and becomes the contact ID on acceptance.
 */
class V2ContactRequest(
    val requestId: String,
    val invitationId: String,
    val identityKey: ByteArray,
    val peerQueueId: String,
    val peerRoutingTagHex: String,
    val localRoutingTagHex: String,
    /** Self-asserted by the sender and unverified; implementations encrypt it at rest. */
    val claimedDisplayName: String,
    val receivedAtEpochMillis: Long,
)

class V2Contact(
    /** Also the libsignal address name for this contact's session and identity. */
    val contactId: String,
    val localDisplayName: String,
    val identityKey: ByteArray,
    val peerQueueId: String,
    /** Tag this device puts on envelopes to the contact (assigned by the contact). */
    val peerRoutingTagHex: String,
    /** Tag the contact puts on envelopes to this device (assigned by this device). */
    val localRoutingTagHex: String,
    val state: V2ContactState,
    val createdAtEpochMillis: Long,
)

sealed class V2Route {
    class ToContact(val contact: V2Contact) : V2Route()
    class ToRequest(val request: V2ContactRequest) : V2Route()
    class ToInvitation(val invitation: V2Invitation) : V2Route()
}

/** A queued outgoing envelope. The ciphertext was produced once, at enqueue time, and is resent as is. */
class V2OutgoingEnvelope(
    val messageId: String,
    val contactId: String,
    val kind: V2MessageKind,
    val innerMessageIdHex: String,
    val peerQueueId: String,
    val envelope: ByteArray,
    val attemptCount: Int,
)

class V2StoredMessage(
    val messageId: String,
    val contactId: String,
    val outgoing: Boolean,
    val innerMessageIdHex: String,
    val text: String,
    val status: V2MessageStatus,
    val createdAtEpochMillis: Long,
)

interface ConversationV2Store {
    /** Contact first, then pending request, then an unused invitation with this tag. */
    fun findRoute(localRoutingTagHex: String): V2Route?

    fun isEnvelopeAccepted(digestHex: String): Boolean

    /** Returns false if the digest was already recorded. */
    fun recordAcceptedEnvelope(digestHex: String, acceptedAtEpochMillis: Long): Boolean

    fun saveInvitation(invitation: V2Invitation)

    fun invitation(invitationId: String): V2Invitation?

    /** The newest unused, unexpired invitation, to show again after a restart. */
    fun latestUsableInvitation(nowEpochMillis: Long): V2Invitation?

    /** Atomic: returns false if the invitation is unknown or already used. */
    fun markInvitationUsed(invitationId: String, requestId: String): Boolean

    fun expiredUnusedInvitations(nowEpochMillis: Long): List<V2Invitation>

    fun deleteInvitation(invitationId: String)

    fun saveRequest(request: V2ContactRequest)

    fun request(requestId: String): V2ContactRequest?

    fun requests(): List<V2ContactRequest>

    fun deleteRequest(requestId: String)

    fun saveContact(contact: V2Contact)

    fun contact(contactId: String): V2Contact?

    fun contactWithIdentity(identityKey: ByteArray): V2Contact?

    fun setContactState(contactId: String, state: V2ContactState)

    /** Returns false (and stores nothing) if this contact's message with that inner ID already exists. */
    fun storeIncomingText(
        contactId: String,
        innerMessageIdHex: String,
        text: String,
        receivedAtEpochMillis: Long,
    ): Boolean

    /** Incoming texts of a contact or request, oldest first. */
    fun incomingTexts(contactId: String): List<V2StoredMessage>

    /** Visible texts of a contact in both directions, oldest first (control messages excluded). */
    fun texts(contactId: String): List<V2StoredMessage>

    /** Every contact, oldest first. */
    fun contacts(): List<V2Contact>

    /** [visibleText] is stored (encrypted) for TEXT only; control messages have none. */
    fun enqueueOutgoing(envelope: V2OutgoingEnvelope, visibleText: String?, createdAtEpochMillis: Long)

    fun outgoing(messageId: String): V2OutgoingEnvelope?

    fun dueOutgoing(nowEpochMillis: Long): List<V2OutgoingEnvelope>

    fun recordSent(messageId: String, attemptCount: Int)

    fun recordFailed(messageId: String, attemptCount: Int, nextAttemptAtEpochMillis: Long)

    /** Marks this contact's outgoing TEXT with that inner ID delivered; false if there is none. */
    fun markDelivered(contactId: String, innerMessageIdHex: String): Boolean

    fun deleteMessagesFor(contactId: String)
}
