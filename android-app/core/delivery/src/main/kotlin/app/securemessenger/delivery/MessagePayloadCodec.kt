package app.securemessenger.delivery

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

enum class DeliveryReceiptType {
    DELIVERED,
}

data class DeliveryReceipt(
    val originalMessageId: String,
    val conversationId: String,
    val originalSenderDeviceId: String,
    val originalRecipientDeviceId: String,
    val timestampEpochMillis: Long,
    val type: DeliveryReceiptType = DeliveryReceiptType.DELIVERED,
)

sealed class MessagePayload {
    data class Text(val value: String) : MessagePayload()
    data class Receipt(val value: DeliveryReceipt) : MessagePayload()
}

/**
 * Encrypted payload envelope for MVP application messages. It does not change the session AEAD
 * protocol; it only lets the application distinguish user text from encrypted control payloads.
 * Legacy raw UTF-8 plaintext remains readable for messages stored before this envelope existed.
 */
object MessagePayloadCodec {
    fun encodeText(value: String): ByteArray =
        encode(TYPE_TEXT) { output ->
            output.writeField(value.toByteArray(Charsets.UTF_8))
        }

    fun encodeDeliveryReceipt(receipt: DeliveryReceipt): ByteArray =
        encode(TYPE_DELIVERY_RECEIPT) { output ->
            output.writeField(receipt.originalMessageId.toByteArray(Charsets.UTF_8))
            output.writeField(receipt.conversationId.toByteArray(Charsets.UTF_8))
            output.writeField(receipt.originalSenderDeviceId.toByteArray(Charsets.UTF_8))
            output.writeField(receipt.originalRecipientDeviceId.toByteArray(Charsets.UTF_8))
            output.writeLong(receipt.timestampEpochMillis)
            output.writeInt(receipt.type.ordinal)
        }

    fun decode(bytes: ByteArray): MessagePayload {
        if (!bytes.startsWith(MAGIC)) {
            return MessagePayload.Text(bytes.toString(Charsets.UTF_8))
        }
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                val magic = ByteArray(MAGIC.size).also(input::readFully)
                require(magic.contentEquals(MAGIC)) { "Invalid payload magic" }
                require(input.readInt() == VERSION) { "Unsupported payload version" }
                when (input.readInt()) {
                    TYPE_TEXT -> MessagePayload.Text(input.readField().toString(Charsets.UTF_8))
                    TYPE_DELIVERY_RECEIPT -> {
                        val originalMessageId = input.readField().toString(Charsets.UTF_8)
                        val conversationId = input.readField().toString(Charsets.UTF_8)
                        val originalSenderDeviceId = input.readField().toString(Charsets.UTF_8)
                        val originalRecipientDeviceId = input.readField().toString(Charsets.UTF_8)
                        val timestampEpochMillis = input.readLong()
                        val type = DeliveryReceiptType.entries.getOrNull(input.readInt())
                            ?: error("Unsupported delivery receipt type")
                        require(input.available() == 0) { "Trailing payload bytes" }
                        MessagePayload.Receipt(
                            DeliveryReceipt(
                                originalMessageId = originalMessageId,
                                conversationId = conversationId,
                                originalSenderDeviceId = originalSenderDeviceId,
                                originalRecipientDeviceId = originalRecipientDeviceId,
                                timestampEpochMillis = timestampEpochMillis,
                                type = type,
                            ),
                        )
                    }
                    else -> error("Unsupported payload type")
                }
            }
        } catch (error: Exception) {
            throw IllegalArgumentException("Malformed secure messenger payload", error)
        }
    }

    private fun encode(type: Int, block: (DataOutputStream) -> Unit): ByteArray =
        ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.write(MAGIC)
                output.writeInt(VERSION)
                output.writeInt(type)
                block(output)
            }
            buffer.toByteArray()
        }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private fun DataOutputStream.writeField(value: ByteArray) {
        require(value.size <= MAX_FIELD_LENGTH)
        writeInt(value.size)
        write(value)
    }

    private fun DataInputStream.readField(): ByteArray {
        val size = readInt()
        require(size in 0..MAX_FIELD_LENGTH) { "Payload field is out of bounds" }
        return ByteArray(size).also(::readFully)
    }

    private val MAGIC = byteArrayOf(0x53, 0x4d, 0x50, 0x41, 0x59, 0x4c, 0x4f, 0x41, 0x44, 0)
    private const val VERSION = 1
    private const val TYPE_TEXT = 1
    private const val TYPE_DELIVERY_RECEIPT = 2
    private const val MAX_FIELD_LENGTH = 8192
}
