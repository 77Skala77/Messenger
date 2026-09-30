package app.securemessenger.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreInstrumentedTest {
    @Test
    fun vaultEncryptionUsesAndroidKeystoreAndAuthenticatesAad() {
        val cipher = AndroidKeystoreVaultCipher("secure_messenger_test_vault")
        cipher.deleteKey()
        val plaintext = "device-local-test-message".toByteArray()
        val aad = "record-id".toByteArray()

        try {
            val encrypted = cipher.encrypt(plaintext, aad)
            assertArrayEquals(plaintext, cipher.decrypt(encrypted, aad))
            assertThrows(AEADBadTagException::class.java) {
                cipher.decrypt(encrypted, "wrong-record".toByteArray())
            }
        } finally {
            cipher.deleteKey()
        }
    }
}
