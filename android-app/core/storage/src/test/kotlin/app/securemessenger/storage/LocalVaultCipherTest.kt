package app.securemessenger.storage

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalVaultCipherTest {
    @Test
    fun `local message value is encrypted at rest and authenticated`() {
        val cipher = JcaLocalVaultCipher(ByteArray(32) { 3 })
        val plaintext = "local stored plaintext".toByteArray()
        val aad = "message-id".toByteArray()
        val blob = cipher.encrypt(plaintext, aad)

        assertFalse(blob.toString(Charsets.UTF_8).contains("local stored plaintext"))
        assertArrayEquals(plaintext, cipher.decrypt(blob, aad))
        assertThrows(VaultRecordRejectedException::class.java) {
            cipher.decrypt(blob, "wrong-message-id".toByteArray())
        }
    }

    /** DEC-023: a damaged blob is a rejected record, never mistaken for a temporary failure. */
    @Test
    fun `truncated malformed or altered vault blobs are rejected`() {
        val cipher = JcaLocalVaultCipher(ByteArray(32) { 3 })
        val aad = "row".toByteArray()
        val blob = cipher.encrypt("value".toByteArray(), aad)
        val flipped = blob.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }

        listOf(
            ByteArray(0),
            blob.copyOf(3),
            blob.copyOf(10),
            blob.copyOf(blob.size - 1),
            blob + byteArrayOf(0),
            flipped,
        ).forEach { damaged ->
            assertThrows(VaultRecordRejectedException::class.java) { cipher.decrypt(damaged, aad) }
        }
        assertThrows(VaultRecordRejectedException::class.java) { JcaLocalVaultCipher(ByteArray(32) { 4 }).decrypt(blob, aad) }
    }

    /**
     * Phase 5: every single-byte change, truncation, and extension of a stored blob, plus random
     * bytes, is a rejected record (DEC-023) and nothing else; a damaged length allocates nothing.
     */
    @Test
    fun `fuzzed vault blobs are only ever rejected records`() {
        val cipher = JcaLocalVaultCipher(ByteArray(32) { 3 })
        val aad = "row".toByteArray()
        val blob = cipher.encrypt("a stored value".toByteArray(), aad)
        val random = java.util.Random(31)
        val inputs = buildList {
            for (position in blob.indices) {
                for (value in intArrayOf(0x00, 0x01, 0x7F, 0x80, 0xFF)) {
                    if (blob[position] != value.toByte()) add(blob.copyOf().also { it[position] = value.toByte() })
                }
                add(blob.copyOf().also { it[position] = (it[position].toInt() xor (1 shl random.nextInt(8))).toByte() })
                add(blob.copyOf(position))
            }
            add(blob + byteArrayOf(0))
            repeat(500) { add(ByteArray(random.nextInt(200)).also(random::nextBytes)) }
            // A length field claiming 16 MB behind a short blob.
            add(java.nio.ByteBuffer.allocate(4 + 12 + 4 + 20).putInt(12).put(ByteArray(12)).putInt(16 * 1024 * 1024).array())
        }
        inputs.forEachIndexed { index, damaged ->
            try {
                cipher.decrypt(damaged, aad)
                throw AssertionError("input $index was accepted")
            } catch (_: VaultRecordRejectedException) {
                // Expected.
            }
        }
    }

    /**
     * Vault calls also run inside libsignal's store callbacks. libsignal passes unchecked exceptions
     * back unchanged but turns checked ones into an AssertionError (see ConversationV2ServiceTest),
     * so every vault failure must stay unchecked.
     */
    @Test
    fun `every vault failure is unchecked`() {
        listOf(
            VaultException::class.java,
            VaultKeyUnavailableException::class.java,
            VaultRecordRejectedException::class.java,
            VaultTemporarilyUnavailableException::class.java,
        ).forEach { type ->
            assertTrue("${type.simpleName} must be unchecked", RuntimeException::class.java.isAssignableFrom(type))
        }
    }

    @Test
    fun `signal record storage source does not emit logs`() {
        val source = File("src/main/kotlin/app/securemessenger/storage/EncryptedSignalStoreRepository.kt").readText()
        assertFalse(source.contains("android.util.Log"))
        assertFalse(Regex("""\bprintln\(""").containsMatchIn(source))
        assertFalse(Regex("""\bprint\(""").containsMatchIn(source))
    }
}
