package app.securemessenger.storage

import java.io.File
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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
        assertThrows(AEADBadTagException::class.java) {
            cipher.decrypt(blob, "wrong-message-id".toByteArray())
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
