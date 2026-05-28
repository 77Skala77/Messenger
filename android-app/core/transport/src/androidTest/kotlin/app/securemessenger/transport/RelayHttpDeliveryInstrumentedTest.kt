package app.securemessenger.transport

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.securemessenger.crypto.EncryptedSessionMessage
import app.securemessenger.crypto.EstablishedSession
import app.securemessenger.crypto.SessionMessageCodec
import app.securemessenger.crypto.SimplifiedSessionManager
import app.securemessenger.crypto.StandardAead
import app.securemessenger.delivery.DeliveryPersistence
import app.securemessenger.delivery.DeliveryReceipt
import app.securemessenger.delivery.DeliveryReceiptAcceptance
import app.securemessenger.delivery.IncomingDeliveryProcessor
import app.securemessenger.delivery.IncomingResult
import app.securemessenger.delivery.OutgoingAttemptResult
import app.securemessenger.delivery.OutgoingDeliveryProcessor
import app.securemessenger.delivery.ReceiveBinding
import app.securemessenger.delivery.StoredOutgoingMessage
import app.securemessenger.domain.Contact
import app.securemessenger.domain.ContactDevice
import app.securemessenger.domain.DeviceApprovalStatus
import app.securemessenger.domain.RelayEncryptedBlob
import app.securemessenger.domain.VerificationStatus
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RelayHttpDeliveryInstrumentedTest {
    @Test
    fun twoPeersDeliverAuthenticatedMessageViaNodeRelay() = runBlocking {
        val preferences = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("delivery_relay_endpoint_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val settings = RelayServerSettings(preferences)
        settings.saveUrl("http://127.0.0.1:8787")
        val transport = RelayHttpTransportClient(settings.currentUrl())
        val bobQueue = "instrumented-bob-${UUID.randomUUID()}"
        try {
            transport.fetchMessages(bobQueue)
        } catch (unavailable: Exception) {
            assumeNoException("Start the local Node relay and run adb reverse tcp:8787 tcp:8787", unavailable)
        }
        assertEquals(RelayHealthStatus.AVAILABLE, transport.checkHealth())
        assertEquals(
            RelayHealthStatus.UNAVAILABLE,
            RelayHttpTransportClient("http://127.0.0.1:8787/not-an-endpoint").checkHealth(),
        )
        assertEquals(
            RelayHealthStatus.CONNECTION_ERROR,
            RelayHttpTransportClient("http://127.0.0.1:1").checkHealth(),
        )
        val aliceManager = SimplifiedSessionManager(StandardAead())
        val bobManager = SimplifiedSessionManager(StandardAead())
        val alice = aliceManager.createIdentity()
        val bob = bobManager.createIdentity()
        val aliceQueue = "instrumented-alice-${UUID.randomUUID()}"
        // An invitation publishes its owner's receiving queue; reciprocal import enables A -> B.
        val aliceSession = aliceManager.acceptInvitation(
            alice,
            bobManager.createInvitation(bob.publicIdentity, bobQueue),
        )
        val bobSession = bobManager.acceptInvitation(
            bob,
            aliceManager.createInvitation(alice.publicIdentity, aliceQueue),
        )
        val bobContact = Contact("bob", "Bob", bobQueue, bob.publicIdentity, VerificationStatus.UNVERIFIED)
        val aliceStore = MemoryPersistence().apply {
            enqueue(
                StoredOutgoingMessage(
                    "delivery-${UUID.randomUUID()}",
                    bobContact,
                    listOf(
                        ContactDevice(
                            bobContact.contactId,
                            bobQueue,
                            bob.publicIdentity,
                            DeviceApprovalStatus.PRIMARY_UNVERIFIED,
                            1,
                        ),
                    ),
                    aliceManager.encryptMessage(aliceSession, "over-node-relay".toByteArray(), 72),
                    0,
                ),
            )
        }
        val bobStore = MemoryPersistence()
        val outgoing = OutgoingDeliveryProcessor(aliceStore, transport)
        val incoming = IncomingDeliveryProcessor(bobStore, transport, bobManager)
        val outgoingId = aliceStore.outgoing.keys.single()

        assertEquals(OutgoingAttemptResult.SENT, outgoing.attemptMessage(outgoingId))
        val delivered = transport.fetchMessages(bobQueue).single()
        assertEquals(
            IncomingResult.ACCEPTED,
            incoming.processFetched(delivered, listOf(ReceiveBinding("alice", bobSession))),
        )
        assertTrue(transport.fetchMessages(bobQueue).isEmpty())
        assertEquals(1, bobStore.accepted.size)

        transport.sendMessage(delivered)
        assertEquals(
            IncomingResult.AUTHENTICATED_DUPLICATE,
            incoming.processFetched(delivered, listOf(ReceiveBinding("alice", bobSession))),
        )
        assertEquals(1, bobStore.accepted.size)
        assertTrue(transport.fetchMessages(bobQueue).isEmpty())

        val tamperedMessage = aliceManager.encryptMessage(aliceSession, "tampered".toByteArray(), 73)
            .copy(associatedData = "wrong-aad".toByteArray())
        val tamperedBlob = RelayEncryptedBlob(
            messageId = "tampered-${UUID.randomUUID()}",
            queueId = bobQueue,
            opaqueCiphertext = SessionMessageCodec.encode(tamperedMessage),
            expiresAtEpochMillis = System.currentTimeMillis() + 60_000,
        )
        transport.sendMessage(tamperedBlob)
        assertEquals(
            IncomingResult.UNAUTHENTICATED,
            incoming.fetchAndProcess(bobQueue, listOf(ReceiveBinding("alice", bobSession))).single(),
        )
        assertEquals(1, bobStore.accepted.size)
        assertEquals(tamperedBlob.messageId, transport.fetchMessages(bobQueue).single().messageId)
        transport.deleteMessage(tamperedBlob.messageId)
    }

    private class MemoryPersistence : DeliveryPersistence {
        val outgoing = mutableMapOf<String, StoredOutgoingMessage>()
        val accepted = mutableListOf<EncryptedSessionMessage>()
        private val replay = mutableSetOf<Triple<String, String, Long>>()

        fun enqueue(message: StoredOutgoingMessage) {
            outgoing[message.messageId] = message
        }

        override suspend fun outgoingMessage(messageId: String): StoredOutgoingMessage? = outgoing[messageId]

        override suspend fun dueOutgoingMessages(nowEpochMillis: Long): List<StoredOutgoingMessage> = emptyList()

        override suspend fun retryableOutgoingMessages(): List<StoredOutgoingMessage> = emptyList()

        override suspend fun recordSent(messageId: String, attemptCount: Int) {
            outgoing[messageId] = outgoing.getValue(messageId).copy(attemptCount = attemptCount)
        }

        override suspend fun recordFailed(messageId: String, attemptCount: Int, nextAttemptAtEpochMillis: Long) {
            outgoing[messageId] = outgoing.getValue(messageId).copy(attemptCount = attemptCount)
        }

        override suspend fun acceptIncoming(
            messageId: String,
            contactId: String,
            session: EstablishedSession,
            message: EncryptedSessionMessage,
        ): Boolean {
            if (!replay.add(Triple(session.conversationId, session.remoteDeviceId, message.messageNumber))) {
                return false
            }
            accepted += message
            return true
        }

        override suspend fun acceptDeliveryReceipt(
            contactId: String,
            session: EstablishedSession,
            message: EncryptedSessionMessage,
            receipt: DeliveryReceipt,
        ): DeliveryReceiptAcceptance {
            if (!replay.add(Triple(session.conversationId, session.remoteDeviceId, message.messageNumber))) {
                return DeliveryReceiptAcceptance.DUPLICATE_SUPPRESSED
            }
            val existing = outgoing[receipt.originalMessageId] ?: return DeliveryReceiptAcceptance.UNKNOWN_MESSAGE
            outgoing[receipt.originalMessageId] = existing.copy(attemptCount = existing.attemptCount)
            return DeliveryReceiptAcceptance.STATUS_UPDATED
        }

        override suspend fun queueOutgoingControlMessage(
            messageId: String,
            contactId: String,
            session: EstablishedSession,
            message: EncryptedSessionMessage,
        ) {
            val original = outgoing.values.firstOrNull() ?: return
            outgoing[messageId] = original.copy(messageId = messageId, encryptedMessage = message, attemptCount = 0)
        }
    }
}
