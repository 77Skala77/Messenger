package app.securemessenger.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Every vault operation throws only [VaultException]s (DEC-023). */
interface LocalVaultCipher {
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray
    fun decrypt(blob: ByteArray, associatedData: ByteArray): ByteArray

    /**
     * Makes a key available for a new, empty vault. Called only when a profile is created
     * (DEC-023); [encrypt] and [decrypt] never create a key.
     */
    fun createKeyIfAbsent() {}
}

/**
 * A failure of the local vault (DEC-023): a storage failure, never a verdict on a message.
 * Unchecked on purpose: vault calls also run inside libsignal's store callbacks, and libsignal
 * passes unchecked exceptions back unchanged but turns checked ones into an `AssertionError`,
 * which ordinary `catch (Exception)` blocks would miss. Messages carry no key material or row IDs.
 */
sealed class VaultException(message: String, cause: Throwable?) : RuntimeException(message, cause)

/** The vault key is missing or permanently unusable. Final: its data can never be decrypted again. */
class VaultKeyUnavailableException(cause: Throwable? = null) :
    VaultException("The local vault key is missing or no longer usable", cause)

/**
 * A stored value does not authenticate under the current key and its row-bound AAD, or is
 * malformed: damaged, altered, or written under another key. Never read it as "not stored".
 */
class VaultRecordRejectedException(cause: Throwable? = null) :
    VaultException("A stored value could not be authenticated", cause)

/** Android Keystore did not answer. May be temporary; nothing is declared lost because of it. */
class VaultTemporarilyUnavailableException(cause: Throwable) :
    VaultException("The local vault is not available right now", cause)

class AndroidKeystoreVaultCipher(
    private val alias: String = "secure_messenger_local_vault_v1",
) : LocalVaultCipher {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray = vaultCall {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // Android Keystore generates a fresh randomized IV; caller-supplied IVs are not accepted.
        cipher.init(Cipher.ENCRYPT_MODE, requireKey())
        cipher.updateAAD(associatedData)
        encode(cipher.iv, cipher.doFinal(plaintext))
    }

    override fun decrypt(blob: ByteArray, associatedData: ByteArray): ByteArray {
        val (nonce, ciphertext) = decode(blob)
        return vaultCall {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, requireKey(), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(associatedData)
            cipher.doFinal(ciphertext)
        }
    }

    /** True if Android Keystore holds an entry under this alias (it may still be unusable). */
    fun hasKey(): Boolean = vaultCall { keyStore().containsAlias(alias) }

    /**
     * Creates the key for a new, empty vault and keeps an existing one. This is the only place a
     * key is created: a lost key is never silently replaced, so data is never split across two
     * keys and the loss is detected instead of hidden (DEC-023).
     */
    override fun createKeyIfAbsent() = vaultCall {
        if (keyStore().containsAlias(alias)) return@vaultCall
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        generator.generateKey()
        Unit
    }

    fun deleteKey() = vaultCall { keyStore().deleteEntry(alias) }

    private fun requireKey(): SecretKey = keyStore().getKey(alias, null) as? SecretKey ?: throw VaultKeyUnavailableException()

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /**
     * Maps every Keystore and JCA failure onto the vault failures. A key the system invalidated
     * or cannot recover is as final as a missing one; anything else from the Keystore may pass.
     */
    private inline fun <T> vaultCall(block: () -> T): T =
        try {
            block()
        } catch (failure: VaultException) {
            throw failure
        } catch (failure: AEADBadTagException) {
            throw VaultRecordRejectedException(failure)
        } catch (failure: KeyPermanentlyInvalidatedException) {
            throw VaultKeyUnavailableException(failure)
        } catch (failure: UnrecoverableKeyException) {
            throw VaultKeyUnavailableException(failure)
        } catch (failure: GeneralSecurityException) {
            throw VaultTemporarilyUnavailableException(failure)
        } catch (failure: IOException) {
            throw VaultTemporarilyUnavailableException(failure)
        } catch (failure: ProviderException) {
            throw VaultTemporarilyUnavailableException(failure)
        }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128

        internal fun encode(nonce: ByteArray, ciphertext: ByteArray): ByteArray =
            ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { output ->
                    output.writeInt(nonce.size)
                    output.write(nonce)
                    output.writeInt(ciphertext.size)
                    output.write(ciphertext)
                }
                buffer.toByteArray()
            }

        /** Throws [VaultRecordRejectedException] for any malformed or truncated blob: a damaged row. */
        internal fun decode(blob: ByteArray): Pair<ByteArray, ByteArray> =
            try {
                DataInputStream(ByteArrayInputStream(blob)).use { input ->
                    val nonceSize = input.readInt()
                    require(nonceSize == 12) { "Invalid local vault nonce" }
                    val nonce = ByteArray(nonceSize).also(input::readFully)
                    val ciphertextSize = input.readInt()
                    // Checked against what is left before allocating, so a damaged length costs nothing.
                    require(ciphertextSize in 16..(16 * 1024 * 1024) && ciphertextSize <= input.available()) { "Invalid local vault blob" }
                    val ciphertext = ByteArray(ciphertextSize).also(input::readFully)
                    require(input.available() == 0) { "Trailing local vault bytes" }
                    nonce to ciphertext
                }
            } catch (failure: EOFException) {
                throw VaultRecordRejectedException(failure)
            } catch (failure: IllegalArgumentException) {
                throw VaultRecordRejectedException(failure)
            }
    }
}

/** Test and fixture vault with a fixed key; same blob format and failure types as the Keystore one. */
class JcaLocalVaultCipher(private val keyBytes: ByteArray) : LocalVaultCipher {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"))
        cipher.updateAAD(associatedData)
        return AndroidKeystoreVaultCipher.encode(cipher.iv, cipher.doFinal(plaintext))
    }

    override fun decrypt(blob: ByteArray, associatedData: ByteArray): ByteArray {
        val (nonce, ciphertext) = AndroidKeystoreVaultCipher.decode(blob)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(associatedData)
        return try {
            cipher.doFinal(ciphertext)
        } catch (failure: AEADBadTagException) {
            throw VaultRecordRejectedException(failure)
        }
    }
}
