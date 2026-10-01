package app.securemessenger.signalprotocol

import app.securemessenger.signalstore.SignalIdentityStoreBoundary
import app.securemessenger.signalstore.SignalKyberPreKeyStoreBoundary
import app.securemessenger.signalstore.SignalLocalIdentityRecord
import app.securemessenger.signalstore.SignalLocalIdentityStoreBoundary
import app.securemessenger.signalstore.SignalPreKeyStoreBoundary
import app.securemessenger.signalstore.SignalSessionStoreBoundary
import app.securemessenger.signalstore.SignalSignedPreKeyStoreBoundary
import java.security.MessageDigest
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.InvalidMessageException
import org.signal.libsignal.protocol.InvalidSessionException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.ReusedBaseKeyException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.KyberPreKeyStore
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyStore
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SessionStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyStore
import app.securemessenger.signalstore.SignalProtocolAddress as StoreAddress

/**
 * A stored libsignal record passed the vault's authentication but cannot be parsed: a local fault
 * (DEC-023). Unchecked, so it crosses libsignal's store callbacks unchanged and is never taken for
 * a verdict on a message.
 */
class StoredRecordCorruptedException(cause: Throwable) :
    IllegalStateException("A stored libsignal record cannot be parsed", cause)

/**
 * libsignal store callbacks backed by the encrypted, neutral store boundaries.
 *
 * Storage failures (the vault's `VaultException`s, [StoredRecordCorruptedException]) are unchecked
 * and propagate unchanged through libsignal: they are never mapped to protocol results.
 *
 * Backs the v2 conversation pipeline, which only debug builds run until the Phase 5 independent
 * review (DEC-021).
 *
 * Every call blocks on storage. A caller must run one protocol operation (one encrypt, decrypt,
 * or bundle processing) at a time per remote address, off the main thread; individual calls are
 * atomic, but a whole operation is only consistent if no other operation interleaves with it.
 *
 * Identity trust is stricter than libsignal's reference store: a remote identity is trusted only
 * when none is stored yet or it matches the stored one, in BOTH directions. A changed key is
 * rejected when receiving too, so it surfaces as a failure instead of being silently accepted;
 * this matches the project's fail-closed KEY_CHANGED policy.
 */
class EncryptedSignalProtocolStore(
    private val localIdentity: SignalLocalIdentityStoreBoundary,
    private val identities: SignalIdentityStoreBoundary,
    private val sessions: SignalSessionStoreBoundary,
    private val preKeys: SignalPreKeyStoreBoundary,
    private val signedPreKeys: SignalSignedPreKeyStoreBoundary,
    private val kyberPreKeys: SignalKyberPreKeyStoreBoundary,
) : IdentityKeyStore, SessionStore, PreKeyStore, SignedPreKeyStore, KyberPreKeyStore {

    // --- IdentityKeyStore ---

    override fun getIdentityKeyPair(): IdentityKeyPair =
        requireLocalIdentity().serializedIdentityKeyPair.parseThenWipe { IdentityKeyPair(it) }

    override fun getLocalRegistrationId(): Int {
        val record = requireLocalIdentity()
        record.serializedIdentityKeyPair.fill(0)
        return record.registrationId
    }

    override fun saveIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
    ): IdentityKeyStore.IdentityChange {
        val existing = getIdentity(address)
        identities.saveIdentityRecord(address.toStore(), identityKey.serialize())
        return if (existing == null || existing == identityKey) {
            IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
        } else {
            IdentityKeyStore.IdentityChange.REPLACED_EXISTING
        }
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction,
    ): Boolean {
        val stored = getIdentity(address)
        return stored == null || stored == identityKey
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? =
        identities.loadIdentityRecord(address.toStore())?.parseThenWipe { IdentityKey(it) }

    /** Not part of libsignal's interface: forgets a remote identity, e.g. for a declined request. */
    fun deleteIdentity(address: SignalProtocolAddress) = identities.deleteIdentityRecord(address.toStore())

    // --- SessionStore ---

    /** Returns null when no session exists, matching libsignal's reference store. */
    override fun loadSession(address: SignalProtocolAddress): SessionRecord? =
        sessions.loadSessionRecord(address.toStore())?.parseThenWipe { SessionRecord(it) }

    @Throws(NoSessionException::class)
    override fun loadExistingSessions(addresses: List<SignalProtocolAddress>): List<SessionRecord> =
        addresses.map { address ->
            // The message deliberately omits the address: exception text can reach logs.
            loadSession(address) ?: throw NoSessionException(address, "No session for requested address")
        }

    /** Excludes device 1, matching libsignal's reference store. */
    override fun getSubDeviceSessions(name: String): List<Int> =
        sessions.sessionDeviceIds(name).filter { it != PRIMARY_DEVICE_ID }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        val serialized = record.serialize()
        try {
            sessions.saveSessionRecord(address.toStore(), serialized)
        } finally {
            serialized.fill(0)
        }
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean =
        sessions.loadSessionRecord(address.toStore())?.also { it.fill(0) } != null

    override fun deleteSession(address: SignalProtocolAddress) =
        sessions.deleteSessionRecord(address.toStore())

    override fun deleteAllSessions(name: String) = sessions.deleteAllSessionRecords(name)

    // --- PreKeyStore ---
    //
    // Key IDs in a PreKey message are attacker-chosen u32 values, and libsignal passes the ones
    // above 2^31 as negative ints. No key of ours has such an ID, so a negative ID means "no such
    // key": a clean rejection, never a storage error that would stall the queue (Phase 5, R1).

    @Throws(InvalidKeyIdException::class)
    override fun loadPreKey(preKeyId: Int): PreKeyRecord =
        ownKeyId(preKeyId)?.let(preKeys::loadPreKeyRecord)?.parseThenWipe { PreKeyRecord(it) }
            ?: throw InvalidKeyIdException("No such pre-key")

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        val serialized = record.serialize()
        try {
            preKeys.savePreKeyRecord(preKeyId, serialized)
        } finally {
            serialized.fill(0)
        }
    }

    override fun containsPreKey(preKeyId: Int): Boolean =
        ownKeyId(preKeyId)?.let(preKeys::loadPreKeyRecord)?.also { it.fill(0) } != null

    override fun removePreKey(preKeyId: Int) {
        ownKeyId(preKeyId)?.let(preKeys::deletePreKeyRecord)
    }

    // --- SignedPreKeyStore ---

    @Throws(InvalidKeyIdException::class)
    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord =
        ownKeyId(signedPreKeyId)?.let(signedPreKeys::loadSignedPreKeyRecord)?.parseThenWipe { SignedPreKeyRecord(it) }
            ?: throw InvalidKeyIdException("No such signed pre-key")

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> =
        signedPreKeys.loadAllSignedPreKeyRecords().map { bytes -> bytes.parseThenWipe { SignedPreKeyRecord(it) } }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        val serialized = record.serialize()
        try {
            signedPreKeys.saveSignedPreKeyRecord(signedPreKeyId, serialized)
        } finally {
            serialized.fill(0)
        }
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean =
        ownKeyId(signedPreKeyId)?.let(signedPreKeys::loadSignedPreKeyRecord)?.also { it.fill(0) } != null

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        ownKeyId(signedPreKeyId)?.let(signedPreKeys::deleteSignedPreKeyRecord)
    }

    // --- KyberPreKeyStore ---

    @Throws(InvalidKeyIdException::class)
    override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord =
        ownKeyId(kyberPreKeyId)?.let(kyberPreKeys::loadKyberPreKeyRecord)?.serializedRecord?.parseThenWipe { KyberPreKeyRecord(it) }
            ?: throw InvalidKeyIdException("No such Kyber pre-key")

    override fun loadKyberPreKeys(): List<KyberPreKeyRecord> =
        kyberPreKeys.loadAllKyberPreKeyRecords().map { stored -> stored.serializedRecord.parseThenWipe { KyberPreKeyRecord(it) } }

    /**
     * libsignal's interface has no last-resort flag, so this stores a ONE-TIME key: the safe
     * default, since a one-time key is deleted after use. Use [storeLastResortKyberPreKey] for
     * the reusable last-resort key.
     */
    override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) =
        saveKyber(kyberPreKeyId, record, lastResort = false)

    fun storeLastResortKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) =
        saveKyber(kyberPreKeyId, record, lastResort = true)

    /** Not part of libsignal's interface: deletes an unused key, e.g. of an expired invitation. */
    fun removeKyberPreKey(kyberPreKeyId: Int) {
        ownKeyId(kyberPreKeyId)?.let(kyberPreKeys::deleteKyberPreKeyRecord)
    }

    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean =
        ownKeyId(kyberPreKeyId)?.let(kyberPreKeys::loadKyberPreKeyRecord)?.also { it.serializedRecord.fill(0) } != null

    @Throws(ReusedBaseKeyException::class)
    override fun markKyberPreKeyUsed(kyberPreKeyId: Int, signedPreKeyId: Int, baseKey: ECPublicKey) {
        val consumed = ownKeyId(kyberPreKeyId) != null && ownKeyId(signedPreKeyId) != null &&
            kyberPreKeys.consumeKyberPreKey(kyberPreKeyId, signedPreKeyId, baseKeyDigest(baseKey))
        if (!consumed) throw ReusedBaseKeyException("Kyber pre-key was already used")
    }

    private fun saveKyber(kyberPreKeyId: Int, record: KyberPreKeyRecord, lastResort: Boolean) {
        val serialized = record.serialize()
        try {
            kyberPreKeys.saveKyberPreKeyRecord(kyberPreKeyId, serialized, lastResort)
        } finally {
            serialized.fill(0)
        }
    }

    private fun requireLocalIdentity(): SignalLocalIdentityRecord =
        localIdentity.loadLocalIdentity()
            ?: throw IllegalStateException("Local Signal identity is not provisioned")

    private fun SignalProtocolAddress.toStore(): StoreAddress = StoreAddress(name, deviceId)

    /** The ID if it can be one of ours (non-negative), else null. */
    private fun ownKeyId(id: Int): Int? = id.takeIf { it >= 0 }

    private companion object {
        const val PRIMARY_DEVICE_ID = 1
        val BASE_KEY_DIGEST_DOMAIN = "secure-messenger-kyber-base-key-v1".toByteArray()

        fun baseKeyDigest(baseKey: ECPublicKey): ByteArray =
            MessageDigest.getInstance("SHA-256").run {
                update(BASE_KEY_DIGEST_DOMAIN)
                digest(baseKey.serialize())
            }

        /**
         * Parses a stored record, then wipes the buffer (libsignal copies the bytes into native
         * state, so wiping afterwards is safe). A parse failure here is a local fault, not a property
         * of any incoming message: it is rethrown as [StoredRecordCorruptedException]. That includes
         * libsignal's unchecked [InvalidSessionException] for a damaged session record (Phase 5 F7).
         */
        inline fun <T> ByteArray.parseThenWipe(parse: (ByteArray) -> T): T =
            try {
                parse(this)
            } catch (failure: InvalidMessageException) {
                throw StoredRecordCorruptedException(failure)
            } catch (failure: InvalidKeyException) {
                throw StoredRecordCorruptedException(failure)
            } catch (failure: InvalidSessionException) {
                throw StoredRecordCorruptedException(failure)
            } finally {
                fill(0)
            }
    }
}
