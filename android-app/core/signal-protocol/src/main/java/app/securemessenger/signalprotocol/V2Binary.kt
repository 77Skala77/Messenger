package app.securemessenger.signalprotocol

import app.securemessenger.domain.RelayQueueCredentials
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.security.SecureRandom

/** Any malformed v2 structure. The message never contains input bytes: it can reach logs. */
class MalformedV2Exception(message: String) : IllegalArgumentException(message)

/**
 * A random 128-bit value: v2 message IDs and routing tags. Immutable; [toString] never prints
 * the value, because routing tags are metadata.
 */
class RandomId private constructor(private val value: ByteArray) {
    fun toByteArray(): ByteArray = value.copyOf()

    fun toHex(): String = value.joinToString("") { "%02x".format(it) }

    override fun equals(other: Any?): Boolean = other is RandomId && value.contentEquals(other.value)

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = "RandomId(redacted)"

    companion object {
        const val SIZE_BYTES = 16

        fun random(random: SecureRandom): RandomId = RandomId(ByteArray(SIZE_BYTES).also(random::nextBytes))

        fun fromBytes(bytes: ByteArray): RandomId {
            if (bytes.size != SIZE_BYTES) throw MalformedV2Exception("Invalid ID length")
            return RandomId(bytes.copyOf())
        }

        fun fromHex(hex: String): RandomId {
            if (hex.length != SIZE_BYTES * 2 || hex.any { it !in '0'..'9' && it !in 'a'..'f' }) {
                throw MalformedV2Exception("Invalid ID encoding")
            }
            return RandomId(ByteArray(SIZE_BYTES) { index -> hex.substring(index * 2, index * 2 + 2).toInt(16).toByte() })
        }
    }
}

internal class V2Writer {
    private val buffer = ByteArrayOutputStream()
    private val output = DataOutputStream(buffer)

    fun raw(bytes: ByteArray) = apply { output.write(bytes) }

    fun u8(value: Int) = apply {
        require(value in 0..0xFF)
        output.writeByte(value)
    }

    fun i32(value: Int) = apply { output.writeInt(value) }

    fun i64(value: Long) = apply { output.writeLong(value) }

    fun field16(bytes: ByteArray) = apply {
        require(bytes.size <= 0xFFFF)
        output.writeShort(bytes.size)
        output.write(bytes)
    }

    fun field32(bytes: ByteArray) = apply {
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    fun toByteArray(): ByteArray {
        output.flush()
        return buffer.toByteArray()
    }
}

/** Bounds-checked reader: every read either succeeds exactly or throws [MalformedV2Exception]. */
internal class V2Reader(private val bytes: ByteArray) {
    private var position = 0

    fun raw(count: Int): ByteArray {
        if (count < 0 || count > bytes.size - position) throw MalformedV2Exception("Truncated input")
        return bytes.copyOfRange(position, position + count).also { position += count }
    }

    fun u8(): Int = raw(1)[0].toInt() and 0xFF

    fun i32(): Int = ByteBuffer.wrap(raw(4)).int

    fun i64(): Long = ByteBuffer.wrap(raw(8)).long

    fun field16(minSize: Int, maxSize: Int): ByteArray {
        val size = ByteBuffer.wrap(raw(2)).short.toInt() and 0xFFFF
        if (size !in minSize..maxSize) throw MalformedV2Exception("Field length out of bounds")
        return raw(size)
    }

    fun field32(minSize: Int, maxSize: Int): ByteArray {
        val size = i32()
        if (size !in minSize..maxSize) throw MalformedV2Exception("Field length out of bounds")
        return raw(size)
    }

    fun expect(constant: ByteArray) {
        if (!raw(constant.size).contentEquals(constant)) throw MalformedV2Exception("Unexpected format marker")
    }

    fun requireEnd() {
        if (position != bytes.size) throw MalformedV2Exception("Trailing bytes")
    }
}

internal object V2Text {
    fun decodeUtf8(bytes: ByteArray): String =
        try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            throw MalformedV2Exception("Invalid UTF-8")
        }

    /** Only self-certifying relay queue IDs (`q3-...`, DEC-024); older forms are rejected. */
    fun isValidQueueId(value: String): Boolean = RelayQueueCredentials.isValidQueueId(value)

    fun queueIdBytes(value: String): ByteArray {
        require(isValidQueueId(value)) { "Invalid queue ID" }
        return value.toByteArray(Charsets.US_ASCII)
    }

    fun readQueueId(reader: V2Reader): String {
        val value = decodeUtf8(reader.field16(MIN_QUEUE_ID, MAX_QUEUE_ID))
        if (!isValidQueueId(value)) throw MalformedV2Exception("Invalid queue ID")
        return value
    }

    const val MIN_QUEUE_ID = 8
    const val MAX_QUEUE_ID = 128
}

internal fun hexOf(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

internal fun sha256(domain: ByteArray, data: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").run {
        update(domain)
        digest(data)
    }
