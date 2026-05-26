package app.securemessenger.crypto

import app.securemessenger.domain.VerificationStatus
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class SessionManagerTest {
    @Test
    fun `two invitation peers encrypt and decrypt one to one message`() {
        val aliceManager = SimplifiedSessionManager(StandardAead())
        val bobManager = SimplifiedSessionManager(StandardAead())
        val alice = aliceManager.createIdentity()
        val bob = bobManager.createIdentity()
        val aliceSession = aliceManager.createSession(alice, bob.publicIdentity)
        val bobSession = bobManager.createSession(bob, alice.publicIdentity)
        val timeBucket = 100L
        val message = aliceManager.encryptMessage(aliceSession, "hello".toByteArray(), timeBucket)

        assertArrayEquals("hello".toByteArray(), bobManager.decryptMessage(bobSession, message))
        assertThrows(ReplayDetectedException::class.java) {
            bobManager.decryptMessage(bobSession, message)
        }
    }

    @Test
    fun `failed authentication does not consume replay number`() {
        val senderManager = SimplifiedSessionManager(StandardAead())
        val receiverManager = SimplifiedSessionManager(StandardAead())
        val sender = senderManager.createIdentity()
        val receiver = receiverManager.createIdentity()
        val sendingSession = senderManager.createSession(sender, receiver.publicIdentity)
        val receivingSession = receiverManager.createSession(receiver, sender.publicIdentity)
        val valid = senderManager.encryptMessage(sendingSession, "hello".toByteArray(), 42)
        val invalid = valid.copy(associatedData = "forged".toByteArray())

        assertThrows(InvalidCiphertextException::class.java) {
            receiverManager.decryptMessage(receivingSession, invalid)
        }
        assertArrayEquals("hello".toByteArray(), receiverManager.decryptMessage(receivingSession, valid))
    }

    @Test
    fun `opposite directions derive distinct message keys at equal counters`() {
        val fixedNonce = object : NonceManager {
            override fun generateNonce(key: ByteArray): ByteArray = ByteArray(12)
        }
        val aliceManager = SimplifiedSessionManager(StandardAead(fixedNonce))
        val bobManager = SimplifiedSessionManager(StandardAead(fixedNonce))
        val alice = aliceManager.createIdentity()
        val bob = bobManager.createIdentity()
        val aliceSession = aliceManager.createSession(alice, bob.publicIdentity)
        val bobSession = bobManager.createSession(bob, alice.publicIdentity)

        val fromAlice = aliceManager.encryptMessage(aliceSession, "same".toByteArray(), 7)
        val fromBob = bobManager.encryptMessage(bobSession, "same".toByteArray(), 7)

        assertFalse(fromAlice.ciphertextPackage.ciphertext.contentEquals(fromBob.ciphertextPackage.ciphertext))
    }

    @Test
    fun `changed fingerprint produces key change warning state`() {
        assertEquals(
            VerificationStatus.KEY_CHANGED,
            ContactTrustEvaluator.statusFor("old-fingerprint", "new-fingerprint"),
        )
    }
}
