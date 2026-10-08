package app.securemessenger.signalprotocol

import java.nio.ByteBuffer
import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.libsignal.protocol.message.CiphertextMessage

class EnvelopeV2Test {
    private val random = SecureRandom()
    private val tag = RandomId.random(random)

    @Test
    fun textTravelsThroughSessionAndEnvelopeWithItsInnerId() {
        val alice = TestSignalDevice()
        val bob = TestSignalDevice()
        bob.sessions.startSession(ALICE_ON_BOB, alice.invitations.create().toPreKeyBundle())
        val sent = InnerPayloadV2.Text(RandomId.random(random), NOW, "привет, Алиса")

        val envelopeBytes = EnvelopeV2Codec.encode(
            EnvelopeV2(tag, bob.sessions.encrypt(ALICE_ON_BOB, InnerPayloadV2Codec.encode(sent))),
        )
        assertFalse(String(envelopeBytes, Charsets.UTF_8).contains("привет"))

        val envelope = EnvelopeV2Codec.decode(envelopeBytes)
        assertEquals(tag, envelope.routingTag)
        assertEquals(CiphertextMessage.PREKEY_TYPE, envelope.ciphertext.type)
        val received = InnerPayloadV2Codec.decode(alice.sessions.decrypt(BOB_ON_ALICE, envelope.ciphertext))
        received as InnerPayloadV2.Text
        assertEquals(sent.messageId, received.messageId)
        assertEquals(NOW, received.sentAtEpochMillis)
        assertEquals("привет, Алиса", received.text)
    }

    @Test
    fun everyPayloadTypeRoundTripsAndIsPaddedToWholeBlocks() {
        val payloads = listOf(
            InnerPayloadV2.Hello(RandomId.random(random), NOW, BOB_QUEUE, RandomId.random(random), "Bob"),
            InnerPayloadV2.Hello(RandomId.random(random), NOW, BOB_QUEUE, RandomId.random(random), ""),
            InnerPayloadV2.Accept(RandomId.random(random), NOW),
            InnerPayloadV2.Text(RandomId.random(random), NOW, "x"),
            InnerPayloadV2.Text(RandomId.random(random), NOW, "y".repeat(InnerPayloadV2Codec.MAX_TEXT_BYTES)),
            InnerPayloadV2.Text(RandomId.random(random), NOW, "ответ", replyTo = RandomId.random(random)),
            InnerPayloadV2.Receipt(RandomId.random(random), NOW, RandomId.random(random)),
        )
        payloads.forEach { payload ->
            val encoded = InnerPayloadV2Codec.encode(payload)
            assertEquals(0, encoded.size % InnerPayloadV2Codec.PADDING_BLOCK_BYTES)
            val decoded = InnerPayloadV2Codec.decode(encoded)
            assertEquals(payload::class, decoded::class)
            assertEquals(payload.messageId, decoded.messageId)
            assertArrayEquals(encoded, InnerPayloadV2Codec.encode(decoded))
        }
        // Short texts of different lengths are indistinguishable by size.
        assertEquals(
            InnerPayloadV2Codec.encode(InnerPayloadV2.Text(RandomId.random(random), NOW, "a")).size,
            InnerPayloadV2Codec.encode(InnerPayloadV2.Text(RandomId.random(random), NOW, "a".repeat(100))).size,
        )
    }

    @Test
    fun payloadDecoderRejectsDamageAndUnsafeNames() {
        val valid = InnerPayloadV2Codec.encode(InnerPayloadV2.Accept(RandomId.random(random), NOW))
        expectMalformedPayload(ByteArray(0))
        expectMalformedPayload(ByteArray(InnerPayloadV2Codec.PADDING_BLOCK_BYTES))
        expectMalformedPayload(valid.copyOf(valid.size - 1))
        expectMalformedPayload(valid.copyOf().also { it[it.size - 1] = 1 })
        expectMalformedPayload(valid.copyOf().also { it[0] = 0 })
        expectMalformedPayload(valid.copyOf().also { it[4] = 2 })
        expectMalformedPayload(valid.copyOf().also { it[5] = 9 })
        // Accept carries nothing after the header: an extra byte before the padding is trailing.
        val acceptWithExtra = valid.copyOf().also { bytes ->
            val marker = bytes.indexOfLast { it != 0.toByte() }
            bytes[marker] = 7
            bytes[marker + 1] = 0x80.toByte()
        }
        expectMalformedPayload(acceptWithExtra)
        // Timestamp zero.
        expectMalformedPayload(valid.copyOf().also { it.fill(0, 22, 30) })

        val invalidUtf8 = InnerPayloadV2Codec.encode(InnerPayloadV2.Text(RandomId.random(random), NOW, "ab"))
            .also { it[30 + 4] = 0xC3.toByte(); it[30 + 5] = 0x28 }
        expectMalformedPayload(invalidUtf8)

        // Phase 5 (I3): zero-width space, line separator, byte-order mark, a tag character (a
        // supplementary code point), and an unpaired surrogate are refused too.
        listOf(
            "Bob‮evil", "tab\there", "new\nline", "x".repeat(InnerPayloadV2Codec.MAX_DISPLAY_NAME_BYTES + 1),
            "Bo​b", "Bob ", "﻿Bob", "Bob󠁁", "Bob\uD83D",
        ).forEach { name ->
                assertFalse(InnerPayloadV2Codec.isAcceptableDisplayName(name))
                assertThrows(IllegalArgumentException::class.java) {
                    InnerPayloadV2Codec.encode(
                        InnerPayloadV2.Hello(RandomId.random(random), NOW, BOB_QUEUE, RandomId.random(random), name),
                    )
                }
            }
        // Ordinary names stay possible, emoji joined by the zero-width joiner included.
        listOf("Алиса", "Bob Smith", "👨‍👩‍👧 Семья", "李雷").forEach { name ->
            assertTrue(name, InnerPayloadV2Codec.isAcceptableDisplayName(name))
        }
        assertThrows(IllegalArgumentException::class.java) {
            InnerPayloadV2Codec.encode(InnerPayloadV2.Text(RandomId.random(random), NOW, ""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            InnerPayloadV2Codec.encode(
                InnerPayloadV2.Text(RandomId.random(random), NOW, "y".repeat(InnerPayloadV2Codec.MAX_TEXT_BYTES + 1)),
            )
        }
    }

    /**
     * DEC-035: a reply is type 5, a text followed by the 16-byte ID it answers. A plain text keeps
     * type 3 and exactly its old bytes, so nothing changes for an app that predates replies.
     */
    @Test
    fun aReplyCarriesTheIdItAnswersAndAPlainTextKeepsItsOldBytes() {
        val messageId = RandomId.random(random)
        val answered = RandomId.random(random)
        val plain = InnerPayloadV2Codec.encode(InnerPayloadV2.Text(messageId, NOW, "ok"))
        val reply = InnerPayloadV2Codec.encode(InnerPayloadV2.Text(messageId, NOW, "ok", replyTo = answered))

        assertEquals(3, plain[5].toInt())
        assertEquals(5, reply[5].toInt())
        val textField = byteArrayOf(0, 0, 0, 2) + "ok".toByteArray()
        assertArrayEquals(textField + byteArrayOf(0x80.toByte()), plain.copyOfRange(30, 30 + textField.size + 1))
        assertArrayEquals(textField + answered.toByteArray() + byteArrayOf(0x80.toByte()), reply.copyOfRange(30, 30 + textField.size + 17))

        val decoded = InnerPayloadV2Codec.decode(reply) as InnerPayloadV2.Text
        assertEquals(answered, decoded.replyTo)
        assertEquals("ok", decoded.text)
        assertArrayEquals(reply, InnerPayloadV2Codec.encode(decoded))
        assertNull((InnerPayloadV2Codec.decode(plain) as InnerPayloadV2.Text).replyTo)
    }

    @Test
    fun aDamagedReplyIsRejected() {
        val messageId = RandomId.random(random)
        val reply = InnerPayloadV2Codec.encode(InnerPayloadV2.Text(messageId, NOW, "ok", RandomId.random(random)))
        val idStart = 30 + 4 + 2
        // Cut inside the ID it answers: the padding marker comes eight bytes too early.
        expectMalformedPayload(reply.copyOf().also { bytes -> bytes[idStart + 8] = 0x80.toByte(); bytes.fill(0, idStart + 9, bytes.size) })
        // A plain text relabelled as a reply has no ID to read.
        val plain = InnerPayloadV2Codec.encode(InnerPayloadV2.Text(messageId, NOW, "ok"))
        expectMalformedPayload(plain.copyOf().also { it[5] = 5 })
        // A text cannot answer itself.
        expectMalformedPayload(reply.copyOf().also { messageId.toByteArray().copyInto(it, idStart) })
        assertThrows(IllegalArgumentException::class.java) {
            InnerPayloadV2Codec.encode(InnerPayloadV2.Text(messageId, NOW, "ok", messageId))
        }
    }

    /** DEC-036: a voice note travels as type 6, and only with fields within the rules. */
    @Test
    fun aVoiceNoteRoundTripsAndABrokenOneIsRejected() {
        val note = voiceNote()
        val encoded = InnerPayloadV2Codec.encode(InnerPayloadV2.Voice(RandomId.random(random), NOW, note))
        assertEquals(6, encoded[5].toInt())
        val decoded = InnerPayloadV2Codec.decode(encoded) as InnerPayloadV2.Voice
        assertEquals(note, decoded.note)
        assertNull(decoded.replyTo)
        assertArrayEquals(encoded, InnerPayloadV2Codec.encode(decoded))

        // An answer to another message carries its inner ID after the note (DEC-035 with DEC-036).
        val answered = RandomId.random(random)
        val reply = InnerPayloadV2Codec.encode(InnerPayloadV2.Voice(RandomId.random(random), NOW, note, answered))
        assertEquals(answered, (InnerPayloadV2Codec.decode(reply) as InnerPayloadV2.Voice).replyTo)

        // A one-time recording says so; nothing else changes.
        val once = InnerPayloadV2Codec.encode(InnerPayloadV2.Voice(RandomId.random(random), NOW, voiceNote(once = true)))
        assertTrue((InnerPayloadV2Codec.decode(once) as InnerPayloadV2.Voice).note.once)
        assertFalse(decoded.note.once)

        // The last bytes before the padding marker: the codec, the options, then the reply marker (0 here).
        val marker = encoded.indexOfLast { it != 0.toByte() }
        // An unknown codec.
        expectMalformedPayload(encoded.copyOf().also { it[marker - 3] = 2 })
        // An option this app does not know.
        expectMalformedPayload(encoded.copyOf().also { it[marker - 2] = 2 })
        expectMalformedPayload(encoded.copyOf().also { it[marker - 2] = 0x81.toByte() })
        // A reply marker other than 0 or 1, and a 1 with no ID behind it.
        expectMalformedPayload(encoded.copyOf().also { it[marker - 1] = 2 })
        expectMalformedPayload(encoded.copyOf().also { it[marker - 1] = 1 })
        // A message that answers itself.
        val self = RandomId.random(random)
        assertThrows(IllegalArgumentException::class.java) {
            InnerPayloadV2Codec.encode(InnerPayloadV2.Voice(self, NOW, note, self))
        }
        // A number of pieces that does not fit the size (after the 30-byte header, three 32-byte values and the size).
        expectMalformedPayload(encoded.copyOf().also { it[30 + 96 + 4] = 9 })
        // Cut short inside the key.
        expectMalformedPayload(encoded.copyOf().also { bytes -> bytes[40] = 0x80.toByte(); bytes.fill(0, 41, bytes.size) })
        assertThrows(IllegalArgumentException::class.java) {
            InnerPayloadV2Codec.encode(InnerPayloadV2.Voice(RandomId.random(random), NOW, voiceNote(durationMillis = 0)))
        }
    }

    /** DEC-036 and DEC-037: "played" names one message; "delete for both" 1 to 100 distinct ones, never itself. */
    @Test
    fun playedAndDeleteRoundTripAndBrokenOnesAreRejected() {
        val original = RandomId.random(random)
        val played = InnerPayloadV2Codec.decode(InnerPayloadV2Codec.encode(InnerPayloadV2.Played(RandomId.random(random), NOW, original)))
        assertEquals(original, (played as InnerPayloadV2.Played).originalMessageId)

        val targets = List(InnerPayloadV2Codec.MAX_DELETE_TARGETS) { RandomId.random(random) }
        val delete = InnerPayloadV2Codec.encode(InnerPayloadV2.Delete(RandomId.random(random), NOW, targets))
        assertEquals(targets, (InnerPayloadV2Codec.decode(delete) as InnerPayloadV2.Delete).targets)

        val one = InnerPayloadV2Codec.encode(InnerPayloadV2.Delete(RandomId.random(random), NOW, listOf(original)))
        // The count is the first byte after the 30-byte header.
        expectMalformedPayload(one.copyOf().also { it[30] = 0 })
        expectMalformedPayload(one.copyOf().also { it[30] = 2 })
        val self = RandomId.random(random)
        listOf(emptyList(), List(InnerPayloadV2Codec.MAX_DELETE_TARGETS + 1) { RandomId.random(random) }, listOf(original, original), listOf(self)).forEach { bad ->
            assertThrows(IllegalArgumentException::class.java) { InnerPayloadV2Codec.encode(InnerPayloadV2.Delete(self, NOW, bad)) }
        }
    }

    private fun voiceNote(durationMillis: Int = 7_000, once: Boolean = false): app.securemessenger.signalstore.V2VoiceNote {
        val key = ByteArray(VoiceMedia.KEY_BYTES).also(random::nextBytes)
        val sealed = VoiceMedia.seal(ByteArray(1_000).also(random::nextBytes), key)
        return app.securemessenger.signalstore.V2VoiceNote(
            queueReadSecret = app.securemessenger.domain.RelayQueueCredentials.generate(random).readSecret(),
            key = key,
            digest = sealed.digest,
            plaintextSize = sealed.plaintextSize,
            chunkCount = sealed.pieces.size,
            durationMillis = durationMillis,
            waveform = ByteArray(30) { (it % 32).toByte() },
            codec = VoiceMedia.CODEC_AAC_ADTS,
            once = once,
        )
    }

    @Test
    fun envelopeRoundTripsAndRejectsLegacyAndDamagedBytes() {
        val ciphertext = SignalCiphertext(CiphertextMessage.WHISPER_TYPE, ByteArray(90) { it.toByte() })
        val bytes = EnvelopeV2Codec.encode(EnvelopeV2(tag, ciphertext))
        val decoded = EnvelopeV2Codec.decode(bytes)
        assertEquals(tag, decoded.routingTag)
        assertEquals(CiphertextMessage.WHISPER_TYPE, decoded.ciphertext.type)
        assertArrayEquals(ciphertext.bytes, decoded.ciphertext.bytes)

        // The legacy envelope starts with the 32-bit version 1.
        val legacyShaped = ByteBuffer.allocate(64).putInt(1).array()
        expectMalformedEnvelope(legacyShaped)
        expectMalformedEnvelope(bytes.copyOf(bytes.size - 1))
        expectMalformedEnvelope(bytes + byteArrayOf(0))
        expectMalformedEnvelope(bytes.copyOf().also { it[4] = 2 })
        expectMalformedEnvelope(bytes.copyOf().also { it[21] = 1 })
        expectMalformedEnvelope(bytes.copyOf().also { it[21] = 4 })
        expectMalformedEnvelope(bytes.copyOf().also { it.fill(0, 22, 26) })
        expectMalformedEnvelope(bytes.copyOf().also { ByteBuffer.wrap(it, 22, 4).putInt(EnvelopeV2Codec.MAX_CIPHERTEXT_BYTES + 1) })
        assertThrows(IllegalArgumentException::class.java) {
            EnvelopeV2Codec.encode(EnvelopeV2(tag, SignalCiphertext(1, ByteArray(10))))
        }
    }

    @Test
    fun envelopeDigestIsDeterministicAndBindsEveryByte() {
        val bytes = EnvelopeV2Codec.encode(EnvelopeV2(tag, SignalCiphertext(CiphertextMessage.WHISPER_TYPE, ByteArray(90))))
        assertArrayEquals(EnvelopeV2Codec.digest(bytes), EnvelopeV2Codec.digest(bytes.copyOf()))
        val changed = bytes.copyOf().also { it[it.size - 1] = 1 }
        assertFalse(EnvelopeV2Codec.digest(bytes).contentEquals(EnvelopeV2Codec.digest(changed)))
        assertEquals(32, EnvelopeV2Codec.digest(bytes).size)
    }

    @Test
    fun randomIdsRoundTripThroughHexAndNeverPrintTheirValue() {
        val id = RandomId.random(random)
        assertEquals(id, RandomId.fromHex(id.toHex()))
        assertEquals(id, RandomId.fromBytes(id.toByteArray()))
        assertFalse(id.toString().contains(id.toHex()))
        assertTrue(id.toHex().length == 32)
        listOf("", "zz".repeat(16), "A".repeat(32), "0".repeat(31)).forEach { hex ->
            assertThrows(MalformedV2Exception::class.java) { RandomId.fromHex(hex) }
        }
    }

    private fun expectMalformedPayload(bytes: ByteArray) {
        assertThrows(MalformedV2Exception::class.java) { InnerPayloadV2Codec.decode(bytes) }
    }

    private fun expectMalformedEnvelope(bytes: ByteArray) {
        assertThrows(MalformedV2Exception::class.java) { EnvelopeV2Codec.decode(bytes) }
    }

    private companion object {
        /** A self-certifying queue ID (DEC-024); HELLO refuses any other form. */
        val BOB_QUEUE = app.securemessenger.domain.RelayQueueCredentials.generate().queueId
        const val NOW = 1_800_000_000_000L
        const val ALICE_ON_BOB = "contact-alice-on-bob"
        const val BOB_ON_ALICE = "contact-bob-on-alice"
    }
}
