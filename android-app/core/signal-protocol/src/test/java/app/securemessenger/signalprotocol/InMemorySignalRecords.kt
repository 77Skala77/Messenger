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
 * Stands in for the storage module's `VaultKeyUnavailableException` (DEC-023) in JVM tests.
 * Unchecked, like every vault failure.
 */
class SimulatedVaultKeyLoss : RuntimeException("Simulated loss of the local vault key")

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

    /**
     * Fault injection (DEC-023): while set, every stored record fails to load with this failure and
     * nothing can be saved, like the real store after the vault key is lost; records that were
     * never stored still read as absent.
     */
    @Volatile
    var failure: (() -> Exception)? = null

    private fun <T> readable(value: T?): T? {
        if (value != null) failure?.let { throw it() }
        return value
    }

    private fun writable() {
        failure?.let { throw it() }
    }

    private var localIdentity: Pair<Int, ByteArray>? = null
    private val identities = mutableMapOf<SignalProtocolAddress, ByteArray>()
    private val sessions = mutableMapOf<SignalProtocolAddress, ByteArray>()
    private val preKeys = mutableMapOf<Int, ByteArray>()
    private val signedPreKeys = mutableMapOf<Int, ByteArray>()
    private val kyberPreKeys = mutableMapOf<Int, Pair<ByteArray, Boolean>>()
    private val kyberBaseKeyUses = mutableSetOf<Triple<Int, Int, String>>()

    @Synchronized
    override fun createLocalIdentity(record: SignalLocalIdentityRecord): Boolean {
        writable()
        if (localIdentity != null) return false
        localIdentity = record.registrationId to record.serializedIdentityKeyPair.copyOf()
        return true
    }

    @Synchronized
    override fun loadLocalIdentity(): SignalLocalIdentityRecord? =
        readable(localIdentity)?.let { (registrationId, keyPair) -> SignalLocalIdentityRecord(registrationId, keyPair.copyOf()) }

    @Synchronized
    override fun saveIdentityRecord(address: SignalProtocolAddress, serializedRecord: ByteArray) {
        writable()
        identities[address] = serializedRecord.copyOf()
    }

    @Synchronized
    override fun loadIdentityRecord(address: SignalProtocolAddress): ByteArray? = readable(identities[address])?.copyOf()

    @Synchronized
    override fun deleteIdentityRecord(address: SignalProtocolAddress) {
        identities.remove(address)
    }

    @Synchronized
    override fun saveSessionRecord(address: SignalProtocolAddress, serializedRecord: ByteArray) {
        writable()
        sessions[address] = serializedRecord.copyOf()
    }

    @Synchronized
    override fun loadSessionRecord(address: SignalProtocolAddress): ByteArray? = readable(sessions[address])?.copyOf()

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
        writable()
        preKeys[preKeyId] = serializedRecord.copyOf()
    }

    @Synchronized
    override fun loadPreKeyRecord(preKeyId: Int): ByteArray? = readable(preKeys[preKeyId])?.copyOf()

    @Synchronized
    override fun deletePreKeyRecord(preKeyId: Int) {
        preKeys.remove(preKeyId)
    }

    @Synchronized
    override fun saveSignedPreKeyRecord(signedPreKeyId: Int, serializedRecord: ByteArray) {
        writable()
        signedPreKeys[signedPreKeyId] = serializedRecord.copyOf()
    }

    @Synchronized
    override fun loadSignedPreKeyRecord(signedPreKeyId: Int): ByteArray? = readable(signedPreKeys[signedPreKeyId])?.copyOf()

    @Synchronized
    override fun loadAllSignedPreKeyRecords(): List<ByteArray> = signedPreKeys.values.map { readable(it)!!.copyOf() }

    @Synchronized
    override fun deleteSignedPreKeyRecord(signedPreKeyId: Int) {
        signedPreKeys.remove(signedPreKeyId)
        kyberBaseKeyUses.removeAll { it.second == signedPreKeyId }
    }

    @Synchronized
    override fun saveKyberPreKeyRecord(kyberPreKeyId: Int, serializedRecord: ByteArray, lastResort: Boolean) {
        writable()
        kyberPreKeys[kyberPreKeyId] = serializedRecord.copyOf() to lastResort
    }

    @Synchronized
    override fun loadKyberPreKeyRecord(kyberPreKeyId: Int): StoredKyberPreKey? =
        readable(kyberPreKeys[kyberPreKeyId])?.let { (record, lastResort) -> StoredKyberPreKey(record.copyOf(), lastResort) }

    @Synchronized
    override fun loadAllKyberPreKeyRecords(): List<StoredKyberPreKey> =
        kyberPreKeys.values.map { stored -> readable(stored)!!.let { (record, lastResort) -> StoredKyberPreKey(record.copyOf(), lastResort) } }

    @Synchronized
    override fun deleteKyberPreKeyRecord(kyberPreKeyId: Int) {
        kyberPreKeys.remove(kyberPreKeyId)
        kyberBaseKeyUses.removeAll { it.first == kyberPreKeyId }
    }

    /** An independent copy of every record: what a thief with a full image of the device gets. */
    @Synchronized
    fun deepCopy(): InMemorySignalRecords = InMemorySignalRecords().also { copy ->
        copy.localIdentity = localIdentity?.let { (registrationId, keyPair) -> registrationId to keyPair.copyOf() }
        identities.forEach { (address, record) -> copy.identities[address] = record.copyOf() }
        sessions.forEach { (address, record) -> copy.sessions[address] = record.copyOf() }
        preKeys.forEach { (id, record) -> copy.preKeys[id] = record.copyOf() }
        signedPreKeys.forEach { (id, record) -> copy.signedPreKeys[id] = record.copyOf() }
        kyberPreKeys.forEach { (id, stored) -> copy.kyberPreKeys[id] = stored.first.copyOf() to stored.second }
        copy.kyberBaseKeyUses.addAll(kyberBaseKeyUses)
    }

    /** Copy of every map, for a test transaction runner that must roll back like Room does. */
    @Synchronized
    fun snapshot(): () -> Unit {
        val savedIdentity = localIdentity
        val savedIdentities = identities.toMap()
        val savedSessions = sessions.toMap()
        val savedPreKeys = preKeys.toMap()
        val savedSignedPreKeys = signedPreKeys.toMap()
        val savedKyberPreKeys = kyberPreKeys.toMap()
        val savedKyberBaseKeyUses = kyberBaseKeyUses.toSet()
        return {
            synchronized(this) {
                localIdentity = savedIdentity
                identities.clear(); identities.putAll(savedIdentities)
                sessions.clear(); sessions.putAll(savedSessions)
                preKeys.clear(); preKeys.putAll(savedPreKeys)
                signedPreKeys.clear(); signedPreKeys.putAll(savedSignedPreKeys)
                kyberPreKeys.clear(); kyberPreKeys.putAll(savedKyberPreKeys)
                kyberBaseKeyUses.clear(); kyberBaseKeyUses.addAll(savedKyberBaseKeyUses)
            }
        }
    }

    @Synchronized
    override fun consumeKyberPreKey(kyberPreKeyId: Int, signedPreKeyId: Int, baseKeyDigest: ByteArray): Boolean {
        val (_, lastResort) = readable(kyberPreKeys[kyberPreKeyId]) ?: return false
        return if (lastResort) {
            kyberBaseKeyUses.add(Triple(kyberPreKeyId, signedPreKeyId, baseKeyDigest.joinToString("") { "%02x".format(it) }))
        } else {
            kyberPreKeys.remove(kyberPreKeyId) != null
        }
    }
}
