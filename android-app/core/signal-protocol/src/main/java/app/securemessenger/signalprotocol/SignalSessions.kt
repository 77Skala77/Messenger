package app.securemessenger.signalprotocol

import org.signal.libsignal.protocol.DuplicateMessageException
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.InvalidMessageException
import org.signal.libsignal.protocol.InvalidVersionException
import org.signal.libsignal.protocol.LegacyMessageException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.ReusedBaseKeyException
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.UntrustedIdentityException
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.PreKeyBundle

/** A libsignal ciphertext and its type: [CiphertextMessage.PREKEY_TYPE] or [CiphertextMessage.WHISPER_TYPE]. */
class SignalCiphertext(val type: Int, val bytes: ByteArray)

enum class SignalSessionFailure {
    /** The remote identity differs from the stored one (DEC-018): surface as KEY_CHANGED. */
    UNTRUSTED_IDENTITY,

    /** Already decrypted, or too old. Indistinguishable from a forged old counter; never accept. */
    DUPLICATE_OR_OLD,

    /** The pre-key it names does not exist (used, expired, or never issued). */
    UNKNOWN_PRE_KEY,
    NO_SESSION,

    /** Malformed, tampered, wrong session, bad signature, or reused base key. */
    INVALID,
}

/** Exception text never carries addresses, keys, or payload bytes: it can reach logs. */
class SignalSessionException(val failure: SignalSessionFailure, cause: Throwable? = null) :
    Exception("Signal session operation failed: $failure", cause)

/**
 * Unauthenticated routing hint from a PreKey message header, readable before decryption. It only
 * says which identity *claims* to have sent the message and which pre-keys it names; decryption
 * is what authenticates it.
 */
class PreKeyMessageHeader(
    val identityKey: IdentityKey,
    val oneTimePreKeyId: Int?,
    val signedPreKeyId: Int,
)

/**
 * One-to-one libsignal sessions (PQXDH + Double Ratchet), addressed by the local contact ID.
 * Address names never leave the device, so each side names its peer by its own contact ID.
 *
 * Not used by the app yet: `SignalStoreFeatureFlags.SIGNAL_BACKED_SESSIONS_ENABLED` is false.
 * Not thread-safe: callers run every operation under the process-wide protocol lock and inside
 * the storage transaction that also persists its result (DEC-019 T2). Otherwise a decrypted
 * message whose ratchet step was stored, but whose plaintext was not, is lost for good.
 */
class SignalSessions(private val store: EncryptedSignalProtocolStore) {

    /** Starts a session from a peer's invitation. Refuses to replace an existing one. */
    fun startSession(contactId: String, preKeys: PreKeyBundle) {
        val remote = address(contactId)
        check(!store.containsSession(remote)) { "A session already exists for this contact" }
        mapFailures {
            // SessionBuilder takes (remote, local); SessionCipher takes (local, remote).
            SessionBuilder(store, store, store, store, remote, LOCAL_ADDRESS).process(preKeys)
        }
    }

    fun encrypt(contactId: String, plaintext: ByteArray): SignalCiphertext {
        val message = mapFailures { cipher(contactId).encrypt(plaintext) }
        return SignalCiphertext(message.type, message.serialize())
    }

    fun decrypt(contactId: String, ciphertext: SignalCiphertext): ByteArray = mapFailures {
        val cipher = cipher(contactId)
        when (ciphertext.type) {
            CiphertextMessage.PREKEY_TYPE -> cipher.decrypt(PreKeySignalMessage(ciphertext.bytes))
            CiphertextMessage.WHISPER_TYPE -> cipher.decrypt(SignalMessage(ciphertext.bytes))
            else -> throw SignalSessionException(SignalSessionFailure.INVALID)
        }
    }

    fun hasSession(contactId: String): Boolean = store.containsSession(address(contactId))

    fun identityOf(contactId: String): IdentityKey? = store.getIdentity(address(contactId))

    fun localIdentityKey(): IdentityKey = store.getIdentityKeyPair().publicKey

    /** Removes the session and the stored identity, e.g. when a request is declined. */
    fun forget(contactId: String) {
        val remote = address(contactId)
        store.deleteSession(remote)
        store.deleteIdentity(remote)
    }

    private fun cipher(contactId: String) =
        SessionCipher(store, store, store, store, store, LOCAL_ADDRESS, address(contactId))

    private fun address(contactId: String): SignalProtocolAddress {
        require(contactId.isNotBlank() && contactId != LOCAL_ADDRESS.name)
        return SignalProtocolAddress(contactId, DEVICE_ID)
    }

    companion object {
        /** Single device per contact until Phase 4. */
        const val DEVICE_ID = 1
        private val LOCAL_ADDRESS = SignalProtocolAddress("self", DEVICE_ID)

        fun readPreKeyHeader(bytes: ByteArray): PreKeyMessageHeader = mapFailures {
            val message = PreKeySignalMessage(bytes)
            PreKeyMessageHeader(
                identityKey = message.identityKey,
                oneTimePreKeyId = message.preKeyId.orElse(null),
                signedPreKeyId = message.signedPreKeyId,
            )
        }

        /**
         * Maps libsignal's protocol exceptions only. Anything else (a storage failure, a missing
         * local identity) propagates unchanged, so it is never mistaken for a bad message.
         */
        private inline fun <T> mapFailures(block: () -> T): T =
            try {
                block()
            } catch (failure: UntrustedIdentityException) {
                throw SignalSessionException(SignalSessionFailure.UNTRUSTED_IDENTITY, failure)
            } catch (failure: DuplicateMessageException) {
                throw SignalSessionException(SignalSessionFailure.DUPLICATE_OR_OLD, failure)
            } catch (failure: InvalidKeyIdException) {
                throw SignalSessionException(SignalSessionFailure.UNKNOWN_PRE_KEY, failure)
            } catch (failure: NoSessionException) {
                throw SignalSessionException(SignalSessionFailure.NO_SESSION, failure)
            } catch (failure: InvalidMessageException) {
                throw SignalSessionException(SignalSessionFailure.INVALID, failure)
            } catch (failure: InvalidKeyException) {
                throw SignalSessionException(SignalSessionFailure.INVALID, failure)
            } catch (failure: InvalidVersionException) {
                throw SignalSessionException(SignalSessionFailure.INVALID, failure)
            } catch (failure: LegacyMessageException) {
                throw SignalSessionException(SignalSessionFailure.INVALID, failure)
            } catch (failure: ReusedBaseKeyException) {
                throw SignalSessionException(SignalSessionFailure.INVALID, failure)
            }
    }
}
