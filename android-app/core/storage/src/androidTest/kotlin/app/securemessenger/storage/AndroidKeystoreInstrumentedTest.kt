package app.securemessenger.storage

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.securemessenger.crypto.SimplifiedSessionManager
import app.securemessenger.crypto.StandardAead
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

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

    @Test
    fun deviceIdentityPrivateKeyStaysNonExportableAndPerformsKeyAgreement() {
        val provider = AndroidKeystoreIdentityProvider(context)
        provider.deleteIdentity()
        try {
            val localIdentity = provider.loadOrCreate()
            val reloadedIdentity = provider.loadOrCreate()
            val peerManager = SimplifiedSessionManager(StandardAead())
            val peerIdentity = peerManager.createIdentity()

            assertEquals(localIdentity.publicIdentity.deviceId, reloadedIdentity.publicIdentity.deviceId)
            assertNull(localIdentity.privateKey.encoded)
            SimplifiedSessionManager(StandardAead()).createSession(localIdentity, peerIdentity.publicIdentity)
        } finally {
            provider.deleteIdentity()
        }
    }
}
