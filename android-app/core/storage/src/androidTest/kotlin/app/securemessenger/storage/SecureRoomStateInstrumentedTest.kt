package app.securemessenger.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.securemessenger.crypto.SimplifiedSessionManager
import app.securemessenger.crypto.StandardAead
import app.securemessenger.delivery.DeliveryReceipt
import app.securemessenger.delivery.DeliveryReceiptAcceptance
import app.securemessenger.delivery.MessagePayloadCodec
import app.securemessenger.domain.Contact
import app.securemessenger.domain.DeviceApprovalStatus
import app.securemessenger.domain.MessageStatus
import app.securemessenger.domain.VerificationStatus
import app.securemessenger.signalstore.SignalLocalIdentityRecord
import app.securemessenger.signalstore.SignalProtocolAddress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
        assertEquals(1, repository.retryableOutgoingMessages().single().attemptCount)
        assertTrue(repository.dueOutgoingMessages(4_999).isEmpty())
        assertEquals(1, repository.dueOutgoingMessages(5_000).single().attemptCount)
        repository.recordSent("pending-1", attemptCount = 2)
        assertTrue(repository.retryableOutgoingMessages().isEmpty())
        assertTrue(repository.dueOutgoingMessages(Long.MAX_VALUE).isEmpty())
        assertTrue(repository.recordDelivered("pending-1"))
        assertEquals(MessageStatus.DELIVERED.name, database.dao().observeMessages("remote").first().single().status)

        repository.revokeDevice("remote", remote.publicIdentity)
        repository.storeContact(contact)
        assertEquals(DeviceApprovalStatus.REVOKED, repository.observeDevices("remote").first().single().approvalStatus)
    }

    @Test
    fun deliveryReceiptOnlyMarksMessagesSentToTheSameContact() = runBlocking {
        val localManager = SimplifiedSessionManager(StandardAead())
        val bobManager = SimplifiedSessionManager(StandardAead())
        val local = localManager.createIdentity()
        val bob = bobManager.createIdentity()
        val carol = SimplifiedSessionManager(StandardAead()).createIdentity()
        repository.storeContact(Contact("bob", "Bob", "bob-queue", bob.publicIdentity, VerificationStatus.UNVERIFIED))
        repository.storeContact(Contact("carol", "Carol", "carol-queue", carol.publicIdentity, VerificationStatus.UNVERIFIED))
        val toBob = localManager.createSession(local, bob.publicIdentity)
        val toCarol = localManager.createSession(local, carol.publicIdentity)
        repository.storeEncryptedMessage(
            "to-carol",
            "carol",
            localManager.encryptMessage(toCarol, "for-carol".toByteArray(), 1),
            outgoing = true,
            status = MessageStatus.SENT.name,
        )
        repository.storeEncryptedMessage(
            "to-bob",
            "bob",
            localManager.encryptMessage(toBob, "for-bob".toByteArray(), 1),
            outgoing = true,
            status = MessageStatus.SENT.name,
        )
        val bobSession = bobManager.createSession(bob, local.publicIdentity)

        // An authenticated receipt from Bob that names a message sent to Carol, as produced when
        // a relay relabels Bob's incoming message with the ID of Carol's message.
        val crossContact = DeliveryReceipt(
            originalMessageId = "to-carol",
            conversationId = toBob.conversationId,
            originalSenderDeviceId = toBob.localDeviceId,
            originalRecipientDeviceId = toBob.remoteDeviceId,
            timestampEpochMillis = 1L,
        )
        val crossContactMessage = bobManager.encryptMessage(
            bobSession,
            MessagePayloadCodec.encodeDeliveryReceipt(crossContact),
            2,
        )
        assertEquals(
            DeliveryReceiptAcceptance.UNKNOWN_MESSAGE,
            repository.acceptDeliveryReceipt("bob", toBob, crossContactMessage, crossContact),
        )
        assertEquals(MessageStatus.SENT.name, database.dao().getOutgoingMessage("to-carol")!!.status)

        val sameContact = crossContact.copy(originalMessageId = "to-bob")
        val sameContactMessage = bobManager.encryptMessage(
            bobSession,
            MessagePayloadCodec.encodeDeliveryReceipt(sameContact),
            2,
        )
        assertEquals(
            DeliveryReceiptAcceptance.STATUS_UPDATED,
            repository.acceptDeliveryReceipt("bob", toBob, sameContactMessage, sameContact),
        )
        assertEquals(MessageStatus.DELIVERED.name, database.dao().getOutgoingMessage("to-bob")!!.status)
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
        signalStore.saveKyberPreKeyRecord(13, kyberPreKeyRecord, lastResort = false)

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
        assertArrayEquals(kyberPreKeyRecord, signalStore.loadKyberPreKeyRecord(13)!!.serializedRecord)

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

    @Test
    fun oneTimeKyberPreKeyIsConsumedExactlyOnce() {
        signalStore.saveKyberPreKeyRecord(21, "one-time-kyber".toByteArray(), lastResort = false)

        assertTrue(signalStore.consumeKyberPreKey(21, 5, digest(1)))
        assertNull(signalStore.loadKyberPreKeyRecord(21))
        assertFalse(signalStore.consumeKyberPreKey(21, 5, digest(2)))
    }

    @Test
    fun lastResortKyberPreKeyRejectsRepeatedBaseKeyButStaysAvailable() {
        signalStore.saveKyberPreKeyRecord(22, "last-resort-kyber".toByteArray(), lastResort = true)

        assertTrue(signalStore.consumeKyberPreKey(22, 5, digest(1)))
        assertFalse(signalStore.consumeKyberPreKey(22, 5, digest(1)))
        assertTrue(signalStore.consumeKyberPreKey(22, 5, digest(2)))
        assertTrue(signalStore.consumeKyberPreKey(22, 6, digest(1)))
        assertTrue(signalStore.loadKyberPreKeyRecord(22)!!.lastResort)
    }

    @Test
    fun tamperedKyberLastResortFlagFailsClosed() {
        signalStore.saveKyberPreKeyRecord(23, "one-time-kyber".toByteArray(), lastResort = false)
        val stored = database.dao().getSignalKyberPreKeyRecord(23)!!
        database.dao().putSignalKyberPreKeyRecord(stored.copy(isLastResort = true))

        assertThrows(Exception::class.java) { signalStore.loadKyberPreKeyRecord(23) }
        assertThrows(Exception::class.java) { signalStore.consumeKyberPreKey(23, 5, digest(1)) }
    }

    @Test
    fun localSignalIdentityIsEncryptedAndNeverOverwritten() {
        val keyPair = "local-identity-private-material".toByteArray()

        assertTrue(signalStore.createLocalIdentity(SignalLocalIdentityRecord(4242, keyPair)))
        assertFalse(signalStore.createLocalIdentity(SignalLocalIdentityRecord(7, "replacement".toByteArray())))

        val stored = database.dao().getSignalLocalIdentity(1)!!.encryptedRecord
        assertFalse(stored.toString(Charsets.UTF_8).contains("local-identity-private-material"))
        val loaded = signalStore.loadLocalIdentity()!!
        assertEquals(4242, loaded.registrationId)
        assertArrayEquals(keyPair, loaded.serializedIdentityKeyPair)
    }

    private fun digest(seed: Int) = ByteArray(32) { seed.toByte() }

    @Test
    fun localWipeClearsTestDeliveryReplayAndDisabledSignalState() = runBlocking {
        val localManager = SimplifiedSessionManager(StandardAead())
        val remoteManager = SimplifiedSessionManager(StandardAead())
        val local = localManager.createIdentity()
        val remote = remoteManager.createIdentity()
        val contact = Contact("reset-contact", "Reset", "reset-queue", remote.publicIdentity, VerificationStatus.UNVERIFIED)
        val session = localManager.createSession(local, remote.publicIdentity)
        val message = localManager.encryptMessage(session, "disposable-test".toByteArray(), 9)
        val signalAddress = SignalProtocolAddress("disabled-reset", 1)

        repository.storeProfile("Test", local.publicIdentity.deviceId)
        repository.storeContact(contact)
        repository.storeSession(contact.contactId, session)
        repository.storeEncryptedMessage("pending-reset", contact.contactId, message, true, MessageStatus.PENDING.name)
        assertTrue(repository.acceptIncoming("incoming-reset", contact.contactId, session, message))
        signalStore.saveIdentityRecord(signalAddress, "disabled-record".toByteArray())
        signalStore.createLocalIdentity(SignalLocalIdentityRecord(99, "local-identity".toByteArray()))
        signalStore.saveKyberPreKeyRecord(31, "last-resort".toByteArray(), lastResort = true)
        signalStore.consumeKyberPreKey(31, 1, ByteArray(32) { 3 })

        repository.wipeLocalData()

        assertNull(database.dao().getProfile())
        assertTrue(database.dao().observeContacts().first().isEmpty())
        assertNull(repository.loadSession(contact.contactId))
        assertTrue(database.dao().getDueOutgoingMessages(Long.MAX_VALUE).isEmpty())
        assertNull(signalStore.loadIdentityRecord(signalAddress))
        assertNull(signalStore.loadLocalIdentity())
        // Reuse tracking was wiped too: a fresh key under the same IDs starts clean.
        signalStore.saveKyberPreKeyRecord(31, "last-resort".toByteArray(), lastResort = true)
        assertTrue(signalStore.consumeKyberPreKey(31, 1, ByteArray(32) { 3 }))
        assertTrue(repository.acceptIncoming("incoming-after-reset", contact.contactId, session, message))
    }
}
