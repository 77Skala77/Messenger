package app.securemessenger.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.securemessenger.crypto.SimplifiedSessionManager
import app.securemessenger.crypto.StandardAead
import app.securemessenger.domain.Contact
import app.securemessenger.domain.DeviceApprovalStatus
import app.securemessenger.domain.MessageStatus
import app.securemessenger.domain.VerificationStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureRoomStateInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, SecureMessengerDatabase::class.java).build()
    private val repository = SecureLocalRepository(database.dao(), JcaLocalVaultCipher(ByteArray(32) { 4 }))

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun encryptedMessageSessionCounterAndReplayMarkerSurviveRoomBoundary() = runBlocking {
        val aliceManager = SimplifiedSessionManager(StandardAead())
        val bobManager = SimplifiedSessionManager(StandardAead())
        val alice = aliceManager.createIdentity()
        val bob = bobManager.createIdentity()
        val aliceSession = aliceManager.createSession(alice, bob.publicIdentity)
        val bobSession = bobManager.createSession(bob, alice.publicIdentity)
        val contact = Contact("alice", "Alice", "queue", alice.publicIdentity, VerificationStatus.UNVERIFIED)
        repository.storeContact(contact)
        val message = aliceManager.encryptMessage(aliceSession, "room-secret".toByteArray(), 8)
        repository.storeSession("alice", bobSession.copy(nextSendingNumber = 17))

        val storedSession = repository.loadSession("alice")!!
        assertEquals(17, storedSession.nextSendingNumber)
        assertTrue(repository.acceptIncoming("m1", "alice", storedSession, message))
        assertFalse(repository.acceptIncoming("m2", "alice", storedSession, message))

        val stored = database.dao().observeMessages("alice").first().single()
        assertFalse(stored.encryptedPayload.toString(Charsets.UTF_8).contains("room-secret"))
        assertEquals(MessageStatus.RECEIVED.name, stored.status)
    }

    @Test
    fun outgoingRetryMetadataAndRevocationRemainDurable() = runBlocking {
        val localManager = SimplifiedSessionManager(StandardAead())
        val remoteManager = SimplifiedSessionManager(StandardAead())
        val local = localManager.createIdentity()
        val remote = remoteManager.createIdentity()
        val contact = Contact("remote", "Remote", "queue", remote.publicIdentity, VerificationStatus.VERIFIED)
        val session = localManager.createSession(local, remote.publicIdentity)
        repository.storeContact(contact)
        repository.storeEncryptedMessage(
            "pending-1",
            "remote",
            localManager.encryptMessage(session, "queued".toByteArray(), 3),
            outgoing = true,
            status = MessageStatus.PENDING.name,
        )

        repository.recordFailed("pending-1", attemptCount = 1, nextAttemptAtEpochMillis = 5_000)
        assertTrue(repository.dueOutgoingMessages(4_999).isEmpty())
        assertEquals(1, repository.dueOutgoingMessages(5_000).single().attemptCount)
        repository.recordSent("pending-1", attemptCount = 2)
        assertTrue(repository.dueOutgoingMessages(Long.MAX_VALUE).isEmpty())

        repository.revokeDevice("remote", remote.publicIdentity)
        repository.storeContact(contact)
        assertEquals(DeviceApprovalStatus.REVOKED, repository.observeDevices("remote").first().single().approvalStatus)
    }
}
