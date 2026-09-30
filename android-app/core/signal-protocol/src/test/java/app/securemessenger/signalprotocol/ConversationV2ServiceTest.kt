package app.securemessenger.signalprotocol

import app.securemessenger.domain.RelayEncryptedBlob
import app.securemessenger.signalstore.V2ContactState
import app.securemessenger.signalstore.V2MessageKind
import app.securemessenger.signalstore.V2MessageStatus
import java.security.GeneralSecurityException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.libsignal.protocol.message.CiphertextMessage

/**
 * The v2 pipeline end to end on the JVM: two or three devices, real libsignal PQXDH + Double
 * Ratchet, a shared relay double that the tests use as the attacker.
 */
class ConversationV2ServiceTest {
    private var clock = 1_800_000_000_000L
    private val relay = FakeRelay()
    private val alice = V2TestDevice("Alice", relay) { clock }
    private val bob = V2TestDevice("Bob", relay) { clock }

    @Test
    fun invitationRequestAcceptanceMessagesAndReceipts() = runBlocking {
        val code = alice.service.createInvitation()
        assertTrue(code.startsWith(InvitationV2.PREFIX))

        val aliceOnBob = bob.service.acceptInvitation(code, "Alice")
        assertEquals(V2ContactState.AWAITING_ACCEPTANCE, bob.store.contact(aliceOnBob)!!.state)
        assertEquals(listOf(V2MessageKind.HELLO), bob.store.outgoingKinds(aliceOnBob))

        assertEquals(listOf(IncomingV2Result.REQUEST_RECEIVED), alice.service.synchronize())
        val request = alice.store.requests().single()
        assertEquals("Bob", request.claimedDisplayName)
        assertTrue(bob.sessions.localIdentityKey().serialize().contentEquals(request.identityKey))
        assertTrue(relay.queued(alice.queueId).isEmpty())

        alice.service.acceptRequest(request.requestId, "Bob")
        val bobOnAlice = request.requestId
        assertEquals(V2ContactState.ACTIVE, alice.store.contact(bobOnAlice)!!.state)
        assertTrue(alice.store.requests().isEmpty())

        assertEquals(listOf(IncomingV2Result.CONTACT_ACCEPTED), bob.service.synchronize())
        assertEquals(V2ContactState.ACTIVE, bob.store.contact(aliceOnBob)!!.state)

        val sent = bob.service.sendText(aliceOnBob, "привет, Алиса")
        assertEquals(listOf(IncomingV2Result.TEXT_ACCEPTED), alice.service.synchronize())
        assertEquals(listOf("привет, Алиса"), alice.store.incomingTexts(bobOnAlice).map { it.text })
        assertEquals(V2MessageStatus.SENT, bob.store.statusOf(sent))

        assertEquals(listOf(IncomingV2Result.RECEIPT_APPLIED), bob.service.synchronize())
        assertEquals(V2MessageStatus.DELIVERED, bob.store.statusOf(sent))

        alice.service.sendText(bobOnAlice, "hi Bob")
        // Only an opaque envelope reaches the relay.
        assertTrue(relay.queued(bob.queueId).none { String(it.opaqueCiphertext, Charsets.ISO_8859_1).contains("hi Bob") })
        assertEquals(listOf(IncomingV2Result.TEXT_ACCEPTED), bob.service.synchronize())
        assertEquals(listOf("hi Bob"), bob.store.incomingTexts(aliceOnBob).map { it.text })
    }

    /** DEC-021: nothing of the removed legacy protocol is ever accepted by v2. */
    @Test
    fun legacyEnvelopesAndInvitationsAreNeverAccepted() = runBlocking {
        val (_, bobOnAlice) = pair()
        // Legacy session-message envelope: 32-bit version 1, message number, bucket, AAD, ciphertext.
        val legacyEnvelope = java.io.ByteArrayOutputStream().also { bytes ->
            java.io.DataOutputStream(bytes).use { out ->
                out.writeInt(1)
                out.writeLong(7)
                out.writeLong(123)
                out.writeInt(16)
                out.write(ByteArray(16) { 1 })
                out.writeInt(48)
                out.write(ByteArray(48) { 2 })
            }
        }.toByteArray()
        relay.inject(RelayEncryptedBlob("legacy-envelope", alice.queueId, legacyEnvelope, Long.MAX_VALUE))

        assertEquals(listOf(IncomingV2Result.UNAUTHENTICATED), alice.service.synchronize())
        assertTrue(alice.store.incomingTexts(bobOnAlice).isEmpty())
        assertEquals(1, relay.queued(alice.queueId).size)

        val legacyInvitation = "smp-invite-v1:" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(120) { 3 })
        val rejected = assertThrows(InvitationRejectedException::class.java) {
            runBlocking { bob.service.acceptInvitation(legacyInvitation, "Legacy") }
        }
        assertEquals(InvitationRejection.UNSUPPORTED_VERSION, rejected.reason)
        assertEquals(1, bob.store.contactIds().size)
    }

    @Test
    fun bothSidesSeeTheSameSafetyNumberAndASubstitutedKeyChangesIt() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        val onBob = bob.service.safetyNumber(aliceOnBob)!!
        val onAlice = alice.service.safetyNumber(bobOnAlice)!!
        assertEquals(onBob, onAlice)
        assertEquals(60, onBob.length)
        assertTrue(onBob.all(Char::isDigit))

        // A third party paired with Alice has a different number: the digits commit to both keys.
        val carol = V2TestDevice("Carol", relay) { clock }
        val aliceOnCarol = carol.service.acceptInvitation(alice.service.createInvitation(), "Alice")
        alice.service.synchronize()
        val carolOnAlice = alice.store.requests().single().requestId
        assertFalse(alice.service.safetyNumber(carolOnAlice) == onAlice)
        assertFalse(carol.service.safetyNumber(aliceOnCarol) == onBob)
        // Unknown contacts have none.
        assertNull(alice.service.safetyNumber("unknown-contact"))
    }

    @Test
    fun messagesBeforeAcceptanceAreHeldWithoutReceiptsThenReceipted() = runBlocking {
        val aliceOnBob = bob.service.acceptInvitation(alice.service.createInvitation(), "Alice")
        val early = bob.service.sendText(aliceOnBob, "are you there?")

        assertEquals(
            listOf(IncomingV2Result.REQUEST_RECEIVED, IncomingV2Result.TEXT_ACCEPTED),
            alice.service.synchronize(),
        )
        val request = alice.store.requests().single()
        assertEquals(listOf("are you there?"), alice.store.incomingTexts(request.requestId).map { it.text })
        assertTrue("no receipt before acceptance", relay.queued(bob.queueId).isEmpty())

        alice.service.acceptRequest(request.requestId, "Bob")
        assertEquals(listOf(V2MessageKind.ACCEPT, V2MessageKind.RECEIPT), alice.store.outgoingKinds(request.requestId))
        val results = bob.service.synchronize()
        assertEquals(setOf(IncomingV2Result.CONTACT_ACCEPTED, IncomingV2Result.RECEIPT_APPLIED), results.toSet())
        assertEquals(V2MessageStatus.DELIVERED, bob.store.statusOf(early))
    }

    @Test
    fun declinedRequestLeavesNoContactSessionIdentityOrMessages() = runBlocking {
        val code = alice.service.createInvitation()
        bob.service.acceptInvitation(code, "Alice").also { bob.service.sendText(it, "hello?") }
        alice.service.synchronize()
        val requestId = alice.store.requests().single().requestId

        alice.service.declineRequest(requestId)

        assertTrue(alice.store.requests().isEmpty())
        assertNull(alice.store.contact(requestId))
        assertTrue(alice.store.incomingTexts(requestId).isEmpty())
        assertFalse(alice.sessions.hasSession(requestId))
        assertNull(alice.sessions.identityOf(requestId))
        // Later messages for the declined pairing no longer route anywhere.
        bob.service.sendText(bob.store.contactIds().single(), "still there?")
        assertEquals(listOf(IncomingV2Result.UNROUTABLE), alice.service.synchronize())
    }

    @Test
    fun duplicateAndRelabelledEnvelopesAreRecognisedWithoutDecryption() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        bob.service.sendText(aliceOnBob, "once")
        val original = relay.queued(alice.queueId).single()

        assertEquals(IncomingV2Result.TEXT_ACCEPTED, alice.service.processIncoming(original))
        assertTrue(relay.queued(alice.queueId).isEmpty())

        // The relay re-delivers the same bytes, also under a different relay message ID.
        relay.inject(original)
        relay.inject(original.copy(messageId = "relabelled-by-relay"))
        assertEquals(listOf(IncomingV2Result.DUPLICATE, IncomingV2Result.DUPLICATE), alice.service.synchronize())
        assertTrue(relay.queued(alice.queueId).isEmpty())
        assertEquals(listOf("once"), alice.store.incomingTexts(bobOnAlice).map { it.text })
    }

    @Test
    fun aFailureWhileStoringRollsBackTheRatchetStepAndTheEnvelopeIsProcessedLater() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        bob.service.sendText(aliceOnBob, "survives a crash")
        alice.store.failNextIncomingStore = true

        assertThrows(IllegalStateException::class.java) { runBlocking { alice.service.synchronize() } }
        assertEquals(1, relay.queued(alice.queueId).size)
        assertTrue(alice.store.incomingTexts(bobOnAlice).isEmpty())

        assertEquals(listOf(IncomingV2Result.TEXT_ACCEPTED), alice.service.synchronize())
        assertEquals(listOf("survives a crash"), alice.store.incomingTexts(bobOnAlice).map { it.text })
        bob.service.sendText(aliceOnBob, "and the session still works")
        assertEquals(listOf(IncomingV2Result.TEXT_ACCEPTED), alice.service.synchronize())
        assertEquals(2, alice.store.incomingTexts(bobOnAlice).size)
    }

    /**
     * DEC-023: with the vault key lost, libsignal's own store callbacks fail. That failure must come
     * out unchanged, as an error, never as a protocol verdict (unauthenticated, key changed): the
     * envelope stays on the relay, no state changes, nothing is queued, and everything works once
     * the records are readable again.
     */
    @Test
    fun aLostVaultKeyInsideLibsignalCallbacksIsAnErrorNeverAVerdictAndChangesNothing() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        bob.service.sendText(aliceOnBob, "arrives later")
        val queuedBefore = alice.store.outgoingKinds(bobOnAlice)
        alice.records.failure = ::SimulatedVaultKeyLoss

        // Unchecked failures cross libsignal unchanged, so callers can recognise them by type.
        assertThrows(SimulatedVaultKeyLoss::class.java) { runBlocking { alice.service.synchronize() } }
        assertEquals(1, relay.queued(alice.queueId).size)
        assertTrue(alice.store.incomingTexts(bobOnAlice).isEmpty())
        assertEquals(V2ContactState.ACTIVE, alice.store.contact(bobOnAlice)!!.state)

        assertThrows(SimulatedVaultKeyLoss::class.java) { runBlocking { alice.service.sendText(bobOnAlice, "never queued") } }
        assertEquals(queuedBefore, alice.store.outgoingKinds(bobOnAlice))

        alice.records.failure = null
        assertEquals(listOf(IncomingV2Result.TEXT_ACCEPTED), alice.service.synchronize())
        assertEquals(listOf("arrives later"), alice.store.incomingTexts(bobOnAlice).map { it.text })
        assertEquals(V2ContactState.ACTIVE, alice.store.contact(bobOnAlice)!!.state)
    }

    /**
     * Why every vault failure is unchecked (DEC-023): libsignal 0.94.1 turns a *checked* exception
     * from a store callback into an `AssertionError`, which `catch (Exception)` blocks miss. If this
     * test starts failing after a libsignal update, re-check that assumption.
     */
    @Test
    fun libsignalTurnsCheckedCallbackExceptionsIntoAssertionErrors() = runBlocking {
        val (aliceOnBob, _) = pair()
        bob.service.sendText(aliceOnBob, "arrives later")
        alice.records.failure = { GeneralSecurityException("checked storage failure") }

        val failure = runCatching { alice.service.synchronize() }.exceptionOrNull()
        assertTrue("got $failure", failure is AssertionError && failure.cause is GeneralSecurityException)
        assertEquals(1, relay.queued(alice.queueId).size)

        alice.records.failure = null
        assertEquals(listOf(IncomingV2Result.TEXT_ACCEPTED), alice.service.synchronize())
    }

    @Test
    fun aSecondScannerOfTheSameInvitationIsNeverRouted() = runBlocking {
        val carol = V2TestDevice("Carol", relay) { clock }
        val code = alice.service.createInvitation()
        bob.service.acceptInvitation(code, "Alice")
        carol.service.acceptInvitation(code, "Alice")

        // The first HELLO burns the invitation; its tag then routes to that request, where the
        // second HELLO fails libsignal's identity check without touching any state.
        assertEquals(
            listOf(IncomingV2Result.REQUEST_RECEIVED, IncomingV2Result.UNAUTHENTICATED),
            alice.service.synchronize(),
        )
        assertEquals(1, alice.store.requests().size)
        assertTrue(bob.sessions.localIdentityKey().serialize().contentEquals(alice.store.requests().single().identityKey))
    }

    @Test
    fun aForgedIdentityClaimUnderAKnownRoutingTagChangesNothing() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        val bobsTagOnAlice = alice.store.contact(bobOnAlice)!!.localRoutingTagHex
        // Mallory (or the relay, which sees every tag) builds a PreKey message from her own
        // session and labels it with Bob's routing tag, claiming a different identity.
        val mallory = V2TestDevice("Mallory", relay) { clock }
        val otherInvitation = V2TestDevice("Other", relay) { clock }.invitationPreKeys()
        mallory.sessions.startSession("mallory-view", otherInvitation.toPreKeyBundle())
        val forged = EnvelopeV2Codec.encode(
            EnvelopeV2(RandomId.fromHex(bobsTagOnAlice), mallory.sessions.encrypt("mallory-view", "evil".toByteArray())),
        )
        relay.inject(RelayEncryptedBlob("forged", alice.queueId, forged, Long.MAX_VALUE))

        assertEquals(listOf(IncomingV2Result.UNAUTHENTICATED), alice.service.synchronize())
        assertEquals(V2ContactState.ACTIVE, alice.store.contact(bobOnAlice)!!.state)
        assertEquals(1, relay.queued(alice.queueId).size)
        relay.take(alice.queueId)

        bob.service.sendText(aliceOnBob, "still trusted")
        assertEquals(listOf(IncomingV2Result.TEXT_ACCEPTED), alice.service.synchronize())
    }

    @Test
    fun garbageAndUnknownTagsStayOnTheRelayUnauthenticated() = runBlocking {
        val (_, bobOnAlice) = pair()
        val knownTag = RandomId.fromHex(alice.store.contact(bobOnAlice)!!.localRoutingTagHex)
        val unknownTag = EnvelopeV2Codec.encode(
            EnvelopeV2(RandomId.fromHex("00".repeat(16)), SignalCiphertext(CiphertextMessage.WHISPER_TYPE, ByteArray(80))),
        )
        val forgedWhisper = EnvelopeV2Codec.encode(
            EnvelopeV2(knownTag, SignalCiphertext(CiphertextMessage.WHISPER_TYPE, ByteArray(80) { 3 })),
        )
        relay.inject(RelayEncryptedBlob("garbage", alice.queueId, ByteArray(50) { 1 }, Long.MAX_VALUE))
        relay.inject(RelayEncryptedBlob("unknown-tag", alice.queueId, unknownTag, Long.MAX_VALUE))
        relay.inject(RelayEncryptedBlob("forged-whisper", alice.queueId, forgedWhisper, Long.MAX_VALUE))

        assertEquals(
            listOf(IncomingV2Result.UNAUTHENTICATED, IncomingV2Result.UNROUTABLE, IncomingV2Result.UNAUTHENTICATED),
            alice.service.synchronize(),
        )
        assertEquals(3, relay.queued(alice.queueId).size)
    }

    @Test
    fun pairingTwiceWithOneIdentityAndScanningOwnInvitationAreRefused() = runBlocking {
        pair()
        val second = alice.service.createInvitation()
        val twice = assertThrows(ConversationV2Exception::class.java) {
            runBlocking { bob.service.acceptInvitation(second, "Alice again") }
        }
        assertEquals("Already paired", twice.message)

        val own = assertThrows(ConversationV2Exception::class.java) {
            runBlocking { alice.service.acceptInvitation(alice.service.createInvitation(), "Me") }
        }
        assertEquals("Own invitation", own.message)
    }

    @Test
    fun expiredInvitationsLoseTheirPreKeysAndALateHelloIsUnroutable() = runBlocking {
        val code = alice.service.createInvitation()
        val invitation = alice.store.latestUsableInvitation(clock)!!
        bob.service.acceptInvitation(code, "Alice")
        val hello = relay.take(alice.queueId)

        clock += InvitationV2.DEFAULT_LIFETIME_MILLIS + 1
        assertEquals(1, alice.service.expireInvitations())
        assertFalse(alice.signalStore.containsSignedPreKey(invitation.signedPreKeyId))
        assertFalse(alice.signalStore.containsPreKey(invitation.oneTimePreKeyId))
        assertFalse(alice.signalStore.containsKyberPreKey(invitation.kyberPreKeyId))

        hello.forEach(relay::inject)
        assertEquals(listOf(IncomingV2Result.UNROUTABLE), alice.service.synchronize())
        assertTrue(alice.store.requests().isEmpty())
    }

    @Test
    fun anExpiredCodeIsRejectedWhenScanned() = runBlocking {
        val code = alice.service.createInvitation()
        clock += InvitationV2.DEFAULT_LIFETIME_MILLIS + 11 * 60 * 1000L
        val rejected = assertThrows(InvitationRejectedException::class.java) {
            runBlocking { bob.service.acceptInvitation(code, "Alice") }
        }
        assertEquals(InvitationRejection.EXPIRED, rejected.reason)
        assertTrue(bob.store.contactIds().isEmpty())
    }

    @Test
    fun sendFailuresAreRetriedWithBackoff() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        relay.failNextSends = 1
        val messageId = bob.service.sendText(aliceOnBob, "retry me")
        assertEquals(V2MessageStatus.FAILED, bob.store.statusOf(messageId))
        assertEquals(0, bob.service.sendDue())

        clock += 5_000
        assertEquals(1, bob.service.sendDue())
        assertEquals(V2MessageStatus.SENT, bob.store.statusOf(messageId))
        assertEquals(listOf(IncomingV2Result.TEXT_ACCEPTED), alice.service.synchronize())
        assertEquals(listOf("retry me"), alice.store.incomingTexts(bobOnAlice).map { it.text })
    }

    /** Bob scans Alice's invitation and Alice accepts. Returns (Alice's ID on Bob, Bob's ID on Alice). */
    private suspend fun pair(): Pair<String, String> {
        val aliceOnBob = bob.service.acceptInvitation(alice.service.createInvitation(), "Alice")
        alice.service.synchronize()
        val bobOnAlice = alice.store.requests().single().requestId
        alice.service.acceptRequest(bobOnAlice, "Bob")
        bob.service.synchronize()
        return aliceOnBob to bobOnAlice
    }

    private fun V2TestDevice.invitationPreKeys() = InvitationPreKeyFactory(signalStore).create()
}
