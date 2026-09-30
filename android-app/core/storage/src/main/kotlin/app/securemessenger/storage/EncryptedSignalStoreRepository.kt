package app.securemessenger.storage

import app.securemessenger.signalstore.SignalIdentityStoreBoundary
import app.securemessenger.signalstore.SignalKyberPreKeyStoreBoundary
import app.securemessenger.signalstore.SignalLocalIdentityRecord
import app.securemessenger.signalstore.SignalLocalIdentityStoreBoundary
import app.securemessenger.signalstore.SignalPreKeyStoreBoundary
import app.securemessenger.signalstore.SignalProtocolAddress
import app.securemessenger.signalstore.SignalSessionStoreBoundary
import app.securemessenger.signalstore.SignalSignedPreKeyStoreBoundary
import app.securemessenger.signalstore.StoredKyberPreKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Persists opaque libsignal record bytes after local vault encryption. Every record is bound by
 * AAD to its type and key, so a row copied under another key fails to decrypt.
 *
 * Blocking by design (see the boundary interfaces). Backs the v2 protocol, which only debug
 * builds use until the independent review of Phase 5 (DEC-021).
 */
class EncryptedSignalStoreRepository(
    private val dao: SecureMessengerDao,
    private val vault: LocalVaultCipher,
) : SignalLocalIdentityStoreBoundary,
    SignalIdentityStoreBoundary,
    SignalSessionStoreBoundary,
    SignalPreKeyStoreBoundary,
    SignalSignedPreKeyStoreBoundary,
    SignalKyberPreKeyStoreBoundary {

    override fun createLocalIdentity(record: SignalLocalIdentityRecord): Boolean {
        val plaintext = encodeLocalIdentity(record)
        val encrypted = try {
            vault.encrypt(plaintext, localIdentityAad())
        } finally {
            plaintext.fill(0)
        }
        return dao.insertSignalLocalIdentityIfAbsent(
            EncryptedSignalLocalIdentityEntity(LOCAL_IDENTITY_SINGLETON_ID, encrypted),
        ) != -1L
    }

    override fun loadLocalIdentity(): SignalLocalIdentityRecord? =
        dao.getSignalLocalIdentity(LOCAL_IDENTITY_SINGLETON_ID)?.let { entity ->
            val plaintext = vault.decrypt(entity.encryptedRecord, localIdentityAad())
            try {
                decodeLocalIdentity(plaintext)
            } finally {
                plaintext.fill(0)
            }
        }

    override fun saveIdentityRecord(address: SignalProtocolAddress, serializedRecord: ByteArray) {
        dao.putSignalIdentityRecord(
            EncryptedSignalIdentityRecordEntity(
                address.recipientId,
                address.deviceId,
                vault.encrypt(serializedRecord, addressAad(IDENTITY, address)),
            ),
        )
    }

    override fun loadIdentityRecord(address: SignalProtocolAddress): ByteArray? =
        dao.getSignalIdentityRecord(address.recipientId, address.deviceId)?.let {
            vault.decrypt(it.encryptedRecord, addressAad(IDENTITY, address))
        }

    override fun deleteIdentityRecord(address: SignalProtocolAddress) =
        dao.deleteSignalIdentityRecord(address.recipientId, address.deviceId)

    override fun saveSessionRecord(address: SignalProtocolAddress, serializedRecord: ByteArray) {
        dao.putSignalSessionRecord(
            EncryptedSignalSessionRecordEntity(
                address.recipientId,
                address.deviceId,
                vault.encrypt(serializedRecord, addressAad(SESSION, address)),
            ),
        )
    }

    override fun loadSessionRecord(address: SignalProtocolAddress): ByteArray? =
        dao.getSignalSessionRecord(address.recipientId, address.deviceId)?.let {
            vault.decrypt(it.encryptedRecord, addressAad(SESSION, address))
        }

    override fun deleteSessionRecord(address: SignalProtocolAddress) =
        dao.deleteSignalSessionRecord(address.recipientId, address.deviceId)

    override fun sessionDeviceIds(recipientId: String): List<Int> =
        dao.getSignalSessionDeviceIds(recipientId)

    override fun deleteAllSessionRecords(recipientId: String) =
        dao.deleteSignalSessionRecordsFor(recipientId)

    override fun savePreKeyRecord(preKeyId: Int, serializedRecord: ByteArray) {
        dao.putSignalPreKeyRecord(
            EncryptedSignalPreKeyRecordEntity(
                validRecordId(preKeyId),
                vault.encrypt(serializedRecord, integerAad(PRE_KEY, preKeyId)),
            ),
        )
    }

    override fun loadPreKeyRecord(preKeyId: Int): ByteArray? =
        dao.getSignalPreKeyRecord(validRecordId(preKeyId))?.let {
            vault.decrypt(it.encryptedRecord, integerAad(PRE_KEY, preKeyId))
        }

    override fun deletePreKeyRecord(preKeyId: Int) = dao.deleteSignalPreKeyRecord(validRecordId(preKeyId))

    override fun saveSignedPreKeyRecord(signedPreKeyId: Int, serializedRecord: ByteArray) {
        dao.putSignalSignedPreKeyRecord(
            EncryptedSignalSignedPreKeyRecordEntity(
                validRecordId(signedPreKeyId),
                vault.encrypt(serializedRecord, integerAad(SIGNED_PRE_KEY, signedPreKeyId)),
            ),
        )
    }

    override fun loadSignedPreKeyRecord(signedPreKeyId: Int): ByteArray? =
        dao.getSignalSignedPreKeyRecord(validRecordId(signedPreKeyId))?.let {
            vault.decrypt(it.encryptedRecord, integerAad(SIGNED_PRE_KEY, signedPreKeyId))
        }

    override fun loadAllSignedPreKeyRecords(): List<ByteArray> =
        dao.getAllSignalSignedPreKeyRecords().map {
            vault.decrypt(it.encryptedRecord, integerAad(SIGNED_PRE_KEY, it.signedPreKeyId))
        }

    override fun deleteSignedPreKeyRecord(signedPreKeyId: Int) {
        dao.deleteSignalSignedPreKeyRecord(validRecordId(signedPreKeyId))
        // A deleted key can never be used again, so its reuse-tracking entries are no longer needed.
        dao.deleteSignalKyberBaseKeyUsesForSignedKey(signedPreKeyId)
    }

    override fun saveKyberPreKeyRecord(kyberPreKeyId: Int, serializedRecord: ByteArray, lastResort: Boolean) {
        dao.putSignalKyberPreKeyRecord(
            EncryptedSignalKyberPreKeyRecordEntity(
                validRecordId(kyberPreKeyId),
                vault.encrypt(serializedRecord, kyberAad(kyberPreKeyId, lastResort)),
                lastResort,
            ),
        )
    }

    override fun loadKyberPreKeyRecord(kyberPreKeyId: Int): StoredKyberPreKey? =
        dao.getSignalKyberPreKeyRecord(validRecordId(kyberPreKeyId))?.let(::decryptKyber)

    override fun loadAllKyberPreKeyRecords(): List<StoredKyberPreKey> =
        dao.getAllSignalKyberPreKeyRecords().map(::decryptKyber)

    override fun deleteKyberPreKeyRecord(kyberPreKeyId: Int) {
        dao.deleteSignalKyberPreKeyRecord(validRecordId(kyberPreKeyId))
        dao.deleteSignalKyberBaseKeyUsesForKyberKey(kyberPreKeyId)
    }

    override fun consumeKyberPreKey(kyberPreKeyId: Int, signedPreKeyId: Int, baseKeyDigest: ByteArray): Boolean {
        require(baseKeyDigest.size == BASE_KEY_DIGEST_BYTES)
        // Decrypting verifies the AAD-bound last-resort flag before it is trusted.
        val stored = loadKyberPreKeyRecord(kyberPreKeyId) ?: return false
        stored.serializedRecord.fill(0)
        return if (stored.lastResort) {
            dao.insertSignalKyberBaseKeyUse(
                SignalKyberBaseKeyUseEntity(kyberPreKeyId, validRecordId(signedPreKeyId), baseKeyDigest.toHex()),
            ) != -1L
        } else {
            dao.deleteOneTimeSignalKyberPreKey(kyberPreKeyId) == 1
        }
    }

    private fun decryptKyber(entity: EncryptedSignalKyberPreKeyRecordEntity): StoredKyberPreKey =
        StoredKyberPreKey(
            serializedRecord = vault.decrypt(entity.encryptedRecord, kyberAad(entity.kyberPreKeyId, entity.isLastResort)),
            lastResort = entity.isLastResort,
        )

    private fun validRecordId(recordId: Int): Int = recordId.also { require(it >= 0) }

    private fun encodeLocalIdentity(record: SignalLocalIdentityRecord): ByteArray =
        ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeInt(record.registrationId)
                output.writeInt(record.serializedIdentityKeyPair.size)
                output.write(record.serializedIdentityKeyPair)
            }
            buffer.toByteArray()
        }

    private fun decodeLocalIdentity(plaintext: ByteArray): SignalLocalIdentityRecord =
        DataInputStream(ByteArrayInputStream(plaintext)).use { input ->
            val registrationId = input.readInt()
            val size = input.readInt()
            require(size in 1..MAX_IDENTITY_KEY_PAIR_BYTES) { "Invalid local identity record" }
            val keyPair = ByteArray(size).also(input::readFully)
            require(input.available() == 0) { "Trailing local identity bytes" }
            SignalLocalIdentityRecord(registrationId, keyPair)
        }

    private fun localIdentityAad(): ByteArray =
        ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeUTF(AAD_VERSION)
                output.writeUTF(LOCAL_IDENTITY)
                output.writeInt(LOCAL_IDENTITY_SINGLETON_ID)
            }
            buffer.toByteArray()
        }

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

    private fun kyberAad(kyberPreKeyId: Int, lastResort: Boolean): ByteArray =
        ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeUTF(KYBER_AAD_VERSION)
                output.writeUTF(KYBER_PRE_KEY)
                output.writeInt(kyberPreKeyId)
                output.writeBoolean(lastResort)
            }
            buffer.toByteArray()
        }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { "%02x".format(it) }

    private companion object {
        const val AAD_VERSION = "signalstore-record-v1"
        // v2 binds the last-resort flag; v1 Kyber rows are deleted by MIGRATION_5_6.
        const val KYBER_AAD_VERSION = "signalstore-record-v2"
        const val LOCAL_IDENTITY = "local-identity"
        const val IDENTITY = "identity"
        const val SESSION = "session"
        const val PRE_KEY = "prekey"
        const val SIGNED_PRE_KEY = "signed-prekey"
        const val KYBER_PRE_KEY = "kyber-prekey"
        const val LOCAL_IDENTITY_SINGLETON_ID = 1
        const val MAX_IDENTITY_KEY_PAIR_BYTES = 1024
        const val BASE_KEY_DIGEST_BYTES = 32
    }
}
