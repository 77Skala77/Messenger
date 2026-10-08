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

/**
 * [PLAYED]: the contact's voice message was played here (DEC-036; every voice message since DEC-039); [DELETE]: "delete for both"
 * (DEC-037). Both are control messages like [RECEIPT]: never shown.
 */
enum class V2MessageKind { HELLO, ACCEPT, TEXT, RECEIPT, VOICE, PLAYED, DELETE }

/** Where a voice message's recording is (DEC-036). */
enum class V2MediaState {
    /** Ours: its encrypted pieces are still going up to the relay; the message itself waits for them. */
    UPLOADING,

    /** The contact's: its pieces are still to be fetched; no receipt until they are all here. */
    DOWNLOADING,

    /** The whole recording is on this phone (ours: also uploaded). */
    READY,

    /** The contact's recording could not be fetched in time, or did not check out. */
    UNAVAILABLE,

    /** The contact's one-time recording was played: its pieces are deleted from this phone. */
    PLAYED,
}

/**
 * A voice message's description (DEC-036): what the encrypted payload carries and what this phone
 * keeps, vault-encrypted. [queueReadSecret] opens the one-time relay queue that holds the encrypted
 * pieces of the recording; [key] decrypts them; [digest] checks the whole. Never logged, never shown.
 */
class V2VoiceNote(
    val queueReadSecret: ByteArray,
    val key: ByteArray,
    val digest: ByteArray,
    val plaintextSize: Int,
    val chunkCount: Int,
    val durationMillis: Int,
    /** One loudness value (0 to 31) per slice of the recording, for the bars in the bubble. */
    val waveform: ByteArray,
    val codec: Int,
    /** A one-time recording: the receiving phone plays it once and then deletes it. */
    val once: Boolean = false,
) {
    override fun equals(other: Any?): Boolean =
        other is V2VoiceNote && queueReadSecret.contentEquals(other.queueReadSecret) && key.contentEquals(other.key) &&
            digest.contentEquals(other.digest) && plaintextSize == other.plaintextSize && chunkCount == other.chunkCount &&
            durationMillis == other.durationMillis && waveform.contentEquals(other.waveform) && codec == other.codec &&
            once == other.once

    override fun hashCode(): Int = digest.contentHashCode()

    override fun toString(): String = "V2VoiceNote(redacted)"
}

/**
 * What a screen may know about a voice message: no key, no queue. [once]: a one-time recording.
 * [listened] (Room v13, DEC-039): the contact's was played here, or ours was played by the contact
 * ("played", type 7); until then Telegram's small dot stands next to its length.
 */
class V2VoiceInfo(
    val durationMillis: Int,
    val waveform: ByteArray,
    val state: V2MediaState,
    val once: Boolean = false,
    val listened: Boolean = true,
)

/** Our voice message whose pieces are not all on the relay yet: [uploaded] of the note's pieces are. */
class V2PendingUpload(val messageId: String, val note: V2VoiceNote, val uploaded: Int)

/** The contact's voice message whose pieces are still to be fetched. */
class V2PendingDownload(
    val messageId: String,
    val contactId: String,
    val innerMessageIdHex: String,
    val note: V2VoiceNote,
    val createdAtEpochMillis: Long,
)

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
    /**
     * When the person confirmed that the safety number on this phone matches the one on the
     * contact's phone (audit H1, DEC-030); null until then. A local mark only: it is never sent.
     * A contact's identity key never changes, so the mark always belongs to the key it was set for.
     */
    val verifiedAtEpochMillis: Long? = null,
    /** When the person pinned this chat to the top of the list (Room v11, DEC-035); null when not pinned. Local only. */
    val pinnedAtEpochMillis: Long? = null,
    /** Notifications for this chat stay silent until then (Room v11, DEC-035); null when not muted. Local only. */
    val mutedUntilEpochMillis: Long? = null,
) {
    /** The mark counts only for a contact that is not blocked: a changed key is never "checked". */
    val safetyNumberChecked: Boolean get() = verifiedAtEpochMillis != null && state != V2ContactState.KEY_CHANGED

    val pinned: Boolean get() = pinnedAtEpochMillis != null

    fun isMuted(nowEpochMillis: Long): Boolean = mutedUntilEpochMillis?.let { it > nowEpochMillis } ?: false
}

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
    /** Earliest time of the next attempt after a failure; null means now. */
    val nextAttemptAtEpochMillis: Long? = null,
    /**
     * A voice message whose recording is still going up (DEC-036): it is not sent yet, and the
     * contact's later envelopes wait behind it, so the order stays and no note arrives without its recording.
     */
    val waitsForMedia: Boolean = false,
)

class V2StoredMessage(
    val messageId: String,
    val contactId: String,
    val outgoing: Boolean,
    val innerMessageIdHex: String,
    val text: String,
    val status: V2MessageStatus,
    /**
     * When the text is shown and sorted: for our own texts the time we wrote them, for incoming ones
     * the sender's time when it lies within a minute before arrival, else the arrival (DEC-034).
     */
    val createdAtEpochMillis: Long,
    /** The inner message ID of the text this one answers (DEC-035), either direction; null for a plain text. */
    val replyToInnerIdHex: String? = null,
    /** Set for a voice message (DEC-036); its [text] is then empty. */
    val voice: V2VoiceInfo? = null,
)

/**
 * One row of the chat list: the contact, its newest text in either direction, and how many of its
 * incoming texts the user has not seen yet. The count is a local mark only (DEC-029); nothing about
 * reading is ever sent, so it is not a read receipt.
 */
class V2ChatSummary(
    val contact: V2Contact,
    val lastText: V2StoredMessage?,
    val unreadCount: Int,
)

interface ConversationV2Store {
    /** Contact first, then pending request, then an unused invitation with this tag. */
    fun findRoute(localRoutingTagHex: String): V2Route?

    fun isEnvelopeAccepted(digestHex: String): Boolean

    /** Returns false if the digest was already recorded. */
    fun recordAcceptedEnvelope(digestHex: String, acceptedAtEpochMillis: Long): Boolean

    /**
     * Forgets digests accepted before [cutoffEpochMillis]; returns how many (second review I5). A
     * replay of a forgotten envelope is still rejected: libsignal refuses spent message keys and
     * pre-keys, and inner message IDs are unique per contact.
     */
    fun forgetAcceptedEnvelopesBefore(cutoffEpochMillis: Long): Int

    fun saveInvitation(invitation: V2Invitation)

    fun invitation(invitationId: String): V2Invitation?

    /** The newest unused, unexpired invitation, to show again after a restart. */
    fun latestUsableInvitation(nowEpochMillis: Long): V2Invitation?

    /** Atomic: returns false if the invitation is unknown or already used. */
    fun markInvitationUsed(invitationId: String, requestId: String): Boolean

    /** The invitation whose HELLO created this request (and, once accepted, contact), or null. */
    fun invitationUsedBy(requestId: String): V2Invitation?

    fun expiredUnusedInvitations(nowEpochMillis: Long): List<V2Invitation>

    fun deleteInvitation(invitationId: String)

    fun saveRequest(request: V2ContactRequest)

    fun request(requestId: String): V2ContactRequest?

    fun requests(): List<V2ContactRequest>

    fun deleteRequest(requestId: String)

    fun saveContact(contact: V2Contact)

    fun contact(contactId: String): V2Contact?

    fun contactWithIdentity(identityKey: ByteArray): V2Contact?

    /** Setting [V2ContactState.KEY_CHANGED] also clears the verified mark: it belonged to the old key. */
    fun setContactState(contactId: String, state: V2ContactState)

    /**
     * Sets or clears (null) the verified mark (DEC-030). Returns false, changing nothing, if there is
     * no such contact. Only the person's explicit confirmation of a matching number may set it.
     */
    fun setContactVerified(contactId: String, verifiedAtEpochMillis: Long?): Boolean

    /**
     * Pins (a time) or unpins (null) a chat (DEC-035). Returns false, changing nothing, if there is no
     * such contact.
     */
    fun setContactPinned(contactId: String, pinnedAtEpochMillis: Long?): Boolean

    /** Mutes a chat's notifications until a time, or unmutes it (null). False if there is no such contact. */
    fun setContactMuted(contactId: String, mutedUntilEpochMillis: Long?): Boolean

    /**
     * Deletes the contact row only; its messages, session and invitation are the caller's to delete in
     * the same transaction. Returns false if there was no such contact.
     */
    fun deleteContact(contactId: String): Boolean

    /**
     * Returns false (and stores nothing) if this contact's message with that inner ID already exists.
     * [createdAtEpochMillis] is the time the text is shown and sorted by (DEC-034); [replyToInnerIdHex]
     * the text it answers, as the sender named it (DEC-035), unchecked: it may be one this phone lacks.
     */
    fun storeIncomingText(
        contactId: String,
        innerMessageIdHex: String,
        text: String,
        createdAtEpochMillis: Long,
        replyToInnerIdHex: String?,
    ): Boolean

    /** Whether this chat holds a text, in either direction, with that inner message ID. */
    fun hasText(contactId: String, innerMessageIdHex: String): Boolean

    /** The shown time of this contact's (or request's) latest incoming text, or null if it has none (DEC-034). */
    fun latestIncomingTextTime(contactId: String): Long?

    /**
     * Deletes this chat's texts and voice messages that have nothing left to do (DEC-035): every
     * incoming one, and every outgoing one already handed to the relay, with the stored pieces of the
     * voice messages among them. A message still waiting to be sent keeps its envelope and stays;
     * control messages are untouched. Returns how many messages were deleted.
     */
    fun clearTexts(contactId: String): Int

    /** Incoming texts and voice messages of a contact or request, oldest first. */
    fun incomingTexts(contactId: String): List<V2StoredMessage>

    /**
     * Visible messages (texts and voice messages) of a contact in both directions, oldest first
     * (control messages excluded); equal times in the order they were stored, never by their random IDs.
     */
    fun texts(contactId: String): List<V2StoredMessage>

    /** The newest of [texts], in the same order, or null. */
    fun lastText(contactId: String): V2StoredMessage?

    /**
     * How many incoming texts of each contact or request the user has not seen yet (DEC-029);
     * chats without any are absent. A local mark only, never sent.
     */
    fun unreadCounts(): Map<String, Int>

    /** Marks every incoming text of a contact or request as seen; returns how many changed. */
    fun markRead(contactId: String): Int

    /** Every contact, oldest first. */
    fun contacts(): List<V2Contact>

    /**
     * [visibleText] is stored (encrypted) for TEXT only; control messages have none. [replyToInnerIdHex]
     * is the text a reply answers (DEC-035), TEXT only.
     */
    fun enqueueOutgoing(envelope: V2OutgoingEnvelope, visibleText: String?, createdAtEpochMillis: Long, replyToInnerIdHex: String?)

    fun outgoing(messageId: String): V2OutgoingEnvelope?

    /**
     * Every envelope not sent yet, due or not, in insertion order (never by timestamp, which can
     * move backwards): the sender keeps each contact's envelopes in this order (DEC-026).
     */
    fun unsentOutgoing(): List<V2OutgoingEnvelope>

    fun recordSent(messageId: String, attemptCount: Int)

    fun recordFailed(messageId: String, attemptCount: Int, nextAttemptAtEpochMillis: Long)

    /** Marks this contact's outgoing TEXT or VOICE with that inner ID delivered; false if there is none. */
    fun markDelivered(contactId: String, innerMessageIdHex: String): Boolean

    /** Deletes every message of this contact or request, with the stored pieces of its voice messages. */
    fun deleteMessagesFor(contactId: String)

    // --- Voice messages (DEC-036). The pieces are stored exactly as they travel: encrypted under the
    // note's key, which is stored vault-encrypted with the message. ---

    /**
     * Queues our voice message: its envelope (held back until every piece is uploaded, see
     * [V2OutgoingEnvelope.waitsForMedia]), its note (vault-encrypted) and its encrypted pieces.
     * [replyToInnerIdHex] names the message it answers (DEC-035), or null.
     */
    fun enqueueOutgoingVoice(
        envelope: V2OutgoingEnvelope,
        note: V2VoiceNote,
        chunks: List<ByteArray>,
        createdAtEpochMillis: Long,
        replyToInnerIdHex: String? = null,
    )

    /** Our voice messages whose pieces are not all uploaded, oldest first. */
    fun pendingVoiceUploads(): List<V2PendingUpload>

    /** [uploaded] pieces of our voice message are on the relay; with all of them it is ready and may be sent. */
    fun recordVoiceUploaded(messageId: String, uploaded: Int)

    /**
     * Stores the contact's voice message as waiting for its recording; false (and nothing stored) if
     * this contact's message with that inner ID already exists. No receipt is due yet.
     */
    fun storeIncomingVoice(
        contactId: String,
        innerMessageIdHex: String,
        note: V2VoiceNote,
        createdAtEpochMillis: Long,
        replyToInnerIdHex: String? = null,
    ): Boolean

    /** The contacts' voice messages whose pieces are still to be fetched, oldest first. */
    fun pendingVoiceDownloads(): List<V2PendingDownload>

    /** Stores one piece of a voice message; false if that piece is already stored. */
    fun storeVoiceChunk(messageId: String, index: Int, chunk: ByteArray): Boolean

    fun voiceChunk(messageId: String, index: Int): ByteArray?

    fun storedVoiceChunkIndices(messageId: String): Set<Int>

    fun setVoiceState(messageId: String, state: V2MediaState)

    /**
     * Marks a voice message in either direction listened (DEC-039): the contact's was played here, or
     * ours was played by the contact. Returns true only if it was not listened before.
     */
    fun setVoiceListened(messageId: String): Boolean

    fun deleteVoiceChunks(messageId: String)

    /** The note of a voice message in either direction, for playing it; null if there is none. */
    fun voiceNote(messageId: String): V2VoiceNote?

    /** The state of a voice message in either direction; null if there is none. */
    fun voiceState(messageId: String): V2MediaState?

    /** Whether a voice message is ours (true) or the contact's (false); null if there is none. */
    fun voiceIsOutgoing(messageId: String): Boolean?

    /** A text or voice message by its local ID, as a chat shows it; null if there is none. */
    fun message(messageId: String): V2StoredMessage?

    /** This chat's text or voice message in one direction by its inner message ID; null if there is none. */
    fun messageByInnerId(contactId: String, innerMessageIdHex: String, outgoing: Boolean): V2StoredMessage?

    /**
     * Deletes one text or voice message with its stored pieces (DEC-037); an outgoing one still
     * waiting to be sent loses its envelope too and is never sent. False if there was none.
     */
    fun deleteMessage(messageId: String): Boolean
}
