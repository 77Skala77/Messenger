package app.securemessenger.storage

import app.securemessenger.signalstore.ConversationV2Store
import app.securemessenger.signalstore.ProtocolTransactionRunner
import app.securemessenger.signalstore.V2Contact
import app.securemessenger.signalstore.V2ContactRequest
import app.securemessenger.signalstore.V2ContactState
import app.securemessenger.signalstore.V2Invitation
import app.securemessenger.signalstore.V2MessageKind
import app.securemessenger.signalstore.V2MessageStatus
import app.securemessenger.signalstore.V2OutgoingEnvelope
import app.securemessenger.signalstore.V2Route
import app.securemessenger.signalstore.V2StoredMessage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
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
            ),
        )
    }

    override fun contact(contactId: String): V2Contact? = dao.getV2Contact(contactId)?.let(::readContact)

    override fun contactWithIdentity(identityKey: ByteArray): V2Contact? =
        dao.getV2ContactByIdentity(identityKey)?.let(::readContact)

    override fun setContactState(contactId: String, state: V2ContactState) {
        check(dao.updateV2ContactState(contactId, state.name) == 1) { "Unknown v2 contact" }
    }

    override fun storeIncomingText(
        contactId: String,
        innerMessageIdHex: String,
        text: String,
        receivedAtEpochMillis: Long,
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
                createdAtEpochMillis = receivedAtEpochMillis,
                peerQueueId = null,
                envelope = null,
                deliveryAttemptCount = 0,
                nextRetryAtEpochMillis = null,
            ),
        ) != -1L
    }

    override fun incomingTexts(contactId: String): List<V2StoredMessage> =
        dao.getV2IncomingMessages(contactId).filter { it.kind == V2MessageKind.TEXT.name }.map(::readMessage)

    override fun texts(contactId: String): List<V2StoredMessage> = dao.getV2Texts(contactId).map(::readMessage)

    override fun contacts(): List<V2Contact> = dao.getV2Contacts().map(::readContact)

    override fun enqueueOutgoing(envelope: V2OutgoingEnvelope, visibleText: String?, createdAtEpochMillis: Long) {
        require((envelope.kind == V2MessageKind.TEXT) == (visibleText != null)) { "Only TEXT carries visible text" }
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

    override fun deleteMessagesFor(contactId: String) = dao.deleteV2MessagesFor(contactId)

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
    )

    private fun readMessage(entity: V2MessageEntity) = V2StoredMessage(
        messageId = entity.messageId,
        contactId = entity.contactId,
        outgoing = entity.outgoing,
        innerMessageIdHex = entity.innerMessageIdHex,
        text = readBody(entity).orEmpty(),
        status = V2MessageStatus.valueOf(entity.status),
        createdAtEpochMillis = entity.createdAtEpochMillis,
    )

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
        )
    }

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
    }
}
