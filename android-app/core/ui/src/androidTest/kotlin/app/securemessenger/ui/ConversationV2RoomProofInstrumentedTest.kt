package app.securemessenger.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.securemessenger.signalprotocol.EnvelopeV2Codec
import app.securemessenger.signalprotocol.IncomingV2Result
import app.securemessenger.signalprotocol.SignalSessionException
import app.securemessenger.signalstore.V2ContactState
import app.securemessenger.signalstore.V2MessageStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 1f exit-gate evidence on a real device: two v2 devices, each on its own Room database file
 * with the real encrypted stores, real libsignal, and a relay double that records every blob.
 */
@RunWith(AndroidJUnit4::class)
class ConversationV2RoomProofInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val relay = MemoryRelay()
    private val alice = RoomV2TestDevice(context, "Alice", relay)
    private val bob = RoomV2TestDevice(context, "Bob", relay)
    private val stolenCopies = mutableListOf<String>()

    @After
    fun cleanUp() {
        alice.delete()
        bob.delete()
        stolenCopies.forEach(context::deleteDatabase)
    }

    @Test
    fun twoDevicesPairChatAndSurviveARestartOverRoom() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        assertEquals(bob.service.safetyNumber(aliceOnBob), alice.service.safetyNumber(bobOnAlice))

        val sent = bob.service.sendText(aliceOnBob, "over Room")
        assertEquals(listOf(IncomingV2Result.TEXT_ACCEPTED), alice.service.synchronize())
        assertEquals(listOf(IncomingV2Result.RECEIPT_APPLIED), bob.service.synchronize())
        assertEquals(V2MessageStatus.DELIVERED, bob.service.texts(aliceOnBob).single { it.messageId == sent }.status)

        val safetyBefore = alice.service.safetyNumber(bobOnAlice)
        alice.restart()
        bob.restart()

        assertEquals(safetyBefore, alice.service.safetyNumber(bobOnAlice))
        assertEquals(V2ContactState.ACTIVE, alice.service.contacts().single().state)
        alice.service.sendText(bobOnAlice, "after restart")
        assertEquals(listOf(IncomingV2Result.TEXT_ACCEPTED), bob.service.synchronize())
        assertEquals(
            listOf("over Room", "after restart"),
            bob.service.texts(aliceOnBob).map { it.text },
        )
    }

    @Test
    fun forwardSecrecyAStolenDatabaseImageCannotDecryptWhatWasAlreadyRead() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        bob.service.sendText(aliceOnBob, "earlier secret")
        val earlier = relay.history.last { it.queueId == alice.queueId }
        assertEquals(listOf(IncomingV2Result.TEXT_ACCEPTED), alice.service.synchronize())

        val thief = alice.stolenSessions("v2-proof-alice-thief.db").also { stolenCopies += "v2-proof-alice-thief.db" }
        val control = alice.stolenSessions("v2-proof-alice-control.db").also { stolenCopies += "v2-proof-alice-control.db" }

        // Control: the stolen image is genuine and decrypts a message sent after the theft.
        bob.service.sendText(aliceOnBob, "sent after the theft")
        val later = relay.history.last { it.queueId == alice.queueId }
        val controlPlaintext = control.decrypt(bobOnAlice, EnvelopeV2Codec.decode(later.opaqueCiphertext).ciphertext)
        assertNotNull(controlPlaintext)
        // Forward secrecy: the envelope read before the theft does not decrypt from the image.
        assertThrows(SignalSessionException::class.java) {
            thief.decrypt(bobOnAlice, EnvelopeV2Codec.decode(earlier.opaqueCiphertext).ciphertext)
        }
        // The real device still works after the theft.
        assertEquals(listOf(IncomingV2Result.TEXT_ACCEPTED), alice.service.synchronize())
    }

    /** DEC-019 T1 at file level: bodies and names are only ever written vault-encrypted. */
    @Test
    fun messageTextAndNamesNeverReachTheDatabaseFilesInClear() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        bob.service.sendText(aliceOnBob, "Plaintext-Marker-From-Bob")
        alice.service.synchronize()
        alice.service.sendText(bobOnAlice, "Plaintext-Marker-From-Alice")
        bob.service.synchronize()
        alice.restart()
        bob.restart()

        listOf("v2-proof-alice.db", "v2-proof-bob.db").forEach { name ->
            val bytes = listOf("", "-wal").map { context.getDatabasePath(name + it) }
                .filter { it.exists() }
                .fold(ByteArray(0)) { all, file -> all + file.readBytes() }
            assertTrue(bytes.isNotEmpty())
            listOf("Plaintext-Marker-From-Bob", "Plaintext-Marker-From-Alice").forEach { marker ->
                assertTrue("$name contains $marker", !bytes.containsSequence(marker.toByteArray()))
                assertTrue("$name contains $marker (UTF-16)", !bytes.containsSequence(marker.toByteArray(Charsets.UTF_16LE)))
            }
        }
        // The messages are there, just encrypted.
        assertEquals(listOf("Plaintext-Marker-From-Bob"), alice.service.texts(bobOnAlice).filter { !it.outgoing }.map { it.text })
    }

    private suspend fun pair(): Pair<String, String> {
        val aliceOnBob = bob.service.acceptInvitation(alice.service.createInvitation(), "Alice")
        assertEquals(listOf(IncomingV2Result.REQUEST_RECEIVED), alice.service.synchronize())
        val bobOnAlice = alice.service.requests().single().requestId
        alice.service.acceptRequest(bobOnAlice, "Bob")
        assertEquals(listOf(IncomingV2Result.CONTACT_ACCEPTED), bob.service.synchronize())
        return aliceOnBob to bobOnAlice
    }

    private fun ByteArray.containsSequence(needle: ByteArray): Boolean {
        if (needle.isEmpty() || needle.size > size) return false
        outer@ for (start in 0..size - needle.size) {
            for (offset in needle.indices) if (this[start + offset] != needle[offset]) continue@outer
            return true
        }
        return false
    }
}
