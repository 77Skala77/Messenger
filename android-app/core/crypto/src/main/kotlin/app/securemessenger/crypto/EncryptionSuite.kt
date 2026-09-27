package app.securemessenger.crypto

data class CiphertextPackage(
    val version: Int,
    val suiteId: String,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val tag: ByteArray,
    val aadHash: ByteArray,
)

interface EncryptionSuite {
    val id: String

    fun encrypt(
        key: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): CiphertextPackage

    fun decrypt(
        key: ByteArray,
        ciphertextPackage: CiphertextPackage,
        associatedData: ByteArray,
    ): ByteArray
}

open class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)
class InvalidCiphertextException(message: String, cause: Throwable? = null) :
    CryptoException(message, cause)
class NonceReuseException : CryptoException("Encryption nonce reuse detected")
class ReplayDetectedException : CryptoException("Message replay detected")
