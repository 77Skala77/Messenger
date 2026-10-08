package app.securemessenger.signalprotocol

import app.securemessenger.domain.RelayQueueCredentials
import app.securemessenger.signalstore.SignalLocalIdentityRecord
import java.security.SecureRandom
import java.util.Base64
import java.util.Random
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.CiphertextMessage
import app.securemessenger.signalstore.SignalProtocolAddress as StoreAddress

/**
 * Phase 5: every decoder that reads untrusted bytes, fuzzed. The property is the same everywhere:
 * an input is either rejected with the decoder's own exception type, or accepted as a value that
 * encodes back to exactly that input, so nothing is ever half-parsed or trusted in a second
 * spelling. `docs/PHASE5_SECURITY_TEST_PLAN.md` lists the inputs and what each test proves.
 */
class V2DecoderFuzzTest {
    private val secure = SecureRandom()

    @Test
    fun envelopeDecoderRejectsCleanlyOrRoundTripsExactly() {
        val random = Random(101)
        val alice = TestSignalDevice()
        val bob = TestSignalDevice()
        bob.sessions.startSession(ALICE, alice.invitations.create().toPreKeyBundle())
        val preKey = EnvelopeV2Codec.encode(EnvelopeV2(RandomId.random(secure), bob.sessions.encrypt(ALICE, ByteArray(160))))
        val whisper = EnvelopeV2Codec.encode(
            EnvelopeV2(RandomId.random(secure), SignalCiphertext(CiphertextMessage.WHISPER_TYPE, ByteArray(120).also(random::nextBytes))),
        )
        val inputs = Fuzz.mutants(whisper, random) + Fuzz.mutants(preKey, random) +
            Fuzz.randomInputs(random, count = 2_000, maxLength = 4_096) + Fuzz.randomInputs(random, count = 1, maxLength = 1 shl 20)

        val tried = decodeEach(inputs, MalformedV2Exception::class, EnvelopeV2Codec::decode) { input, envelope ->
            assertArrayEquals(input.description, input.bytes, EnvelopeV2Codec.encode(envelope))
        }
        assertTrue(tried > 5_000)
    }

    @Test
    fun payloadDecoderRejectsCleanlyOrRoundTripsUpToPadding() {
        val random = Random(202)
        val payloads = listOf(
            InnerPayloadV2.Hello(RandomId.random(secure), NOW, QUEUE, RandomId.random(secure), "Bob"),
            InnerPayloadV2.Accept(RandomId.random(secure), NOW),
            InnerPayloadV2.Text(RandomId.random(secure), NOW, "привет"),
            InnerPayloadV2.Text(RandomId.random(secure), NOW, "x".repeat(400)),
            InnerPayloadV2.Text(RandomId.random(secure), NOW, "ответ", replyTo = RandomId.random(secure)),
            InnerPayloadV2.Voice(RandomId.random(secure), NOW, voiceNote()),
            InnerPayloadV2.Voice(RandomId.random(secure), NOW, voiceNote(once = true), replyTo = RandomId.random(secure)),
            InnerPayloadV2.Played(RandomId.random(secure), NOW, RandomId.random(secure)),
            InnerPayloadV2.Delete(RandomId.random(secure), NOW, List(3) { RandomId.random(secure) }),
            InnerPayloadV2.Receipt(RandomId.random(secure), NOW, RandomId.random(secure)),
        )
        var inputs = Fuzz.randomInputs(random, count = 1_000, maxLength = 2_048) + paddedRandomBodies(random)
        payloads.forEach { inputs += Fuzz.mutants(InnerPayloadV2Codec.encode(it), random) }

        decodeEach(inputs, MalformedV2Exception::class, InnerPayloadV2Codec::decode) { input, payload ->
            // The sender may pad more than needed; the content before the padding must be exact.
            assertArrayEquals(input.description, unpadded(input.bytes), unpadded(InnerPayloadV2Codec.encode(payload)))
            assertTrue(input.description, payload.sentAtEpochMillis > 0)
            if (payload is InnerPayloadV2.Hello) {
                assertTrue(input.description, RelayQueueCredentials.isValidQueueId(payload.receiveQueueId))
                assertTrue(input.description, InnerPayloadV2Codec.isAcceptableDisplayName(payload.displayName))
            }
        }
    }

    @Test
    fun invitationCodeTextIsAcceptedOnlyInItsCanonicalForm() {
        val random = Random(303)
        val text = InvitationV2.publish(bundleFor(TestSignalDevice()), secure).code.encode()
        var tried = 0
        for ((description, candidate) in textMutants(text, random)) {
            tried++
            val code = try {
                InvitationV2.decodeCode(candidate)
            } catch (_: InvitationRejectedException) {
                continue
            } catch (failure: Throwable) {
                fail("$description: unexpected ${failure::class.java.name}")
                throw failure
            }
            // One invitation has exactly one text: anything accepted must be that text.
            assertEquals(description, candidate, code.encode())
        }
        assertTrue(tried > 2_000)
    }

    @Test
    fun bundleDecoderRejectsCleanlyOrRoundTripsExactly() {
        val random = Random(404)
        val bundle = bundleFor(TestSignalDevice())
        val encoded = InvitationBundleCodec.encode(bundle)
        // Most mutants pass the structure checks and then cost two signature verifications each.
        // The 1,569 Kyber key bytes all take that same path, so they are sampled, not exhausted.
        val kyberKey = bundle.preKeys.kyberPreKey.serialize()
        val kyberStart = (0..encoded.size - kyberKey.size).first { start ->
            kyberKey.indices.all { encoded[start + it] == kyberKey[it] }
        }
        val inputs = Fuzz.mutants(encoded, random, boundaryValuesPerPosition = 2, skip = kyberStart until kyberStart + kyberKey.size) +
            Fuzz.sampledMutants(encoded, random, count = 300) +
            Fuzz.randomInputs(random, count = 500, maxLength = InvitationBundleCodec.MAX_BYTES + 16) +
            Fuzz.randomInputs(random, count = 200, maxLength = 64).map { Mutant(it.description + " after the header", encoded.copyOf(5) + it.bytes) }

        decodeEach(inputs, InvitationRejectedException::class, InvitationBundleCodec::decode) { input, bundle ->
            assertArrayEquals(input.description, input.bytes, InvitationBundleCodec.encode(bundle))
        }
    }

    /** What the relay holds in a mailbox is untrusted; so is what a sealed bundle decrypts to. */
    @Test
    fun relayedBlobsAndSealedPlaintextsAreRejectedCleanly() {
        val random = Random(505)
        val published = InvitationV2.publish(bundleFor(TestSignalDevice()), secure)
        val secret = Base64.getUrlDecoder().decode(published.code.encode().removePrefix(InvitationV2.PREFIX)).copyOfRange(1, 33)

        // Blobs that do not match the commitment are never opened.
        decodeEach(Fuzz.sampledMutants(published.sealedBundle, random, 500) + Fuzz.randomInputs(random, 200, 5_000), InvitationRejectedException::class, { blob ->
            InvitationV2.open(published.code, listOf(blob), NOW)
        }) { input, _ -> fail("${input.description}: a blob without the committed digest was opened") }

        // A code committing to a damaged blob: the seal must catch every change.
        decodeEach(Fuzz.sampledMutants(published.sealedBundle, random, 300), InvitationRejectedException::class, { blob ->
            InvitationV2.open(InvitationCode(secret, InvitationV2.sealedBundleDigest(blob)), listOf(blob), NOW)
        }) { input, _ -> fail("${input.description}: a damaged sealed bundle was opened") }

        // Correctly sealed but damaged plaintexts reach the bundle decoder, which must hold.
        val code = InvitationCode(secret, ByteArray(32))
        val plaintext = InvitationBundleCodec.encode(bundleFor(TestSignalDevice()))
        val genuine = sealRaw(code.sealKey(), plaintext)
        val opened = InvitationV2.open(InvitationCode(secret, InvitationV2.sealedBundleDigest(genuine)), listOf(genuine), NOW)
        assertArrayEquals("the test's own sealing matches the app's", plaintext, InvitationBundleCodec.encode(opened))
        decodeEach(Fuzz.sampledMutants(plaintext, random, 500), InvitationRejectedException::class, { bytes ->
            val sealed = sealRaw(code.sealKey(), bytes)
            InvitationV2.open(InvitationCode(secret, InvitationV2.sealedBundleDigest(sealed)), listOf(sealed), NOW)
        }) { input, bundle -> assertArrayEquals(input.description, input.bytes, InvitationBundleCodec.encode(bundle)) }
    }

    @Test
    fun preKeyHeaderReaderRejectsCleanly() {
        val random = Random(606)
        val alice = TestSignalDevice()
        val bob = TestSignalDevice()
        bob.sessions.startSession(ALICE, alice.invitations.create().toPreKeyBundle())
        val preKey = bob.sessions.encrypt(ALICE, ByteArray(160)).bytes
        decodeEach(
            Fuzz.mutants(preKey, random, boundaryValuesPerPosition = 1) + Fuzz.randomInputs(random, 1_000, 3_000),
            SignalSessionException::class,
            SignalSessions::readPreKeyHeader,
        )
    }

    /**
     * libsignal authenticates everything that matters, but a PreKey message has outer fields that
     * are not covered by its MAC. So a changed message may still decrypt, but only to the original
     * plaintext; anything else must be a [SignalSessionException], and no failed attempt may damage
     * the session (the original still decrypts afterwards).
     */
    @Test
    fun sessionDecryptAuthenticatesOrRejectsCleanly() {
        val random = Random(707)
        val aliceRecords = InMemorySignalRecords().also { LocalSignalIdentityProvisioner(it).provisionIfAbsent() }
        val alice = TestSignalDevice(aliceRecords)
        val bob = TestSignalDevice()
        bob.sessions.startSession(ALICE, alice.invitations.create().toPreKeyBundle())
        val first = bob.sessions.encrypt(ALICE, "first".toByteArray())
        assertEquals(CiphertextMessage.PREKEY_TYPE, first.type)

        expectOnlyOriginalOrRejection(alice, aliceRecords, "first", first.mutated(Fuzz.sampledMutants(first.bytes, random, 400)))
        assertArrayEquals("first".toByteArray(), alice.sessions.decrypt(BOB, first))

        bob.sessions.decrypt(ALICE, alice.sessions.encrypt(BOB, "reply".toByteArray()))
        val second = bob.sessions.encrypt(ALICE, "second".toByteArray())
        assertEquals(CiphertextMessage.WHISPER_TYPE, second.type)
        expectOnlyOriginalOrRejection(alice, aliceRecords, "second", second.mutated(Fuzz.mutants(second.bytes, random, boundaryValuesPerPosition = 2)))
        // Type confusion: each message's bytes under the other libsignal type, and an unknown type.
        expectOnlyOriginalOrRejection(
            alice,
            aliceRecords,
            "second",
            sequenceOf(
                "Whisper bytes as PreKey" to SignalCiphertext(CiphertextMessage.PREKEY_TYPE, second.bytes),
                "PreKey bytes as Whisper" to SignalCiphertext(CiphertextMessage.WHISPER_TYPE, first.bytes),
                "unknown type 1" to SignalCiphertext(1, second.bytes),
            ),
        )
        assertArrayEquals("second".toByteArray(), alice.sessions.decrypt(BOB, second))
    }

    /**
     * Stored records come from the vault, so their damage is a local fault (DEC-023), never a
     * message verdict: it may only surface as [StoredRecordCorruptedException], or parse.
     */
    @Test
    fun damagedStoredRecordsAreLocalFaults() {
        val random = Random(808)
        val records = InMemorySignalRecords().also { LocalSignalIdentityProvisioner(it).provisionIfAbsent() }
        val device = TestSignalDevice(records)
        val peer = TestSignalDevice()
        val preKeys = device.invitations.create()
        peer.sessions.startSession(ALICE, preKeys.toPreKeyBundle())
        device.sessions.decrypt(BOB, peer.sessions.encrypt(ALICE, ByteArray(160)))
        val address = StoreAddress(BOB, SignalSessions.DEVICE_ID)
        val libsignalAddress = SignalProtocolAddress(BOB, SignalSessions.DEVICE_ID)

        val genuineSession = records.loadSessionRecord(address)!!
        val genuineIdentity = records.loadIdentityRecord(address)!!
        val genuineSigned = records.loadSignedPreKeyRecord(preKeys.signedPreKeyId)!!
        val genuineLocal = records.loadLocalIdentity()!!.serializedIdentityKeyPair
        val other = TestSignalDevice()
        val otherKeys = other.invitations.create()
        val genuinePreKey = other.store.loadPreKey(otherKeys.oneTimePreKeyId).serialize()
        val genuineKyber = other.store.loadKyberPreKey(otherKeys.kyberPreKeyId).serialize()

        fun inputs(genuine: ByteArray) =
            Fuzz.sampledMutants(genuine, random, 150) + Fuzz.randomInputs(random, 150, 512)

        decodeEach(inputs(genuineSession), StoredRecordCorruptedException::class, { bytes ->
            records.saveSessionRecord(address, bytes)
            device.store.loadSession(libsignalAddress)
        })
        decodeEach(inputs(genuineIdentity), StoredRecordCorruptedException::class, { bytes ->
            records.saveIdentityRecord(address, bytes)
            device.store.getIdentity(libsignalAddress)
        })
        decodeEach(inputs(genuineSigned), StoredRecordCorruptedException::class, { bytes ->
            records.saveSignedPreKeyRecord(77, bytes)
            device.store.loadSignedPreKey(77)
        })
        decodeEach(inputs(genuinePreKey), StoredRecordCorruptedException::class, { bytes ->
            records.savePreKeyRecord(78, bytes)
            device.store.loadPreKey(78)
        })
        decodeEach(inputs(genuineKyber), StoredRecordCorruptedException::class, { bytes ->
            records.saveKyberPreKeyRecord(79, bytes, lastResort = false)
            device.store.loadKyberPreKey(79)
        })
        decodeEach(inputs(genuineLocal).filter { it.bytes.isNotEmpty() }, StoredRecordCorruptedException::class, { bytes ->
            val fresh = InMemorySignalRecords().also { it.createLocalIdentity(SignalLocalIdentityRecord(1, bytes)) }
            EncryptedSignalProtocolStore(fresh, fresh, fresh, fresh, fresh, fresh).getIdentityKeyPair()
        })
    }

    @Test
    fun queueIdsAndRandomIdsAcceptOnlyTheirExactForms() {
        val random = Random(909)
        val genuine = RelayQueueCredentials.generate(secure).queueId
        for ((description, candidate) in textMutants(genuine, random, prefix = "q3-")) {
            if (RelayQueueCredentials.isValidQueueId(candidate)) {
                // Checked without a regular expression: exactly "q3-" and 43 ASCII base64url characters.
                val body = candidate.removePrefix("q3-")
                assertTrue(description, candidate.startsWith("q3-") && body.length == 43)
                assertTrue(description, body.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' })
            }
        }
        val hex = RandomId.random(secure).toHex()
        for ((description, candidate) in textMutants(hex, random, prefix = "")) {
            val id = try {
                RandomId.fromHex(candidate)
            } catch (_: MalformedV2Exception) {
                continue
            }
            assertEquals(description, candidate, id.toHex())
        }
    }

    // --- helpers ---

    /** A valid voice note (DEC-036) for the payload corpus. */
    private fun voiceNote(once: Boolean = false): app.securemessenger.signalstore.V2VoiceNote {
        val key = ByteArray(VoiceMedia.KEY_BYTES).also(secure::nextBytes)
        val sealed = VoiceMedia.seal(ByteArray(60_000).also(secure::nextBytes), key)
        return app.securemessenger.signalstore.V2VoiceNote(
            queueReadSecret = RelayQueueCredentials.generate(secure).readSecret(),
            key = key,
            digest = sealed.digest,
            plaintextSize = sealed.plaintextSize,
            chunkCount = sealed.pieces.size,
            durationMillis = 15_000,
            waveform = ByteArray(VoiceMedia.WAVEFORM_MAX_SAMPLES) { (it % 32).toByte() },
            codec = VoiceMedia.CODEC_AAC_ADTS,
            once = once,
        )
    }

    private fun SignalCiphertext.mutated(mutants: Sequence<Mutant>): Sequence<Pair<String, SignalCiphertext>> =
        mutants.map { it.description to SignalCiphertext(type, it.bytes) }

    /** Each candidate is tried on the same session state, which is restored afterwards. */
    private fun expectOnlyOriginalOrRejection(
        receiver: TestSignalDevice,
        records: InMemorySignalRecords,
        expected: String,
        candidates: Sequence<Pair<String, SignalCiphertext>>,
    ) {
        for ((description, ciphertext) in candidates) {
            val restore = records.snapshot()
            try {
                val plaintext = receiver.sessions.decrypt(BOB, ciphertext)
                assertArrayEquals("$description decrypted to something else", expected.toByteArray(), plaintext)
            } catch (_: SignalSessionException) {
                // Rejected: nothing was stored.
            } catch (failure: Throwable) {
                fail("$description: unexpected ${failure::class.java.name}")
            } finally {
                restore()
            }
        }
    }

    /** Character-level mutants of a text: substitutions, deletions, insertions, truncations. */
    private fun textMutants(text: String, random: Random, prefix: String = InvitationV2.PREFIX): Sequence<Pair<String, String>> = sequence {
        val probes = listOf(
            'A', 'B', 'Q', 'a', 'b', 'z', 'Z', '0', '9', '-', '_', '+', '/', '=', ' ', '\t', '\n', '.', ':',
            'é', 'Я', '\u0000', '‮', 'Ａ', '\uD83D',
        )
        for (position in text.indices) {
            for (probe in probes) {
                if (text[position] != probe) {
                    yield("character $position = U+%04X".format(probe.code) to text.substring(0, position) + probe + text.substring(position + 1))
                }
            }
            yield("delete character $position" to text.removeRange(position, position + 1))
            val inserted = probes[random.nextInt(probes.size)]
            yield("insert U+%04X at $position".format(inserted.code) to text.substring(0, position) + inserted + text.substring(position))
            yield("truncate to $position" to text.substring(0, position))
        }
        yield("upper case" to text.uppercase())
        yield("lower case" to text.lowercase())
        yield("leading space" to " $text")
        yield("trailing newline" to "$text\n")
        yield("doubled" to text + text)
        yield("overlong" to text + "A".repeat(300))
        if (prefix.isNotEmpty()) {
            yield("prefix only" to prefix)
            yield("other prefix" to "smp-invite-v4:" + text.removePrefix(prefix))
            yield("no colon" to text.replaceFirst(":", ""))
        }
    }

    private fun paddedRandomBodies(random: Random): Sequence<Mutant> = sequence {
        val header = byteArrayOf(0x53, 0x4D, 0x49, 0x32, 1)
        for (index in 0 until Fuzz.budget(1_500)) {
            val type = 1 + index % 5
            val body = header + byteArrayOf(type.toByte()) + ByteArray(random.nextInt(120)).also(random::nextBytes)
            val paddedSize = (body.size / InnerPayloadV2Codec.PADDING_BLOCK_BYTES + 1) * InnerPayloadV2Codec.PADDING_BLOCK_BYTES
            yield(Mutant("padded random body $index of type $type", body.copyOf(paddedSize).also { it[body.size] = 0x80.toByte() }))
        }
    }

    private fun unpadded(padded: ByteArray): ByteArray = padded.copyOf(padded.indexOfLast { it != 0.toByte() })

    /** AES-256-GCM exactly as `InvitationV2.seal`, for plaintexts the app itself would never produce. */
    private fun sealRaw(key: ByteArray, plaintext: ByteArray): ByteArray {
        val nonce = ByteArray(12).also(secure::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD("smp-invite-v3/sealed-bundle".toByteArray())
        return byteArrayOf(1) + nonce + cipher.doFinal(plaintext)
    }

    private fun bundleFor(device: TestSignalDevice) = InvitationBundle(
        preKeys = device.invitations.create(),
        receiveQueueId = RelayQueueCredentials.generate(secure).queueId,
        routingTag = RandomId.random(secure),
        expiresAtEpochMillis = NOW + InvitationV2.DEFAULT_LIFETIME_MILLIS,
    )

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val ALICE = "contact-alice"
        const val BOB = "contact-bob"
        val QUEUE = RelayQueueCredentials.generate().queueId
    }
}
