package app.securemessenger.crypto

import java.security.MessageDigest
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class StandardAead(
    private val nonceManager: NonceManager = PersistentCounterNonceManager(InMemoryNonceStateStore()),
) : EncryptionSuite {
    override val id: String = "STANDARD_AEAD"

    override fun encrypt(
        key: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): CiphertextPackage {
        validateKey(key)
        val nonce = nonceManager.generateNonce(key)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(associatedData)
        val sealed = cipher.doFinal(plaintext)
        val tagOffset = sealed.size - TAG_BYTES
        check(tagOffset >= 0) { "Invalid AEAD output" }
        return CiphertextPackage(
            version = 1,
            suiteId = id,
            nonce = nonce,
            ciphertext = sealed.copyOfRange(0, tagOffset),
            tag = sealed.copyOfRange(tagOffset, sealed.size),
            aadHash = MessageDigest.getInstance("SHA-256").digest(associatedData),
        )
    }

    override fun decrypt(
        key: ByteArray,
        ciphertextPackage: CiphertextPackage,
        associatedData: ByteArray,
    ): ByteArray {
        validateKey(key)
        if (ciphertextPackage.version != 1 || ciphertextPackage.suiteId != id) {
            throw InvalidCiphertextException("Unsupported ciphertext version or suite")
        }
        if (ciphertextPackage.nonce.size != NONCE_BYTES || ciphertextPackage.tag.size != TAG_BYTES) {
            throw InvalidCiphertextException("Invalid AES-GCM nonce or tag length")
        }
        val suppliedAadHash = MessageDigest.getInstance("SHA-256").digest(associatedData)
        if (!MessageDigest.isEqual(ciphertextPackage.aadHash, suppliedAadHash)) {
            throw InvalidCiphertextException("Associated data does not match")
        }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, ciphertextPackage.nonce),
            )
            cipher.updateAAD(associatedData)
            cipher.doFinal(ciphertextPackage.ciphertext + ciphertextPackage.tag)
        } catch (error: AEADBadTagException) {
            throw InvalidCiphertextException("Authentication tag verification failed", error)
        }
    }

    private fun validateKey(key: ByteArray) {
        require(key.size == KEY_BYTES) { "STANDARD_AEAD requires an AES-256 key" }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BYTES = 32
        const val NONCE_BYTES = 12
        const val TAG_BYTES = 16
        const val TAG_BITS = TAG_BYTES * 8
    }
}
