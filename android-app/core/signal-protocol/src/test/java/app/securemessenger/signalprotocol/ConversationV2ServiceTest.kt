package app.securemessenger.signalprotocol

import app.securemessenger.domain.RelayEncryptedBlob
import app.securemessenger.signalstore.V2ContactState
import app.securemessenger.signalstore.V2MediaState
import app.securemessenger.signalstore.V2MessageKind
import app.securemessenger.signalstore.V2MessageStatus
import java.security.GeneralSecurityException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
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
        assertTrue("a rejected envelope is deleted (DEC-026)", relay.queued(alice.queueId).isEmpty())

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
        relay.inject(RelayEncryptedBlob("relabelled-by-relay", original.queueId, original.opaqueCiphertext, original.expiresAtEpochMillis))
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
     * Audit V2-01: Bob's HELLO hits a storage failure on Alice's side and stays on the relay. His
     * text right behind it cannot be routed until the HELLO is in, so it must not be rejected for
     * good and deleted in the same sync: it waits, and both arrive in order in the next one.
     */
    @Test
    fun aTextBehindAHelloThatFailedLocallyWaitsForItInsteadOfBeingDeleted() = runBlocking {
        val aliceOnBob = bob.service.acceptInvitation(alice.service.createInvitation(), "Alice")
        bob.service.sendText(aliceOnBob, "it's Bob")
        alice.store.failNextRequestSave = true

        assertThrows(IllegalStateException::class.java) { runBlocking { alice.service.synchronize() } }
        assertEquals(2, relay.queued(alice.queueId).size)
        assertTrue(alice.store.requests().isEmpty())

        assertEquals(listOf(IncomingV2Result.REQUEST_RECEIVED, IncomingV2Result.TEXT_ACCEPTED), alice.service.synchronize())
        val request = alice.store.requests().single()
        assertEquals(listOf("it's Bob"), alice.store.incomingTexts(request.requestId).map { it.text })
        assertTrue(relay.queued(alice.queueId).isEmpty())
    }

    /**
     * V2-01, bounded: behind a failed envelope at most ten rejected ones with its routing tag wait for
     * the next sync; the rest is deleted at once, and the waiting junk goes as soon as it is decided.
     */
    @Test
    fun onlyAFewRejectedEnvelopesWaitBehindAFailedOneAndJunkIsStillDeleted() = runBlocking {
        val code = alice.service.createInvitation()
        val invitationTag = RandomId.fromHex(alice.store.latestUsableInvitation(clock)!!.localRoutingTagHex)
        bob.service.acceptInvitation(code, "Alice")
        repeat(15) { index ->
            val junk = SignalCiphertext(CiphertextMessage.WHISPER_TYPE, ByteArray(80) { index.toByte() })
            relay.inject(RelayEncryptedBlob("junk-$index", alice.queueId, EnvelopeV2Codec.encode(EnvelopeV2(invitationTag, junk)), Long.MAX_VALUE))
        }
        alice.store.failNextRequestSave = true

        assertThrows(IllegalStateException::class.java) { runBlocking { alice.service.synchronize() } }
        assertEquals("the HELLO and ten waiting envelopes", 11, relay.queued(alice.queueId).size)

        assertEquals(listOf(IncomingV2Result.REQUEST_RECEIVED) + List(10) { IncomingV2Result.UNAUTHENTICATED }, alice.service.synchronize())
        assertTrue(relay.queued(alice.queueId).isEmpty())
        assertEquals(1, alice.store.requests().size)
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

        // synchronize() finishes the rest of the queue, then reports it as an exception whose cause
        // is libsignal's AssertionError around the checked failure (Phase 5).
        val failure = runCatching { alice.service.synchronize() }.exceptionOrNull()
        val libsignalError = failure?.cause
        assertTrue(
            "got $failure",
            failure is ConversationV2Exception && libsignalError is AssertionError && libsignalError.cause is GeneralSecurityException,
        )
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

    /**
     * Second review I2 and the owner's wish (2026-10-06): once an invitation is used, its sealed bundle
     * is withdrawn from the mailbox, so whoever scans the code later is told at once that it no
     * longer works, instead of waiting for an answer that never comes.
     */
    @Test
    fun aUsedInvitationIsWithdrawnSoALaterScanIsToldItNoLongerWorks() = runBlocking {
        val code = alice.service.createInvitation()
        val mailbox = alice.store.latestUsableInvitation(clock)!!.mailboxQueueId
        bob.service.acceptInvitation(code, "Alice")

        assertEquals(listOf(IncomingV2Result.REQUEST_RECEIVED), alice.service.synchronize())
        assertTrue("the sealed bundle is gone from the mailbox", relay.queued(mailbox).isEmpty())

        val carol = V2TestDevice("Carol", relay) { clock }
        val refused = assertThrows(InvitationRejectedException::class.java) {
            runBlocking { carol.service.acceptInvitation(code, "Alice") }
        }
        assertEquals(InvitationRejection.NOT_FOUND, refused.reason)
        assertTrue(carol.store.contactIds().isEmpty())
    }

    @Test
    fun aRelayFailureWhileWithdrawingTheBundleChangesNoVerdict() = runBlocking {
        val code = alice.service.createInvitation()
        val mailbox = alice.store.latestUsableInvitation(clock)!!.mailboxQueueId
        bob.service.acceptInvitation(code, "Alice")
        relay.unreachableQueues += mailbox

        assertEquals(listOf(IncomingV2Result.REQUEST_RECEIVED), alice.service.synchronize())
        assertEquals(1, alice.store.requests().size)

        // The bundle stays until it expires; a later scan then waits in vain, as before.
        relay.unreachableQueues -= mailbox
        assertEquals(1, relay.queued(mailbox).size)
    }

    /**
     * Second review I5: digests are forgotten after 30 days, and a relay that then replays every old
     * envelope (HELLO, a text held for the request, a later text) changes nothing: libsignal refuses
     * spent keys and message counters.
     */
    @Test
    fun forgottenDigestsLetNoOldEnvelopeBackIn() = runBlocking {
        val aliceOnBob = bob.service.acceptInvitation(alice.service.createInvitation(), "Alice")
        bob.service.sendText(aliceOnBob, "held for the request")
        val captured = relay.queued(alice.queueId).toMutableList()
        alice.service.synchronize()
        val bobOnAlice = alice.store.requests().single().requestId
        alice.service.acceptRequest(bobOnAlice, "Bob")
        bob.service.synchronize()
        bob.service.sendText(aliceOnBob, "after acceptance")
        captured += relay.queued(alice.queueId)
        alice.service.synchronize()
        assertEquals(3, captured.size)

        // Within the retention nothing is forgotten.
        clock += ConversationV2Service.ACCEPTED_DIGEST_RETENTION_MILLIS - 60_000L
        assertEquals(0, alice.service.forgetOldEnvelopeDigests())
        clock += 120_000L
        assertEquals(3, alice.service.forgetOldEnvelopeDigests())
        assertEquals(0, alice.store.acceptedDigestCount())

        val before = alice.store.dump()
        captured.forEach(relay::inject)
        val results = alice.service.synchronize()

        assertEquals(List(3) { IncomingV2Result.UNAUTHENTICATED }, results)
        assertEquals(before, alice.store.dump())
        assertEquals(listOf("held for the request", "after acceptance"), alice.store.incomingTexts(bobOnAlice).map { it.text })
        assertTrue(relay.queued(alice.queueId).isEmpty())
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
        assertTrue(relay.queued(alice.queueId).isEmpty())

        bob.service.sendText(aliceOnBob, "still trusted")
        assertEquals(listOf(IncomingV2Result.TEXT_ACCEPTED), alice.service.synchronize())
    }

    /** DEC-026: what can never become valid is rejected and deleted, so it cannot fill the queue. */
    @Test
    fun garbageAndUnknownTagsAreRejectedAndDeleted() = runBlocking {
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
        assertTrue(relay.queued(alice.queueId).isEmpty())
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

    /** Audit L2: a failed upload leaves the invitation stored; "try again" makes the same code work. */
    @Test
    fun aFailedInvitationUploadCanBeRetriedAndThenTheSameCodeWorks() = runBlocking {
        relay.failNextSends = 1
        assertThrows(IllegalStateException::class.java) { runBlocking { alice.service.createInvitation() } }
        val code = alice.service.currentInvitationCode()!!
        // Nothing is on the relay yet: a scanner cannot find the bundle.
        val notFound = assertThrows(InvitationRejectedException::class.java) {
            runBlocking { bob.service.acceptInvitation(code, "Alice") }
        }
        assertEquals(InvitationRejection.NOT_FOUND, notFound.reason)

        alice.service.retryCurrentInvitationUpload()

        val contactId = bob.service.acceptInvitation(code, "Alice")
        assertEquals(V2ContactState.AWAITING_ACCEPTANCE, bob.store.contact(contactId)!!.state)
    }

    @Test
    fun retryingWithoutAUsableInvitationChangesNothing() = runBlocking {
        assertNull(alice.service.currentInvitationCode())
        assertThrows(ConversationV2Exception::class.java) { runBlocking { alice.service.retryCurrentInvitationUpload() } }
        assertNull(alice.service.currentInvitationCode())
    }

    /** DEC-030 (audit H1): accepting after "the numbers match" marks the contact verified, on this phone only. */
    @Test
    fun acceptingAfterComparingTheNumberMarksTheContactVerified() = runBlocking {
        val aliceOnBob = bob.service.acceptInvitation(alice.service.createInvitation(), "Alice")
        alice.service.synchronize()
        val request = alice.store.requests().single()
        val shown = alice.service.safetyNumber(request.requestId)!!

        alice.service.acceptRequest(request.requestId, "Bob", comparedSafetyNumber = shown)

        val contact = alice.store.contact(request.requestId)!!
        assertEquals(clock, contact.verifiedAtEpochMillis)
        assertTrue(contact.safetyNumberChecked)
        // Nothing about it is sent: Bob's phone stays unverified until Bob confirms himself.
        bob.service.synchronize()
        assertNull(bob.store.contact(aliceOnBob)!!.verifiedAtEpochMillis)
    }

    @Test
    fun nothingButThePersonsConfirmationSetsTheMark() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        bob.service.sendText(aliceOnBob, "hi")
        alice.service.synchronize()
        alice.service.sendText(bobOnAlice, "hello")
        bob.service.synchronize()
        alice.service.synchronize()

        assertFalse(alice.store.contact(bobOnAlice)!!.safetyNumberChecked)
        assertFalse(bob.store.contact(aliceOnBob)!!.safetyNumberChecked)
    }

    /**
     * Audit H1: someone photographed Alice's QR code and paired first under Bob's name. The real Bob
     * scans the same code (his HELLO is rejected, the invitation is used) and reads Alice the number
     * on his phone. It is not the request's number, so "the numbers match" accepts nothing.
     */
    @Test
    fun aRequestFromWhoeverPhotographedTheCodeIsNotAcceptedWithTheRealContactsNumber() = runBlocking {
        val code = alice.service.createInvitation()
        val impostor = V2TestDevice("Bob", relay) { clock }
        impostor.service.acceptInvitation(code, "Alice")
        val aliceOnBob = bob.service.acceptInvitation(code, "Alice")
        alice.service.synchronize()
        val request = alice.store.requests().single()
        assertEquals("Bob", request.claimedDisplayName)
        assertTrue(impostor.sessions.localIdentityKey().serialize().contentEquals(request.identityKey))
        val before = alice.store.dump()

        val readByBob = bob.service.safetyNumber(aliceOnBob)!!
        val refused = assertThrows(ConversationV2Exception::class.java) {
            runBlocking { alice.service.acceptRequest(request.requestId, "Bob", comparedSafetyNumber = readByBob) }
        }

        assertEquals("Safety number changed", refused.message)
        assertEquals(before, alice.store.dump())
        assertTrue(alice.store.contactIds().isEmpty())
    }

    @Test
    fun aComparedNumberThatIsNotTheRequestsAcceptsNothing() = runBlocking {
        bob.service.acceptInvitation(alice.service.createInvitation(), "Alice")
        alice.service.synchronize()
        val requestId = alice.store.requests().single().requestId
        val shown = alice.service.safetyNumber(requestId)!!
        val before = alice.store.dump()

        for (compared in listOf(oneDigitOff(shown), shown.dropLast(1), "", "no digits here")) {
            assertThrows(ConversationV2Exception::class.java) {
                runBlocking { alice.service.acceptRequest(requestId, "Bob", comparedSafetyNumber = compared) }
            }
        }
        assertEquals(before, alice.store.dump())
    }

    @Test
    fun markingNeedsTheNumberThePersonSawAndCanBeUndone() = runBlocking {
        val (aliceOnBob, _) = pair()
        val shown = bob.service.safetyNumber(aliceOnBob)!!

        assertThrows(ConversationV2Exception::class.java) { bob.service.markVerified(aliceOnBob, oneDigitOff(shown)) }
        assertNull(bob.store.contact(aliceOnBob)!!.verifiedAtEpochMillis)

        // Grouped as on the screen, the same digits still match.
        bob.service.markVerified(aliceOnBob, shown.chunked(5).joinToString(" "))
        assertEquals(clock, bob.store.contact(aliceOnBob)!!.verifiedAtEpochMillis)

        assertThrows(ConversationV2Exception::class.java) { bob.service.markVerified("unknown-contact", shown) }
        assertThrows(ConversationV2Exception::class.java) { bob.service.clearVerified("unknown-contact") }
        bob.service.clearVerified(aliceOnBob)
        assertNull(bob.store.contact(aliceOnBob)!!.verifiedAtEpochMillis)
    }

    /** The scanner compares right after scanning (owner decision, DEC-030): the number is already final. */
    @Test
    fun theScannerCanMarkWhileWaitingAndTheMarkSurvivesAcceptance() = runBlocking {
        val aliceOnBob = bob.service.acceptInvitation(alice.service.createInvitation(), "Alice")
        assertEquals(V2ContactState.AWAITING_ACCEPTANCE, bob.store.contact(aliceOnBob)!!.state)
        bob.service.markVerified(aliceOnBob, bob.service.safetyNumber(aliceOnBob)!!)

        alice.service.synchronize()
        alice.service.acceptRequest(alice.store.requests().single().requestId, "Bob")
        assertEquals(listOf(IncomingV2Result.CONTACT_ACCEPTED), bob.service.synchronize())

        val contact = bob.store.contact(aliceOnBob)!!
        assertEquals(V2ContactState.ACTIVE, contact.state)
        assertTrue(contact.safetyNumberChecked)
    }

    @Test
    fun aBlockedContactLosesItsMarkAndCannotBeMarked() = runBlocking {
        val (aliceOnBob, _) = pair()
        val shown = bob.service.safetyNumber(aliceOnBob)!!
        bob.service.markVerified(aliceOnBob, shown)

        bob.store.setContactState(aliceOnBob, V2ContactState.KEY_CHANGED)

        assertNull(bob.store.contact(aliceOnBob)!!.verifiedAtEpochMillis)
        assertThrows(ConversationV2Exception::class.java) { bob.service.markVerified(aliceOnBob, shown) }
        assertNull(bob.store.contact(aliceOnBob)!!.verifiedAtEpochMillis)
    }

    /** DEC-029: the chat list counts incoming texts the user has not seen; showing them clears the count. */
    @Test
    fun unseenIncomingTextsAreCountedUntilTheConversationShowsThem() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        assertEquals(0, alice.service.chatSummaries().single().unreadCount)

        bob.service.sendText(aliceOnBob, "one")
        bob.service.sendText(aliceOnBob, "two")
        alice.service.synchronize()
        val summary = alice.service.chatSummaries().single()
        assertEquals(bobOnAlice, summary.contact.contactId)
        assertEquals(2, summary.unreadCount)
        assertEquals("two", summary.lastText!!.text)

        // Alice's own texts never count; the newest text in either direction is the last one.
        alice.service.sendText(bobOnAlice, "three")
        assertEquals(2, alice.service.chatSummaries().single().unreadCount)
        assertEquals("three", alice.service.chatSummaries().single().lastText!!.text)

        assertEquals(listOf("one", "two", "three"), alice.service.textsMarkingRead(bobOnAlice).map { it.text })
        assertEquals(0, alice.service.chatSummaries().single().unreadCount)
        // Reading the list does not mark anything.
        bob.service.sendText(aliceOnBob, "four")
        alice.service.synchronize()
        alice.service.texts(bobOnAlice)
        assertEquals(1, alice.service.chatSummaries().single().unreadCount)
    }

    /** DEC-029: the mark is local. Showing a conversation sends nothing: there are no read receipts. */
    @Test
    fun seeingTextsSendsNothing() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        bob.service.sendText(aliceOnBob, "seen?")
        alice.service.synchronize()
        bob.service.synchronize()
        val queuedBefore = alice.store.outgoingKinds(bobOnAlice)
        val relayBefore = relay.queued(bob.queueId).size

        alice.service.textsMarkingRead(bobOnAlice)
        alice.service.synchronize()

        assertEquals(queuedBefore, alice.store.outgoingKinds(bobOnAlice))
        assertEquals(relayBefore, relay.queued(bob.queueId).size)
    }

    /** Texts held for a request count as unseen, and still do once the request becomes a contact. */
    @Test
    fun textsHeldForARequestStayUnseenAfterAcceptanceAndEachChatCountsAlone() = runBlocking {
        val carol = V2TestDevice("Carol", relay) { clock }
        val (_, bobOnAlice) = pair()
        val aliceOnCarol = carol.service.acceptInvitation(alice.service.createInvitation(), "Alice")
        carol.service.sendText(aliceOnCarol, "early")
        alice.service.synchronize()
        val carolsRequest = alice.store.requests().single().requestId
        assertEquals(1, alice.store.unreadCounts()[carolsRequest])

        alice.service.acceptRequest(carolsRequest, "Carol")
        val unread = alice.service.chatSummaries().associate { it.contact.contactId to it.unreadCount }
        assertEquals(mapOf(bobOnAlice to 0, carolsRequest to 1), unread)

        alice.service.textsMarkingRead(bobOnAlice)
        assertEquals(1, alice.service.chatSummaries().single { it.contact.contactId == carolsRequest }.unreadCount)
    }

    /**
     * DEC-034: an incoming text sorts by the time its sender wrote it when that is at most a minute
     * before it arrived, so both phones show the conversation in the same order, with the same times.
     */
    @Test
    fun bothPhonesShowTheConversationInTheOrderItWasWritten() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        bob.service.sendText(aliceOnBob, "first, from Bob")
        clock += 5_000
        // Alice writes before she has fetched Bob's text.
        alice.service.sendText(bobOnAlice, "second, from Alice")
        clock += 20_000
        alice.service.synchronize()
        bob.service.synchronize()

        val onAlice = alice.service.texts(bobOnAlice)
        val onBob = bob.service.texts(aliceOnBob)
        assertEquals(listOf("first, from Bob", "second, from Alice"), onAlice.map { it.text })
        assertEquals(listOf("first, from Bob", "second, from Alice"), onBob.map { it.text })
        assertEquals(onAlice.map { it.createdAtEpochMillis }, onBob.map { it.createdAtEpochMillis })
    }

    /**
     * DEC-034: a text that came too late shows at its arrival; one written after it, that came in
     * time, must still show below it, never above: a contact's texts keep their own order.
     */
    @Test
    fun aContactsTextsKeepTheirOrderWhenTheFirstCameTooLate() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        bob.service.sendText(aliceOnBob, "A")
        clock += 30_000
        bob.service.sendText(aliceOnBob, "B")
        clock += 50_000
        // A is 80 seconds old here, B only 50.
        alice.service.synchronize()

        val texts = alice.service.texts(bobOnAlice)
        assertEquals(listOf("A", "B"), texts.map { it.text })
        assertEquals(listOf(clock, clock), texts.map { it.createdAtEpochMillis })
        assertEquals(clock - 1, ConversationV2Service.shownTimeOf(clock - 10_000, clock, notBefore = clock - 1))
        assertEquals("never later than the arrival", clock, ConversationV2Service.shownTimeOf(clock - 10_000, clock, notBefore = clock + 5))
    }

    /** DEC-034: the sender's time is believed only up to a minute back, and never ahead of the arrival. */
    @Test
    fun aTextOlderThanAMinuteOrDatedAheadShowsWhenItArrived() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        bob.service.sendText(aliceOnBob, "late")
        clock += ConversationV2Service.SENDER_TIME_WINDOW_MILLIS + 1
        alice.service.synchronize()
        assertEquals(clock, alice.service.texts(bobOnAlice).single().createdAtEpochMillis)

        val arrived = clock
        val window = ConversationV2Service.SENDER_TIME_WINDOW_MILLIS
        assertEquals(arrived, ConversationV2Service.shownTimeOf(arrived, arrived))
        assertEquals(arrived - window, ConversationV2Service.shownTimeOf(arrived - window, arrived))
        assertEquals(arrived, ConversationV2Service.shownTimeOf(arrived - window - 1, arrived))
        // A sender whose clock runs ahead cannot push its text after later ones, or into the future.
        assertEquals(arrived, ConversationV2Service.shownTimeOf(arrived + 1, arrived))
        assertEquals(arrived, ConversationV2Service.shownTimeOf(Long.MAX_VALUE, arrived))
    }

    /** DEC-035: a reply names the text it answers by its inner ID; each phone finds it in its own copy. */
    @Test
    fun aReplyNamesTheTextItAnswersOnBothPhones() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        bob.service.sendText(aliceOnBob, "lunch?")
        alice.service.synchronize()
        val question = alice.service.texts(bobOnAlice).single()

        alice.service.sendText(bobOnAlice, "yes", replyToInnerIdHex = question.innerMessageIdHex)
        bob.service.synchronize()

        val answerOnAlice = alice.service.texts(bobOnAlice).last()
        val answerOnBob = bob.service.texts(aliceOnBob).last()
        assertEquals("yes", answerOnBob.text)
        assertEquals(question.innerMessageIdHex, answerOnAlice.replyToInnerIdHex)
        assertEquals(question.innerMessageIdHex, answerOnBob.replyToInnerIdHex)
        // On Bob's phone that ID belongs to his own text.
        assertEquals("lunch?", bob.service.texts(aliceOnBob).single { it.innerMessageIdHex == answerOnBob.replyToInnerIdHex }.text)
        // A reply is receipted like any text, and a plain text answers nothing.
        alice.service.synchronize()
        assertEquals(V2MessageStatus.DELIVERED, alice.store.statusOf(answerOnAlice.messageId))
        assertNull(question.replyToInnerIdHex)
    }

    @Test
    fun aReplyToATextThisPhoneDoesNotHaveIsRefusedAndQueuesNothing() = runBlocking {
        val (_, bobOnAlice) = pair()
        val before = alice.store.dump()
        val unknown = RandomId.random(java.security.SecureRandom()).toHex()

        val failure = assertThrows(ConversationV2Exception::class.java) {
            runBlocking { alice.service.sendText(bobOnAlice, "?", replyToInnerIdHex = unknown) }
        }
        assertEquals("Unknown message", failure.message)
        assertEquals(before, alice.store.dump())
    }

    /** DEC-035: pinning and muting are marks on this phone; they send nothing and change no protocol state. */
    @Test
    fun pinningAndMutingAreLocalMarksAndSendNothing() = runBlocking {
        val (_, bobOnAlice) = pair()
        bob.service.synchronize()
        val queuedBefore = alice.store.outgoingKinds(bobOnAlice)
        val relayBefore = relay.queued(bob.queueId).size

        alice.service.setPinned(bobOnAlice, true)
        alice.service.setMuted(bobOnAlice, ConversationV2Service.MUTED_FOREVER)
        val marked = alice.service.chatSummaries().single().contact
        assertEquals(clock, marked.pinnedAtEpochMillis)
        assertTrue(marked.isMuted(clock))

        alice.service.setPinned(bobOnAlice, false)
        alice.service.setMuted(bobOnAlice, clock + 1_000)
        val changed = alice.store.contact(bobOnAlice)!!
        assertFalse(changed.pinned)
        assertTrue(changed.isMuted(clock))
        assertFalse("a mute for a while ends by itself", changed.isMuted(clock + 1_000))
        assertEquals(V2ContactState.ACTIVE, changed.state)
        assertThrows(ConversationV2Exception::class.java) { alice.service.setPinned("unknown-contact", true) }
        assertThrows(ConversationV2Exception::class.java) { alice.service.setMuted("unknown-contact", null) }

        alice.service.synchronize()
        assertEquals(queuedBefore, alice.store.outgoingKinds(bobOnAlice))
        assertEquals(relayBefore, relay.queued(bob.queueId).size)
    }

    /** DEC-035: marking a chat read from the list is local, like the "seen" mark of DEC-029. */
    @Test
    fun markingAChatReadFromTheListClearsItsCountAndSendsNothing() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        bob.service.sendText(aliceOnBob, "one")
        bob.service.sendText(aliceOnBob, "two")
        alice.service.synchronize()
        bob.service.synchronize()
        val relayBefore = relay.queued(bob.queueId).size

        assertEquals(2, alice.service.markChatRead(bobOnAlice))
        assertEquals(0, alice.service.chatSummaries().single().unreadCount)
        alice.service.synchronize()
        assertEquals(relayBefore, relay.queued(bob.queueId).size)
    }

    /**
     * DEC-035: clearing a chat deletes its texts on this phone. A text still waiting to be sent stays
     * (its envelope holds a committed ratchet step) and still goes out; the contact stays as it was.
     */
    @Test
    fun clearingHistoryKeepsTextsStillBeingSentAndTheContact() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        bob.service.sendText(aliceOnBob, "from Bob")
        alice.service.synchronize()
        alice.service.sendText(bobOnAlice, "sent")
        relay.failNextSends = 1
        val waiting = alice.service.sendText(bobOnAlice, "still waiting")
        assertEquals(V2MessageStatus.FAILED, alice.store.statusOf(waiting))

        assertEquals(2, alice.service.clearHistory(bobOnAlice))
        assertEquals(listOf("still waiting"), alice.service.texts(bobOnAlice).map { it.text })
        assertEquals(V2ContactState.ACTIVE, alice.store.contact(bobOnAlice)!!.state)
        assertThrows(ConversationV2Exception::class.java) { alice.service.clearHistory("unknown-contact") }

        clock += 60 * 60_000L
        alice.service.sendDue()
        bob.service.synchronize()
        assertEquals(listOf("from Bob", "sent", "still waiting"), bob.service.texts(aliceOnBob).map { it.text })
    }

    /**
     * DEC-035: deleting a contact removes it with its texts, queued envelopes, session and identity.
     * The contact is not told; what it sends afterwards finds no route and is deleted unread. Writing
     * again takes a new invitation, and the identity is then trusted afresh, like any new contact.
     */
    @Test
    fun aDeletedContactIsGoneWithItsKeysAndItsLaterTextsFindNoRoute() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        bob.service.sendText(aliceOnBob, "hello")
        alice.service.synchronize()
        relay.failNextSends = 1
        alice.service.sendText(bobOnAlice, "never sent")
        assertTrue(alice.store.invitationUsedBy(bobOnAlice) != null)

        alice.service.deleteContact(bobOnAlice)
        assertNull(alice.store.contact(bobOnAlice))
        assertTrue(alice.service.texts(bobOnAlice).isEmpty())
        assertTrue(alice.store.unsentOutgoing().isEmpty())
        assertFalse(alice.sessions.hasSession(bobOnAlice))
        assertNull(alice.sessions.identityOf(bobOnAlice))
        assertNull(alice.store.invitationUsedBy(bobOnAlice))
        assertThrows(ConversationV2Exception::class.java) { alice.service.deleteContact(bobOnAlice) }

        bob.service.sendText(aliceOnBob, "are you there?")
        assertEquals(listOf(IncomingV2Result.UNROUTABLE), alice.service.synchronize())
        assertTrue(relay.queued(alice.queueId).isEmpty())

        // Bob still has Alice, so he deletes her too; then a new invitation pairs them as new contacts.
        bob.service.deleteContact(aliceOnBob)
        val newAliceOnBob = bob.service.acceptInvitation(alice.service.createInvitation(), "Alice")
        alice.service.synchronize()
        val newBobOnAlice = alice.store.requests().single().requestId
        assertTrue(newBobOnAlice != bobOnAlice)
        alice.service.acceptRequest(newBobOnAlice, "Bob")
        bob.service.synchronize()
        bob.service.sendText(newAliceOnBob, "hi again")
        alice.service.synchronize()
        assertEquals(listOf("hi again"), alice.service.texts(newBobOnAlice).map { it.text })
    }

    // --- Voice messages (DEC-036) ---

    private val recording = ByteArray(150_000).also(java.util.Random(36)::nextBytes)
    private val waveform = ByteArray(50) { (it % 32).toByte() }

    /** The one-time queue that holds the pieces of [messageId]'s recording, as the sender knows it. */
    private fun V2TestDevice.voiceQueue(messageId: String) =
        app.securemessenger.domain.RelayQueueCredentials.fromReadSecret(store.voiceNote(messageId)!!.queueReadSecret)

    /**
     * DEC-036: the note travels in the session, the recording in encrypted pieces beside it; the
     * receiver fetches and checks the pieces, deletes them from the relay, and only then receipts:
     * "Delivered" means the recording itself is on the other phone (owner's choice).
     */
    @Test
    fun aVoiceMessageArrivesWithItsRecordingAndIsDeliveredOnlyThen() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        val sent = bob.service.sendVoice(aliceOnBob, recording, 7_000, waveform)
        val queue = bob.voiceQueue(sent)
        assertEquals(VoiceMedia.chunkCountFor(recording.size), relay.queued(queue.queueId).size)
        assertEquals(V2MediaState.READY, bob.store.voiceState(sent))
        assertEquals(V2MessageStatus.SENT, bob.store.statusOf(sent))
        // Only encrypted pieces of one size are on the relay.
        assertTrue(relay.queued(queue.queueId).all { it.opaqueCiphertext.size == VoiceMedia.PIECE_BYTES })

        assertEquals(listOf(IncomingV2Result.VOICE_ACCEPTED), alice.service.synchronize())
        val received = alice.service.texts(bobOnAlice).single()
        assertEquals(V2MediaState.READY, received.voice!!.state)
        assertEquals(7_000, received.voice!!.durationMillis)
        assertArrayEquals(recording, alice.service.voiceRecording(received.messageId))
        assertTrue("the pieces were deleted from the relay", relay.queued(queue.queueId).isEmpty())
        // Our own copy plays too.
        assertArrayEquals(recording, bob.service.voiceRecording(sent))

        assertEquals(listOf(IncomingV2Result.RECEIPT_APPLIED), bob.service.synchronize())
        assertEquals(V2MessageStatus.DELIVERED, bob.store.statusOf(sent))
    }

    @Test
    fun aMissingPieceHoldsTheReceiptUntilItArrivesAndJunkIsDeletedUnread() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        val sent = bob.service.sendVoice(aliceOnBob, recording, 7_000, waveform)
        val queue = bob.voiceQueue(sent)
        val held = relay.queued(queue.queueId).first()
        relay.deleteMessage(queue, held.messageId)
        relay.inject(RelayEncryptedBlob("junk", queue.queueId, ByteArray(VoiceMedia.PIECE_BYTES) { 7 }, Long.MAX_VALUE))

        alice.service.synchronize()
        val waiting = alice.service.texts(bobOnAlice).single()
        assertEquals(V2MediaState.DOWNLOADING, waiting.voice!!.state)
        assertNull(alice.service.voiceRecording(waiting.messageId))
        assertTrue("the pieces it has and the junk are gone from the relay", relay.queued(queue.queueId).isEmpty())
        bob.service.synchronize()
        assertEquals("no receipt before the recording is here", V2MessageStatus.SENT, bob.store.statusOf(sent))

        relay.inject(RelayEncryptedBlob("late", queue.queueId, held.opaqueCiphertext, Long.MAX_VALUE))
        alice.service.synchronize()
        assertEquals(V2MediaState.READY, alice.service.texts(bobOnAlice).single().voice!!.state)
        assertArrayEquals(recording, alice.service.voiceRecording(waiting.messageId))
        bob.service.synchronize()
        assertEquals(V2MessageStatus.DELIVERED, bob.store.statusOf(sent))
    }

    /** DEC-036: the note waits until its recording is up, and the contact's later texts wait behind it. */
    @Test
    fun theNoteWaitsForItsRecordingAndLaterTextsWaitBehindIt() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        relay.failNextSends = 1
        val voice = bob.service.sendVoice(aliceOnBob, recording, 7_000, waveform)
        bob.service.sendText(aliceOnBob, "after the voice")
        assertEquals(V2MediaState.UPLOADING, bob.store.voiceState(voice))
        assertTrue("nothing reached Alice's inbox", relay.queued(alice.queueId).isEmpty())

        bob.service.synchronize()
        assertEquals(V2MediaState.READY, bob.store.voiceState(voice))
        assertEquals(listOf(IncomingV2Result.VOICE_ACCEPTED, IncomingV2Result.TEXT_ACCEPTED), alice.service.synchronize())
        val shown = alice.service.texts(bobOnAlice)
        assertEquals(listOf(true, false), shown.map { it.voice != null })
        assertEquals("after the voice", shown.last().text)
        assertEquals(V2MediaState.READY, shown.first().voice!!.state)
    }

    /** DEC-036: a recording the relay no longer has is given up after a day and an hour, without a receipt. */
    @Test
    fun aRecordingThatNeverComesIsGivenUpWithoutAReceipt() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        val sent = bob.service.sendVoice(aliceOnBob, recording, 7_000, waveform)
        relay.take(bob.voiceQueue(sent).queueId)

        alice.service.synchronize()
        assertEquals(V2MediaState.DOWNLOADING, alice.service.texts(bobOnAlice).single().voice!!.state)
        clock += ConversationV2Service.MEDIA_WAIT_MILLIS + 1
        alice.service.synchronize()
        assertEquals(V2MediaState.UNAVAILABLE, alice.service.texts(bobOnAlice).single().voice!!.state)
        bob.service.synchronize()
        assertEquals(V2MessageStatus.SENT, bob.store.statusOf(sent))
    }

    /**
     * DEC-036: a recording whose stored piece is gone (a damaged store) can never go up whole; it is
     * given up, so it does not hold the contact's later texts back for good. Its note still goes, and
     * the contact never gets the recording, so it is never "Delivered".
     */
    @Test
    fun aDamagedRecordingIsGivenUpAndDoesNotHoldTheChat() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        relay.failNextSends = 1
        val voice = bob.service.sendVoice(aliceOnBob, recording, 7_000, waveform)
        bob.store.loseVoiceChunk(voice, 1)
        bob.service.sendText(aliceOnBob, "after the voice")

        bob.service.synchronize()
        assertEquals(V2MediaState.UNAVAILABLE, bob.store.voiceState(voice))
        assertNull(bob.service.voiceRecording(voice))
        assertEquals(listOf(IncomingV2Result.VOICE_ACCEPTED, IncomingV2Result.TEXT_ACCEPTED), alice.service.synchronize())
        assertEquals(V2MediaState.DOWNLOADING, alice.service.texts(bobOnAlice).first().voice!!.state)
        bob.service.synchronize()
        assertEquals(V2MessageStatus.SENT, bob.store.statusOf(voice))
    }

    /**
     * DEC-036: a one-time recording plays once on the contact's phone and is deleted there in the same
     * step; it is "Delivered" like any other once it arrived. Ours stays and plays as usual.
     */
    @Test
    fun aOneTimeRecordingPlaysOnceOnTheContactsPhone() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        val sent = bob.service.sendVoice(aliceOnBob, recording, 7_000, waveform, once = true)
        alice.service.synchronize()
        val received = alice.service.texts(bobOnAlice).single()
        assertTrue(received.voice!!.once)
        assertEquals(V2MediaState.READY, received.voice!!.state)
        assertNull("not through the ordinary way", alice.service.voiceRecording(received.messageId))

        // Our own copy plays as usual until the contact has played it, and is never consumed by us.
        assertArrayEquals(recording, bob.service.voiceRecording(sent))
        assertNull("ours is never consumed", bob.service.playOnce(sent))

        assertArrayEquals(recording, alice.service.playOnce(received.messageId))
        assertNull("only once", alice.service.playOnce(received.messageId))
        assertEquals(V2MediaState.PLAYED, alice.service.texts(bobOnAlice).single().voice!!.state)
        assertEquals(0, alice.store.voiceChunkCount(received.messageId))
        assertNull(alice.service.voiceRecording(received.messageId))

        // Bob learns that it was delivered and then played (owner: like Telegram); his copy goes too.
        assertEquals(listOf(IncomingV2Result.RECEIPT_APPLIED, IncomingV2Result.PLAYED_APPLIED), bob.service.synchronize())
        assertEquals(V2MessageStatus.DELIVERED, bob.store.statusOf(sent))
        val ours = bob.service.texts(aliceOnBob).single()
        assertTrue(ours.voice!!.once)
        assertEquals(V2MediaState.PLAYED, ours.voice!!.state)
        assertEquals(0, bob.store.voiceChunkCount(sent))
        assertNull(bob.service.voiceRecording(sent))

        // An ordinary recording is never consumed; fetching it to play tells nothing (only
        // markVoiceListened does, DEC-039).
        bob.service.sendVoice(aliceOnBob, recording, 7_000, waveform)
        alice.service.synchronize()
        val ordinary = alice.service.texts(bobOnAlice).single { it.voice?.once == false }
        assertNull(alice.service.playOnce(ordinary.messageId))
        assertArrayEquals(recording, alice.service.voiceRecording(ordinary.messageId))
        assertEquals(listOf(IncomingV2Result.RECEIPT_APPLIED), bob.service.synchronize())
    }

    /**
     * DEC-036, DEC-039: "played" from the contact touches only our own voice messages to that contact,
     * once they are all up: an ordinary recording is marked listened (once) and kept; a text or an
     * unknown ID changes nothing.
     */
    @Test
    fun aPlayedMarkTouchesOnlyOurOwnVoiceMessages() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        val ordinary = bob.service.sendVoice(aliceOnBob, recording, 7_000, waveform)
        bob.service.sendText(aliceOnBob, "a text")
        alice.service.synchronize()
        bob.service.synchronize()
        val ours = bob.service.texts(aliceOnBob)

        // Alice's phone, made to send "played" for anything (as a hostile app could).
        fun played(innerIdHex: String) {
            val payload = InnerPayloadV2Codec.encode(InnerPayloadV2.Played(RandomId.random(java.security.SecureRandom()), clock, RandomId.fromHex(innerIdHex)))
            val tag = RandomId.fromHex(alice.store.contact(bobOnAlice)!!.peerRoutingTagHex)
            val envelope = EnvelopeV2Codec.encode(EnvelopeV2(tag, alice.sessions.encrypt(bobOnAlice, payload)))
            relay.inject(RelayEncryptedBlob("played-$innerIdHex", bob.queueId, envelope, Long.MAX_VALUE))
        }
        ours.forEach { played(it.innerMessageIdHex) }
        played("ab".repeat(16))

        assertEquals(
            listOf(IncomingV2Result.PLAYED_APPLIED, IncomingV2Result.AUTHENTICATED_IGNORED, IncomingV2Result.AUTHENTICATED_IGNORED),
            bob.service.synchronize(),
        )
        assertTrue(bob.service.texts(aliceOnBob).single { it.messageId == ordinary }.voice!!.listened)
        assertEquals("never deleted", V2MediaState.READY, bob.store.voiceState(ordinary))
        assertArrayEquals(recording, bob.service.voiceRecording(ordinary))

        // A second "played" for the same message changes nothing.
        played(ours.single { it.messageId == ordinary }.innerMessageIdHex)
        assertEquals(listOf(IncomingV2Result.AUTHENTICATED_IGNORED), bob.service.synchronize())
    }

    /**
     * DEC-039: pressing play on the contact's ordinary voice message marks it listened and tells the
     * contact once ("played", type 7); the contact's copy is then listened too, and stays playable.
     * Our own messages and one-time recordings are never marked this way.
     */
    @Test
    fun aListenedVoiceMessageIsToldOnceAndShownOnBothPhones() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        val sent = bob.service.sendVoice(aliceOnBob, recording, 7_000, waveform)
        alice.service.synchronize()
        val received = alice.service.texts(bobOnAlice).single()
        assertFalse(received.voice!!.listened)
        assertFalse(bob.service.texts(aliceOnBob).single().voice!!.listened)

        assertFalse("ours is never marked by us", bob.service.markVoiceListened(sent))
        assertTrue(alice.service.markVoiceListened(received.messageId))
        assertTrue(alice.service.texts(bobOnAlice).single().voice!!.listened)
        assertFalse("only once", alice.service.markVoiceListened(received.messageId))
        assertArrayEquals("it stays playable here", recording, alice.service.voiceRecording(received.messageId))

        assertEquals(listOf(IncomingV2Result.RECEIPT_APPLIED, IncomingV2Result.PLAYED_APPLIED), bob.service.synchronize())
        val ours = bob.service.texts(aliceOnBob).single().voice!!
        assertTrue(ours.listened)
        assertEquals(V2MediaState.READY, ours.state)
        assertArrayEquals("and there", recording, bob.service.voiceRecording(sent))
        assertTrue("nothing more was sent", bob.service.synchronize().isEmpty())

        // A one-time recording goes through playOnce only.
        bob.service.sendVoice(aliceOnBob, recording, 7_000, waveform, once = true)
        alice.service.synchronize()
        val once = alice.service.texts(bobOnAlice).single { it.voice?.once == true }
        assertFalse(alice.service.markVoiceListened(once.messageId))
        assertFalse(alice.service.texts(bobOnAlice).single { it.voice?.once == true }.voice!!.listened)
    }

    /**
     * DEC-039: a voice message from a pending request, or from a blocked contact, is marked listened
     * here when played, but nothing is sent: a request gets no receipts, a blocked contact nothing.
     */
    @Test
    fun aListenedVoiceMessageIsNotToldToARequestOrABlockedContact() = runBlocking {
        val aliceOnBob = bob.service.acceptInvitation(alice.service.createInvitation(), "Alice")
        bob.service.sendVoice(aliceOnBob, recording, 7_000, waveform)
        alice.service.synchronize()
        val requestId = alice.store.requests().single().requestId
        val fromRequest = alice.store.incomingTexts(requestId).single()
        assertTrue(alice.service.markVoiceListened(fromRequest.messageId))
        assertTrue(alice.store.incomingTexts(requestId).single().voice!!.listened)
        assertTrue("no receipt for a request", relay.queued(bob.queueId).isEmpty())

        alice.service.acceptRequest(requestId, "Bob")
        bob.service.synchronize()
        bob.service.sendVoice(aliceOnBob, recording, 7_000, waveform)
        alice.service.synchronize()
        bob.service.synchronize()
        val second = alice.service.texts(requestId).single { !it.voice!!.listened }
        alice.store.setContactState(requestId, V2ContactState.KEY_CHANGED)
        assertTrue(alice.service.markVoiceListened(second.messageId))
        assertTrue("nothing goes to a blocked contact", relay.queued(bob.queueId).isEmpty())
    }

    /**
     * DEC-037: a message is deleted on this phone only, or for both of us; the contact's app deletes its
     * copies (either direction) when the request arrives. Nothing is sent for "this phone only".
     */
    @Test
    fun aMessageIsDeletedHereOnlyOrForBoth() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        bob.service.sendText(aliceOnBob, "keep")
        bob.service.sendText(aliceOnBob, "mine, gone for both")
        alice.service.sendText(bobOnAlice, "hers, gone for both")
        bob.service.sendText(aliceOnBob, "gone here only")
        alice.service.synchronize()
        bob.service.synchronize()
        alice.service.synchronize()
        val bobs = bob.service.texts(aliceOnBob).associateBy { it.text }

        assertEquals(1, bob.service.deleteMessages(aliceOnBob, listOf(bobs.getValue("gone here only").messageId), forBoth = false))
        assertTrue("nothing is sent for this phone only", relay.queued(alice.queueId).isEmpty())

        val both = listOf(bobs.getValue("mine, gone for both").messageId, bobs.getValue("hers, gone for both").messageId)
        assertEquals(2, bob.service.deleteMessages(aliceOnBob, both, forBoth = true))
        assertEquals(listOf("keep"), bob.service.texts(aliceOnBob).map { it.text })
        assertEquals(listOf(IncomingV2Result.DELETE_APPLIED), alice.service.synchronize())
        assertEquals(listOf("keep", "gone here only"), alice.service.texts(bobOnAlice).map { it.text })

        // Already gone, unknown, or another chat's: nothing more is deleted, nothing else changes.
        assertEquals(0, bob.service.deleteMessages(aliceOnBob, both, forBoth = true))
        assertEquals(0, bob.service.deleteMessages("someone-else", listOf(bobs.getValue("keep").messageId), forBoth = false))
        assertEquals(listOf("keep"), bob.service.texts(aliceOnBob).map { it.text })

        // A blocked contact (changed key) cannot be asked; this phone's copy can still go.
        bob.store.setContactState(aliceOnBob, V2ContactState.KEY_CHANGED)
        val blocked = assertThrows(ConversationV2Exception::class.java) {
            runBlocking { bob.service.deleteMessages(aliceOnBob, listOf(bobs.getValue("keep").messageId), forBoth = true) }
        }
        assertEquals("Contact is blocked", blocked.message)
        assertEquals(1, bob.service.deleteMessages(aliceOnBob, listOf(bobs.getValue("keep").messageId), forBoth = false))
    }

    /** DEC-037: a request to delete names only this chat's messages; anything else in it is ignored. */
    @Test
    fun aDeleteRequestReachesOnlyThisChat() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        bob.service.sendText(aliceOnBob, "stays")
        alice.service.synchronize()
        bob.service.synchronize()
        val payload = InnerPayloadV2Codec.encode(InnerPayloadV2.Delete(RandomId.random(java.security.SecureRandom()), clock, listOf(RandomId.fromHex("cd".repeat(16)))))
        val tag = RandomId.fromHex(alice.store.contact(bobOnAlice)!!.peerRoutingTagHex)
        relay.inject(RelayEncryptedBlob("delete-unknown", bob.queueId, EnvelopeV2Codec.encode(EnvelopeV2(tag, alice.sessions.encrypt(bobOnAlice, payload))), Long.MAX_VALUE))

        assertEquals(listOf(IncomingV2Result.AUTHENTICATED_IGNORED), bob.service.synchronize())
        assertEquals(listOf("stays"), bob.service.texts(aliceOnBob).map { it.text })
    }

    /** DEC-035 with DEC-036: a voice message can answer a text, like a text can; only known messages. */
    @Test
    fun aVoiceMessageCanAnswerAText() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        alice.service.sendText(bobOnAlice, "question")
        bob.service.synchronize()
        val question = bob.service.texts(aliceOnBob).single()

        val answer = bob.service.sendVoice(aliceOnBob, recording, 7_000, waveform, question.innerMessageIdHex)
        assertEquals(question.innerMessageIdHex, bob.service.texts(aliceOnBob).single { it.messageId == answer }.replyToInnerIdHex)
        alice.service.synchronize()
        val received = alice.service.texts(bobOnAlice).single { it.voice != null }
        assertEquals(question.innerMessageIdHex, received.replyToInnerIdHex)

        val unknown = assertThrows(ConversationV2Exception::class.java) {
            runBlocking { bob.service.sendVoice(aliceOnBob, recording, 7_000, waveform, "00".repeat(16)) }
        }
        assertEquals("Unknown message", unknown.message)
    }

    /** DEC-036: a voice message from a request is held like a text, fetched, and receipted on acceptance. */
    @Test
    fun aVoiceMessageFromARequestIsReceiptedOnlyOnAcceptance() = runBlocking {
        val aliceOnBob = bob.service.acceptInvitation(alice.service.createInvitation(), "Alice")
        val sent = bob.service.sendVoice(aliceOnBob, recording, 7_000, waveform)

        assertEquals(listOf(IncomingV2Result.REQUEST_RECEIVED, IncomingV2Result.VOICE_ACCEPTED), alice.service.synchronize())
        val requestId = alice.store.requests().single().requestId
        assertEquals(V2MediaState.READY, alice.store.incomingTexts(requestId).single().voice!!.state)
        assertTrue("no receipt for a request", relay.queued(bob.queueId).isEmpty())

        alice.service.acceptRequest(requestId, "Bob")
        bob.service.synchronize()
        assertEquals(V2MessageStatus.DELIVERED, bob.store.statusOf(sent))
    }

    /** DEC-035 with DEC-036: clearing a chat or deleting a contact also drops the stored pieces. */
    @Test
    fun clearingOrDeletingDropsTheStoredPieces() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair()
        val sent = bob.service.sendVoice(aliceOnBob, recording, 7_000, waveform)
        alice.service.synchronize()
        val received = alice.service.texts(bobOnAlice).single().messageId
        assertTrue(alice.store.voiceChunkCount(received) > 0)

        assertEquals(1, alice.service.clearHistory(bobOnAlice))
        assertEquals(0, alice.store.voiceChunkCount(received))
        assertTrue(bob.store.voiceChunkCount(sent) > 0)
        bob.service.deleteContact(aliceOnBob)
        assertEquals(0, bob.store.voiceChunkCount(sent))
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

    /** The same number with its last digit changed: what a person might misread, or another identity's number. */
    private fun oneDigitOff(number: String): String = number.dropLast(1) + ((number.last().digitToInt() + 1) % 10)
}
