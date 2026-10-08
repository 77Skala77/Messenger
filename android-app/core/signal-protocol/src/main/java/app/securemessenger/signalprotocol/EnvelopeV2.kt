package app.securemessenger.signalprotocol

import app.securemessenger.domain.RelayQueueCredentials
import app.securemessenger.signalstore.V2VoiceNote
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
        /**
         * The text this one answers, by its inner message ID (DEC-035), or null for a plain text.
         * Only the reference travels: each phone shows the quote from its own copy of the conversation.
         */
        val replyTo: RandomId? = null,
    ) : InnerPayloadV2()

    /**
     * A voice message (DEC-036): the note that leads to its recording, whose encrypted pieces wait in
     * a one-time relay queue. Only the note travels in the session; the recording never does.
     */
    class Voice(
        override val messageId: RandomId,
        override val sentAtEpochMillis: Long,
        val note: V2VoiceNote,
        /** The message this one answers, like a text's (DEC-035), or null. */
        val replyTo: RandomId? = null,
    ) : InnerPayloadV2()

    /** "Delivered": the receiver authenticated, decrypted, and durably stored [originalMessageId]. */
    class Receipt(
        override val messageId: RandomId,
        override val sentAtEpochMillis: Long,
        val originalMessageId: RandomId,
    ) : InnerPayloadV2()

    /**
     * "Played" (DEC-036, DEC-039, owner): the receiver pressed play on our voice message
     * [originalMessageId]. A one-time recording was deleted with that (DEC-036); any other is now
     * listened (DEC-039), like Telegram. Sent once per voice message, never for texts.
     */
    class Played(
        override val messageId: RandomId,
        override val sentAtEpochMillis: Long,
        val originalMessageId: RandomId,
    ) : InnerPayloadV2()

    /**
     * "Delete for both" (DEC-037, owner): the sender deleted these messages of our chat, in either
     * direction, and asks this phone to delete its copies too. At most [InnerPayloadV2Codec.MAX_DELETE_TARGETS].
     */
    class Delete(
        override val messageId: RandomId,
        override val sentAtEpochMillis: Long,
        val targets: List<RandomId>,
    ) : InnerPayloadV2()
}

/**
 * Binary format: `"SMI2"`, version 1, type, message ID, sent-at time, type-specific fields, then
 * padding (`0x80` and zeros) to a multiple of [PADDING_BLOCK_BYTES] so that ciphertext sizes do not
 * reveal exact text lengths.
 *
 * A text that answers another (type 5, DEC-035) is a text (type 3) followed by the 16-byte inner
 * message ID it answers. A plain text keeps type 3, byte for byte as before; an app that predates
 * type 5 finds it unparseable and drops it after authentication, so it fails closed.
 *
 * A voice message (type 6, DEC-036) carries its note: the one-time queue's 32-byte read secret, the
 * 32-byte key, the 32-byte digest, the recording's size (32 bits), its number of pieces (8 bits),
 * its length in milliseconds (32 bits), the loudness bars (16-bit length, at most 100 values of 0 to
 * 31), the codec (8 bits) and the options (8 bits: 1 = one-time, played once and then deleted by the
 * receiver; any other bit is refused); then 0, or 1 followed by the 16-byte inner message ID it
 * answers. An app that predates type 6 drops it the same way.
 *
 * "Played" (type 7, DEC-036; for every voice message since DEC-039) has a receipt's layout: the
 * 16-byte inner message ID of the voice message that was played (a one-time one is deleted with it).
 * "Delete for both" (type 8, DEC-037) is a count (1 to 100) and that many distinct 16-byte inner
 * message IDs of the chat, never its own.
 */
object InnerPayloadV2Codec {
    const val PADDING_BLOCK_BYTES = 160
    const val MAX_TEXT_BYTES = 16 * 1024

    /** At most this many messages in one "delete for both" (DEC-037): more go in several. */
    const val MAX_DELETE_TARGETS = 100
    const val MAX_DISPLAY_NAME_BYTES = 128
    private val MAGIC = byteArrayOf(0x53, 0x4D, 0x49, 0x32) // "SMI2"
    private const val VERSION = 1
    private const val TYPE_HELLO = 1
    private const val TYPE_ACCEPT = 2
    private const val TYPE_TEXT = 3
    private const val TYPE_RECEIPT = 4
    private const val TYPE_TEXT_REPLY = 5
    private const val TYPE_VOICE = 6
    private const val TYPE_PLAYED = 7
    private const val TYPE_DELETE = 8
    private const val NO_REPLY = 0
    private const val WITH_REPLY = 1

    /** A voice option: the receiving phone plays the recording once, then deletes it (DEC-036). */
    private const val OPTION_ONCE = 1
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
                require(payload.replyTo != payload.messageId) { "A text cannot answer itself" }
                writer.field32(text)
                payload.replyTo?.let { writer.raw(it.toByteArray()) }
            }
            is InnerPayloadV2.Voice -> {
                val note = payload.note
                require(VoiceMedia.isAcceptable(note)) { "Invalid voice note" }
                require(payload.replyTo != payload.messageId) { "A message cannot answer itself" }
                writer.raw(note.queueReadSecret)
                    .raw(note.key)
                    .raw(note.digest)
                    .i32(note.plaintextSize)
                    .u8(note.chunkCount)
                    .i32(note.durationMillis)
                    .field16(note.waveform)
                    .u8(note.codec)
                    .u8(if (note.once) OPTION_ONCE else 0)
                    .u8(if (payload.replyTo == null) NO_REPLY else WITH_REPLY)
                payload.replyTo?.let { writer.raw(it.toByteArray()) }
            }
            is InnerPayloadV2.Receipt -> writer.raw(payload.originalMessageId.toByteArray())
            is InnerPayloadV2.Played -> writer.raw(payload.originalMessageId.toByteArray())
            is InnerPayloadV2.Delete -> {
                require(payload.targets.size in 1..MAX_DELETE_TARGETS) { "Invalid number of messages to delete" }
                require(payload.targets.distinct().size == payload.targets.size) { "A message named twice" }
                require(payload.messageId !in payload.targets) { "A request cannot delete itself" }
                writer.u8(payload.targets.size)
                payload.targets.forEach { writer.raw(it.toByteArray()) }
            }
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
            TYPE_TEXT_REPLY -> {
                val text = V2Text.decodeUtf8(reader.field32(1, MAX_TEXT_BYTES))
                val replyTo = RandomId.fromBytes(reader.raw(RandomId.SIZE_BYTES))
                if (replyTo == messageId) throw MalformedV2Exception("A text cannot answer itself")
                InnerPayloadV2.Text(messageId, sentAt, text, replyTo)
            }
            TYPE_RECEIPT -> InnerPayloadV2.Receipt(messageId, sentAt, RandomId.fromBytes(reader.raw(RandomId.SIZE_BYTES)))
            TYPE_PLAYED -> InnerPayloadV2.Played(messageId, sentAt, RandomId.fromBytes(reader.raw(RandomId.SIZE_BYTES)))
            TYPE_DELETE -> {
                val count = reader.u8()
                if (count !in 1..MAX_DELETE_TARGETS) throw MalformedV2Exception("Invalid number of messages to delete")
                val targets = List(count) { RandomId.fromBytes(reader.raw(RandomId.SIZE_BYTES)) }
                if (targets.distinct().size != count) throw MalformedV2Exception("A message named twice")
                if (messageId in targets) throw MalformedV2Exception("A request cannot delete itself")
                InnerPayloadV2.Delete(messageId, sentAt, targets)
            }
            TYPE_VOICE -> {
                val queueReadSecret = reader.raw(RelayQueueCredentials.READ_SECRET_BYTES)
                val key = reader.raw(VoiceMedia.KEY_BYTES)
                val digest = reader.raw(VoiceMedia.DIGEST_BYTES)
                val plaintextSize = reader.i32()
                val chunkCount = reader.u8()
                val durationMillis = reader.i32()
                val waveform = reader.field16(0, VoiceMedia.WAVEFORM_MAX_SAMPLES)
                val codec = reader.u8()
                val options = reader.u8()
                // Only the one-time option exists; any other bit is a payload this app cannot honour.
                if ((options and OPTION_ONCE.inv()) != 0) throw MalformedV2Exception("Unknown voice option")
                val note = V2VoiceNote(
                    queueReadSecret, key, digest, plaintextSize, chunkCount, durationMillis, waveform, codec,
                    once = (options and OPTION_ONCE) != 0,
                )
                if (!VoiceMedia.isAcceptable(note)) throw MalformedV2Exception("Invalid voice note")
                val replyTo = when (reader.u8()) {
                    NO_REPLY -> null
                    WITH_REPLY -> RandomId.fromBytes(reader.raw(RandomId.SIZE_BYTES))
                    else -> throw MalformedV2Exception("Invalid reply marker")
                }
                if (replyTo == messageId) throw MalformedV2Exception("A message cannot answer itself")
                InnerPayloadV2.Voice(messageId, sentAt, note, replyTo)
            }
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
        is InnerPayloadV2.Text -> if (payload.replyTo == null) TYPE_TEXT else TYPE_TEXT_REPLY
        is InnerPayloadV2.Voice -> TYPE_VOICE
        is InnerPayloadV2.Receipt -> TYPE_RECEIPT
        is InnerPayloadV2.Played -> TYPE_PLAYED
        is InnerPayloadV2.Delete -> TYPE_DELETE
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
