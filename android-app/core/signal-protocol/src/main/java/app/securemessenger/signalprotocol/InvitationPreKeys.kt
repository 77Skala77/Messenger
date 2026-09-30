package app.securemessenger.signalprotocol

import java.security.SecureRandom
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.util.Medium

/**
 * Public half of the pre-keys behind one invitation (DEC-019 T5): its own signed pre-key, one
 * one-time EC pre-key and one one-time Kyber pre-key, and no last-resort key, so one invitation
 * can start at most one session. Holds no private material.
 */
class InvitationPreKeys(
    val registrationId: Int,
    val identityKey: IdentityKey,
    val signedPreKeyId: Int,
    val signedPreKey: ECPublicKey,
    val signedPreKeySignature: ByteArray,
    val oneTimePreKeyId: Int,
    val oneTimePreKey: ECPublicKey,
    val kyberPreKeyId: Int,
    val kyberPreKey: KEMPublicKey,
    val kyberPreKeySignature: ByteArray,
) {
    /** libsignal verifies both signatures against [identityKey] when a session is built from this. */
    fun toPreKeyBundle(): PreKeyBundle =
        PreKeyBundle(
            registrationId,
            SignalSessions.DEVICE_ID,
            oneTimePreKeyId,
            oneTimePreKey,
            signedPreKeyId,
            signedPreKey,
            signedPreKeySignature,
            identityKey,
            kyberPreKeyId,
            kyberPreKey,
            kyberPreKeySignature,
        )
}

/**
 * Creates the private and public pre-keys for one invitation.
 *
 * Callers hold the process-wide protocol lock (DEC-019 T2): key IDs are chosen by checking the
 * store, which is only race-free while no other protocol operation runs.
 */
class InvitationPreKeyFactory(
    private val store: EncryptedSignalProtocolStore,
    private val random: SecureRandom = SecureRandom(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun create(): InvitationPreKeys {
        val identity = store.getIdentityKeyPair()
        val createdAt = now()

        val signedPreKeyId = unusedId(store::containsSignedPreKey)
        val signedPreKeyPair = ECKeyPair.generate()
        val signedPreKeySignature = identity.privateKey.calculateSignature(signedPreKeyPair.publicKey.serialize())
        store.storeSignedPreKey(
            signedPreKeyId,
            SignedPreKeyRecord(signedPreKeyId, createdAt, signedPreKeyPair, signedPreKeySignature),
        )

        val oneTimePreKeyId = unusedId(store::containsPreKey)
        val oneTimePreKeyPair = ECKeyPair.generate()
        store.storePreKey(oneTimePreKeyId, PreKeyRecord(oneTimePreKeyId, oneTimePreKeyPair))

        val kyberPreKeyId = unusedId(store::containsKyberPreKey)
        val kyberPreKeyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val kyberPreKeySignature = identity.privateKey.calculateSignature(kyberPreKeyPair.publicKey.serialize())
        // storeKyberPreKey stores a ONE-TIME key (deleted on first use); see EncryptedSignalProtocolStore.
        store.storeKyberPreKey(
            kyberPreKeyId,
            KyberPreKeyRecord(kyberPreKeyId, createdAt, kyberPreKeyPair, kyberPreKeySignature),
        )

        return InvitationPreKeys(
            registrationId = store.getLocalRegistrationId(),
            identityKey = identity.publicKey,
            signedPreKeyId = signedPreKeyId,
            signedPreKey = signedPreKeyPair.publicKey,
            signedPreKeySignature = signedPreKeySignature,
            oneTimePreKeyId = oneTimePreKeyId,
            oneTimePreKey = oneTimePreKeyPair.publicKey,
            kyberPreKeyId = kyberPreKeyId,
            kyberPreKey = kyberPreKeyPair.publicKey,
            kyberPreKeySignature = kyberPreKeySignature,
        )
    }

    /** Deletes whatever is left of an invitation's pre-keys, e.g. when it expires unused. */
    fun delete(signedPreKeyId: Int, oneTimePreKeyId: Int, kyberPreKeyId: Int) {
        store.removePreKey(oneTimePreKeyId)
        store.removeKyberPreKey(kyberPreKeyId)
        store.removeSignedPreKey(signedPreKeyId)
    }

    private fun unusedId(taken: (Int) -> Boolean): Int {
        repeat(MAX_ID_ATTEMPTS) {
            // libsignal key IDs are 24-bit ("Medium"); 0 is avoided as a conventional "no key" value.
            val candidate = 1 + random.nextInt(Medium.MAX_VALUE)
            if (!taken(candidate)) return candidate
        }
        throw IllegalStateException("No unused pre-key ID found")
    }

    private companion object {
        const val MAX_ID_ATTEMPTS = 32
    }
}
