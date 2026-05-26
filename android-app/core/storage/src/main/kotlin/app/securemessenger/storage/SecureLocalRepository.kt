package app.securemessenger.storage

import app.securemessenger.crypto.EncryptedSessionMessage
import app.securemessenger.crypto.EstablishedSession
import app.securemessenger.crypto.SessionMessageCodec
import app.securemessenger.crypto.SessionStateCodec
import app.securemessenger.delivery.DeliveryPersistence
import app.securemessenger.delivery.StoredOutgoingMessage
import app.securemessenger.domain.Contact
import app.securemessenger.domain.ContactDevice
import app.securemessenger.domain.DeviceApprovalStatus
import app.securemessenger.domain.DevicePublicIdentity
import app.securemessenger.domain.Invitation
import app.securemessenger.domain.MessageStatus
import app.securemessenger.domain.VerificationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SecureLocalRepository(
    private val dao: SecureMessengerDao,
    private val vault: LocalVaultCipher,
) : DeliveryPersistence {
    suspend fun storeProfile(displayName: String, deviceId: String) {
        val encryptedName = vault.encrypt(displayName.toByteArray(), PROFILE_AAD)
        dao.putProfile(EncryptedProfileEntity("local", encryptedName, deviceId))
    }

    suspend fun storeContact(contact: Contact) {
        val encryptedName = vault.encrypt(contact.localDisplayName.toByteArray(), contact.contactId.toByteArray())
        dao.putContact(
            ContactEntity(
                contact.contactId,
                encryptedName,
                contact.queueId,
                contact.identity.deviceId,
                contact.identity.encodedPublicKey,
                contact.identity.fingerprint,
                contact.verificationStatus.name,
            ),
        )
        val priorDevice = dao.getContactDevice(contact.contactId, contact.identity.deviceId)
        val approvalStatus = when {
            contact.verificationStatus == VerificationStatus.KEY_CHANGED -> DeviceApprovalStatus.KEY_CHANGED
            priorDevice?.approvalStatus == DeviceApprovalStatus.REVOKED.name -> DeviceApprovalStatus.REVOKED
            contact.verificationStatus == VerificationStatus.VERIFIED -> DeviceApprovalStatus.VERIFIED
            else -> DeviceApprovalStatus.PRIMARY_UNVERIFIED
        }
        dao.putContactDevice(
            ContactDeviceEntity(
                contactId = contact.contactId,
                deviceId = contact.identity.deviceId,
                queueId = contact.queueId,
                encodedPublicKey = contact.identity.encodedPublicKey,
                fingerprint = contact.identity.fingerprint,
                approvalStatus = approvalStatus.name,
                firstSeenEpochMillis = priorDevice?.firstSeenEpochMillis ?: System.currentTimeMillis(),
            ),
        )
    }

    fun readContact(entity: ContactEntity): Contact {
        val localName = vault.decrypt(entity.encryptedLocalDisplayName, entity.contactId.toByteArray())
            .toString(Charsets.UTF_8)
        return Contact(
            contactId = entity.contactId,
            localDisplayName = localName,
            queueId = entity.queueId,
            identity = DevicePublicIdentity(entity.deviceId, entity.encodedPublicKey, entity.fingerprint),
            verificationStatus = VerificationStatus.valueOf(entity.verificationStatus),
        )
    }

    suspend fun storeSession(contactId: String, session: EstablishedSession) {
        val encoded = SessionStateCodec.encode(session)
        try {
            dao.putSession(
                EncryptedSessionEntity(contactId, vault.encrypt(encoded, contactId.toByteArray()), System.currentTimeMillis()),
            )
        } finally {
            encoded.fill(0)
        }
    }

    suspend fun loadSession(contactId: String): EstablishedSession? {
        val stored = dao.getSession(contactId) ?: return null
        val encoded = vault.decrypt(stored.encryptedState, contactId.toByteArray())
        return try {
            SessionStateCodec.decode(encoded)
        } finally {
            encoded.fill(0)
        }
    }

    suspend fun storeEncryptedMessage(
        messageId: String,
        contactId: String,
        message: EncryptedSessionMessage,
        outgoing: Boolean,
        status: String,
    ) {
        dao.putMessage(
            EncryptedMessageEntity(
                messageId = messageId,
                contactId = contactId,
                encryptedPayload = SessionMessageCodec.encode(message),
                encodedAssociatedData = message.associatedData,
                outgoing = outgoing,
                status = status,
                createdAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    fun readEncryptedMessage(entity: EncryptedMessageEntity): EncryptedSessionMessage =
        SessionMessageCodec.decode(entity.encryptedPayload)

    override suspend fun outgoingMessage(messageId: String): StoredOutgoingMessage? =
        dao.getOutgoingMessage(messageId)?.let { toStoredOutgoing(it) }

    override suspend fun dueOutgoingMessages(nowEpochMillis: Long): List<StoredOutgoingMessage> =
        dao.getDueOutgoingMessages(nowEpochMillis).mapNotNull { entity ->
            runCatching { toStoredOutgoing(entity) }.getOrNull()
        }

    override suspend fun recordSent(messageId: String, attemptCount: Int) =
        dao.updateDeliveryState(messageId, MessageStatus.SENT.name, attemptCount, null)

    override suspend fun recordFailed(messageId: String, attemptCount: Int, nextAttemptAtEpochMillis: Long) =
        dao.updateDeliveryState(messageId, MessageStatus.FAILED.name, attemptCount, nextAttemptAtEpochMillis)

    override suspend fun acceptIncoming(
        messageId: String,
        contactId: String,
        session: EstablishedSession,
        message: EncryptedSessionMessage,
    ): Boolean =
        dao.acceptAndStoreIncoming(
            AcceptedReplayEntity(
                conversationId = session.conversationId,
                senderDeviceId = session.remoteDeviceId,
                messageNumber = message.messageNumber,
                acceptedAtEpochMillis = System.currentTimeMillis(),
            ),
            EncryptedMessageEntity(
                messageId = messageId,
                contactId = contactId,
                encryptedPayload = SessionMessageCodec.encode(message),
                encodedAssociatedData = message.associatedData,
                outgoing = false,
                status = MessageStatus.RECEIVED.name,
                createdAtEpochMillis = System.currentTimeMillis(),
            ),
        )

    fun observeDevices(contactId: String): Flow<List<ContactDevice>> =
        dao.observeContactDevices(contactId).map { devices ->
            devices.map(::readDevice)
        }

    suspend fun deliveryDevices(contactId: String): List<ContactDevice> =
        dao.getContactDevices(contactId).map(::readDevice)

    suspend fun stageNewDevice(contactId: String, invitation: Invitation): DeviceApprovalStatus {
        val identity = invitation.identity
        val existing = dao.getContactDevice(contactId, identity.deviceId)
        val status = when {
            existing == null -> DeviceApprovalStatus.PENDING_APPROVAL
            existing.fingerprint != identity.fingerprint -> DeviceApprovalStatus.KEY_CHANGED
            else -> DeviceApprovalStatus.valueOf(existing.approvalStatus)
        }
        dao.putContactDevice(
            ContactDeviceEntity(
                contactId = contactId,
                deviceId = identity.deviceId,
                queueId = invitation.queueId,
                encodedPublicKey = identity.encodedPublicKey,
                fingerprint = identity.fingerprint,
                approvalStatus = status.name,
                firstSeenEpochMillis = existing?.firstSeenEpochMillis ?: System.currentTimeMillis(),
            ),
        )
        return status
    }

    suspend fun approveDevice(contactId: String, identity: DevicePublicIdentity) {
        val existing = dao.getContactDevice(contactId, identity.deviceId) ?: return
        dao.putContactDevice(existing.copy(approvalStatus = DeviceApprovalStatus.VERIFIED.name))
    }

    suspend fun revokeDevice(contactId: String, identity: DevicePublicIdentity) {
        val existing = dao.getContactDevice(contactId, identity.deviceId) ?: return
        dao.putContactDevice(existing.copy(approvalStatus = DeviceApprovalStatus.REVOKED.name))
    }

    private suspend fun toStoredOutgoing(entity: EncryptedMessageEntity): StoredOutgoingMessage {
        val contact = dao.getContact(entity.contactId)?.let(::readContact)
            ?: error("Outgoing message contact is missing")
        return StoredOutgoingMessage(
            messageId = entity.messageId,
            contact = contact,
            devices = dao.getContactDevices(entity.contactId).map(::readDevice),
            encryptedMessage = readEncryptedMessage(entity),
            attemptCount = entity.deliveryAttemptCount,
        )
    }

    private fun readDevice(entity: ContactDeviceEntity): ContactDevice =
        ContactDevice(
            contactId = entity.contactId,
            queueId = entity.queueId,
            identity = DevicePublicIdentity(entity.deviceId, entity.encodedPublicKey, entity.fingerprint),
            approvalStatus = DeviceApprovalStatus.valueOf(entity.approvalStatus),
            firstSeenEpochMillis = entity.firstSeenEpochMillis,
        )

    suspend fun wipeLocalData() {
        dao.deleteAcceptedReplay()
        dao.deleteMessages()
        dao.deleteSessions()
        dao.deleteContactDevices()
        dao.deleteContacts()
        dao.deleteProfile()
    }

    private companion object {
        val PROFILE_AAD = "local-profile-v1".toByteArray()
    }
}
