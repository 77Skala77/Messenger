package app.securemessenger.signalprotocol

import app.securemessenger.domain.RelayEncryptedBlob
import app.securemessenger.domain.TransportClient
import app.securemessenger.signalstore.ConversationV2Store
import app.securemessenger.signalstore.ProtocolTransactionRunner
import app.securemessenger.signalstore.V2Contact
import app.securemessenger.signalstore.V2ContactRequest
import app.securemessenger.signalstore.V2ContactState
import app.securemessenger.signalstore.V2Invitation
import app.securemessenger.signalstore.V2MessageKind
import app.securemessenger.signalstore.V2MessageStatus
import app.securemessenger.signalstore.V2OutgoingEnvelope
import app.securemessenger.signalstore.V2Route
import app.securemessenger.signalstore.V2StoredMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * In-memory [ConversationV2Store] with the same observable contract as
 * `EncryptedConversationV2Repository` (whose Room behaviour the storage instrumented tests cover),
 * minus encryption. Tests only.
 */
class InMemoryConversationV2Store : ConversationV2Store {
    private class Message(
        val stored: V2StoredMessage,
        val kind: V2MessageKind,
        var status: V2MessageStatus,
        val peerQueueId: String?,
        var envelope: ByteArray?,
        var attemptCount: Int,
        var nextRetryAt: Long?,
    )

    private val invitations = linkedMapOf<String, V2Invitation>()
    private val requests = linkedMapOf<String, V2ContactRequest>()
    private val contacts = linkedMapOf<String, V2Contact>()
    private val messages = linkedMapOf<String, Message>()
    private val accepted = mutableSetOf<String>()
    private var nextLocalId = 0

    /** Fault injection: the next incoming text store throws, as if the process died there. */
    var failNextIncomingStore = false

    @Synchronized
    fun snapshot(): () -> Unit {
        val savedInvitations = LinkedHashMap(invitations)
        val savedRequests = LinkedHashMap(requests)
        val savedContacts = LinkedHashMap(contacts)
        val savedMessages = messages.mapValues { (_, m) ->
            Message(m.stored, m.kind, m.status, m.peerQueueId, m.envelope, m.attemptCount, m.nextRetryAt)
        }
        val savedAccepted = accepted.toSet()
        return {
            synchronized(this) {
                invitations.clear(); invitations.putAll(savedInvitations)
                requests.clear(); requests.putAll(savedRequests)
                contacts.clear(); contacts.putAll(savedContacts)
                messages.clear(); messages.putAll(savedMessages)
                accepted.clear(); accepted.addAll(savedAccepted)
            }
        }
    }

    @Synchronized
    override fun findRoute(localRoutingTagHex: String): V2Route? =
        contacts.values.firstOrNull { it.localRoutingTagHex == localRoutingTagHex }?.let { V2Route.ToContact(it) }
            ?: requests.values.firstOrNull { it.localRoutingTagHex == localRoutingTagHex }?.let { V2Route.ToRequest(it) }
            ?: invitations.values.firstOrNull { it.localRoutingTagHex == localRoutingTagHex && it.usedByRequestId == null }
                ?.let { V2Route.ToInvitation(it) }

    @Synchronized
    override fun isEnvelopeAccepted(digestHex: String) = digestHex in accepted

    @Synchronized
    override fun recordAcceptedEnvelope(digestHex: String, acceptedAtEpochMillis: Long) = accepted.add(digestHex)

    @Synchronized
    override fun saveInvitation(invitation: V2Invitation) {
        check(invitation.invitationId !in invitations)
        check(invitations.values.none { it.localRoutingTagHex == invitation.localRoutingTagHex })
        invitations[invitation.invitationId] = invitation
    }

    @Synchronized
    override fun invitation(invitationId: String) = invitations[invitationId]

    @Synchronized
    override fun latestUsableInvitation(nowEpochMillis: Long) =
        invitations.values.filter { it.usedByRequestId == null && it.expiresAtEpochMillis > nowEpochMillis }
            .maxByOrNull { it.createdAtEpochMillis }

    @Synchronized
    override fun markInvitationUsed(invitationId: String, requestId: String): Boolean {
        val invitation = invitations[invitationId] ?: return false
        if (invitation.usedByRequestId != null) return false
        invitations[invitationId] = invitation.copyUsed(requestId)
        return true
    }

    @Synchronized
    override fun expiredUnusedInvitations(nowEpochMillis: Long) =
        invitations.values.filter { it.usedByRequestId == null && it.expiresAtEpochMillis <= nowEpochMillis }

    @Synchronized
    override fun deleteInvitation(invitationId: String) {
        invitations.remove(invitationId)
    }

    @Synchronized
    override fun saveRequest(request: V2ContactRequest) {
        check(request.requestId !in requests)
        check(requests.values.none { it.localRoutingTagHex == request.localRoutingTagHex })
        requests[request.requestId] = request
    }

    @Synchronized
    override fun request(requestId: String) = requests[requestId]

    @Synchronized
    override fun requests() = requests.values.toList()

    @Synchronized
    override fun deleteRequest(requestId: String) {
        requests.remove(requestId)
    }

    @Synchronized
    override fun saveContact(contact: V2Contact) {
        check(contact.contactId !in contacts)
        check(contacts.values.none { it.localRoutingTagHex == contact.localRoutingTagHex })
        contacts[contact.contactId] = contact
    }

    @Synchronized
    override fun contact(contactId: String) = contacts[contactId]

    @Synchronized
    override fun contactWithIdentity(identityKey: ByteArray) =
        contacts.values.firstOrNull { it.identityKey.contentEquals(identityKey) }

    @Synchronized
    override fun setContactState(contactId: String, state: V2ContactState) {
        val contact = checkNotNull(contacts[contactId]) { "Unknown v2 contact" }
        contacts[contactId] = V2Contact(
            contact.contactId, contact.localDisplayName, contact.identityKey, contact.peerQueueId,
            contact.peerRoutingTagHex, contact.localRoutingTagHex, state, contact.createdAtEpochMillis,
        )
    }

    @Synchronized
    override fun storeIncomingText(contactId: String, innerMessageIdHex: String, text: String, receivedAtEpochMillis: Long): Boolean {
        if (failNextIncomingStore) {
            failNextIncomingStore = false
            throw IllegalStateException("Simulated crash while storing")
        }
        if (messages.values.any { !it.stored.outgoing && it.stored.contactId == contactId && it.stored.innerMessageIdHex == innerMessageIdHex }) {
            return false
        }
        val id = "local-${nextLocalId++}"
        messages[id] = Message(
            V2StoredMessage(id, contactId, false, innerMessageIdHex, text, V2MessageStatus.RECEIVED, receivedAtEpochMillis),
            V2MessageKind.TEXT, V2MessageStatus.RECEIVED, null, null, 0, null,
        )
        return true
    }

    @Synchronized
    override fun incomingTexts(contactId: String) =
        messages.values.filter { !it.stored.outgoing && it.stored.contactId == contactId }.map { it.stored }

    @Synchronized
    override fun texts(contactId: String) =
        messages.values.filter { it.stored.contactId == contactId && it.kind == V2MessageKind.TEXT }.map {
            V2StoredMessage(
                it.stored.messageId, it.stored.contactId, it.stored.outgoing, it.stored.innerMessageIdHex,
                it.stored.text, it.status, it.stored.createdAtEpochMillis,
            )
        }

    @Synchronized
    override fun contacts() = contacts.values.toList()

    @Synchronized
    override fun enqueueOutgoing(envelope: V2OutgoingEnvelope, visibleText: String?, createdAtEpochMillis: Long) {
        require((envelope.kind == V2MessageKind.TEXT) == (visibleText != null))
        check(envelope.messageId !in messages)
        check(messages.values.none { it.stored.outgoing && it.stored.contactId == envelope.contactId && it.stored.innerMessageIdHex == envelope.innerMessageIdHex })
        messages[envelope.messageId] = Message(
            V2StoredMessage(
                envelope.messageId, envelope.contactId, true, envelope.innerMessageIdHex, visibleText.orEmpty(),
                V2MessageStatus.PENDING, createdAtEpochMillis,
            ),
            envelope.kind, V2MessageStatus.PENDING, envelope.peerQueueId, envelope.envelope, envelope.attemptCount, null,
        )
    }

    @Synchronized
    override fun outgoing(messageId: String) = messages[messageId]?.takeIf { it.stored.outgoing }?.toEnvelope()

    @Synchronized
    override fun dueOutgoing(nowEpochMillis: Long) = messages.values.filter {
        it.stored.outgoing && it.status in setOf(V2MessageStatus.PENDING, V2MessageStatus.FAILED) &&
            it.envelope != null && (it.nextRetryAt == null || it.nextRetryAt!! <= nowEpochMillis)
    }.mapNotNull { it.toEnvelope() }

    @Synchronized
    override fun recordSent(messageId: String, attemptCount: Int) {
        val message = messages[messageId] ?: return
        if (message.status !in setOf(V2MessageStatus.PENDING, V2MessageStatus.FAILED)) return
        message.status = V2MessageStatus.SENT
        message.attemptCount = attemptCount
        message.nextRetryAt = null
        message.envelope = null
    }

    @Synchronized
    override fun recordFailed(messageId: String, attemptCount: Int, nextAttemptAtEpochMillis: Long) {
        val message = messages[messageId] ?: return
        if (message.status !in setOf(V2MessageStatus.PENDING, V2MessageStatus.FAILED)) return
        message.status = V2MessageStatus.FAILED
        message.attemptCount = attemptCount
        message.nextRetryAt = nextAttemptAtEpochMillis
    }

    @Synchronized
    override fun markDelivered(contactId: String, innerMessageIdHex: String): Boolean {
        val message = messages.values.firstOrNull {
            it.stored.outgoing && it.kind == V2MessageKind.TEXT && it.stored.contactId == contactId &&
                it.stored.innerMessageIdHex == innerMessageIdHex
        } ?: return false
        message.status = V2MessageStatus.DELIVERED
        message.envelope = null
        return true
    }

    @Synchronized
    override fun deleteMessagesFor(contactId: String) {
        messages.values.removeAll { it.stored.contactId == contactId }
    }

    @Synchronized
    fun statusOf(messageId: String): V2MessageStatus? = messages[messageId]?.status

    @Synchronized
    fun contactIds(): List<String> = contacts.keys.toList()

    @Synchronized
    fun outgoingKinds(contactId: String): List<V2MessageKind> =
        messages.values.filter { it.stored.outgoing && it.stored.contactId == contactId }.map { it.kind }

    private fun Message.toEnvelope(): V2OutgoingEnvelope? {
        val bytes = envelope ?: return null
        return V2OutgoingEnvelope(stored.messageId, stored.contactId, kind, stored.innerMessageIdHex, peerQueueId!!, bytes, attemptCount)
    }

    private fun V2Invitation.copyUsed(requestId: String) = V2Invitation(
        invitationId, localRoutingTagHex, signedPreKeyId, oneTimePreKeyId, kyberPreKeyId, mailboxQueueId,
        codeText, sealedBundle, createdAtEpochMillis, expiresAtEpochMillis, requestId,
    )
}

/** Rolls back both in-memory stores when the block throws, the way a Room transaction does. */
class SnapshotTransactionRunner(
    private val records: InMemorySignalRecords,
    private val store: InMemoryConversationV2Store,
) : ProtocolTransactionRunner {
    override fun <T> inTransaction(block: () -> T): T {
        val restoreRecords = records.snapshot()
        val restoreStore = store.snapshot()
        return try {
            block()
        } catch (failure: Throwable) {
            restoreRecords()
            restoreStore()
            throw failure
        }
    }
}

/** An untrusted relay double: tests read, drop, duplicate, relabel, and inject blobs freely. */
class FakeRelay : TransportClient {
    private val blobs = linkedMapOf<String, RelayEncryptedBlob>()
    var failNextSends = 0

    override suspend fun sendMessage(blob: RelayEncryptedBlob) = synchronized(this) {
        if (failNextSends > 0) {
            failNextSends--
            error("relay offline")
        }
        blobs[blob.messageId] = blob
    }

    override suspend fun fetchMessages(queueId: String): List<RelayEncryptedBlob> =
        synchronized(this) { blobs.values.filter { it.queueId == queueId } }

    override suspend fun deleteMessage(messageId: String) {
        synchronized(this) { blobs.remove(messageId) }
    }

    override fun observeIncomingMessages(queueId: String): Flow<RelayEncryptedBlob> = emptyFlow()

    @Synchronized
    fun queued(queueId: String): List<RelayEncryptedBlob> = blobs.values.filter { it.queueId == queueId }

    @Synchronized
    fun inject(blob: RelayEncryptedBlob) {
        blobs[blob.messageId] = blob
    }

    @Synchronized
    fun take(queueId: String): List<RelayEncryptedBlob> = queued(queueId).also { taken -> taken.forEach { blobs.remove(it.messageId) } }
}

/** One device running the v2 pipeline over in-memory stores and a shared [FakeRelay]. */
class V2TestDevice(val name: String, relay: FakeRelay, clock: () -> Long) {
    val records = InMemorySignalRecords().also { LocalSignalIdentityProvisioner(it).provisionIfAbsent() }
    val signalStore = EncryptedSignalProtocolStore(records, records, records, records, records, records)
    val sessions = SignalSessions(signalStore)
    val store = InMemoryConversationV2Store()
    val queueId = "${name.lowercase()}-receive-queue"
    val service = ConversationV2Service(
        sessions = sessions,
        invitationKeys = InvitationPreKeyFactory(signalStore),
        store = store,
        transactions = SnapshotTransactionRunner(records, store),
        transport = relay,
        localReceiveQueueId = queueId,
        localDisplayName = { name },
        now = clock,
    )
}
