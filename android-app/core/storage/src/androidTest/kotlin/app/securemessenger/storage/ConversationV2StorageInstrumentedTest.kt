package app.securemessenger.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.securemessenger.signalstore.SignalProtocolAddress
import app.securemessenger.signalstore.V2Contact
import app.securemessenger.signalstore.V2ContactRequest
import app.securemessenger.signalstore.V2ContactState
import app.securemessenger.signalstore.V2Invitation
import app.securemessenger.signalstore.V2MessageKind
import app.securemessenger.signalstore.V2OutgoingEnvelope
import app.securemessenger.signalstore.V2Route
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationV2StorageInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, SecureMessengerDatabase::class.java).build()
    private val dao = database.dao()
    private val vault = JcaLocalVaultCipher(ByteArray(32) { 5 })
    private val repository = EncryptedConversationV2Repository(dao, vault)
    private val signalStore = EncryptedSignalStoreRepository(dao, JcaLocalVaultCipher(ByteArray(32) { 7 }))
    private val transactions = RoomProtocolTransactionRunner(database)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun secretsAndBodiesAreEncryptedAtRestAndReadBack() {
        repository.saveInvitation(invitation("inv-1", TAG_A, codeText = "smp-invite-v2:SEAL-KEY-MATERIAL"))
        repository.saveRequest(request("req-1", TAG_B, claimedName = "Claimed Name Mallory"))
        repository.saveContact(contact("contact-1", TAG_C, name = "Alice Private Name"))
        assertTrue(repository.storeIncomingText("contact-1", INNER_1, "incoming top secret", 10))
        repository.enqueueOutgoing(outgoing("out-1", "contact-1", V2MessageKind.TEXT, INNER_2), "outgoing top secret", 11)

        val rawValues = listOf(
            dao.getV2Invitation("inv-1")!!.encryptedCodeText,
            dao.getV2ContactRequest("req-1")!!.encryptedClaimedName,
            dao.getV2Contact("contact-1")!!.encryptedDisplayName,
            dao.getV2IncomingMessages("contact-1").single().encryptedBody!!,
            dao.getV2OutgoingMessage("out-1")!!.encryptedBody!!,
        ).map { String(it, Charsets.ISO_8859_1) }
        listOf("SEAL-KEY", "Mallory", "Alice", "incoming top secret", "outgoing top secret").forEach { secret ->
            assertTrue(rawValues.none { it.contains(secret) })
        }

        assertEquals("smp-invite-v2:SEAL-KEY-MATERIAL", repository.invitation("inv-1")!!.codeText)
        assertEquals("Claimed Name Mallory", repository.request("req-1")!!.claimedDisplayName)
        assertEquals("Alice Private Name", repository.contact("contact-1")!!.localDisplayName)
        assertEquals("incoming top secret", repository.incomingTexts("contact-1").single().text)
        assertEquals("outgoing top secret", repository.readBody(dao.getV2OutgoingMessage("out-1")!!))
    }

    @Test
    fun anEncryptedValueMovedToAnotherRowFailsToDecrypt() {
        repository.saveContact(contact("contact-1", TAG_A, name = "Alice"))
        repository.saveContact(contact("contact-2", TAG_B, name = "Bob"))
        val aliceName = dao.getV2Contact("contact-1")!!.encryptedDisplayName
        database.openHelper.writableDatabase.execSQL(
            "UPDATE v2_contacts SET encryptedDisplayName = ? WHERE contactId = 'contact-2'",
            arrayOf(aliceName),
        )
        assertThrows(Exception::class.java) { repository.contact("contact-2") }
    }

    @Test
    fun routingPrefersContactThenRequestThenUnusedInvitation() {
        repository.saveInvitation(invitation("inv-1", TAG_A))
        assertTrue(repository.findRoute(TAG_A) is V2Route.ToInvitation)

        assertTrue(repository.markInvitationUsed("inv-1", "req-1"))
        assertFalse(repository.markInvitationUsed("inv-1", "req-2"))
        assertNull(repository.findRoute(TAG_A))
        assertEquals("req-1", repository.invitation("inv-1")!!.usedByRequestId)

        repository.saveRequest(request("req-1", TAG_A))
        assertEquals("req-1", (repository.findRoute(TAG_A) as V2Route.ToRequest).request.requestId)

        repository.deleteRequest("req-1")
        repository.saveContact(contact("req-1", TAG_A))
        assertEquals("req-1", (repository.findRoute(TAG_A) as V2Route.ToContact).contact.contactId)
        assertNull(repository.findRoute(TAG_C))
    }

    @Test
    fun duplicatesAreRecordedOnceAndInvitationsExpire() {
        assertTrue(repository.storeIncomingText("contact-1", INNER_1, "once", 1))
        assertFalse(repository.storeIncomingText("contact-1", INNER_1, "again", 2))
        assertTrue(repository.storeIncomingText("contact-2", INNER_1, "same inner ID, other contact", 3))
        assertEquals(listOf("once"), repository.incomingTexts("contact-1").map { it.text })

        assertFalse(repository.isEnvelopeAccepted(DIGEST))
        assertTrue(repository.recordAcceptedEnvelope(DIGEST, 1))
        assertFalse(repository.recordAcceptedEnvelope(DIGEST, 2))
        assertTrue(repository.isEnvelopeAccepted(DIGEST))

        repository.saveInvitation(invitation("old", TAG_A, createdAt = 1, expiresAt = 100))
        repository.saveInvitation(invitation("new", TAG_B, createdAt = 2, expiresAt = 1_000))
        assertEquals("new", repository.latestUsableInvitation(nowEpochMillis = 50)!!.invitationId)
        assertEquals(listOf("old"), repository.expiredUnusedInvitations(nowEpochMillis = 100).map { it.invitationId })
        repository.deleteInvitation("old")
        assertNull(repository.invitation("old"))
    }

    @Test
    fun outgoingQueueRetriesThenDropsCiphertextAndReceiptsStayContactScoped() {
        repository.enqueueOutgoing(outgoing("out-1", "contact-1", V2MessageKind.TEXT, INNER_1), "hi", 1)
        repository.enqueueOutgoing(outgoing("rcpt-1", "contact-1", V2MessageKind.RECEIPT, INNER_2), null, 2)
        assertEquals(listOf("out-1", "rcpt-1"), repository.unsentOutgoing().map { it.messageId })

        // A failed envelope stays first, with its retry time: the sender keeps the order (DEC-026).
        repository.recordFailed("out-1", attemptCount = 1, nextAttemptAtEpochMillis = 5_000)
        val unsent = repository.unsentOutgoing()
        assertEquals(listOf("out-1", "rcpt-1"), unsent.map { it.messageId })
        assertEquals(5_000L, unsent.first().nextAttemptAtEpochMillis)
        assertEquals(1, unsent.first().attemptCount)
        assertNull(unsent.last().nextAttemptAtEpochMillis)

        repository.recordSent("out-1", attemptCount = 2)
        assertNull(dao.getV2OutgoingMessage("out-1")!!.envelope)
        assertNull(repository.outgoing("out-1"))
        assertEquals(listOf("rcpt-1"), repository.unsentOutgoing().map { it.messageId })

        assertFalse(repository.markDelivered("contact-2", INNER_1))
        assertFalse(repository.markDelivered("contact-1", INNER_2))
        assertTrue(repository.markDelivered("contact-1", INNER_1))
        assertEquals("DELIVERED", dao.getV2OutgoingMessage("out-1")!!.status)

        assertThrows(IllegalArgumentException::class.java) {
            repository.enqueueOutgoing(outgoing("bad", "contact-1", V2MessageKind.RECEIPT, INNER_3), "no text for receipts", 3)
        }
        assertThrows(IllegalStateException::class.java) {
            repository.enqueueOutgoing(outgoing("dup", "contact-1", V2MessageKind.TEXT, INNER_1), "duplicate inner ID", 4)
        }
    }

    /**
     * Unsent envelopes come back in insertion order: within one millisecond (an ACCEPT and its
     * receipts), and also when the wall clock moved back in between (second review R4).
     */
    @Test
    fun unsentEnvelopesKeepTheirInsertionOrderWhateverTheClockSays() {
        repository.enqueueOutgoing(outgoing("z-first", "contact-1", V2MessageKind.ACCEPT, INNER_1), null, 7)
        repository.enqueueOutgoing(outgoing("a-second", "contact-1", V2MessageKind.RECEIPT, INNER_2), null, 7)
        repository.enqueueOutgoing(outgoing("m-third", "contact-1", V2MessageKind.RECEIPT, INNER_3), null, 3)
        assertEquals(listOf("z-first", "a-second", "m-third"), repository.unsentOutgoing().map { it.messageId })
    }

    @Test
    fun aFailureInsideATransactionRollsBackSignalRecordsAndConversationRowsTogether() {
        val address = SignalProtocolAddress("contact-1", 1)
        assertThrows(IllegalStateException::class.java) {
            transactions.inTransaction {
                signalStore.saveSessionRecord(address, "ratchet-step".toByteArray())
                repository.storeIncomingText("contact-1", INNER_1, "decrypted text", 1)
                repository.recordAcceptedEnvelope(DIGEST, 1)
                throw IllegalStateException("simulated crash before commit")
            }
        }
        assertNull(signalStore.loadSessionRecord(address))
        assertTrue(repository.incomingTexts("contact-1").isEmpty())
        assertFalse(repository.isEnvelopeAccepted(DIGEST))

        val stored = transactions.inTransaction {
            signalStore.saveSessionRecord(address, "ratchet-step".toByteArray())
            repository.storeIncomingText("contact-1", INNER_1, "decrypted text", 1)
        }
        assertTrue(stored)
        assertArrayEquals("ratchet-step".toByteArray(), signalStore.loadSessionRecord(address))
        assertEquals("decrypted text", repository.incomingTexts("contact-1").single().text)
    }

    @Test
    fun localWipeClearsEveryV2Table() = runBlocking {
        repository.saveInvitation(invitation("inv-1", TAG_A))
        repository.saveRequest(request("req-1", TAG_B))
        repository.saveContact(contact("contact-1", TAG_C))
        repository.storeIncomingText("contact-1", INNER_1, "x", 1)
        repository.recordAcceptedEnvelope(DIGEST, 1)

        LocalProfileRepository(dao, vault).wipeLocalData()

        assertNull(repository.invitation("inv-1"))
        assertTrue(repository.requests().isEmpty())
        assertNull(repository.contact("contact-1"))
        assertTrue(repository.incomingTexts("contact-1").isEmpty())
        assertFalse(repository.isEnvelopeAccepted(DIGEST))
    }

    /**
     * DEC-029 on Room: incoming texts are unseen until marked; the user's own texts never count;
     * the last text follows the conversation's order and is decrypted; marking is per chat.
     */
    @Test
    fun unseenTextsAreCountedPerChatAndTheLastTextIsTheNewestInEitherDirection() {
        assertTrue(repository.storeIncomingText("contact-1", INNER_1, "first", 10))
        assertTrue(repository.storeIncomingText("contact-1", INNER_2, "second", 20))
        repository.enqueueOutgoing(outgoing("out-1", "contact-1", V2MessageKind.TEXT, INNER_3), "my answer", 30)
        repository.enqueueOutgoing(outgoing("rcpt-1", "contact-1", V2MessageKind.RECEIPT, INNER_4), null, 40)
        assertTrue(repository.storeIncomingText("contact-2", INNER_1, "other chat", 15))

        assertEquals(mapOf("contact-1" to 2, "contact-2" to 1), repository.unreadCounts())
        // The receipt is newer, but it is not a text.
        assertEquals("my answer", repository.lastText("contact-1")!!.text)
        assertEquals("other chat", repository.lastText("contact-2")!!.text)
        assertNull(repository.lastText("nobody"))

        assertEquals(2, repository.markRead("contact-1"))
        assertEquals(0, repository.markRead("contact-1"))
        assertEquals(mapOf("contact-2" to 1), repository.unreadCounts())

        assertTrue(repository.storeIncomingText("contact-1", INNER_4, "newest", 50))
        assertEquals(mapOf("contact-1" to 1, "contact-2" to 1), repository.unreadCounts())
        assertEquals("newest", repository.lastText("contact-1")!!.text)
    }

    @Test
    fun contactStateChangesAndIdentityLookupWork() {
        repository.saveContact(contact("contact-1", TAG_A, identity = byteArrayOf(5, 1, 2, 3)))
        assertEquals("contact-1", repository.contactWithIdentity(byteArrayOf(5, 1, 2, 3))!!.contactId)
        assertNull(repository.contactWithIdentity(byteArrayOf(5, 9, 9, 9)))

        repository.setContactState("contact-1", V2ContactState.ACTIVE)
        assertEquals(V2ContactState.ACTIVE, repository.contact("contact-1")!!.state)
        assertThrows(IllegalStateException::class.java) { repository.setContactState("missing", V2ContactState.ACTIVE) }
    }

    /**
     * DEC-030 on Room: the verified mark is stored with a new contact, set and cleared on its own,
     * kept when the state moves on, and removed with a changed key; an unknown contact changes nothing.
     */
    @Test
    fun theVerifiedMarkIsKeptAcrossStateChangesAndDropsWithAChangedKey() {
        repository.saveContact(contact("contact-1", TAG_A).withVerifiedAt(1_000L))
        repository.saveContact(contact("contact-2", TAG_B))
        assertEquals(1_000L, repository.contact("contact-1")!!.verifiedAtEpochMillis)
        assertNull(repository.contact("contact-2")!!.verifiedAtEpochMillis)

        assertTrue(repository.setContactVerified("contact-2", 2_000L))
        repository.setContactState("contact-2", V2ContactState.ACTIVE)
        assertEquals(2_000L, repository.contact("contact-2")!!.verifiedAtEpochMillis)
        assertTrue(repository.contact("contact-2")!!.safetyNumberChecked)

        assertTrue(repository.setContactVerified("contact-2", null))
        assertNull(repository.contact("contact-2")!!.verifiedAtEpochMillis)

        repository.setContactState("contact-1", V2ContactState.KEY_CHANGED)
        assertNull(repository.contact("contact-1")!!.verifiedAtEpochMillis)

        assertFalse(repository.setContactVerified("missing", 3_000L))
        assertEquals(listOf(null, null), repository.contacts().map { it.verifiedAtEpochMillis })
    }

    /** Second review I5 and R6 (DEC-032) on Room: old digests go, recent ones stay; whatever knows the inbox is counted. */
    @Test
    fun oldDigestsAreForgottenAndEverythingThatKnowsTheInboxIsCounted() {
        val recent = DIGEST.replace('d', 'e')
        assertTrue(repository.recordAcceptedEnvelope(DIGEST, 1_000))
        assertTrue(repository.recordAcceptedEnvelope(recent, 5_000))

        assertEquals(1, repository.forgetAcceptedEnvelopesBefore(2_000))
        assertFalse(repository.isEnvelopeAccepted(DIGEST))
        assertTrue(repository.isEnvelopeAccepted(recent))

        assertEquals(0, dao.countV2QueueHolders())
        repository.saveInvitation(invitation("inv-1", TAG_A))
        repository.saveRequest(request("req-1", TAG_B))
        repository.saveContact(contact("contact-1", TAG_C))
        assertEquals(3, dao.countV2QueueHolders())
    }

    private fun V2Contact.withVerifiedAt(time: Long) = V2Contact(
        contactId, localDisplayName, identityKey, peerQueueId, peerRoutingTagHex, localRoutingTagHex, state,
        createdAtEpochMillis, time,
    )

    private fun invitation(
        id: String,
        tag: String,
        codeText: String = "smp-invite-v2:code",
        createdAt: Long = 1,
        expiresAt: Long = Long.MAX_VALUE,
    ) = V2Invitation(id, tag, 11, 12, 13, "inv2-mailbox-$id", codeText, byteArrayOf(1, 2, 3), createdAt, expiresAt)

    private fun request(id: String, tag: String, claimedName: String = "Bob") =
        V2ContactRequest(id, "inv-1", byteArrayOf(5, 7), "bob-receive-queue", TAG_PEER, tag, claimedName, 1)

    private fun contact(
        id: String,
        tag: String,
        name: String = "Alice",
        identity: ByteArray = byteArrayOf(5, 8),
    ) = V2Contact(id, name, identity, "alice-receive-queue", TAG_PEER, tag, V2ContactState.AWAITING_ACCEPTANCE, 1)

    private fun outgoing(id: String, contactId: String, kind: V2MessageKind, inner: String) =
        V2OutgoingEnvelope(id, contactId, kind, inner, "peer-receive-queue", byteArrayOf(9, 9, 9), 0)

    private companion object {
        const val TAG_A = "000102030405060708090a0b0c0d0e0f"
        const val TAG_B = "101112131415161718191a1b1c1d1e1f"
        const val TAG_C = "202122232425262728292a2b2c2d2e2f"
        const val TAG_PEER = "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff"
        const val INNER_1 = "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf"
        const val INNER_2 = "b0b1b2b3b4b5b6b7b8b9babbbcbdbebf"
        const val INNER_3 = "c0c1c2c3c4c5c6c7c8c9cacbcccdcecf"
        const val INNER_4 = "e0e1e2e3e4e5e6e7e8e9eaebecedeeef"
        const val DIGEST = "d0d1d2d3d4d5d6d7d8d9dadbdcdddedfd0d1d2d3d4d5d6d7d8d9dadbdcdddedf"
    }
}
