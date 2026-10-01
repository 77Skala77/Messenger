package app.securemessenger.signalprotocol

import org.signal.libsignal.protocol.message.CiphertextMessage

/**
 * Relay-visible v2 envelope (DEC-019 T3): a recipient-assigned routing tag and one libsignal
 * ciphertext. It carries no sender identifier; the tag tells the recipient which contact (or,
 * for a HELLO, which invitation) the message belongs to, so no trial decryption is needed.
 */
class EnvelopeV2(val routingTag: RandomId, val ciphertext: SignalCiphertext)

/**
 * Binary format: `"SME2"`, version 1, 16-byte routing tag, libsignal type (2 or 3), 32-bit length
 * and the ciphertext; nothing else. The magic never equals the legacy envelope's leading version
 * integer (1), so neither decoder accepts the other's bytes.
 */
object EnvelopeV2Codec {
    const val MAX_CIPHERTEXT_BYTES = 128 * 1024
    private val MAGIC = byteArrayOf(0x53, 0x4D, 0x45, 0x32) // "SME2"
    private const val VERSION = 1
    private val DIGEST_DOMAIN = "smp-envelope-v2/digest\u0000".toByteArray()

    fun encode(envelope: EnvelopeV2): ByteArray {
        require(envelope.ciphertext.type in SIGNAL_TYPES)
        require(envelope.ciphertext.bytes.size in 1..MAX_CIPHERTEXT_BYTES)
        return V2Writer()
            .raw(MAGIC)
            .u8(VERSION)
            .raw(envelope.routingTag.toByteArray())
            .u8(envelope.ciphertext.type)
            .field32(envelope.ciphertext.bytes)
            .toByteArray()
    }

    fun decode(bytes: ByteArray): EnvelopeV2 {
        val reader = V2Reader(bytes)
        reader.expect(MAGIC)
        if (reader.u8() != VERSION) throw MalformedV2Exception("Unsupported envelope version")
        val routingTag = RandomId.fromBytes(reader.raw(RandomId.SIZE_BYTES))
        val type = reader.u8()
        if (type !in SIGNAL_TYPES) throw MalformedV2Exception("Unsupported ciphertext type")
        val ciphertext = reader.field32(1, MAX_CIPHERTEXT_BYTES)
        reader.requireEnd()
        return EnvelopeV2(routingTag, SignalCiphertext(type, ciphertext))
    }

    /**
     * Digest of the exact relayed bytes. Stored with an accepted message in the same transaction,
     * so a byte-identical re-delivery is recognised and deleted without decrypting (DEC-019 T2).
     */
    fun digest(envelopeBytes: ByteArray): ByteArray = sha256(DIGEST_DOMAIN, envelopeBytes)

    private val SIGNAL_TYPES = setOf(CiphertextMessage.WHISPER_TYPE, CiphertextMessage.PREKEY_TYPE)
}

/**
 * Plaintext inside a libsignal message. The sender's message ID is here, authenticated by the
 * session, never taken from the relay (DEC-019 T4, DEC-020).
 */
sealed class InnerPayloadV2 {
    abstract val messageId: RandomId
    abstract val sentAtEpochMillis: Long

    /** First message of a session: where and under which tag the initiator receives. */
    class Hello(
        override val messageId: RandomId,
        override val sentAtEpochMillis: Long,
        val receiveQueueId: String,
        val routingTag: RandomId,
        /** Self-asserted and unverified; shown only as a claim in the contact request. */
        val displayName: String,
    ) : InnerPayloadV2()

    /** The inviter accepted the contact request. */
    class Accept(
        override val messageId: RandomId,
        override val sentAtEpochMillis: Long,
    ) : InnerPayloadV2()

    class Text(
        override val messageId: RandomId,
        override val sentAtEpochMillis: Long,
        val text: String,
    ) : InnerPayloadV2()

    /** "Delivered": the receiver authenticated, decrypted, and durably stored [originalMessageId]. */
    class Receipt(
        override val messageId: RandomId,
        override val sentAtEpochMillis: Long,
        val originalMessageId: RandomId,
    ) : InnerPayloadV2()
}

/**
 * Binary format: `"SMI2"`, version 1, type, message ID, sent-at time, type-specific fields, then
 * padding (`0x80` and zeros) to a multiple of [PADDING_BLOCK_BYTES] so that ciphertext sizes do not
 * reveal exact text lengths.
 */
object InnerPayloadV2Codec {
    const val PADDING_BLOCK_BYTES = 160
    const val MAX_TEXT_BYTES = 16 * 1024
    const val MAX_DISPLAY_NAME_BYTES = 128
    private val MAGIC = byteArrayOf(0x53, 0x4D, 0x49, 0x32) // "SMI2"
    private const val VERSION = 1
    private const val TYPE_HELLO = 1
    private const val TYPE_ACCEPT = 2
    private const val TYPE_TEXT = 3
    private const val TYPE_RECEIPT = 4
    private const val PADDING_MARKER: Byte = 0x80.toByte()

    fun encode(payload: InnerPayloadV2): ByteArray {
        val writer = V2Writer()
            .raw(MAGIC)
            .u8(VERSION)
            .u8(typeOf(payload))
            .raw(payload.messageId.toByteArray())
            .i64(payload.sentAtEpochMillis)
        when (payload) {
            is InnerPayloadV2.Hello -> {
                require(isAcceptableDisplayName(payload.displayName)) { "Invalid display name" }
                writer.field16(V2Text.queueIdBytes(payload.receiveQueueId))
                    .raw(payload.routingTag.toByteArray())
                    .field16(payload.displayName.toByteArray(Charsets.UTF_8))
            }
            is InnerPayloadV2.Accept -> Unit
            is InnerPayloadV2.Text -> {
                val text = payload.text.toByteArray(Charsets.UTF_8)
                require(text.size in 1..MAX_TEXT_BYTES) { "Invalid text length" }
                writer.field32(text)
            }
            is InnerPayloadV2.Receipt -> writer.raw(payload.originalMessageId.toByteArray())
        }
        return pad(writer.toByteArray())
    }

    fun decode(padded: ByteArray): InnerPayloadV2 {
        val reader = V2Reader(unpad(padded))
        reader.expect(MAGIC)
        if (reader.u8() != VERSION) throw MalformedV2Exception("Unsupported payload version")
        val type = reader.u8()
        val messageId = RandomId.fromBytes(reader.raw(RandomId.SIZE_BYTES))
        val sentAt = reader.i64()
        if (sentAt <= 0) throw MalformedV2Exception("Invalid timestamp")
        val payload = when (type) {
            TYPE_HELLO -> {
                val queueId = V2Text.readQueueId(reader)
                val routingTag = RandomId.fromBytes(reader.raw(RandomId.SIZE_BYTES))
                val displayName = V2Text.decodeUtf8(reader.field16(0, MAX_DISPLAY_NAME_BYTES))
                if (!isAcceptableDisplayName(displayName)) throw MalformedV2Exception("Invalid display name")
                InnerPayloadV2.Hello(messageId, sentAt, queueId, routingTag, displayName)
            }
            TYPE_ACCEPT -> InnerPayloadV2.Accept(messageId, sentAt)
            TYPE_TEXT -> InnerPayloadV2.Text(messageId, sentAt, V2Text.decodeUtf8(reader.field32(1, MAX_TEXT_BYTES)))
            TYPE_RECEIPT -> InnerPayloadV2.Receipt(messageId, sentAt, RandomId.fromBytes(reader.raw(RandomId.SIZE_BYTES)))
            else -> throw MalformedV2Exception("Unsupported payload type")
        }
        reader.requireEnd()
        return payload
    }

    /**
     * A display name comes from the other person and is shown before they are trusted, so it may
     * not contain anything that could disguise it: controls, line or paragraph separators, unpaired
     * surrogates, and invisible format characters (bidirectional overrides, zero-width spaces, tags),
     * except the zero-width joiner that emoji sequences need. Checked per code point (Phase 5, I3).
     */
    fun isAcceptableDisplayName(value: String): Boolean =
        value.toByteArray(Charsets.UTF_8).size <= MAX_DISPLAY_NAME_BYTES &&
            value.codePoints().noneMatch { codePoint ->
                val type = Character.getType(codePoint)
                Character.isISOControl(codePoint) ||
                    (type == Character.FORMAT.toInt() && codePoint != ZERO_WIDTH_JOINER) ||
                    type == Character.LINE_SEPARATOR.toInt() ||
                    type == Character.PARAGRAPH_SEPARATOR.toInt() ||
                    type == Character.SURROGATE.toInt()
            }

    private fun typeOf(payload: InnerPayloadV2): Int = when (payload) {
        is InnerPayloadV2.Hello -> TYPE_HELLO
        is InnerPayloadV2.Accept -> TYPE_ACCEPT
        is InnerPayloadV2.Text -> TYPE_TEXT
        is InnerPayloadV2.Receipt -> TYPE_RECEIPT
    }

    private fun pad(bytes: ByteArray): ByteArray {
        val paddedSize = (bytes.size / PADDING_BLOCK_BYTES + 1) * PADDING_BLOCK_BYTES
        return bytes.copyOf(paddedSize).also { it[bytes.size] = PADDING_MARKER }
    }

    private fun unpad(padded: ByteArray): ByteArray {
        if (padded.isEmpty() || padded.size % PADDING_BLOCK_BYTES != 0) throw MalformedV2Exception("Invalid padding")
        val marker = padded.indexOfLast { it != 0.toByte() }
        if (marker < 0 || padded[marker] != PADDING_MARKER) throw MalformedV2Exception("Invalid padding")
        return padded.copyOf(marker)
    }

    private const val ZERO_WIDTH_JOINER = 0x200D
}
