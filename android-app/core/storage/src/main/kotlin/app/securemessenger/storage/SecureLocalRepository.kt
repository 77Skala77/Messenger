package app.securemessenger.storage

import app.securemessenger.crypto.CiphertextPackageCodec
import app.securemessenger.crypto.EncryptedSessionMessage
import app.securemessenger.crypto.EstablishedSession
import app.securemessenger.domain.Contact
import app.securemessenger.domain.DevicePublicIdentity
import app.securemessenger.domain.VerificationStatus
import java.nio.ByteBuffer

class SecureLocalRepository(
    private val dao: SecureMessengerDao,
    private val vault: LocalVaultCipher,
) {
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
        val encoded = ByteBuffer.allocate(Int.SIZE_BYTES + session.rootKey.size)
            .putInt(session.rootKey.size)
            .put(session.rootKey)
            .array()
        try {
            dao.putSession(
                EncryptedSessionEntity(contactId, vault.encrypt(encoded, contactId.toByteArray()), System.currentTimeMillis()),
            )
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
                encryptedPayload = CiphertextPackageCodec.encode(message.ciphertextPackage),
                encodedAssociatedData = message.associatedData,
                outgoing = outgoing,
                status = status,
                createdAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun wipeLocalData() {
        dao.deleteMessages()
        dao.deleteSessions()
        dao.deleteContacts()
        dao.deleteProfile()
    }

    private companion object {
        val PROFILE_AAD = "local-profile-v1".toByteArray()
    }
}
