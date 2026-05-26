package app.securemessenger.storage

import app.securemessenger.signalstore.SignalIdentityStoreBoundary
import app.securemessenger.signalstore.SignalKyberPreKeyStoreBoundary
import app.securemessenger.signalstore.SignalPreKeyStoreBoundary
import app.securemessenger.signalstore.SignalProtocolAddress
import app.securemessenger.signalstore.SignalSessionStoreBoundary
import app.securemessenger.signalstore.SignalSignedPreKeyStoreBoundary
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * Storage-only spike implementation. It persists opaque record bytes after local vault
 * encryption; it does not establish Signal sessions or enable a message path.
 */
class EncryptedSignalStoreRepository(
    private val dao: SecureMessengerDao,
    private val vault: LocalVaultCipher,
) : SignalIdentityStoreBoundary,
    SignalSessionStoreBoundary,
    SignalPreKeyStoreBoundary,
    SignalSignedPreKeyStoreBoundary,
    SignalKyberPreKeyStoreBoundary {

    override suspend fun saveIdentityRecord(address: SignalProtocolAddress, serializedRecord: ByteArray) {
        dao.putSignalIdentityRecord(
            EncryptedSignalIdentityRecordEntity(
                address.recipientId,
                address.deviceId,
                vault.encrypt(serializedRecord, addressAad(IDENTITY, address)),
            ),
        )
    }

    override suspend fun loadIdentityRecord(address: SignalProtocolAddress): ByteArray? =
        dao.getSignalIdentityRecord(address.recipientId, address.deviceId)?.let {
            vault.decrypt(it.encryptedRecord, addressAad(IDENTITY, address))
        }

    override suspend fun deleteIdentityRecord(address: SignalProtocolAddress) =
        dao.deleteSignalIdentityRecord(address.recipientId, address.deviceId)

    override suspend fun saveSessionRecord(address: SignalProtocolAddress, serializedRecord: ByteArray) {
        dao.putSignalSessionRecord(
            EncryptedSignalSessionRecordEntity(
                address.recipientId,
                address.deviceId,
                vault.encrypt(serializedRecord, addressAad(SESSION, address)),
            ),
        )
    }

    override suspend fun loadSessionRecord(address: SignalProtocolAddress): ByteArray? =
        dao.getSignalSessionRecord(address.recipientId, address.deviceId)?.let {
            vault.decrypt(it.encryptedRecord, addressAad(SESSION, address))
        }

    override suspend fun deleteSessionRecord(address: SignalProtocolAddress) =
        dao.deleteSignalSessionRecord(address.recipientId, address.deviceId)

    override suspend fun savePreKeyRecord(preKeyId: Int, serializedRecord: ByteArray) {
        require(preKeyId >= 0)
        dao.putSignalPreKeyRecord(
            EncryptedSignalPreKeyRecordEntity(preKeyId, vault.encrypt(serializedRecord, integerAad(PRE_KEY, preKeyId))),
        )
    }

    override suspend fun loadPreKeyRecord(preKeyId: Int): ByteArray? =
        dao.getSignalPreKeyRecord(validRecordId(preKeyId))?.let {
            vault.decrypt(it.encryptedRecord, integerAad(PRE_KEY, preKeyId))
        }

    override suspend fun deletePreKeyRecord(preKeyId: Int) = dao.deleteSignalPreKeyRecord(validRecordId(preKeyId))

    override suspend fun saveSignedPreKeyRecord(signedPreKeyId: Int, serializedRecord: ByteArray) {
        require(signedPreKeyId >= 0)
        dao.putSignalSignedPreKeyRecord(
            EncryptedSignalSignedPreKeyRecordEntity(
                signedPreKeyId,
                vault.encrypt(serializedRecord, integerAad(SIGNED_PRE_KEY, signedPreKeyId)),
            ),
        )
    }

    override suspend fun loadSignedPreKeyRecord(signedPreKeyId: Int): ByteArray? =
        dao.getSignalSignedPreKeyRecord(validRecordId(signedPreKeyId))?.let {
            vault.decrypt(it.encryptedRecord, integerAad(SIGNED_PRE_KEY, signedPreKeyId))
        }

    override suspend fun deleteSignedPreKeyRecord(signedPreKeyId: Int) =
        dao.deleteSignalSignedPreKeyRecord(validRecordId(signedPreKeyId))

    override suspend fun saveKyberPreKeyRecord(kyberPreKeyId: Int, serializedRecord: ByteArray) {
        require(kyberPreKeyId >= 0)
        dao.putSignalKyberPreKeyRecord(
            EncryptedSignalKyberPreKeyRecordEntity(
                kyberPreKeyId,
                vault.encrypt(serializedRecord, integerAad(KYBER_PRE_KEY, kyberPreKeyId)),
            ),
        )
    }

    override suspend fun loadKyberPreKeyRecord(kyberPreKeyId: Int): ByteArray? =
        dao.getSignalKyberPreKeyRecord(validRecordId(kyberPreKeyId))?.let {
            vault.decrypt(it.encryptedRecord, integerAad(KYBER_PRE_KEY, kyberPreKeyId))
        }

    override suspend fun deleteKyberPreKeyRecord(kyberPreKeyId: Int) =
        dao.deleteSignalKyberPreKeyRecord(validRecordId(kyberPreKeyId))

    private fun validRecordId(recordId: Int): Int = recordId.also { require(it >= 0) }

    private fun addressAad(type: String, address: SignalProtocolAddress): ByteArray =
        ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeUTF(AAD_VERSION)
                output.writeUTF(type)
                output.writeUTF(address.recipientId)
                output.writeInt(address.deviceId)
            }
            buffer.toByteArray()
        }

    private fun integerAad(type: String, recordId: Int): ByteArray =
        ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeUTF(AAD_VERSION)
                output.writeUTF(type)
                output.writeInt(recordId)
            }
            buffer.toByteArray()
        }

    private companion object {
        const val AAD_VERSION = "signalstore-record-v1"
        const val IDENTITY = "identity"
        const val SESSION = "session"
        const val PRE_KEY = "prekey"
        const val SIGNED_PRE_KEY = "signed-prekey"
        const val KYBER_PRE_KEY = "kyber-prekey"
    }
}
