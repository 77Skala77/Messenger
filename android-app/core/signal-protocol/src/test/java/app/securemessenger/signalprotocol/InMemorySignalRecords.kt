package app.securemessenger.signalprotocol

import app.securemessenger.signalstore.SignalIdentityStoreBoundary
import app.securemessenger.signalstore.SignalKyberPreKeyStoreBoundary
import app.securemessenger.signalstore.SignalLocalIdentityRecord
import app.securemessenger.signalstore.SignalLocalIdentityStoreBoundary
import app.securemessenger.signalstore.SignalPreKeyStoreBoundary
import app.securemessenger.signalstore.SignalProtocolAddress
import app.securemessenger.signalstore.SignalSessionStoreBoundary
import app.securemessenger.signalstore.SignalSignedPreKeyStoreBoundary
import app.securemessenger.signalstore.StoredKyberPreKey

/**
 * Test double with the same observable contract as EncryptedSignalStoreRepository, minus
 * encryption (covered by the storage module's instrumented tests). It copies on save and on load,
 * exactly like the real store returns freshly decrypted arrays, because the adapter wipes loaded
 * buffers after parsing them.
 */
class InMemorySignalRecords :
    SignalLocalIdentityStoreBoundary,
    SignalIdentityStoreBoundary,
    SignalSessionStoreBoundary,
    SignalPreKeyStoreBoundary,
    SignalSignedPreKeyStoreBoundary,
    SignalKyberPreKeyStoreBoundary {

    private var localIdentity: Pair<Int, ByteArray>? = null
    private val identities = mutableMapOf<SignalProtocolAddress, ByteArray>()
    private val sessions = mutableMapOf<SignalProtocolAddress, ByteArray>()
    private val preKeys = mutableMapOf<Int, ByteArray>()
    private val signedPreKeys = mutableMapOf<Int, ByteArray>()
    private val kyberPreKeys = mutableMapOf<Int, Pair<ByteArray, Boolean>>()
    private val kyberBaseKeyUses = mutableSetOf<Triple<Int, Int, String>>()

    @Synchronized
    override fun createLocalIdentity(record: SignalLocalIdentityRecord): Boolean {
        if (localIdentity != null) return false
        localIdentity = record.registrationId to record.serializedIdentityKeyPair.copyOf()
        return true
    }

    @Synchronized
    override fun loadLocalIdentity(): SignalLocalIdentityRecord? =
        localIdentity?.let { (registrationId, keyPair) -> SignalLocalIdentityRecord(registrationId, keyPair.copyOf()) }

    @Synchronized
    override fun saveIdentityRecord(address: SignalProtocolAddress, serializedRecord: ByteArray) {
        identities[address] = serializedRecord.copyOf()
    }

    @Synchronized
    override fun loadIdentityRecord(address: SignalProtocolAddress): ByteArray? = identities[address]?.copyOf()

    @Synchronized
    override fun deleteIdentityRecord(address: SignalProtocolAddress) {
        identities.remove(address)
    }

    @Synchronized
    override fun saveSessionRecord(address: SignalProtocolAddress, serializedRecord: ByteArray) {
        sessions[address] = serializedRecord.copyOf()
    }

    @Synchronized
    override fun loadSessionRecord(address: SignalProtocolAddress): ByteArray? = sessions[address]?.copyOf()

    @Synchronized
    override fun deleteSessionRecord(address: SignalProtocolAddress) {
        sessions.remove(address)
    }

    @Synchronized
    override fun sessionDeviceIds(recipientId: String): List<Int> =
        sessions.keys.filter { it.recipientId == recipientId }.map { it.deviceId }

    @Synchronized
    override fun deleteAllSessionRecords(recipientId: String) {
        sessions.keys.removeAll { it.recipientId == recipientId }
    }

    @Synchronized
    override fun savePreKeyRecord(preKeyId: Int, serializedRecord: ByteArray) {
        preKeys[preKeyId] = serializedRecord.copyOf()
    }

    @Synchronized
    override fun loadPreKeyRecord(preKeyId: Int): ByteArray? = preKeys[preKeyId]?.copyOf()

    @Synchronized
    override fun deletePreKeyRecord(preKeyId: Int) {
        preKeys.remove(preKeyId)
    }

    @Synchronized
    override fun saveSignedPreKeyRecord(signedPreKeyId: Int, serializedRecord: ByteArray) {
        signedPreKeys[signedPreKeyId] = serializedRecord.copyOf()
    }

    @Synchronized
    override fun loadSignedPreKeyRecord(signedPreKeyId: Int): ByteArray? = signedPreKeys[signedPreKeyId]?.copyOf()

    @Synchronized
    override fun loadAllSignedPreKeyRecords(): List<ByteArray> = signedPreKeys.values.map { it.copyOf() }

    @Synchronized
    override fun deleteSignedPreKeyRecord(signedPreKeyId: Int) {
        signedPreKeys.remove(signedPreKeyId)
        kyberBaseKeyUses.removeAll { it.second == signedPreKeyId }
    }

    @Synchronized
    override fun saveKyberPreKeyRecord(kyberPreKeyId: Int, serializedRecord: ByteArray, lastResort: Boolean) {
        kyberPreKeys[kyberPreKeyId] = serializedRecord.copyOf() to lastResort
    }

    @Synchronized
    override fun loadKyberPreKeyRecord(kyberPreKeyId: Int): StoredKyberPreKey? =
        kyberPreKeys[kyberPreKeyId]?.let { (record, lastResort) -> StoredKyberPreKey(record.copyOf(), lastResort) }

    @Synchronized
    override fun loadAllKyberPreKeyRecords(): List<StoredKyberPreKey> =
        kyberPreKeys.values.map { (record, lastResort) -> StoredKyberPreKey(record.copyOf(), lastResort) }

    @Synchronized
    override fun deleteKyberPreKeyRecord(kyberPreKeyId: Int) {
        kyberPreKeys.remove(kyberPreKeyId)
        kyberBaseKeyUses.removeAll { it.first == kyberPreKeyId }
    }

    @Synchronized
    override fun consumeKyberPreKey(kyberPreKeyId: Int, signedPreKeyId: Int, baseKeyDigest: ByteArray): Boolean {
        val (_, lastResort) = kyberPreKeys[kyberPreKeyId] ?: return false
        return if (lastResort) {
            kyberBaseKeyUses.add(Triple(kyberPreKeyId, signedPreKeyId, baseKeyDigest.joinToString("") { "%02x".format(it) }))
        } else {
            kyberPreKeys.remove(kyberPreKeyId) != null
        }
    }
}
