package app.securemessenger.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Authenticated encrypted message wire/storage envelope. Plaintext is never serialized here.
 */
object SessionMessageCodec {
    fun encode(message: EncryptedSessionMessage): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(VERSION)
                output.writeLong(message.messageNumber)
                output.writeLong(message.timestampBucket)
                output.writeField(message.associatedData)
                output.writeField(CiphertextPackageCodec.encode(message.ciphertextPackage), MAX_CIPHERTEXT_LENGTH)
            }
            bytes.toByteArray()
        }

    fun decode(bytes: ByteArray): EncryptedSessionMessage = try {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            if (input.readInt() != VERSION) {
                throw InvalidCiphertextException("Unsupported session message version")
            }
            val messageNumber = input.readLong()
            val timestampBucket = input.readLong()
            if (messageNumber <= 0 || timestampBucket < 0) {
                throw InvalidCiphertextException("Invalid session message metadata")
            }
            val associatedData = input.readField()
            val ciphertextPackage = CiphertextPackageCodec.decode(input.readField(MAX_CIPHERTEXT_LENGTH))
            if (input.available() != 0) {
                throw InvalidCiphertextException("Trailing session message bytes")
            }
            EncryptedSessionMessage(messageNumber, timestampBucket, associatedData, ciphertextPackage)
        }
    } catch (error: CryptoException) {
        throw error
    } catch (error: Exception) {
        throw InvalidCiphertextException("Malformed session message", error)
    }

    private fun DataOutputStream.writeField(value: ByteArray, limit: Int = MAX_FIELD_LENGTH) {
        require(value.size <= limit)
        writeInt(value.size)
        write(value)
    }

    private fun DataInputStream.readField(limit: Int = MAX_FIELD_LENGTH): ByteArray {
        val length = readInt()
        if (length < 0 || length > limit) {
            throw InvalidCiphertextException("Session message field is out of bounds")
        }
        return ByteArray(length).also(::readFully)
    }

    private const val VERSION = 1
    private const val MAX_FIELD_LENGTH = 8192
    private const val MAX_CIPHERTEXT_LENGTH = 16 * 1024 * 1024
}
