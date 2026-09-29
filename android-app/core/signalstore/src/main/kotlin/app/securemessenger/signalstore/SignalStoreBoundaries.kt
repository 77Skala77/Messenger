package app.securemessenger.signalstore

object SignalStoreFeatureFlags {
    /**
     * No production or prototype conversation path may opt in until the documented migration
     * gates have been completed and an independently reviewed integration exists.
     */
    const val SIGNAL_BACKED_SESSIONS_ENABLED: Boolean = false
}

data class SignalProtocolAddress(
    val recipientId: String,
    val deviceId: Int,
) {
    init {
        require(recipientId.isNotBlank())
        // libsignal rejects device IDs outside 1..127, so the neutral address does too.
        require(deviceId in MIN_DEVICE_ID..MAX_DEVICE_ID)
    }

    companion object {
        const val MIN_DEVICE_ID = 1
        const val MAX_DEVICE_ID = 127
    }
}

/*
 * Every boundary below is blocking, not suspending: libsignal invokes its store callbacks
 * synchronously, including from native code, inside a single protocol operation. Callers must
 * run protocol operations off the Android main thread; Room rejects main-thread access, so a
 * misuse fails loudly instead of silently.
 *
 * Record byte arrays are opaque serialized protocol-library values. Implementations must encrypt
 * them at rest because records may contain private or session key material.
 */

/** The local device's own long-term identity. Created once and never overwritten. */
class SignalLocalIdentityRecord(
    val registrationId: Int,
    val serializedIdentityKeyPair: ByteArray,
) {
    init {
        require(registrationId > 0)
        require(serializedIdentityKeyPair.isNotEmpty())
    }
}

interface SignalLocalIdentityStoreBoundary {
    /** Returns false, without changing anything, if a local identity already exists. */
    fun createLocalIdentity(record: SignalLocalIdentityRecord): Boolean
    fun loadLocalIdentity(): SignalLocalIdentityRecord?
}

interface SignalIdentityStoreBoundary {
    fun saveIdentityRecord(address: SignalProtocolAddress, serializedRecord: ByteArray)
    fun loadIdentityRecord(address: SignalProtocolAddress): ByteArray?
    fun deleteIdentityRecord(address: SignalProtocolAddress)
}

interface SignalSessionStoreBoundary {
    fun saveSessionRecord(address: SignalProtocolAddress, serializedRecord: ByteArray)
    fun loadSessionRecord(address: SignalProtocolAddress): ByteArray?
    fun deleteSessionRecord(address: SignalProtocolAddress)
    fun sessionDeviceIds(recipientId: String): List<Int>
    fun deleteAllSessionRecords(recipientId: String)
}

interface SignalPreKeyStoreBoundary {
    fun savePreKeyRecord(preKeyId: Int, serializedRecord: ByteArray)
    fun loadPreKeyRecord(preKeyId: Int): ByteArray?
    fun deletePreKeyRecord(preKeyId: Int)
}

interface SignalSignedPreKeyStoreBoundary {
    fun saveSignedPreKeyRecord(signedPreKeyId: Int, serializedRecord: ByteArray)
    fun loadSignedPreKeyRecord(signedPreKeyId: Int): ByteArray?
    fun loadAllSignedPreKeyRecords(): List<ByteArray>
    fun deleteSignedPreKeyRecord(signedPreKeyId: Int)
}

class StoredKyberPreKey(
    val serializedRecord: ByteArray,
    val lastResort: Boolean,
)

interface SignalKyberPreKeyStoreBoundary {
    /**
     * [lastResort] must be bound to the stored record so that tampering with it is detected;
     * treating a one-time key as last-resort would allow that key to be reused.
     */
    fun saveKyberPreKeyRecord(kyberPreKeyId: Int, serializedRecord: ByteArray, lastResort: Boolean)
    fun loadKyberPreKeyRecord(kyberPreKeyId: Int): StoredKyberPreKey?
    fun loadAllKyberPreKeyRecords(): List<StoredKyberPreKey>
    fun deleteKyberPreKeyRecord(kyberPreKeyId: Int)

    /**
     * Atomically consumes a Kyber pre-key for one incoming session: a one-time key is deleted; for
     * a last-resort key the `(kyberPreKeyId, signedPreKeyId, baseKeyDigest)` tuple is recorded.
     * Returns false — and the caller must reject the message — if the one-time key is already gone,
     * the tuple was seen before, or the key is unknown.
     */
    fun consumeKyberPreKey(kyberPreKeyId: Int, signedPreKeyId: Int, baseKeyDigest: ByteArray): Boolean
}

/**
 * Reserved for a later, separately reviewed group design; no sender-key storage is activated now.
 */
interface SignalSenderKeyStoreBoundary {
    fun saveSenderKeyRecord(distributionId: String, serializedRecord: ByteArray)
    fun loadSenderKeyRecord(distributionId: String): ByteArray?
    fun deleteSenderKeyRecord(distributionId: String)
}

class SignalSpikeDisabledException :
    IllegalStateException("Signal-backed sessions are disabled; current chats use the prototype session layer")

interface SignalSessionManagerAdapter {
    val enabled: Boolean
    fun createExperimentalSession(invitation: ExperimentalSignalPqxdhInvitation): Nothing
}

class DisabledSignalSessionManagerAdapter : SignalSessionManagerAdapter {
    override val enabled: Boolean = SignalStoreFeatureFlags.SIGNAL_BACKED_SESSIONS_ENABLED

    override fun createExperimentalSession(invitation: ExperimentalSignalPqxdhInvitation): Nothing =
        throw SignalSpikeDisabledException()
}
