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
import app.securemessenger.signalstore.V2MediaState
import app.securemessenger.signalstore.V2MessageKind
import app.securemessenger.signalstore.V2MessageStatus
import app.securemessenger.signalstore.V2OutgoingEnvelope
import app.securemessenger.signalstore.V2PendingDownload
import app.securemessenger.signalstore.V2PendingUpload
import app.securemessenger.signalstore.V2Route
import app.securemessenger.signalstore.V2StoredMessage
import app.securemessenger.signalstore.V2VoiceInfo
import app.securemessenger.signalstore.V2VoiceNote

/**
 * In-memory [ConversationV2Store] with the same observable contract as
 * `EncryptedConversationV2Repository` (whose Room behaviour the storage instrumented tests cover),
 * minus encryption. Tests only.
 */
class InMemoryConversationV2Store : ConversationV2Store {
    private class Message(
        val stored: V2StoredMessage,
        val kind: V2MessageKind,
        var status: V2MessageStatus,
        val peerQueueId: String?,
        var envelope: ByteArray?,
        var attemptCount: Int,
        var nextRetryAt: Long?,
        var read: Boolean,
        val note: V2VoiceNote? = null,
        var mediaState: V2MediaState? = null,
        var uploaded: Int = 0,
        var listened: Boolean = false,
    )

    /** The stored pieces of each voice message, by local message ID and index. */
    private val chunks = linkedMapOf<String, MutableMap<Int, ByteArray>>()

    private val invitations = linkedMapOf<String, V2Invitation>()
    private val requests = linkedMapOf<String, V2ContactRequest>()
    private val contacts = linkedMapOf<String, V2Contact>()
    private val messages = linkedMapOf<String, Message>()
    /** Digest of each accepted envelope, and when it was accepted. */
    private val accepted = linkedMapOf<String, Long>()
    private var nextLocalId = 0

    /** Fault injection: the next incoming text store throws, as if the process died there. */
    var failNextIncomingStore = false

    /** Fault injection: the next request save throws, as if storage failed while a HELLO was processed. */
    var failNextRequestSave = false

    @Synchronized
    fun snapshot(): () -> Unit {
        val savedInvitations = LinkedHashMap(invitations)
        val savedRequests = LinkedHashMap(requests)
        val savedContacts = LinkedHashMap(contacts)
        val savedMessages = messages.mapValues { (_, m) ->
            Message(m.stored, m.kind, m.status, m.peerQueueId, m.envelope, m.attemptCount, m.nextRetryAt, m.read, m.note, m.mediaState, m.uploaded)
        }
        val savedAccepted = LinkedHashMap(accepted)
        val savedChunks = chunks.mapValues { (_, pieces) -> LinkedHashMap(pieces) }
        return {
            synchronized(this) {
                invitations.clear(); invitations.putAll(savedInvitations)
                requests.clear(); requests.putAll(savedRequests)
                contacts.clear(); contacts.putAll(savedContacts)
                messages.clear(); messages.putAll(savedMessages)
                accepted.clear(); accepted.putAll(savedAccepted)
                chunks.clear(); savedChunks.forEach { (id, pieces) -> chunks[id] = LinkedHashMap(pieces) }
            }
        }
    }

    @Synchronized
    override fun findRoute(localRoutingTagHex: String): V2Route? =
        contacts.values.firstOrNull { it.localRoutingTagHex == localRoutingTagHex }?.let { V2Route.ToContact(it) }
            ?: requests.values.firstOrNull { it.localRoutingTagHex == localRoutingTagHex }?.let { V2Route.ToRequest(it) }
            ?: invitations.values.firstOrNull { it.localRoutingTagHex == localRoutingTagHex && it.usedByRequestId == null }
                ?.let { V2Route.ToInvitation(it) }

    @Synchronized
    override fun isEnvelopeAccepted(digestHex: String) = digestHex in accepted

    @Synchronized
    override fun recordAcceptedEnvelope(digestHex: String, acceptedAtEpochMillis: Long): Boolean {
        if (digestHex in accepted) return false
        accepted[digestHex] = acceptedAtEpochMillis
        return true
    }

    @Synchronized
    override fun forgetAcceptedEnvelopesBefore(cutoffEpochMillis: Long): Int {
        val old = accepted.filterValues { it < cutoffEpochMillis }.keys
        old.forEach(accepted::remove)
        return old.size
    }

    @Synchronized
    fun acceptedDigestCount(): Int = accepted.size

    @Synchronized
    override fun saveInvitation(invitation: V2Invitation) {
        check(invitation.invitationId !in invitations)
        check(invitations.values.none { it.localRoutingTagHex == invitation.localRoutingTagHex })
        invitations[invitation.invitationId] = invitation
    }

    @Synchronized
    override fun invitation(invitationId: String) = invitations[invitationId]

    @Synchronized
    override fun latestUsableInvitation(nowEpochMillis: Long) =
        invitations.values.filter { it.usedByRequestId == null && it.expiresAtEpochMillis > nowEpochMillis }
            .maxByOrNull { it.createdAtEpochMillis }

    @Synchronized
    override fun markInvitationUsed(invitationId: String, requestId: String): Boolean {
        val invitation = invitations[invitationId] ?: return false
        if (invitation.usedByRequestId != null) return false
        invitations[invitationId] = invitation.copyUsed(requestId)
        return true
    }

    @Synchronized
    override fun invitationUsedBy(requestId: String) = invitations.values.firstOrNull { it.usedByRequestId == requestId }

    @Synchronized
    override fun expiredUnusedInvitations(nowEpochMillis: Long) =
        invitations.values.filter { it.usedByRequestId == null && it.expiresAtEpochMillis <= nowEpochMillis }

    @Synchronized
    override fun deleteInvitation(invitationId: String) {
        invitations.remove(invitationId)
    }

    @Synchronized
    override fun saveRequest(request: V2ContactRequest) {
        if (failNextRequestSave) {
            failNextRequestSave = false
            throw IllegalStateException("Simulated storage failure while saving a request")
        }
        check(request.requestId !in requests)
        check(requests.values.none { it.localRoutingTagHex == request.localRoutingTagHex })
        requests[request.requestId] = request
    }

    @Synchronized
    override fun request(requestId: String) = requests[requestId]

    @Synchronized
    override fun requests() = requests.values.toList()

    @Synchronized
    override fun deleteRequest(requestId: String) {
        requests.remove(requestId)
    }

    @Synchronized
    override fun saveContact(contact: V2Contact) {
        check(contact.contactId !in contacts)
        check(contacts.values.none { it.localRoutingTagHex == contact.localRoutingTagHex })
        contacts[contact.contactId] = contact
    }

    @Synchronized
    override fun contact(contactId: String) = contacts[contactId]

    @Synchronized
    override fun contactWithIdentity(identityKey: ByteArray) =
        contacts.values.firstOrNull { it.identityKey.contentEquals(identityKey) }

    @Synchronized
    override fun setContactState(contactId: String, state: V2ContactState) {
        val contact = checkNotNull(contacts[contactId]) { "Unknown v2 contact" }
        // Like the Room repository: a changed key takes the verified mark with it.
        val verifiedAt = if (state == V2ContactState.KEY_CHANGED) null else contact.verifiedAtEpochMillis
        contacts[contactId] = contact.copy(state = state, verifiedAtEpochMillis = verifiedAt)
    }

    @Synchronized
    override fun setContactVerified(contactId: String, verifiedAtEpochMillis: Long?): Boolean {
        val contact = contacts[contactId] ?: return false
        contacts[contactId] = contact.copy(verifiedAtEpochMillis = verifiedAtEpochMillis)
        return true
    }

    @Synchronized
    override fun setContactPinned(contactId: String, pinnedAtEpochMillis: Long?): Boolean {
        val contact = contacts[contactId] ?: return false
        contacts[contactId] = contact.copy(pinnedAtEpochMillis = pinnedAtEpochMillis)
        return true
    }

    @Synchronized
    override fun setContactMuted(contactId: String, mutedUntilEpochMillis: Long?): Boolean {
        val contact = contacts[contactId] ?: return false
        contacts[contactId] = contact.copy(mutedUntilEpochMillis = mutedUntilEpochMillis)
        return true
    }

    @Synchronized
    override fun deleteContact(contactId: String): Boolean = contacts.remove(contactId) != null

    private fun V2Contact.copy(
        state: V2ContactState = this.state,
        verifiedAtEpochMillis: Long? = this.verifiedAtEpochMillis,
        pinnedAtEpochMillis: Long? = this.pinnedAtEpochMillis,
        mutedUntilEpochMillis: Long? = this.mutedUntilEpochMillis,
    ) = V2Contact(
        contactId, localDisplayName, identityKey, peerQueueId, peerRoutingTagHex, localRoutingTagHex, state,
        createdAtEpochMillis, verifiedAtEpochMillis, pinnedAtEpochMillis, mutedUntilEpochMillis,
    )

    @Synchronized
    override fun storeIncomingText(
        contactId: String,
        innerMessageIdHex: String,
        text: String,
        createdAtEpochMillis: Long,
        replyToInnerIdHex: String?,
    ): Boolean {
        if (failNextIncomingStore) {
            failNextIncomingStore = false
            throw IllegalStateException("Simulated crash while storing")
        }
        if (messages.values.any { !it.stored.outgoing && it.stored.contactId == contactId && it.stored.innerMessageIdHex == innerMessageIdHex }) {
            return false
        }
        val id = "local-${nextLocalId++}"
        messages[id] = Message(
            V2StoredMessage(id, contactId, false, innerMessageIdHex, text, V2MessageStatus.RECEIVED, createdAtEpochMillis, replyToInnerIdHex),
            V2MessageKind.TEXT, V2MessageStatus.RECEIVED, null, null, 0, null, read = false,
        )
        return true
    }

    @Synchronized
    override fun hasText(contactId: String, innerMessageIdHex: String): Boolean =
        messages.values.any { it.kind in VISIBLE && it.stored.contactId == contactId && it.stored.innerMessageIdHex == innerMessageIdHex }

    @Synchronized
    override fun latestIncomingTextTime(contactId: String): Long? =
        messages.values.filter { !it.stored.outgoing && it.kind in VISIBLE && it.stored.contactId == contactId }
            .maxOfOrNull { it.stored.createdAtEpochMillis }

    /** Like the Room query: incoming texts and voice messages, and outgoing ones whose envelope is gone, with their pieces. */
    @Synchronized
    override fun clearTexts(contactId: String): Int {
        val settled = messages.filterValues {
            it.kind in VISIBLE && it.stored.contactId == contactId && (!it.stored.outgoing || it.envelope == null)
        }.keys
        settled.forEach { id ->
            messages.remove(id)
            chunks.remove(id)
        }
        return settled.size
    }

    @Synchronized
    override fun incomingTexts(contactId: String) =
        messages.values.filter { !it.stored.outgoing && it.stored.contactId == contactId && it.kind in VISIBLE }.map(::visible)

    /** By shown time, then insertion order, like the Room query. */
    @Synchronized
    override fun texts(contactId: String) =
        messages.values.filter { it.stored.contactId == contactId && it.kind in VISIBLE }
            .sortedBy { it.stored.createdAtEpochMillis }
            .map(::visible)

    @Synchronized
    override fun lastText(contactId: String) = texts(contactId).lastOrNull()

    @Synchronized
    override fun unreadCounts(): Map<String, Int> =
        messages.values.filter { !it.stored.outgoing && it.kind in VISIBLE && !it.read }
            .groupingBy { it.stored.contactId }
            .eachCount()

    @Synchronized
    override fun markRead(contactId: String): Int {
        val unread = messages.values.filter { !it.stored.outgoing && it.stored.contactId == contactId && !it.read }
        unread.forEach { it.read = true }
        return unread.size
    }

    private fun visible(message: Message) = V2StoredMessage(
        message.stored.messageId, message.stored.contactId, message.stored.outgoing, message.stored.innerMessageIdHex,
        message.stored.text, message.status, message.stored.createdAtEpochMillis, message.stored.replyToInnerIdHex,
        message.note?.let { note -> V2VoiceInfo(note.durationMillis, note.waveform, checkNotNull(message.mediaState), note.once, message.listened) },
    )

    @Synchronized
    override fun contacts() = contacts.values.toList()

    @Synchronized
    override fun enqueueOutgoing(envelope: V2OutgoingEnvelope, visibleText: String?, createdAtEpochMillis: Long, replyToInnerIdHex: String?) {
        require((envelope.kind == V2MessageKind.TEXT) == (visibleText != null))
        require(replyToInnerIdHex == null || envelope.kind == V2MessageKind.TEXT)
        check(envelope.messageId !in messages)
        check(messages.values.none { it.stored.outgoing && it.stored.contactId == envelope.contactId && it.stored.innerMessageIdHex == envelope.innerMessageIdHex })
        messages[envelope.messageId] = Message(
            V2StoredMessage(
                envelope.messageId, envelope.contactId, true, envelope.innerMessageIdHex, visibleText.orEmpty(),
                V2MessageStatus.PENDING, createdAtEpochMillis, replyToInnerIdHex,
            ),
            envelope.kind, V2MessageStatus.PENDING, envelope.peerQueueId, envelope.envelope, envelope.attemptCount, null,
            read = true,
        )
    }

    @Synchronized
    override fun outgoing(messageId: String) = messages[messageId]?.takeIf { it.stored.outgoing }?.toEnvelope()

    @Synchronized
    override fun unsentOutgoing() = messages.values.filter {
        it.stored.outgoing && it.status in setOf(V2MessageStatus.PENDING, V2MessageStatus.FAILED) && it.envelope != null
    }.mapNotNull { it.toEnvelope() }

    @Synchronized
    override fun recordSent(messageId: String, attemptCount: Int) {
        val message = messages[messageId] ?: return
        if (message.status !in setOf(V2MessageStatus.PENDING, V2MessageStatus.FAILED)) return
        message.status = V2MessageStatus.SENT
        message.attemptCount = attemptCount
        message.nextRetryAt = null
        message.envelope = null
    }

    @Synchronized
    override fun recordFailed(messageId: String, attemptCount: Int, nextAttemptAtEpochMillis: Long) {
        val message = messages[messageId] ?: return
        if (message.status !in setOf(V2MessageStatus.PENDING, V2MessageStatus.FAILED)) return
        message.status = V2MessageStatus.FAILED
        message.attemptCount = attemptCount
        message.nextRetryAt = nextAttemptAtEpochMillis
    }

    @Synchronized
    override fun markDelivered(contactId: String, innerMessageIdHex: String): Boolean {
        val message = messages.values.firstOrNull {
            it.stored.outgoing && it.kind in VISIBLE && it.stored.contactId == contactId &&
                it.stored.innerMessageIdHex == innerMessageIdHex
        } ?: return false
        message.status = V2MessageStatus.DELIVERED
        message.envelope = null
        return true
    }

    @Synchronized
    override fun deleteMessagesFor(contactId: String) {
        val gone = messages.filterValues { it.stored.contactId == contactId }.keys
        gone.forEach { id ->
            messages.remove(id)
            chunks.remove(id)
        }
    }

    // --- Voice messages, like the Room repository. ---

    @Synchronized
    override fun enqueueOutgoingVoice(
        envelope: V2OutgoingEnvelope,
        note: V2VoiceNote,
        chunks: List<ByteArray>,
        createdAtEpochMillis: Long,
        replyToInnerIdHex: String?,
    ) {
        require(envelope.kind == V2MessageKind.VOICE)
        require(chunks.size == note.chunkCount)
        check(envelope.messageId !in messages)
        messages[envelope.messageId] = Message(
            V2StoredMessage(
                envelope.messageId, envelope.contactId, true, envelope.innerMessageIdHex, "", V2MessageStatus.PENDING,
                createdAtEpochMillis, replyToInnerIdHex,
            ),
            V2MessageKind.VOICE, V2MessageStatus.PENDING, envelope.peerQueueId, envelope.envelope, envelope.attemptCount, null,
            read = true, note = note, mediaState = V2MediaState.UPLOADING,
        )
        this.chunks[envelope.messageId] = chunks.withIndex().associateTo(linkedMapOf()) { (index, piece) -> index to piece }
    }

    @Synchronized
    override fun pendingVoiceUploads() = messages.values
        .filter { it.stored.outgoing && it.kind == V2MessageKind.VOICE && it.mediaState == V2MediaState.UPLOADING }
        .map { V2PendingUpload(it.stored.messageId, checkNotNull(it.note), it.uploaded) }

    @Synchronized
    override fun recordVoiceUploaded(messageId: String, uploaded: Int) {
        val message = messages[messageId] ?: return
        // As on Room: progress only moves forward, and only while uploading.
        if (message.mediaState != V2MediaState.UPLOADING || uploaded <= message.uploaded) return
        message.uploaded = uploaded
        if (uploaded >= checkNotNull(message.note).chunkCount) message.mediaState = V2MediaState.READY
    }

    @Synchronized
    override fun storeIncomingVoice(
        contactId: String,
        innerMessageIdHex: String,
        note: V2VoiceNote,
        createdAtEpochMillis: Long,
        replyToInnerIdHex: String?,
    ): Boolean {
        if (messages.values.any { !it.stored.outgoing && it.stored.contactId == contactId && it.stored.innerMessageIdHex == innerMessageIdHex }) {
            return false
        }
        val id = "local-${nextLocalId++}"
        messages[id] = Message(
            V2StoredMessage(id, contactId, false, innerMessageIdHex, "", V2MessageStatus.RECEIVED, createdAtEpochMillis, replyToInnerIdHex),
            V2MessageKind.VOICE, V2MessageStatus.RECEIVED, null, null, 0, null,
            read = false, note = note, mediaState = V2MediaState.DOWNLOADING,
        )
        return true
    }

    @Synchronized
    override fun pendingVoiceDownloads() = messages.values
        .filter { !it.stored.outgoing && it.kind == V2MessageKind.VOICE && it.mediaState == V2MediaState.DOWNLOADING }
        .map { V2PendingDownload(it.stored.messageId, it.stored.contactId, it.stored.innerMessageIdHex, checkNotNull(it.note), it.stored.createdAtEpochMillis) }

    @Synchronized
    override fun storeVoiceChunk(messageId: String, index: Int, chunk: ByteArray): Boolean {
        val pieces = chunks.getOrPut(messageId) { linkedMapOf() }
        if (index in pieces) return false
        pieces[index] = chunk
        return true
    }

    @Synchronized
    override fun voiceChunk(messageId: String, index: Int): ByteArray? = chunks[messageId]?.get(index)

    @Synchronized
    override fun storedVoiceChunkIndices(messageId: String): Set<Int> = chunks[messageId]?.keys?.toSet().orEmpty()

    @Synchronized
    override fun setVoiceState(messageId: String, state: V2MediaState) {
        messages[messageId]?.mediaState = state
    }

    /** Like Room: only a voice message, and true only the first time. */
    @Synchronized
    override fun setVoiceListened(messageId: String): Boolean {
        val message = messages[messageId]?.takeIf { it.kind == V2MessageKind.VOICE } ?: return false
        if (message.listened) return false
        message.listened = true
        return true
    }

    @Synchronized
    override fun deleteVoiceChunks(messageId: String) {
        chunks.remove(messageId)
    }

    @Synchronized
    override fun voiceNote(messageId: String): V2VoiceNote? = messages[messageId]?.note

    @Synchronized
    override fun voiceState(messageId: String): V2MediaState? = messages[messageId]?.mediaState

    @Synchronized
    override fun voiceIsOutgoing(messageId: String): Boolean? =
        messages[messageId]?.takeIf { it.kind == V2MessageKind.VOICE }?.stored?.outgoing

    @Synchronized
    override fun message(messageId: String): V2StoredMessage? =
        messages[messageId]?.takeIf { it.kind in VISIBLE }?.let(::visible)

    @Synchronized
    override fun deleteMessage(messageId: String): Boolean {
        if (messages[messageId]?.kind !in VISIBLE) return false
        messages.remove(messageId)
        chunks.remove(messageId)
        return true
    }

    @Synchronized
    override fun messageByInnerId(contactId: String, innerMessageIdHex: String, outgoing: Boolean): V2StoredMessage? =
        messages.values.firstOrNull {
            it.kind in VISIBLE && it.stored.contactId == contactId && it.stored.innerMessageIdHex == innerMessageIdHex &&
                it.stored.outgoing == outgoing
        }?.let(::visible)

    @Synchronized
    fun voiceChunkCount(messageId: String): Int = chunks[messageId]?.size ?: 0

    /** A damaged store: one stored piece is gone. */
    @Synchronized
    fun loseVoiceChunk(messageId: String, index: Int) {
        chunks[messageId]?.remove(index)
    }

    @Synchronized
    fun statusOf(messageId: String): V2MessageStatus? = messages[messageId]?.status

    @Synchronized
    fun contactIds(): List<String> = contacts.keys.toList()

    @Synchronized
    fun outgoingKinds(contactId: String): List<V2MessageKind> =
        messages.values.filter { it.stored.outgoing && it.stored.contactId == contactId }.map { it.kind }

    /** Each invitation's routing tag and whether it was used. */
    @Synchronized
    fun invitationStates(): List<String> =
        invitations.values.map { "invitation ${it.localRoutingTagHex} used=${it.usedByRequestId != null}" }

    /** Every stored value as text, for tests that prove an operation changed nothing at all. */
    @Synchronized
    fun dump(): List<String> = buildList {
        invitations.values.forEach { add("invitation ${it.invitationId} ${it.localRoutingTagHex} ${it.usedByRequestId}") }
        requests.values.forEach {
            add("request ${it.requestId} ${it.localRoutingTagHex} ${it.peerRoutingTagHex} ${it.peerQueueId} ${hexOf(it.identityKey)} ${it.claimedDisplayName}")
        }
        contacts.values.forEach {
            add(
                "contact ${it.contactId} ${it.state} ${it.localRoutingTagHex} ${it.peerRoutingTagHex} ${it.peerQueueId} " +
                    "${hexOf(it.identityKey)} verified=${it.verifiedAtEpochMillis} pinned=${it.pinnedAtEpochMillis} " +
                    "muted=${it.mutedUntilEpochMillis}",
            )
        }
        messages.values.forEach {
            add(
                "message ${it.stored.messageId} ${it.stored.contactId} ${it.kind} ${it.status} ${it.stored.innerMessageIdHex} " +
                    "${it.stored.text} ${it.attemptCount} ${it.nextRetryAt} ${it.envelope?.let(::hexOf)} " +
                    "at=${it.stored.createdAtEpochMillis} reply=${it.stored.replyToInnerIdHex} media=${it.mediaState} " +
                    "up=${it.uploaded} pieces=${chunks[it.stored.messageId]?.keys?.sorted()}",
            )
        }
        accepted.keys.sorted().forEach { add("accepted $it") }
    }

    private fun Message.toEnvelope(): V2OutgoingEnvelope? {
        val bytes = envelope ?: return null
        return V2OutgoingEnvelope(
            stored.messageId, stored.contactId, kind, stored.innerMessageIdHex, peerQueueId!!, bytes, attemptCount, nextRetryAt,
            waitsForMedia = mediaState == V2MediaState.UPLOADING,
        )
    }

    private companion object {
        /** What a chat shows: texts and voice messages; control messages are not shown. */
        val VISIBLE = setOf(V2MessageKind.TEXT, V2MessageKind.VOICE)
    }

    private fun V2Invitation.copyUsed(requestId: String) = V2Invitation(
        invitationId, localRoutingTagHex, signedPreKeyId, oneTimePreKeyId, kyberPreKeyId, mailboxQueueId,
        codeText, sealedBundle, createdAtEpochMillis, expiresAtEpochMillis, requestId,
    )
}

/** Rolls back both in-memory stores when the block throws, the way a Room transaction does. */
class SnapshotTransactionRunner(
    private val records: InMemorySignalRecords,
    private val store: InMemoryConversationV2Store,
) : ProtocolTransactionRunner {
    override fun <T> inTransaction(block: () -> T): T {
        val restoreRecords = records.snapshot()
        val restoreStore = store.snapshot()
        return try {
            block()
        } catch (failure: Throwable) {
            restoreRecords()
            restoreStore()
            throw failure
        }
    }
}

/**
 * An untrusted relay double with the v3 API (DEC-024): it assigns message IDs and binds deletion to
 * the queue, like the real relay; tests read, drop, duplicate, relabel, and inject blobs freely.
 */
class FakeRelay : TransportClient {
    private val blobs = linkedMapOf<String, RelayEncryptedBlob>()
    private var nextId = 0
    var failNextSends = 0

    /** Fault injection: fetches and deletes on these queues fail, as if the relay were unreachable for them. */
    val unreachableQueues = mutableSetOf<String>()

    /** Like the real relay (DEC-026), a fetch returns only the oldest messages, at most this many. */
    var pageSize = ConversationV2Service.RELAY_PAGE_SIZE

    override suspend fun sendMessage(queueId: String, opaqueCiphertext: ByteArray, expiresAtEpochMillis: Long): String =
        synchronized(this) {
            if (failNextSends > 0) {
                failNextSends--
                error("relay offline")
            }
            val messageId = "relay-${nextId++}"
            blobs[messageId] = RelayEncryptedBlob(messageId, queueId, opaqueCiphertext, expiresAtEpochMillis)
            messageId
        }

    override suspend fun fetchMessages(queue: RelayQueueCredentials): List<RelayEncryptedBlob> =
        synchronized(this) {
            if (queue.queueId in unreachableQueues) throw java.io.IOException("relay unreachable")
            blobs.values.filter { it.queueId == queue.queueId }.take(pageSize)
        }

    override suspend fun deleteMessage(queue: RelayQueueCredentials, messageId: String) {
        synchronized(this) {
            if (queue.queueId in unreachableQueues) throw java.io.IOException("relay unreachable")
            if (blobs[messageId]?.queueId == queue.queueId) blobs.remove(messageId)
        }
    }

    @Synchronized
    fun queued(queueId: String): List<RelayEncryptedBlob> = blobs.values.filter { it.queueId == queueId }

    @Synchronized
    fun inject(blob: RelayEncryptedBlob) {
        blobs[blob.messageId] = blob
    }

    @Synchronized
    fun take(queueId: String): List<RelayEncryptedBlob> = queued(queueId).also { taken -> taken.forEach { blobs.remove(it.messageId) } }
}

/** One device running the v2 pipeline over in-memory stores and a shared [FakeRelay]. */
class V2TestDevice(val name: String, relay: FakeRelay, clock: () -> Long) {
    val records = InMemorySignalRecords().also { LocalSignalIdentityProvisioner(it).provisionIfAbsent() }
    val signalStore = EncryptedSignalProtocolStore(records, records, records, records, records, records)
    val sessions = SignalSessions(signalStore)
    val store = InMemoryConversationV2Store()
    val queue = RelayQueueCredentials.generate()
    val queueId = queue.queueId
    val service = ConversationV2Service(
        sessions = sessions,
        invitationKeys = InvitationPreKeyFactory(signalStore),
        store = store,
        transactions = SnapshotTransactionRunner(records, store),
        transport = relay,
        localReceiveQueue = queue,
        localDisplayName = { name },
        now = clock,
    )
}
