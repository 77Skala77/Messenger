package app.securemessenger.storage

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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Callable

/**
 * Room transactions for v2 protocol operations. Room joins every blocking DAO call made on the
 * same thread inside the block, including the libsignal store writes, into one SQLite
 * transaction, so a decrypted message is never stored without its ratchet step or vice versa.
 */
class RoomProtocolTransactionRunner(private val database: SecureMessengerDatabase) : ProtocolTransactionRunner {
    override fun <T> inTransaction(block: () -> T): T = database.runInTransaction(Callable { block() })
}

/**
 * v2 conversation state (DEC-019). Names, QR text, and message bodies are vault-encrypted with
 * AAD bound to their row, so a value copied into another row fails to decrypt. Blocking by
 * design. Used by debug builds only until the independent review of Phase 5 (DEC-021).
 */
class EncryptedConversationV2Repository(
    private val dao: SecureMessengerDao,
    private val vault: LocalVaultCipher,
    private val newMessageId: () -> String = { UUID.randomUUID().toString() },
) : ConversationV2Store {

    override fun findRoute(localRoutingTagHex: String): V2Route? =
        dao.getV2ContactByTag(localRoutingTagHex)?.let { V2Route.ToContact(readContact(it)) }
            ?: dao.getV2ContactRequestByTag(localRoutingTagHex)?.let { V2Route.ToRequest(readRequest(it)) }
            ?: dao.getUnusedV2InvitationByTag(localRoutingTagHex)?.let { V2Route.ToInvitation(readInvitation(it)) }

    override fun isEnvelopeAccepted(digestHex: String): Boolean = dao.countV2AcceptedEnvelope(digestHex) > 0

    override fun recordAcceptedEnvelope(digestHex: String, acceptedAtEpochMillis: Long): Boolean =
        dao.insertV2AcceptedEnvelope(V2AcceptedEnvelopeEntity(digestHex, acceptedAtEpochMillis)) != -1L

    override fun forgetAcceptedEnvelopesBefore(cutoffEpochMillis: Long): Int = dao.deleteV2AcceptedEnvelopesBefore(cutoffEpochMillis)

    override fun saveInvitation(invitation: V2Invitation) {
        dao.insertV2Invitation(
            V2InvitationEntity(
                invitationId = invitation.invitationId,
                localRoutingTagHex = invitation.localRoutingTagHex,
                signedPreKeyId = invitation.signedPreKeyId,
                oneTimePreKeyId = invitation.oneTimePreKeyId,
                kyberPreKeyId = invitation.kyberPreKeyId,
                mailboxQueueId = invitation.mailboxQueueId,
                encryptedCodeText = vault.encrypt(invitation.codeText.toByteArray(), aad(INVITATION_CODE, invitation.invitationId)),
                sealedBundle = invitation.sealedBundle,
                createdAtEpochMillis = invitation.createdAtEpochMillis,
                expiresAtEpochMillis = invitation.expiresAtEpochMillis,
                usedByRequestId = invitation.usedByRequestId,
            ),
        )
    }

    override fun invitation(invitationId: String): V2Invitation? = dao.getV2Invitation(invitationId)?.let(::readInvitation)

    override fun latestUsableInvitation(nowEpochMillis: Long): V2Invitation? =
        dao.getLatestUsableV2Invitation(nowEpochMillis)?.let(::readInvitation)

    override fun markInvitationUsed(invitationId: String, requestId: String): Boolean =
        dao.markV2InvitationUsed(invitationId, requestId) == 1

    override fun invitationUsedBy(requestId: String): V2Invitation? = dao.getV2InvitationUsedBy(requestId)?.let(::readInvitation)

    override fun expiredUnusedInvitations(nowEpochMillis: Long): List<V2Invitation> =
        dao.getExpiredUnusedV2Invitations(nowEpochMillis).map(::readInvitation)

    override fun deleteInvitation(invitationId: String) = dao.deleteV2Invitation(invitationId)

    override fun saveRequest(request: V2ContactRequest) {
        dao.insertV2ContactRequest(
            V2ContactRequestEntity(
                requestId = request.requestId,
                invitationId = request.invitationId,
                identityKey = request.identityKey,
                peerQueueId = request.peerQueueId,
                peerRoutingTagHex = request.peerRoutingTagHex,
                localRoutingTagHex = request.localRoutingTagHex,
                encryptedClaimedName = vault.encrypt(
                    request.claimedDisplayName.toByteArray(),
                    aad(REQUEST_NAME, request.requestId),
                ),
                receivedAtEpochMillis = request.receivedAtEpochMillis,
            ),
        )
    }

    override fun request(requestId: String): V2ContactRequest? = dao.getV2ContactRequest(requestId)?.let(::readRequest)

    override fun requests(): List<V2ContactRequest> = dao.getV2ContactRequests().map(::readRequest)

    override fun deleteRequest(requestId: String) = dao.deleteV2ContactRequest(requestId)

    override fun saveContact(contact: V2Contact) {
        dao.insertV2Contact(
            V2ContactEntity(
                contactId = contact.contactId,
                encryptedDisplayName = vault.encrypt(contact.localDisplayName.toByteArray(), aad(CONTACT_NAME, contact.contactId)),
                identityKey = contact.identityKey,
                peerQueueId = contact.peerQueueId,
                peerRoutingTagHex = contact.peerRoutingTagHex,
                localRoutingTagHex = contact.localRoutingTagHex,
                state = contact.state.name,
                createdAtEpochMillis = contact.createdAtEpochMillis,
                verifiedAtEpochMillis = contact.verifiedAtEpochMillis,
                pinnedAtEpochMillis = contact.pinnedAtEpochMillis,
                mutedUntilEpochMillis = contact.mutedUntilEpochMillis,
            ),
        )
    }

    override fun contact(contactId: String): V2Contact? = dao.getV2Contact(contactId)?.let(::readContact)

    override fun contactWithIdentity(identityKey: ByteArray): V2Contact? =
        dao.getV2ContactByIdentity(identityKey)?.let(::readContact)

    override fun setContactState(contactId: String, state: V2ContactState) {
        check(dao.updateV2ContactState(contactId, state.name) == 1) { "Unknown v2 contact" }
        if (state == V2ContactState.KEY_CHANGED) dao.updateV2ContactVerified(contactId, null)
    }

    override fun setContactVerified(contactId: String, verifiedAtEpochMillis: Long?): Boolean =
        dao.updateV2ContactVerified(contactId, verifiedAtEpochMillis) == 1

    override fun setContactPinned(contactId: String, pinnedAtEpochMillis: Long?): Boolean =
        dao.updateV2ContactPinned(contactId, pinnedAtEpochMillis) == 1

    override fun setContactMuted(contactId: String, mutedUntilEpochMillis: Long?): Boolean =
        dao.updateV2ContactMuted(contactId, mutedUntilEpochMillis) == 1

    override fun deleteContact(contactId: String): Boolean = dao.deleteV2Contact(contactId) == 1

    override fun hasText(contactId: String, innerMessageIdHex: String): Boolean = dao.countV2Texts(contactId, innerMessageIdHex) > 0

    override fun latestIncomingTextTime(contactId: String): Long? = dao.getLatestV2IncomingTextTime(contactId)

    override fun clearTexts(contactId: String): Int {
        // The pieces first: the query finds them through the messages it is about to delete.
        dao.deleteV2SettledMediaChunks(contactId)
        return dao.deleteV2SettledTexts(contactId)
    }

    override fun storeIncomingText(
        contactId: String,
        innerMessageIdHex: String,
        text: String,
        createdAtEpochMillis: Long,
        replyToInnerIdHex: String?,
    ): Boolean {
        val messageId = newMessageId()
        return dao.insertV2Message(
            V2MessageEntity(
                messageId = messageId,
                contactId = contactId,
                outgoing = false,
                kind = V2MessageKind.TEXT.name,
                innerMessageIdHex = innerMessageIdHex,
                encryptedBody = encryptBody(text, messageId, contactId, outgoing = false),
                status = V2MessageStatus.RECEIVED.name,
                createdAtEpochMillis = createdAtEpochMillis,
                peerQueueId = null,
                envelope = null,
                deliveryAttemptCount = 0,
                nextRetryAtEpochMillis = null,
                isRead = false,
                replyToInnerIdHex = replyToInnerIdHex,
            ),
        ) != -1L
    }

    override fun incomingTexts(contactId: String): List<V2StoredMessage> =
        dao.getV2IncomingMessages(contactId).filter { it.kind in VISIBLE_KINDS }.map(::readMessage)

    override fun texts(contactId: String): List<V2StoredMessage> = dao.getV2Texts(contactId).map(::readMessage)

    override fun lastText(contactId: String): V2StoredMessage? = dao.getLastV2Text(contactId)?.let(::readMessage)

    override fun unreadCounts(): Map<String, Int> =
        dao.getV2UnreadCounts().associate { row -> row.contactId to row.unreadCount }

    override fun markRead(contactId: String): Int = dao.markV2MessagesRead(contactId)

    override fun contacts(): List<V2Contact> = dao.getV2Contacts().map(::readContact)

    override fun enqueueOutgoing(
        envelope: V2OutgoingEnvelope,
        visibleText: String?,
        createdAtEpochMillis: Long,
        replyToInnerIdHex: String?,
    ) {
        require((envelope.kind == V2MessageKind.TEXT) == (visibleText != null)) { "Only TEXT carries visible text" }
        require(replyToInnerIdHex == null || envelope.kind == V2MessageKind.TEXT) { "Only TEXT answers a text" }
        val inserted = dao.insertV2Message(
            V2MessageEntity(
                messageId = envelope.messageId,
                contactId = envelope.contactId,
                outgoing = true,
                kind = envelope.kind.name,
                innerMessageIdHex = envelope.innerMessageIdHex,
                encryptedBody = visibleText?.let { encryptBody(it, envelope.messageId, envelope.contactId, outgoing = true) },
                status = V2MessageStatus.PENDING.name,
                createdAtEpochMillis = createdAtEpochMillis,
                peerQueueId = envelope.peerQueueId,
                envelope = envelope.envelope,
                deliveryAttemptCount = envelope.attemptCount,
                nextRetryAtEpochMillis = null,
                // The user wrote it: nothing to see.
                isRead = true,
                replyToInnerIdHex = replyToInnerIdHex,
            ),
        )
        // Inner IDs are random 128-bit values: a collision means a bug, and silently dropping the
        // ciphertext would lose a ratchet step that was already committed.
        check(inserted != -1L) { "Outgoing v2 message already exists" }
    }

    override fun outgoing(messageId: String): V2OutgoingEnvelope? =
        dao.getV2OutgoingMessage(messageId)?.let(::readOutgoing)

    override fun unsentOutgoing(): List<V2OutgoingEnvelope> = dao.getUnsentV2OutgoingMessages().mapNotNull(::readOutgoing)

    override fun recordSent(messageId: String, attemptCount: Int) = dao.markV2MessageSent(messageId, attemptCount)

    override fun recordFailed(messageId: String, attemptCount: Int, nextAttemptAtEpochMillis: Long) =
        dao.markV2MessageFailed(messageId, attemptCount, nextAttemptAtEpochMillis)

    override fun markDelivered(contactId: String, innerMessageIdHex: String): Boolean =
        dao.markV2MessageDelivered(contactId, innerMessageIdHex) > 0

    override fun deleteMessagesFor(contactId: String) {
        dao.deleteV2MediaChunksOfContact(contactId)
        dao.deleteV2MessagesFor(contactId)
    }

    // --- Voice messages (Room v12, DEC-036). The note (with the key and the queue's read secret) is
    // vault-encrypted with an AAD of its own; the pieces are stored as they travel. ---

    override fun enqueueOutgoingVoice(
        envelope: V2OutgoingEnvelope,
        note: V2VoiceNote,
        chunks: List<ByteArray>,
        createdAtEpochMillis: Long,
        replyToInnerIdHex: String?,
    ) {
        require(envelope.kind == V2MessageKind.VOICE) { "Only VOICE carries a voice note" }
        require(chunks.size == note.chunkCount) { "The pieces do not match the note" }
        val inserted = dao.insertV2Message(
            V2MessageEntity(
                messageId = envelope.messageId,
                contactId = envelope.contactId,
                outgoing = true,
                kind = V2MessageKind.VOICE.name,
                innerMessageIdHex = envelope.innerMessageIdHex,
                encryptedBody = encryptNote(note, envelope.messageId, envelope.contactId, outgoing = true),
                status = V2MessageStatus.PENDING.name,
                createdAtEpochMillis = createdAtEpochMillis,
                peerQueueId = envelope.peerQueueId,
                envelope = envelope.envelope,
                deliveryAttemptCount = envelope.attemptCount,
                nextRetryAtEpochMillis = null,
                isRead = true,
                replyToInnerIdHex = replyToInnerIdHex,
                mediaState = V2MediaState.UPLOADING.name,
                mediaProgress = 0,
            ),
        )
        check(inserted != -1L) { "Outgoing v2 message already exists" }
        chunks.forEachIndexed { index, piece -> check(dao.insertV2MediaChunk(V2MediaChunkEntity(envelope.messageId, index, piece)) != -1L) }
    }

    override fun pendingVoiceUploads(): List<V2PendingUpload> =
        dao.getV2PendingVoiceUploads().map { entity -> V2PendingUpload(entity.messageId, readNote(entity), entity.mediaProgress) }

    override fun recordVoiceUploaded(messageId: String, uploaded: Int) {
        val entity = dao.getV2Message(messageId) ?: return
        // Two syncs may upload at once (the screen's and the background's): progress only moves forward.
        if (entity.mediaState != V2MediaState.UPLOADING.name || uploaded <= entity.mediaProgress) return
        val state = if (uploaded >= readNote(entity).chunkCount) V2MediaState.READY else V2MediaState.UPLOADING
        dao.updateV2MediaProgress(messageId, uploaded, state.name)
    }

    override fun storeIncomingVoice(
        contactId: String,
        innerMessageIdHex: String,
        note: V2VoiceNote,
        createdAtEpochMillis: Long,
        replyToInnerIdHex: String?,
    ): Boolean {
        val messageId = newMessageId()
        return dao.insertV2Message(
            V2MessageEntity(
                messageId = messageId,
                contactId = contactId,
                outgoing = false,
                kind = V2MessageKind.VOICE.name,
                innerMessageIdHex = innerMessageIdHex,
                encryptedBody = encryptNote(note, messageId, contactId, outgoing = false),
                status = V2MessageStatus.RECEIVED.name,
                createdAtEpochMillis = createdAtEpochMillis,
                peerQueueId = null,
                envelope = null,
                deliveryAttemptCount = 0,
                nextRetryAtEpochMillis = null,
                isRead = false,
                replyToInnerIdHex = replyToInnerIdHex,
                mediaState = V2MediaState.DOWNLOADING.name,
            ),
        ) != -1L
    }

    override fun pendingVoiceDownloads(): List<V2PendingDownload> =
        dao.getV2PendingVoiceDownloads().map { entity ->
            V2PendingDownload(entity.messageId, entity.contactId, entity.innerMessageIdHex, readNote(entity), entity.createdAtEpochMillis)
        }

    override fun storeVoiceChunk(messageId: String, index: Int, chunk: ByteArray): Boolean =
        dao.insertV2MediaChunk(V2MediaChunkEntity(messageId, index, chunk)) != -1L

    override fun voiceChunk(messageId: String, index: Int): ByteArray? = dao.getV2MediaChunk(messageId, index)?.piece

    override fun storedVoiceChunkIndices(messageId: String): Set<Int> = dao.getV2MediaChunkIndices(messageId).toSet()

    override fun setVoiceState(messageId: String, state: V2MediaState) {
        dao.updateV2MediaState(messageId, state.name)
    }

    override fun setVoiceListened(messageId: String): Boolean = dao.markV2MediaListened(messageId) > 0

    override fun deleteVoiceChunks(messageId: String) = dao.deleteV2MediaChunks(messageId)

    override fun voiceNote(messageId: String): V2VoiceNote? =
        dao.getV2Message(messageId)?.takeIf { it.kind == V2MessageKind.VOICE.name }?.let(::readNote)

    override fun voiceState(messageId: String): V2MediaState? =
        dao.getV2Message(messageId)?.takeIf { it.kind == V2MessageKind.VOICE.name }?.mediaState?.let(V2MediaState::valueOf)

    override fun voiceIsOutgoing(messageId: String): Boolean? =
        dao.getV2Message(messageId)?.takeIf { it.kind == V2MessageKind.VOICE.name }?.outgoing

    override fun message(messageId: String): V2StoredMessage? =
        dao.getV2Message(messageId)?.takeIf { it.kind in VISIBLE_KINDS }?.let(::readMessage)

    override fun messageByInnerId(contactId: String, innerMessageIdHex: String, outgoing: Boolean): V2StoredMessage? =
        dao.getV2MessageByInnerId(contactId, innerMessageIdHex, outgoing)?.let(::readMessage)

    override fun deleteMessage(messageId: String): Boolean {
        val entity = dao.getV2Message(messageId)?.takeIf { it.kind in VISIBLE_KINDS } ?: return false
        dao.deleteV2MediaChunks(entity.messageId)
        return dao.deleteV2Message(entity.messageId) == 1
    }

    /** Decrypts a stored body for display; used by the UI layer, never by the relay path. */
    fun readBody(entity: V2MessageEntity): String? =
        entity.encryptedBody?.let { vault.decrypt(it, bodyAad(entity.messageId, entity.contactId, entity.outgoing)).toString(Charsets.UTF_8) }

    private fun readInvitation(entity: V2InvitationEntity) = V2Invitation(
        invitationId = entity.invitationId,
        localRoutingTagHex = entity.localRoutingTagHex,
        signedPreKeyId = entity.signedPreKeyId,
        oneTimePreKeyId = entity.oneTimePreKeyId,
        kyberPreKeyId = entity.kyberPreKeyId,
        mailboxQueueId = entity.mailboxQueueId,
        codeText = vault.decrypt(entity.encryptedCodeText, aad(INVITATION_CODE, entity.invitationId)).toString(Charsets.UTF_8),
        sealedBundle = entity.sealedBundle,
        createdAtEpochMillis = entity.createdAtEpochMillis,
        expiresAtEpochMillis = entity.expiresAtEpochMillis,
        usedByRequestId = entity.usedByRequestId,
    )

    private fun readRequest(entity: V2ContactRequestEntity) = V2ContactRequest(
        requestId = entity.requestId,
        invitationId = entity.invitationId,
        identityKey = entity.identityKey,
        peerQueueId = entity.peerQueueId,
        peerRoutingTagHex = entity.peerRoutingTagHex,
        localRoutingTagHex = entity.localRoutingTagHex,
        claimedDisplayName = vault.decrypt(entity.encryptedClaimedName, aad(REQUEST_NAME, entity.requestId)).toString(Charsets.UTF_8),
        receivedAtEpochMillis = entity.receivedAtEpochMillis,
    )

    private fun readContact(entity: V2ContactEntity) = V2Contact(
        contactId = entity.contactId,
        localDisplayName = vault.decrypt(entity.encryptedDisplayName, aad(CONTACT_NAME, entity.contactId)).toString(Charsets.UTF_8),
        identityKey = entity.identityKey,
        peerQueueId = entity.peerQueueId,
        peerRoutingTagHex = entity.peerRoutingTagHex,
        localRoutingTagHex = entity.localRoutingTagHex,
        state = V2ContactState.valueOf(entity.state),
        createdAtEpochMillis = entity.createdAtEpochMillis,
        verifiedAtEpochMillis = entity.verifiedAtEpochMillis,
        pinnedAtEpochMillis = entity.pinnedAtEpochMillis,
        mutedUntilEpochMillis = entity.mutedUntilEpochMillis,
    )

    private fun readMessage(entity: V2MessageEntity): V2StoredMessage {
        val voice = entity.kind == V2MessageKind.VOICE.name
        return V2StoredMessage(
            messageId = entity.messageId,
            contactId = entity.contactId,
            outgoing = entity.outgoing,
            innerMessageIdHex = entity.innerMessageIdHex,
            text = if (voice) "" else readBody(entity).orEmpty(),
            status = V2MessageStatus.valueOf(entity.status),
            createdAtEpochMillis = entity.createdAtEpochMillis,
            replyToInnerIdHex = entity.replyToInnerIdHex,
            voice = if (voice) {
                val note = readNote(entity)
                V2VoiceInfo(note.durationMillis, note.waveform, V2MediaState.valueOf(checkNotNull(entity.mediaState)), note.once, entity.mediaListened)
            } else {
                null
            },
        )
    }

    private fun readOutgoing(entity: V2MessageEntity): V2OutgoingEnvelope? {
        val envelope = entity.envelope ?: return null
        val peerQueueId = entity.peerQueueId ?: return null
        return V2OutgoingEnvelope(
            messageId = entity.messageId,
            contactId = entity.contactId,
            kind = V2MessageKind.valueOf(entity.kind),
            innerMessageIdHex = entity.innerMessageIdHex,
            peerQueueId = peerQueueId,
            envelope = envelope,
            attemptCount = entity.deliveryAttemptCount,
            nextAttemptAtEpochMillis = entity.nextRetryAtEpochMillis,
            waitsForMedia = entity.mediaState == V2MediaState.UPLOADING.name,
        )
    }

    private fun encryptNote(note: V2VoiceNote, messageId: String, contactId: String, outgoing: Boolean): ByteArray {
        val plaintext = encodeNote(note)
        return try {
            vault.encrypt(plaintext, noteAad(messageId, contactId, outgoing))
        } finally {
            plaintext.fill(0)
        }
    }

    private fun readNote(entity: V2MessageEntity): V2VoiceNote {
        val blob = entity.encryptedBody ?: throw VaultRecordRejectedException()
        val plaintext = vault.decrypt(blob, noteAad(entity.messageId, entity.contactId, entity.outgoing))
        return try {
            decodeNote(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun noteAad(messageId: String, contactId: String, outgoing: Boolean): ByteArray =
        aad(VOICE_NOTE, messageId, contactId, if (outgoing) "out" else "in")

    private fun encryptBody(text: String, messageId: String, contactId: String, outgoing: Boolean): ByteArray {
        val plaintext = text.toByteArray(Charsets.UTF_8)
        return try {
            vault.encrypt(plaintext, bodyAad(messageId, contactId, outgoing))
        } finally {
            plaintext.fill(0)
        }
    }

    private fun bodyAad(messageId: String, contactId: String, outgoing: Boolean): ByteArray =
        aad(MESSAGE_BODY, messageId, contactId, if (outgoing) "out" else "in")

    private fun aad(type: String, vararg parts: String): ByteArray =
        ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeUTF(AAD_VERSION)
                output.writeUTF(type)
                parts.forEach(output::writeUTF)
            }
            buffer.toByteArray()
        }

    private companion object {
        const val AAD_VERSION = "conversation-v2-record-v1"
        const val INVITATION_CODE = "invitation-code"
        const val REQUEST_NAME = "request-claimed-name"
        const val CONTACT_NAME = "contact-name"
        const val MESSAGE_BODY = "message-body"
        const val VOICE_NOTE = "voice-note"
        const val NOTE_FORMAT = 1
        const val NOTE_SECRET_BYTES = 32
        const val NOTE_MAX_WAVEFORM = 1_024

        /** What a chat shows: texts and voice messages (DEC-036); control messages are not shown. */
        val VISIBLE_KINDS = setOf(V2MessageKind.TEXT.name, V2MessageKind.VOICE.name)

        /**
         * The stored form of a note: format 1, the three 32-byte values, sizes and length, the bars, the
         * codec, and 1 for a one-time recording (else 0).
         */
        fun encodeNote(note: V2VoiceNote): ByteArray =
            ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { output ->
                    output.writeByte(NOTE_FORMAT)
                    output.write(note.queueReadSecret)
                    output.write(note.key)
                    output.write(note.digest)
                    output.writeInt(note.plaintextSize)
                    output.writeInt(note.chunkCount)
                    output.writeInt(note.durationMillis)
                    output.writeShort(note.waveform.size)
                    output.write(note.waveform)
                    output.writeByte(note.codec)
                    output.writeByte(if (note.once) 1 else 0)
                }
                buffer.toByteArray()
            }

        /** A stored note that does not read back is a damaged row (DEC-023), never "no note". */
        fun decodeNote(bytes: ByteArray): V2VoiceNote =
            try {
                DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                    require(input.readUnsignedByte() == NOTE_FORMAT) { "Unknown note format" }
                    val queueReadSecret = ByteArray(NOTE_SECRET_BYTES).also(input::readFully)
                    val key = ByteArray(NOTE_SECRET_BYTES).also(input::readFully)
                    val digest = ByteArray(NOTE_SECRET_BYTES).also(input::readFully)
                    val plaintextSize = input.readInt()
                    val chunkCount = input.readInt()
                    val durationMillis = input.readInt()
                    val waveformSize = input.readUnsignedShort()
                    require(waveformSize <= NOTE_MAX_WAVEFORM) { "Invalid note" }
                    val waveform = ByteArray(waveformSize).also(input::readFully)
                    val codec = input.readUnsignedByte()
                    val once = when (input.readUnsignedByte()) {
                        0 -> false
                        1 -> true
                        else -> throw IllegalArgumentException("Invalid note")
                    }
                    require(input.available() == 0) { "Trailing note bytes" }
                    V2VoiceNote(queueReadSecret, key, digest, plaintextSize, chunkCount, durationMillis, waveform, codec, once)
                }
            } catch (failure: IOException) {
                throw VaultRecordRejectedException(failure)
            } catch (failure: IllegalArgumentException) {
                throw VaultRecordRejectedException(failure)
            }
    }
}
