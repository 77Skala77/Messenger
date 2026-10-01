package app.securemessenger.signalprotocol

import app.securemessenger.domain.RelayEncryptedBlob
import app.securemessenger.signalstore.V2ContactState
import app.securemessenger.signalstore.V2MessageStatus
import java.security.SecureRandom
import java.util.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.state.PreKeyBundle

/**
 * Phase 5 replay / tamper / reorder / downgrade matrix for the v2 pipeline, over real libsignal
 * sessions. The relay double is the attacker, and so is anyone who saw a QR code or a routing tag.
 * Each test states the property it proves; `docs/PHASE5_SECURITY_TEST_PLAN.md` maps them to the
 * threats.
 */
class V2AttackMatrixTest {
    private var clock = 1_800_000_000_000L
    private val secure = SecureRandom()
    private val relay = FakeRelay()
    private val alice = V2TestDevice("Alice", relay) { clock }
    private val bob = V2TestDevice("Bob", relay) { clock }
    private var crafted = 0

    /**
     * Tamper: changed copies of real envelopes in every routing state (invitation, pending request,
     * contact before and after acceptance, receipts). A changed envelope is either rejected without
     * changing anything at all, or authenticated with exactly the original's effect (libsignal leaves
     * a few outer PreKey fields outside its MAC). No attempt throws: a throw would stall the queue.
     * The genuine envelope still works afterwards.
     */
    @Test
    fun noChangedEnvelopeIsTrustedOrChangesAnything() = runBlocking {
        val random = Random(11)
        val aliceOnBob = bob.service.acceptInvitation(alice.service.createInvitation(), "Alice")
        val hello = relay.take(alice.queueId).single()
        expectTamperingRejected(alice, hello, random, IncomingV2Result.REQUEST_RECEIVED)

        bob.service.sendText(aliceOnBob, "before acceptance")
        val heldText = relay.take(alice.queueId).single()
        expectTamperingRejected(alice, heldText, random, IncomingV2Result.TEXT_ACCEPTED)

        alice.service.acceptRequest(alice.store.requests().single().requestId, "Bob")
        val (accept, receipt) = relay.take(bob.queueId)
        expectTamperingRejected(bob, accept, random, IncomingV2Result.CONTACT_ACCEPTED)
        expectTamperingRejected(bob, receipt, random, IncomingV2Result.RECEIPT_APPLIED)

        bob.service.sendText(aliceOnBob, "after acceptance")
        val text = relay.take(alice.queueId).single()
        expectTamperingRejected(alice, text, random, IncomingV2Result.TEXT_ACCEPTED)

        // Replay: the relay's old copies, as they were and changed, after everything moved on.
        listOf(hello, heldText, text).forEach { expectReplayRejected(alice, it, random) }
        listOf(accept, receipt).forEach { expectReplayRejected(bob, it, random) }
    }

    /**
     * Order (F3): Bob's HELLO fails to send once. His next text waits for it, so the relay gets them
     * in order and Alice holds the text for the request, as with any early text.
     */
    @Test
    fun aFailedHelloHoldsBackLaterMessagesSoNothingOvertakesIt() = runBlocking {
        val code = alice.service.createInvitation()
        relay.failNextSends = 1
        val aliceOnBob = bob.service.acceptInvitation(code, "Alice")
        bob.service.sendText(aliceOnBob, "hello before the hello")
        assertTrue("the text waits for the HELLO", relay.queued(alice.queueId).isEmpty())
        clock += 5_000
        assertEquals(2, bob.service.sendDue())

        assertEquals(listOf(IncomingV2Result.REQUEST_RECEIVED, IncomingV2Result.TEXT_ACCEPTED), alice.service.synchronize())
        val request = alice.store.requests().single()
        assertEquals(listOf("hello before the hello"), alice.store.incomingTexts(request.requestId).map { it.text })
        alice.service.acceptRequest(request.requestId, "Bob")
        bob.service.synchronize()
        assertEquals(V2ContactState.ACTIVE, bob.store.contact(aliceOnBob)!!.state)
    }

    /**
     * Reorder (F3): the relay moves Bob's text before his HELLO. Only a HELLO may open an invitation,
     * so the text is rejected without spending anything, and the HELLO then pairs as usual. (The
     * moved text is lost; a relay could drop it anyway.)
     */
    @Test
    fun aTextTheRelayMovesBeforeItsHelloBurnsNothing() = runBlocking {
        val aliceOnBob = bob.service.acceptInvitation(alice.service.createInvitation(), "Alice")
        bob.service.sendText(aliceOnBob, "moved ahead")
        val (hello, text) = relay.take(alice.queueId)

        assertEquals(IncomingV2Result.UNAUTHENTICATED, alice.processUnchanged(text))
        assertEquals(IncomingV2Result.REQUEST_RECEIVED, alice.service.processIncoming(hello))
        assertEquals(1, alice.store.requests().size)
    }

    /**
     * Flood (F1, F2, F5): junk from anyone who knows the queue ID is deleted once decided, so it can
     * neither fill the queue nor hide a real message behind full pages.
     */
    @Test
    fun aFloodOfJunkIsDeletedAndTheRealMessageBehindItArrives() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair(alice, bob)
        val random = Random(12)
        val knownTag = RandomId.fromHex(alice.store.contact(bobOnAlice)!!.localRoutingTagHex)
        val bobsIdentity = bob.sessions.localIdentityKey().serialize()
        val preKeyTemplate = TestSignalDevice().let { stranger ->
            stranger.sessions.startSession("someone", TestSignalDevice().invitations.create().toPreKeyBundle())
            stranger.sessions.encrypt("someone", ByteArray(160)).bytes
        }
        repeat(250) { index ->
            val junk = when (index % 4) {
                0 -> ByteArray(random.nextInt(300)).also(random::nextBytes)
                1 -> EnvelopeV2Codec.encode(EnvelopeV2(RandomId.random(secure), whisperOf(ByteArray(80).also(random::nextBytes))))
                2 -> EnvelopeV2Codec.encode(EnvelopeV2(knownTag, whisperOf(ByteArray(80).also(random::nextBytes))))
                // PreKey forgeries claiming Bob's (public) identity with key IDs above 2^31 (R1).
                else -> EnvelopeV2Codec.encode(
                    EnvelopeV2(
                        knownTag,
                        SignalCiphertext(
                            CiphertextMessage.PREKEY_TYPE,
                            PreKeyMessageFields.parse(preKeyTemplate)
                                .withBytes(PreKeyMessageFields.IDENTITY_KEY, bobsIdentity)
                                .withVarint(PreKeyMessageFields.SIGNED_PRE_KEY_ID, 0x80000000L + index)
                                .encode(),
                        ),
                    ),
                )
            }
            relay.inject(RelayEncryptedBlob("junk-$index", alice.queueId, junk, Long.MAX_VALUE))
        }
        bob.service.sendText(aliceOnBob, "behind the junk")

        val results = alice.service.synchronize()

        assertEquals(251, results.size)
        assertEquals(listOf("behind the junk"), alice.store.incomingTexts(bobOnAlice).map { it.text })
        assertTrue(relay.queued(alice.queueId).isEmpty())
        assertEquals(V2ContactState.ACTIVE, alice.store.contact(bobOnAlice)!!.state)
    }

    /** "Delivered" only from the contact the message went to, and only for an outgoing text. */
    @Test
    fun receiptsCountOnlyFromTheContactTheMessageWentTo() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair(alice, bob)
        val carol = V2TestDevice("Carol", relay) { clock }
        val (aliceOnCarol, _) = pair(alice, carol)
        val toBob = alice.service.sendText(bobOnAlice, "for Bob")
        val innerId = RandomId.fromHex(alice.store.texts(bobOnAlice).single { it.messageId == toBob }.innerMessageIdHex)

        // Carol learned the inner ID (Bob told her) and receipts it on her own valid session.
        val fromCarol = carol.craft(aliceOnCarol, InnerPayloadV2.Receipt(RandomId.random(secure), clock, innerId))
        assertEquals(IncomingV2Result.AUTHENTICATED_IGNORED, alice.service.processIncoming(fromCarol))
        assertEquals(V2MessageStatus.SENT, alice.store.statusOf(toBob))

        // Bob receipts a message Alice received from him: that is not an outgoing text of Alice's.
        bob.service.sendText(aliceOnBob, "from Bob")
        alice.service.synchronize()
        val received = RandomId.fromHex(alice.store.incomingTexts(bobOnAlice).single().innerMessageIdHex)
        val wrongDirection = bob.craft(aliceOnBob, InnerPayloadV2.Receipt(RandomId.random(secure), clock, received))
        assertEquals(IncomingV2Result.AUTHENTICATED_IGNORED, alice.service.processIncoming(wrongDirection))

        // The genuine receipt still works.
        bob.service.synchronize()
        assertTrue(IncomingV2Result.RECEIPT_APPLIED in alice.service.synchronize())
        assertEquals(V2MessageStatus.DELIVERED, alice.store.statusOf(toBob))
    }

    /** HELLO, ACCEPT, and RECEIPT in the wrong state are authenticated but change nothing. */
    @Test
    fun controlMessagesOutOfPlaceChangeNothing() = runBlocking {
        val aliceOnBob = bob.service.acceptInvitation(alice.service.createInvitation(), "Alice")
        alice.service.synchronize()
        val requestId = alice.store.requests().single().requestId
        val beforeOutgoing = alice.store.outgoingKinds(requestId)

        // A pending request cannot accept itself, deliver receipts, or say hello twice.
        listOf(
            InnerPayloadV2.Accept(RandomId.random(secure), clock),
            InnerPayloadV2.Receipt(RandomId.random(secure), clock, RandomId.random(secure)),
            InnerPayloadV2.Hello(RandomId.random(secure), clock, bob.queueId, RandomId.random(secure), "Bob again"),
        ).forEach { payload ->
            assertEquals(IncomingV2Result.AUTHENTICATED_IGNORED, alice.service.processIncoming(bob.craft(aliceOnBob, payload)))
        }
        assertTrue(alice.store.contacts().isEmpty())
        assertEquals(listOf(requestId), alice.store.requests().map { it.requestId })
        assertEquals("no receipts for a request", beforeOutgoing, alice.store.outgoingKinds(requestId))

        // An active contact cannot repeat HELLO or ACCEPT.
        alice.service.acceptRequest(requestId, "Bob")
        bob.service.synchronize()
        listOf(
            InnerPayloadV2.Hello(RandomId.random(secure), clock, bob.queueId, RandomId.random(secure), "Bob again"),
            InnerPayloadV2.Accept(RandomId.random(secure), clock),
        ).forEach { payload ->
            assertEquals(IncomingV2Result.AUTHENTICATED_IGNORED, alice.service.processIncoming(bob.craft(aliceOnBob, payload)))
        }
        assertEquals(V2ContactState.ACTIVE, alice.store.contact(requestId)!!.state)
        assertTrue(alice.store.requests().isEmpty())

        // An authenticated payload that does not parse is dropped; the session goes on.
        val garbage = ByteArray(2 * InnerPayloadV2Codec.PADDING_BLOCK_BYTES).also(secure::nextBytes)
        assertEquals(IncomingV2Result.AUTHENTICATED_IGNORED, alice.service.processIncoming(bob.craftRaw(aliceOnBob, garbage)))
        bob.service.sendText(aliceOnBob, "still fine")
        assertEquals(listOf(IncomingV2Result.TEXT_ACCEPTED), alice.service.synchronize())
    }

    /** Downgrade: other envelope, libsignal message, and inner payload versions are never accepted. */
    @Test
    fun otherVersionsAreNeverAccepted() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair(alice, bob)
        bob.service.sendText(aliceOnBob, "current versions")
        val genuine = relay.take(alice.queueId).single()

        for (version in listOf(0, 2, 3, 0xFF)) {
            val changed = genuine.opaqueCiphertext.copyOf().also { it[ENVELOPE_VERSION_OFFSET] = version.toByte() }
            assertEquals("envelope version $version", IncomingV2Result.UNAUTHENTICATED, alice.processUnchanged(genuine.withBytes(changed)))
        }
        // libsignal's version byte: 0x44 today; 0x33 is the pre-PQXDH version 3.
        assertEquals(0x44, genuine.opaqueCiphertext[CIPHERTEXT_OFFSET].toInt())
        for (version in listOf(0x33, 0x34, 0x43, 0x22, 0x55, 0x00)) {
            val changed = genuine.opaqueCiphertext.copyOf().also { it[CIPHERTEXT_OFFSET] = version.toByte() }
            assertEquals("libsignal version $version", IncomingV2Result.UNAUTHENTICATED, alice.processUnchanged(genuine.withBytes(changed)))
        }
        assertEquals(IncomingV2Result.TEXT_ACCEPTED, alice.service.processIncoming(genuine))

        // A later inner payload version from the genuine contact: authenticated, dropped, session intact.
        val laterPayload = InnerPayloadV2Codec.encode(InnerPayloadV2.Text(RandomId.random(secure), clock, "v2?")).also { it[4] = 2 }
        assertEquals(IncomingV2Result.AUTHENTICATED_IGNORED, alice.service.processIncoming(bob.craftRaw(aliceOnBob, laterPayload)))
        assertEquals(listOf("current versions"), alice.store.incomingTexts(bobOnAlice).map { it.text })
        bob.service.sendText(aliceOnBob, "after")
        assertEquals(listOf(IncomingV2Result.TEXT_ACCEPTED), alice.service.synchronize())
    }

    /** The relay moves Bob's envelope to Carol's routing tag: rejected, nothing changes for anyone. */
    @Test
    fun relabellingAnEnvelopeForAnotherContactIsNeverTrusted() = runBlocking {
        val (aliceOnBob, _) = pair(alice, bob)
        val carol = V2TestDevice("Carol", relay) { clock }
        val (aliceOnCarol, carolOnAlice) = pair(alice, carol)
        bob.service.sendText(aliceOnBob, "from Bob")
        val genuine = relay.take(alice.queueId).single()
        val carolsTag = RandomId.fromHex(alice.store.contact(carolOnAlice)!!.localRoutingTagHex).toByteArray()
        val relabelled = genuine.opaqueCiphertext.copyOf().also { carolsTag.copyInto(it, ROUTING_TAG_OFFSET) }

        assertEquals(IncomingV2Result.UNAUTHENTICATED, alice.processUnchanged(genuine.withBytes(relabelled)))
        assertEquals(IncomingV2Result.TEXT_ACCEPTED, alice.service.processIncoming(genuine))
        carol.service.sendText(aliceOnCarol, "from Carol")
        assertEquals(listOf(IncomingV2Result.TEXT_ACCEPTED), alice.service.synchronize())
    }

    /**
     * A HELLO must use exactly its invitation's pre-keys, the Kyber key included. Someone who saw two
     * of Alice's QR codes mixes the EC pre-keys of the first with the Kyber pre-key of the second:
     * rejected, nothing is consumed, and the second invitation still works for its real scanner.
     */
    @Test
    fun aHelloMustUseItsOwnInvitationsKyberKey() = runBlocking {
        val first = alice.service.createInvitation()
        val second = alice.service.createInvitation()
        val firstBundle = openBundle(first)
        val secondBundle = openBundle(second)
        val mallory = V2TestDevice("Mallory", relay) { clock }
        val one = firstBundle.preKeys
        val two = secondBundle.preKeys
        mallory.sessions.startSession(
            "alice",
            PreKeyBundle(
                one.registrationId, SignalSessions.DEVICE_ID, one.oneTimePreKeyId, one.oneTimePreKey, one.signedPreKeyId,
                one.signedPreKey, one.signedPreKeySignature, one.identityKey, two.kyberPreKeyId, two.kyberPreKey, two.kyberPreKeySignature,
            ),
        )
        val hello = InnerPayloadV2.Hello(RandomId.random(secure), clock, mallory.queueId, RandomId.random(secure), "Mallory")
        val envelope = EnvelopeV2Codec.encode(EnvelopeV2(firstBundle.routingTag, mallory.sessions.encrypt("alice", InnerPayloadV2Codec.encode(hello))))

        assertEquals(IncomingV2Result.UNAUTHENTICATED, alice.processUnchanged(RelayEncryptedBlob("mixed", alice.queueId, envelope, Long.MAX_VALUE)))
        assertTrue(alice.signalStore.containsKyberPreKey(two.kyberPreKeyId))

        val carol = V2TestDevice("Carol", relay) { clock }
        carol.service.acceptInvitation(second, "Alice")
        assertEquals(listOf(IncomingV2Result.REQUEST_RECEIVED), alice.service.synchronize())
    }

    /**
     * F8: once a HELLO used an invitation, none of its private pre-keys remain, and Bob's later
     * PreKey messages (sent before he hears back) still decrypt: they use the session, not the keys.
     */
    @Test
    fun aUsedInvitationKeepsNoPreKeysAndLaterPreKeyMessagesStillDecrypt() = runBlocking {
        val code = alice.service.createInvitation()
        val invitation = alice.store.latestUsableInvitation(clock)!!
        val aliceOnBob = bob.service.acceptInvitation(code, "Alice")
        bob.service.sendText(aliceOnBob, "a second PreKey message")

        assertEquals(listOf(IncomingV2Result.REQUEST_RECEIVED, IncomingV2Result.TEXT_ACCEPTED), alice.service.synchronize())
        assertFalse(alice.signalStore.containsSignedPreKey(invitation.signedPreKeyId))
        assertFalse(alice.signalStore.containsPreKey(invitation.oneTimePreKeyId))
        assertFalse(alice.signalStore.containsKyberPreKey(invitation.kyberPreKeyId))
    }

    /**
     * R1 (second review): key IDs in a PreKey message are u32 on the wire, and libsignal hands the
     * ones above 2^31 to the store as negative ints. On every route that must be a clean rejection
     * that changes nothing and is deleted, never an exception that would stall the whole queue.
     */
    @Test
    fun preKeyMessagesNamingHugeKeyIdsAreRejectedOnEveryRoute() = runBlocking {
        val code = alice.service.createInvitation()
        val invitation = alice.store.latestUsableInvitation(clock)!!
        val aliceOnBob = bob.service.acceptInvitation(code, "Alice")
        val hello = relay.take(alice.queueId).single()
        val layout = PreKeyMessageFields.parse(EnvelopeV2Codec.decode(hello.opaqueCiphertext).ciphertext.bytes).varints()
        assertEquals(invitation.oneTimePreKeyId.toLong(), layout[PreKeyMessageFields.PRE_KEY_ID])
        assertEquals(invitation.signedPreKeyId.toLong(), layout[PreKeyMessageFields.SIGNED_PRE_KEY_ID])
        assertEquals(invitation.kyberPreKeyId.toLong(), layout[PreKeyMessageFields.KYBER_PRE_KEY_ID])

        suspend fun expectRejected(device: V2TestDevice, tag: RandomId? = null, freshBaseKey: Boolean, identity: ByteArray? = null) {
            for (field in listOf(PreKeyMessageFields.PRE_KEY_ID, PreKeyMessageFields.SIGNED_PRE_KEY_ID, PreKeyMessageFields.KYBER_PRE_KEY_ID)) {
                for (value in listOf(0x80000000L, 0xFFFFFFFFL)) {
                    val forged = forgedPreKey(hello, tag) {
                        withVarint(field, value)
                        if (freshBaseKey) withBytes(PreKeyMessageFields.BASE_KEY, byteArrayOf(5) + ByteArray(32).also(secure::nextBytes))
                        identity?.let { withBytes(PreKeyMessageFields.IDENTITY_KEY, it) }
                    }
                    assertEquals("field $field = $value", IncomingV2Result.UNAUTHENTICATED, device.processUnchanged(forged))
                }
            }
        }

        // The unused invitation: its EC pre-key IDs are in the bundle, so anyone with the QR code knows them.
        expectRejected(alice, freshBaseKey = false)
        // The pending request, then the contact: the peer's identity key is public; the base key is new.
        assertEquals(IncomingV2Result.REQUEST_RECEIVED, alice.service.processIncoming(hello))
        expectRejected(alice, freshBaseKey = true)
        alice.service.acceptRequest(alice.store.requests().single().requestId, "Bob")
        expectRejected(alice, freshBaseKey = true)
        // The scanner's side, claiming the inviter's identity.
        bob.service.synchronize()
        val bobsTag = RandomId.fromHex(bob.store.contact(aliceOnBob)!!.localRoutingTagHex)
        expectRejected(bob, tag = bobsTag, freshBaseKey = true, identity = alice.sessions.localIdentityKey().serialize())

        bob.service.sendText(aliceOnBob, "still fine")
        assertEquals(listOf(IncomingV2Result.TEXT_ACCEPTED), alice.service.synchronize())
    }

    /**
     * R2 (second review): a contact who also saw another of Alice's QR codes starts a new session
     * from that invitation's pre-keys under her own routing tag. Rejected before libsignal sees it,
     * so the other invitation keeps its keys and still works for its real scanner.
     */
    @Test
    fun aContactCannotSpendAnotherInvitationsPreKeys() = runBlocking {
        val mallory = V2TestDevice("Mallory", relay) { clock }
        val (aliceOnMallory, _) = pair(alice, mallory)
        val second = alice.service.createInvitation()
        val secondBundle = openBundle(second)
        mallory.sessions.startSession("alice-again", secondBundle.preKeys.toPreKeyBundle())
        val text = InnerPayloadV2Codec.encode(InnerPayloadV2.Text(RandomId.random(secure), clock, "new session"))
        val tag = RandomId.fromHex(mallory.store.contact(aliceOnMallory)!!.peerRoutingTagHex)
        val envelope = EnvelopeV2Codec.encode(EnvelopeV2(tag, mallory.sessions.encrypt("alice-again", text)))

        assertEquals(IncomingV2Result.UNAUTHENTICATED, alice.processUnchanged(RelayEncryptedBlob("rekey", alice.queueId, envelope, Long.MAX_VALUE)))
        assertTrue(alice.signalStore.containsKyberPreKey(secondBundle.preKeys.kyberPreKeyId))
        val carol = V2TestDevice("Carol", relay) { clock }
        carol.service.acceptInvitation(second, "Alice")
        assertEquals(listOf(IncomingV2Result.REQUEST_RECEIVED), alice.service.synchronize())
    }

    /** R3 (second review): a HELLO that arrives after the invitation expired is unroutable, even before the cleanup. */
    @Test
    fun aHelloAfterExpiryIsUnroutableEvenBeforeTheCleanup() = runBlocking {
        bob.service.acceptInvitation(alice.service.createInvitation(), "Alice")
        clock += InvitationV2.DEFAULT_LIFETIME_MILLIS + InvitationV2.CLOCK_SKEW_MILLIS + 1

        assertEquals(listOf(IncomingV2Result.UNROUTABLE), alice.service.synchronize())
        assertTrue(alice.store.requests().isEmpty())
    }

    /**
     * R4 (second review): the wall clock moves back after Bob's HELLO failed to send. The text he
     * writes next is older by its timestamp, but still waits for the HELLO: order is insertion order.
     */
    @Test
    fun theSendOrderHoldsWhenTheClockMovesBack() = runBlocking {
        val code = alice.service.createInvitation()
        relay.failNextSends = 1
        val aliceOnBob = bob.service.acceptInvitation(code, "Alice")
        clock -= 60_000
        bob.service.sendText(aliceOnBob, "written after the HELLO")
        assertTrue(relay.queued(alice.queueId).isEmpty())

        clock += 65_000
        assertEquals(2, bob.service.sendDue())
        assertEquals(listOf(IncomingV2Result.REQUEST_RECEIVED, IncomingV2Result.TEXT_ACCEPTED), alice.service.synchronize())
    }

    /**
     * Defence in depth (R1): an envelope that reaches no verdict (here a simulated storage failure)
     * stays on the relay without stopping the others; receipts still go out; the failure is reported.
     */
    @Test
    fun oneEnvelopeThatFailsDoesNotStallTheOthers() = runBlocking {
        val (aliceOnBob, bobOnAlice) = pair(alice, bob)
        bob.service.sendText(aliceOnBob, "first")
        bob.service.sendText(aliceOnBob, "second")
        alice.store.failNextIncomingStore = true

        assertThrows(IllegalStateException::class.java) { runBlocking { alice.service.synchronize() } }
        assertEquals(listOf("second"), alice.store.incomingTexts(bobOnAlice).map { it.text })
        assertEquals("the failed one stays on the relay", 1, relay.queued(alice.queueId).size)
        assertEquals("the receipt for the other went out", 1, relay.queued(bob.queueId).size)

        assertEquals(listOf(IncomingV2Result.TEXT_ACCEPTED), alice.service.synchronize())
        assertEquals(setOf("first", "second"), alice.store.incomingTexts(bobOnAlice).map { it.text }.toSet())
    }

    // --- helpers ---

    /** A real PreKey envelope with edited protobuf fields, optionally under another routing tag. */
    private fun forgedPreKey(template: RelayEncryptedBlob, tag: RandomId? = null, edit: PreKeyMessageFields.() -> Unit): RelayEncryptedBlob {
        val envelope = EnvelopeV2Codec.decode(template.opaqueCiphertext)
        val ciphertext = PreKeyMessageFields.parse(envelope.ciphertext.bytes).apply(edit).encode()
        val bytes = EnvelopeV2Codec.encode(EnvelopeV2(tag ?: envelope.routingTag, SignalCiphertext(CiphertextMessage.PREKEY_TYPE, ciphertext)))
        return RelayEncryptedBlob("forged-${crafted++}", template.queueId, bytes, Long.MAX_VALUE)
    }

    /** Processes [blob] and requires that it changed no record and no stored value at all. */
    private suspend fun V2TestDevice.processUnchanged(blob: RelayEncryptedBlob): IncomingV2Result {
        val records = records.fingerprint()
        val stored = store.dump()
        val result = service.processIncoming(blob)
        assertEquals("libsignal records changed by a $result envelope", records, this.records.fingerprint())
        assertEquals("stored values changed by a $result envelope", stored, store.dump())
        return result
    }

    private suspend fun expectTamperingRejected(device: V2TestDevice, original: RelayEncryptedBlob, random: Random, expected: IncomingV2Result) {
        val reference = device.outcomeOf(original, "the original")
        assertEquals(expected, reference.first)
        val records = device.records.fingerprint()
        val stored = device.store.dump()
        for (mutant in Fuzz.sampledMutants(original.opaqueCiphertext, random, SAMPLES)) {
            val (result, observable) = device.outcomeOf(original.withBytes(mutant.bytes), mutant.description) {
                if (it == IncomingV2Result.UNAUTHENTICATED || it == IncomingV2Result.UNROUTABLE) {
                    assertEquals("${mutant.description}: records changed by a rejected envelope", records, device.records.fingerprint())
                    assertEquals("${mutant.description}: stored values changed by a rejected envelope", stored, device.store.dump())
                }
            }
            if (result != IncomingV2Result.UNAUTHENTICATED && result != IncomingV2Result.UNROUTABLE) {
                assertEquals("${mutant.description} was trusted with another effect", reference, result to observable)
            }
        }
        assertEquals(expected, device.service.processIncoming(original))
    }

    private suspend fun expectReplayRejected(device: V2TestDevice, old: RelayEncryptedBlob, random: Random) {
        val candidates = sequenceOf(Mutant("the original replayed", old.opaqueCiphertext)) +
            Fuzz.sampledMutants(old.opaqueCiphertext, random, SAMPLES / 2)
        for (candidate in candidates) {
            val result = try {
                device.processUnchanged(old.withBytes(candidate.bytes))
            } catch (failure: AssertionError) {
                throw AssertionError("${candidate.description}: ${failure.message}", failure)
            }
            assertTrue(
                "${candidate.description} replayed as $result",
                result in setOf(IncomingV2Result.DUPLICATE, IncomingV2Result.UNAUTHENTICATED, IncomingV2Result.UNROUTABLE),
            )
        }
    }

    /**
     * Processes [blob] on a snapshot of [this] device's state and restores it afterwards. Returns
     * the result and what a person could observe (no random local IDs). [check] runs before the
     * restore. Any exception fails the test: every envelope must end in a verdict.
     */
    private suspend fun V2TestDevice.outcomeOf(
        blob: RelayEncryptedBlob,
        description: String,
        check: (IncomingV2Result) -> Unit = {},
    ): Pair<IncomingV2Result, List<String>> {
        val restoreRecords = records.snapshot()
        val restoreStore = store.snapshot()
        try {
            val result = try {
                service.processIncoming(blob)
            } catch (failure: Throwable) {
                fail("$description: unexpected ${failure::class.java.name}")
                throw failure
            }
            check(result)
            return result to observable()
        } finally {
            restoreRecords()
            restoreStore()
        }
    }

    /** What a person could observe, without the random local IDs that a repeated step draws anew. */
    private fun V2TestDevice.observable(): List<String> {
        val tags = store.contacts().associate { it.contactId to it.localRoutingTagHex } +
            store.requests().associate { it.requestId to it.localRoutingTagHex }
        return buildList {
            store.contacts().forEach {
                add("contact ${it.localRoutingTagHex} ${it.state} ${hexOf(it.identityKey)} ${it.peerQueueId} ${it.peerRoutingTagHex}")
            }
            store.requests().forEach {
                add("request ${it.localRoutingTagHex} ${hexOf(it.identityKey)} ${it.peerQueueId} ${it.peerRoutingTagHex} ${it.claimedDisplayName}")
            }
            tags.forEach { (id, tag) ->
                store.incomingTexts(id).forEach { add("received $tag ${it.innerMessageIdHex} ${it.text}") }
                store.texts(id).filter { it.outgoing }.forEach { add("sent $tag ${it.innerMessageIdHex} ${it.status}") }
                add("queued $tag ${store.outgoingKinds(id)}")
            }
            addAll(store.invitationStates())
        }.sorted()
    }

    /** [scanner] scans [inviter]'s invitation and [inviter] accepts. Returns (inviter on scanner, scanner on inviter). */
    private suspend fun pair(inviter: V2TestDevice, scanner: V2TestDevice): Pair<String, String> {
        val inviterOnScanner = scanner.service.acceptInvitation(inviter.service.createInvitation(), inviter.name)
        inviter.service.synchronize()
        val scannerOnInviter = inviter.store.requests().single().requestId
        inviter.service.acceptRequest(scannerOnInviter, scanner.name)
        scanner.service.synchronize()
        return inviterOnScanner to scannerOnInviter
    }

    /** What a malicious peer can send: any payload bytes, encrypted on its own valid session. */
    private fun V2TestDevice.craftRaw(contactId: String, plaintext: ByteArray): RelayEncryptedBlob {
        val contact = checkNotNull(store.contact(contactId))
        val ciphertext = sessions.encrypt(contactId, plaintext)
        val envelope = EnvelopeV2Codec.encode(EnvelopeV2(RandomId.fromHex(contact.peerRoutingTagHex), ciphertext))
        return RelayEncryptedBlob("crafted-${crafted++}", contact.peerQueueId, envelope, Long.MAX_VALUE)
    }

    private fun V2TestDevice.craft(contactId: String, payload: InnerPayloadV2) = craftRaw(contactId, InnerPayloadV2Codec.encode(payload))

    private suspend fun openBundle(codeText: String): InvitationBundle {
        val code = InvitationV2.decodeCode(codeText)
        return InvitationV2.open(code, relay.fetchMessages(code.mailbox).map { it.opaqueCiphertext }, clock)
    }

    private fun RelayEncryptedBlob.withBytes(bytes: ByteArray) =
        RelayEncryptedBlob("$messageId-changed-${crafted++}", queueId, bytes, expiresAtEpochMillis)

    private fun whisperOf(bytes: ByteArray) = SignalCiphertext(CiphertextMessage.WHISPER_TYPE, bytes)

    private companion object {
        const val SAMPLES = 300

        /** "SME2", version, 16-byte routing tag, type, 32-bit length, then the libsignal ciphertext. */
        const val ENVELOPE_VERSION_OFFSET = 4
        const val ROUTING_TAG_OFFSET = 5
        const val CIPHERTEXT_OFFSET = 26
    }
}
