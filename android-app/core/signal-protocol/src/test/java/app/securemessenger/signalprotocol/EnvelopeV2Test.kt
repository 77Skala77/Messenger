package app.securemessenger.signalprotocol

import java.nio.ByteBuffer
import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        listOf("Bob‮evil", "tab\there", "new\nline", "x".repeat(InnerPayloadV2Codec.MAX_DISPLAY_NAME_BYTES + 1))
            .forEach { name ->
                assertFalse(InnerPayloadV2Codec.isAcceptableDisplayName(name))
                assertThrows(IllegalArgumentException::class.java) {
                    InnerPayloadV2Codec.encode(
                        InnerPayloadV2.Hello(RandomId.random(random), NOW, BOB_QUEUE, RandomId.random(random), name),
                    )
                }
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
