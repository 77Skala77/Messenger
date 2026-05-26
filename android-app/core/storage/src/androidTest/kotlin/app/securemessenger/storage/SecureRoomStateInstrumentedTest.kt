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
import app.securemessenger.signalstore.SignalProtocolAddress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureRoomStateInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, SecureMessengerDatabase::class.java).build()
    private val repository = SecureLocalRepository(database.dao(), JcaLocalVaultCipher(ByteArray(32) { 4 }))
    private val signalStore = EncryptedSignalStoreRepository(database.dao(), JcaLocalVaultCipher(ByteArray(32) { 7 }))

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

    @Test
    fun signalSpikeRecordsAreEncryptedAtRestAndCanBeDeleted() = runBlocking {
        val address = SignalProtocolAddress("future-recipient", 2)
        val identityRecord = "identity-record-private-material".toByteArray()
        val sessionRecord = "session-record-secret-state".toByteArray()
        val preKeyRecord = "prekey-private-material".toByteArray()
        val signedPreKeyRecord = "signed-prekey-private-material".toByteArray()
        val kyberPreKeyRecord = "kyber-prekey-private-material".toByteArray()

        signalStore.saveIdentityRecord(address, identityRecord)
        signalStore.saveSessionRecord(address, sessionRecord)
        signalStore.savePreKeyRecord(11, preKeyRecord)
        signalStore.saveSignedPreKeyRecord(12, signedPreKeyRecord)
        signalStore.saveKyberPreKeyRecord(13, kyberPreKeyRecord)

        val encryptedRecords = listOf(
            database.dao().getSignalIdentityRecord(address.recipientId, address.deviceId)!!.encryptedRecord,
            database.dao().getSignalSessionRecord(address.recipientId, address.deviceId)!!.encryptedRecord,
            database.dao().getSignalPreKeyRecord(11)!!.encryptedRecord,
            database.dao().getSignalSignedPreKeyRecord(12)!!.encryptedRecord,
            database.dao().getSignalKyberPreKeyRecord(13)!!.encryptedRecord,
        )
        listOf(identityRecord, sessionRecord, preKeyRecord, signedPreKeyRecord, kyberPreKeyRecord)
            .zip(encryptedRecords)
            .forEach { (record, stored) ->
                assertFalse(stored.toString(Charsets.UTF_8).contains(record.toString(Charsets.UTF_8)))
            }

        assertArrayEquals(identityRecord, signalStore.loadIdentityRecord(address))
        assertArrayEquals(sessionRecord, signalStore.loadSessionRecord(address))
        assertArrayEquals(preKeyRecord, signalStore.loadPreKeyRecord(11))
        assertArrayEquals(signedPreKeyRecord, signalStore.loadSignedPreKeyRecord(12))
        assertArrayEquals(kyberPreKeyRecord, signalStore.loadKyberPreKeyRecord(13))

        signalStore.deleteIdentityRecord(address)
        signalStore.deleteSessionRecord(address)
        signalStore.deletePreKeyRecord(11)
        signalStore.deleteSignedPreKeyRecord(12)
        signalStore.deleteKyberPreKeyRecord(13)

        assertNull(signalStore.loadIdentityRecord(address))
        assertNull(signalStore.loadSessionRecord(address))
        assertNull(signalStore.loadPreKeyRecord(11))
        assertNull(signalStore.loadSignedPreKeyRecord(12))
        assertNull(signalStore.loadKyberPreKeyRecord(13))
    }
}
