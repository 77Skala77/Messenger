package app.securemessenger.crypto

/**
 * Design-only boundary for the proposed Threefish -> Serpent -> AES -> SHACAL-2 cascade.
 *
 * It must remain disabled until independently reviewed primitive implementations, domain-separated
 * KDF construction, authenticated framing, and vectors exist. It is not a security feature today.
 */
class ParanoiaC4Aead : EncryptionSuite {
    override val id: String = "PARANOIA_C4_AEAD"

    override fun encrypt(
        key: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): CiphertextPackage = throw ExperimentalSuiteDisabledException()

    override fun decrypt(
        key: ByteArray,
        ciphertextPackage: CiphertextPackage,
        associatedData: ByteArray,
    ): ByteArray = throw ExperimentalSuiteDisabledException()
}

object CryptoFeatureFlags {
    const val PARANOIA_C4_AEAD_AVAILABLE: Boolean = false
}
