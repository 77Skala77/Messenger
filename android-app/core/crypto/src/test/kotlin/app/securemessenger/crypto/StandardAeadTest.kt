package app.securemessenger.crypto

import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class StandardAeadTest {
    private val key = ByteArray(32).also(SecureRandom()::nextBytes)
    private val aad = "authenticated-routing-data".toByteArray()

    @Test
    fun `encryption roundtrip preserves plaintext`() {
        val suite = StandardAead()
        val plaintext = "message plaintext exists only in memory".toByteArray()
        val encrypted = suite.encrypt(key, plaintext, aad)

        assertFalse(encrypted.ciphertext.contentEquals(plaintext))
        assertArrayEquals(plaintext, suite.decrypt(key, encrypted, aad))
    }

    @Test
    fun `wrong associated data is rejected`() {
        val suite = StandardAead()
        val encrypted = suite.encrypt(key, "secret".toByteArray(), aad)

        assertThrows(InvalidCiphertextException::class.java) {
            suite.decrypt(key, encrypted, "tampered-routing".toByteArray())
        }
    }

    @Test
    fun `wrong authentication tag is rejected`() {
        val suite = StandardAead()
        val encrypted = suite.encrypt(key, "secret".toByteArray(), aad)
        val corruptedTag = encrypted.tag.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }

        assertThrows(InvalidCiphertextException::class.java) {
            suite.decrypt(key, encrypted.copy(tag = corruptedTag), aad)
        }
    }

    @Test
    fun `persistent nonce manager detects repeated stored nonce`() {
        val prefix = byteArrayOf(1, 2, 3, 4)
        val brokenStore = object : NonceStateStore {
            override fun read(scope: String): NonceState = NonceState(prefix, 0L)
            override fun write(scope: String, state: NonceState) = Unit
        }
        val manager = PersistentCounterNonceManager(brokenStore)
        manager.generateNonce(key)

        assertThrows(NonceReuseException::class.java) {
            manager.generateNonce(key)
        }
    }
}
