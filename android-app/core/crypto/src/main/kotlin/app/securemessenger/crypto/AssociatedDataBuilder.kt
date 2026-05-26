package app.securemessenger.crypto

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

data class MessageAssociatedData(
    val protocolVersion: Int,
    val conversationId: String,
    val senderDeviceId: String,
    val recipientDeviceId: String,
    val messageNumber: Long,
    val timestampBucket: Long,
    val contentType: String,
    val encryptionSuiteId: String,
)

object AssociatedDataBuilder {
    fun build(data: MessageAssociatedData): ByteArray {
        require(data.protocolVersion > 0)
        require(data.messageNumber >= 0)
        return ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeInt(data.protocolVersion)
                output.writeLengthPrefixed(data.conversationId)
                output.writeLengthPrefixed(data.senderDeviceId)
                output.writeLengthPrefixed(data.recipientDeviceId)
                output.writeLong(data.messageNumber)
                output.writeLong(data.timestampBucket)
                output.writeLengthPrefixed(data.contentType)
                output.writeLengthPrefixed(data.encryptionSuiteId)
            }
            buffer.toByteArray()
        }
    }

    private fun DataOutputStream.writeLengthPrefixed(value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        require(encoded.size <= MAX_FIELD_LENGTH)
        writeInt(encoded.size)
        write(encoded)
    }

    private const val MAX_FIELD_LENGTH = 4096
}
