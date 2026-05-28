package app.securemessenger.delivery

import app.securemessenger.crypto.EncryptedSessionMessage
import app.securemessenger.crypto.EstablishedSession
import app.securemessenger.crypto.SessionMessageCodec
import app.securemessenger.crypto.SimplifiedSessionManager
import app.securemessenger.crypto.StandardAead
import app.securemessenger.domain.Contact
import app.securemessenger.domain.ContactDevice
import app.securemessenger.domain.DeviceApprovalStatus
import app.securemessenger.domain.DeliveryBlockReason
import app.securemessenger.domain.MessageStatus
import app.securemessenger.domain.RelayEncryptedBlob
import app.securemessenger.domain.PairingStatus
import app.securemessenger.domain.TransportClient
import app.securemessenger.domain.VerificationStatus
import app.securemessenger.domain.pairingStatus
import app.securemessenger.domain.deliveryBlockReason
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryPipelineTest {
    @Test
    fun `two peers exchange authenticated message through relay and reject duplicate`() = runBlocking {
        val fixture = PairedPeers()
        val plaintext = "relay-delivery-check".toByteArray()
        val encrypted = fixture.aliceManager.encryptMessage(fixture.aliceSession, plaintext, 41)
        fixture.aliceStore.enqueue("message-1", fixture.bobContact, encrypted)
        val outbound = fixture.outgoing.attemptMessage("message-1")
        val deliveredBlob = fixture.relay.fetchMessages(BOB_QUEUE).single()

        assertEquals(OutgoingAttemptResult.SENT, outbound)
        assertFalse(deliveredBlob.opaqueCiphertext.toString(Charsets.UTF_8).contains("relay-delivery-check"))
        assertEquals(
            IncomingResult.ACCEPTED,
            fixture.incoming.processFetched(deliveredBlob, listOf(ReceiveBinding("alice", fixture.bobSession))),
        )
        assertTrue(fixture.relay.fetchMessages(BOB_QUEUE).isEmpty())
        assertEquals(1, fixture.bobStore.acceptedMessages.size)
        assertArrayEquals(
            plaintext,
            fixture.bobManager.decryptStoredMessage(
                fixture.bobSession,
                fixture.bobStore.acceptedMessages.single(),
                outgoing = false,
            ),
        )

        fixture.relay.sendMessage(deliveredBlob)
        assertEquals(
            IncomingResult.AUTHENTICATED_DUPLICATE,
            fixture.incoming.processFetched(deliveredBlob, listOf(ReceiveBinding("alice", fixture.bobSession))),
        )
        assertEquals(1, fixture.bobStore.acceptedMessages.size)
        assertTrue(fixture.relay.fetchMessages(BOB_QUEUE).isEmpty())
    }

    @Test
    fun `wrong aad and tag remain unauthenticated and are never stored`() = runBlocking {
        val fixture = PairedPeers()
        val valid = fixture.aliceManager.encryptMessage(fixture.aliceSession, "do-not-store".toByteArray(), 9)
        val wrongAad = valid.copy(associatedData = "wrong-aad".toByteArray())
        val wrongTag = valid.copy(
            ciphertextPackage = valid.ciphertextPackage.copy(
                tag = valid.ciphertextPackage.tag.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() },
            ),
        )

        listOf(wrongAad, wrongTag).forEachIndexed { index, tampered ->
            val blob = RelayEncryptedBlob(
                messageId = "tampered-$index",
                queueId = BOB_QUEUE,
                opaqueCiphertext = SessionMessageCodec.encode(tampered),
                expiresAtEpochMillis = Long.MAX_VALUE,
            )
            fixture.relay.sendMessage(blob)
            assertEquals(
                IncomingResult.UNAUTHENTICATED,
                fixture.incoming.processFetched(blob, listOf(ReceiveBinding("alice", fixture.bobSession))),
            )
        }

        assertTrue(fixture.bobStore.acceptedMessages.isEmpty())
        assertEquals(2, fixture.relay.fetchMessages(BOB_QUEUE).size)
    }

    @Test
    fun `network failure persists retry metadata and a due retry succeeds`() = runBlocking {
        var now = 1_000L
        val fixture = PairedPeers(now = { now })
        fixture.relay.failNextSends = 1
        val encrypted = fixture.aliceManager.encryptMessage(fixture.aliceSession, "retry".toByteArray(), 10)
        fixture.aliceStore.enqueue("retry-1", fixture.bobContact, encrypted)

        assertEquals(OutgoingAttemptResult.RETRY_SCHEDULED, fixture.outgoing.attemptMessage("retry-1"))
        val failed = fixture.aliceStore.outgoing.getValue("retry-1")
        assertEquals(1, failed.attemptCount)
        assertEquals(6_000L, failed.nextRetryAtEpochMillis)
        assertTrue(fixture.outgoing.retryDueMessages().isEmpty())

        now = 6_000L
        assertEquals(listOf(OutgoingAttemptResult.SENT), fixture.outgoing.retryDueMessages())
        assertEquals(2, fixture.aliceStore.outgoing.getValue("retry-1").attemptCount)
        assertEquals(1, fixture.relay.fetchMessages(BOB_QUEUE).size)
    }

    @Test
    fun `sync executor retries due outgoing ciphertext through transport boundary`() = runBlocking {
        var now = 1_000L
        val fixture = PairedPeers(now = { now })
        fixture.relay.failNextSends = 1
        val encrypted = fixture.aliceManager.encryptMessage(fixture.aliceSession, "scheduled".toByteArray(), 12)
        fixture.aliceStore.enqueue("scheduled-1", fixture.bobContact, encrypted)
        assertEquals(OutgoingAttemptResult.RETRY_SCHEDULED, fixture.outgoing.attemptMessage("scheduled-1"))

        now = 6_000L
        val result = DeliverySyncExecutor(
            outgoing = fixture.outgoing,
            incoming = fixture.incoming,
            queueIdProvider = { null },
            receiveBindingsProvider = { emptyList() },
        ).synchronizeNow()

        assertEquals(listOf(OutgoingAttemptResult.SENT), result.outgoingResults)
        assertTrue(result.incomingResults.isEmpty())
        assertEquals(1, fixture.relay.fetchMessages(BOB_QUEUE).size)
    }

    @Test
    fun `manual sync force retries failed message before next scheduled retry`() = runBlocking {
        var now = 1_000L
        val fixture = PairedPeers(now = { now })
        fixture.relay.failNextSends = 1
        val encrypted = fixture.aliceManager.encryptMessage(fixture.aliceSession, "manual-retry".toByteArray(), 12)
        fixture.aliceStore.enqueue("manual-retry-1", fixture.bobContact, encrypted)
        assertEquals(OutgoingAttemptResult.RETRY_SCHEDULED, fixture.outgoing.attemptMessage("manual-retry-1"))
        assertTrue(fixture.outgoing.retryDueMessages().isEmpty())

        val result = DeliverySyncExecutor(
            outgoing = fixture.outgoing,
            incoming = fixture.incoming,
            queueIdProvider = { null },
            receiveBindingsProvider = { emptyList() },
        ).synchronizeNow(forceRetry = true)

        assertEquals(listOf(OutgoingAttemptResult.SENT), result.outgoingResults)
        assertEquals(2, fixture.aliceStore.outgoing.getValue("manual-retry-1").attemptCount)
        assertEquals(MessageStatus.SENT, fixture.aliceStore.outgoing.getValue("manual-retry-1").status)
        assertEquals(1, fixture.relay.fetchMessages(BOB_QUEUE).size)
    }

    @Test
    fun `retry after relay recovery does not create duplicate plaintext`() = runBlocking {
        var now = 1_000L
        val fixture = PairedPeers(now = { now })
        fixture.relay.failNextSends = 1
        val encrypted = fixture.aliceManager.encryptMessage(fixture.aliceSession, "retry-once".toByteArray(), 14)
        fixture.aliceStore.enqueue("retry-once-1", fixture.bobContact, encrypted)
        assertEquals(OutgoingAttemptResult.RETRY_SCHEDULED, fixture.outgoing.attemptMessage("retry-once-1"))

        val sync = DeliverySyncExecutor(
            outgoing = fixture.outgoing,
            incoming = fixture.incoming,
            queueIdProvider = { BOB_QUEUE },
            receiveBindingsProvider = { listOf(ReceiveBinding("alice", fixture.bobSession)) },
        )
        assertEquals(listOf(OutgoingAttemptResult.SENT), sync.synchronizeNow(forceRetry = true).outgoingResults)
        assertEquals(1, fixture.bobStore.acceptedMessages.size)

        now = 10_000L
        val second = sync.synchronizeNow(forceRetry = true)
        assertTrue(second.outgoingResults.isEmpty())
        assertEquals(1, fixture.bobStore.acceptedMessages.size)
        assertTrue(fixture.relay.fetchMessages(BOB_QUEUE).isEmpty())
    }

    @Test
    fun `fan out policy never selects pending revoked changed or secondary devices`() {
        val fixture = PairedPeers()
        val planner = PrimarySessionOnlyFanOutPlanner()
        val primary = fixture.bobDevice

        assertTrue(planner.targetsForSingleDeviceCiphertext(fixture.bobContact, emptyList()).isEmpty())
        assertEquals(1, planner.targetsForSingleDeviceCiphertext(fixture.bobContact, listOf(primary)).size)
        listOf(
            DeviceApprovalStatus.PENDING_APPROVAL,
            DeviceApprovalStatus.REVOKED,
            DeviceApprovalStatus.KEY_CHANGED,
        ).forEach { state ->
            assertTrue(planner.targetsForSingleDeviceCiphertext(fixture.bobContact, listOf(primary.copy(approvalStatus = state))).isEmpty())
        }
        val secondary = primary.copy(
            identity = fixture.aliceIdentity.publicIdentity,
            queueId = "secondary-queue",
            approvalStatus = DeviceApprovalStatus.VERIFIED,
        )
        assertEquals(1, planner.targetsForSingleDeviceCiphertext(fixture.bobContact, listOf(primary, secondary)).size)
    }

    @Test
    fun `pairing status requires explicit reciprocal confirmation and usable device`() {
        val fixture = PairedPeers()

        assertEquals(PairingStatus.WAITING_FOR_RETURN_INVITATION, pairingStatus(null, emptyList()))
        assertEquals(PairingStatus.DEVICE_BLOCKED, pairingStatus(fixture.bobContact, emptyList()))
        assertEquals(
            PairingStatus.DEVICE_BLOCKED,
            pairingStatus(
                fixture.bobContact,
                listOf(fixture.bobDevice.copy(approvalStatus = DeviceApprovalStatus.REVOKED)),
            ),
        )
        assertEquals(
            PairingStatus.WAITING_FOR_RETURN_INVITATION,
            pairingStatus(fixture.bobContact, listOf(fixture.bobDevice)),
        )
        assertEquals(
            PairingStatus.READY_FOR_CONVERSATION,
            pairingStatus(fixture.bobContact, listOf(fixture.bobDevice), reciprocalInvitationConfirmed = true),
        )
    }

    @Test
    fun `delivery block reason distinguishes device policy failures`() {
        val fixture = PairedPeers()
        assertEquals(
            DeliveryBlockReason.MISSING_PRIMARY_DEVICE,
            deliveryBlockReason(fixture.bobContact, emptyList()),
        )
        assertEquals(
            DeliveryBlockReason.PENDING_APPROVAL,
            deliveryBlockReason(
                fixture.bobContact,
                listOf(fixture.bobDevice.copy(approvalStatus = DeviceApprovalStatus.PENDING_APPROVAL)),
            ),
        )
        assertEquals(
            DeliveryBlockReason.REVOKED,
            deliveryBlockReason(
                fixture.bobContact,
                listOf(fixture.bobDevice.copy(approvalStatus = DeviceApprovalStatus.REVOKED)),
            ),
        )
        assertEquals(
            DeliveryBlockReason.KEY_CHANGED,
            deliveryBlockReason(
                fixture.bobContact,
                listOf(fixture.bobDevice.copy(approvalStatus = DeviceApprovalStatus.KEY_CHANGED)),
            ),
        )
    }

    private class PairedPeers(now: () -> Long = { 1_000L }) {
        val aliceManager = SimplifiedSessionManager(StandardAead())
        val bobManager = SimplifiedSessionManager(StandardAead())
        val aliceIdentity = aliceManager.createIdentity()
        private val bobIdentity = bobManager.createIdentity()
        // Invitation QR represents the creator's receiving queue; reciprocal pairing enables A -> B.
        private val aliceInvite = aliceManager.createInvitation(aliceIdentity.publicIdentity, ALICE_QUEUE)
        private val bobInvite = bobManager.createInvitation(bobIdentity.publicIdentity, BOB_QUEUE)
        val bobSession = bobManager.acceptInvitation(bobIdentity, aliceInvite)
        val aliceSession = aliceManager.acceptInvitation(aliceIdentity, bobInvite)
        val bobContact = Contact("bob", "Bob", BOB_QUEUE, bobIdentity.publicIdentity, VerificationStatus.UNVERIFIED)
        val bobDevice = ContactDevice(
            "bob",
            BOB_QUEUE,
            bobIdentity.publicIdentity,
            DeviceApprovalStatus.PRIMARY_UNVERIFIED,
            1,
        )
        val relay = MailboxRelay()
        val aliceStore = MemoryDeliveryStore()
        val bobStore = MemoryDeliveryStore()
        val outgoing = OutgoingDeliveryProcessor(aliceStore, relay, now = now)
        val incoming = IncomingDeliveryProcessor(bobStore, relay, bobManager)

        init {
            aliceStore.devices["bob"] = listOf(bobDevice)
        }
    }

    private data class MutableOutbound(
        val record: StoredOutgoingMessage,
        val status: MessageStatus = MessageStatus.PENDING,
        val nextRetryAtEpochMillis: Long? = null,
    ) {
        val attemptCount: Int get() = record.attemptCount
    }

    private class MemoryDeliveryStore : DeliveryPersistence {
        val outgoing = mutableMapOf<String, MutableOutbound>()
        val devices = mutableMapOf<String, List<ContactDevice>>()
        val acceptedMessages = mutableListOf<EncryptedSessionMessage>()
        private val replayNumbers = mutableSetOf<Triple<String, String, Long>>()

        fun enqueue(id: String, contact: Contact, message: EncryptedSessionMessage) {
            outgoing[id] = MutableOutbound(
                StoredOutgoingMessage(id, contact, devices[contact.contactId].orEmpty(), message, 0),
            )
        }

        override suspend fun outgoingMessage(messageId: String): StoredOutgoingMessage? =
            outgoing[messageId]?.record

        override suspend fun dueOutgoingMessages(nowEpochMillis: Long): List<StoredOutgoingMessage> =
            outgoing.values.filter {
                it.status in setOf(MessageStatus.PENDING, MessageStatus.FAILED) &&
                    (it.nextRetryAtEpochMillis == null || it.nextRetryAtEpochMillis <= nowEpochMillis)
            }.map { it.record }

        override suspend fun retryableOutgoingMessages(): List<StoredOutgoingMessage> =
            outgoing.values.filter {
                it.status in setOf(MessageStatus.PENDING, MessageStatus.FAILED)
            }.map { it.record }

        override suspend fun recordSent(messageId: String, attemptCount: Int) {
            outgoing[messageId] = outgoing.getValue(messageId).copy(
                record = outgoing.getValue(messageId).record.copy(attemptCount = attemptCount),
                status = MessageStatus.SENT,
                nextRetryAtEpochMillis = Long.MAX_VALUE,
            )
        }

        override suspend fun recordFailed(messageId: String, attemptCount: Int, nextAttemptAtEpochMillis: Long) {
            outgoing[messageId] = outgoing.getValue(messageId).copy(
                record = outgoing.getValue(messageId).record.copy(attemptCount = attemptCount),
                status = MessageStatus.FAILED,
                nextRetryAtEpochMillis = nextAttemptAtEpochMillis,
            )
        }

        override suspend fun acceptIncoming(
            messageId: String,
            contactId: String,
            session: EstablishedSession,
            message: EncryptedSessionMessage,
        ): Boolean {
            val key = Triple(session.conversationId, session.remoteDeviceId, message.messageNumber)
            if (!replayNumbers.add(key)) return false
            acceptedMessages += message
            return true
        }
    }

    private class MailboxRelay : TransportClient {
        private val queued = mutableMapOf<String, RelayEncryptedBlob>()
        var failNextSends = 0

        override suspend fun sendMessage(blob: RelayEncryptedBlob) {
            if (failNextSends > 0) {
                failNextSends--
                error("offline")
            }
            queued[blob.messageId] = blob
        }

        override suspend fun fetchMessages(queueId: String): List<RelayEncryptedBlob> =
            queued.values.filter { it.queueId == queueId }

        override suspend fun deleteMessage(messageId: String) {
            queued.remove(messageId)
        }

        override fun observeIncomingMessages(queueId: String): Flow<RelayEncryptedBlob> = emptyFlow()
    }

    private companion object {
        const val ALICE_QUEUE = "alice-queue"
        const val BOB_QUEUE = "bob-queue-1"
    }
}
