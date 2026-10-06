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
