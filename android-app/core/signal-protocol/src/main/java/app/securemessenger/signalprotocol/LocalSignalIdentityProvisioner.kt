package app.securemessenger.signalprotocol

import app.securemessenger.signalstore.SignalLocalIdentityRecord
import app.securemessenger.signalstore.SignalLocalIdentityStoreBoundary
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.util.KeyHelper

/**
 * Creates this device's long-term libsignal identity exactly once. It never replaces an existing
 * identity: doing so would silently invalidate every session and every safety number.
 * Generation is explicit rather than a side effect of reading, so a missing identity fails closed
 * in [EncryptedSignalProtocolStore.getIdentityKeyPair] instead of being recreated behind the
 * user's back.
 */
class LocalSignalIdentityProvisioner(
    private val store: SignalLocalIdentityStoreBoundary,
) {
    /** Returns true if a new identity was created, false if one already existed. */
    fun provisionIfAbsent(): Boolean {
        val serialized = IdentityKeyPair.generate().serialize()
        return try {
            // The store's create is atomic and refuses to overwrite, so there is no check-then-act race.
            store.createLocalIdentity(
                SignalLocalIdentityRecord(KeyHelper.generateRegistrationId(false), serialized),
            )
        } finally {
            serialized.fill(0)
        }
    }
}
