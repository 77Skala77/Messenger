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
        require(deviceId >= 0)
    }
}

/**
 * Record byte arrays are opaque serialized protocol-library values. Implementations must encrypt
 * them at rest because records may contain private or session key material.
 */
interface SignalIdentityStoreBoundary {
    suspend fun saveIdentityRecord(address: SignalProtocolAddress, serializedRecord: ByteArray)
    suspend fun loadIdentityRecord(address: SignalProtocolAddress): ByteArray?
    suspend fun deleteIdentityRecord(address: SignalProtocolAddress)
}

interface SignalSessionStoreBoundary {
    suspend fun saveSessionRecord(address: SignalProtocolAddress, serializedRecord: ByteArray)
    suspend fun loadSessionRecord(address: SignalProtocolAddress): ByteArray?
    suspend fun deleteSessionRecord(address: SignalProtocolAddress)
}

interface SignalPreKeyStoreBoundary {
    suspend fun savePreKeyRecord(preKeyId: Int, serializedRecord: ByteArray)
    suspend fun loadPreKeyRecord(preKeyId: Int): ByteArray?
    suspend fun deletePreKeyRecord(preKeyId: Int)
}

interface SignalSignedPreKeyStoreBoundary {
    suspend fun saveSignedPreKeyRecord(signedPreKeyId: Int, serializedRecord: ByteArray)
    suspend fun loadSignedPreKeyRecord(signedPreKeyId: Int): ByteArray?
    suspend fun deleteSignedPreKeyRecord(signedPreKeyId: Int)
}

interface SignalKyberPreKeyStoreBoundary {
    suspend fun saveKyberPreKeyRecord(kyberPreKeyId: Int, serializedRecord: ByteArray)
    suspend fun loadKyberPreKeyRecord(kyberPreKeyId: Int): ByteArray?
    suspend fun deleteKyberPreKeyRecord(kyberPreKeyId: Int)
}

/**
 * Reserved for a later, separately reviewed group design; no sender-key storage is activated now.
 */
interface SignalSenderKeyStoreBoundary {
    suspend fun saveSenderKeyRecord(distributionId: String, serializedRecord: ByteArray)
    suspend fun loadSenderKeyRecord(distributionId: String): ByteArray?
    suspend fun deleteSenderKeyRecord(distributionId: String)
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
