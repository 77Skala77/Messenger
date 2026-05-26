package app.securemessenger.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Serializes session state only for encryption by the local Keystore-backed vault.
 */
object SessionStateCodec {
    fun encode(session: EstablishedSession): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(VERSION)
                output.writeText(session.conversationId)
                output.writeText(session.localDeviceId)
                output.writeText(session.remoteDeviceId)
                output.writeField(session.rootKey)
                output.writeLong(session.nextSendingNumber)
            }
            bytes.toByteArray()
        }

    fun decode(bytes: ByteArray): EstablishedSession = try {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            if (input.readInt() != VERSION) {
                throw InvalidCiphertextException("Unsupported session state version")
            }
            val session = EstablishedSession(
                conversationId = input.readText(),
                localDeviceId = input.readText(),
                remoteDeviceId = input.readText(),
                rootKey = input.readField(MAX_KEY_LENGTH),
                nextSendingNumber = input.readLong(),
            )
            if (session.rootKey.size != EXPECTED_ROOT_KEY_LENGTH || session.nextSendingNumber <= 0 || input.available() != 0) {
                session.rootKey.fill(0)
                throw InvalidCiphertextException("Invalid session state")
            }
            session
        }
    } catch (error: CryptoException) {
        throw error
    } catch (error: Exception) {
        throw InvalidCiphertextException("Malformed session state", error)
    }

    private fun DataOutputStream.writeText(value: String) = writeField(value.toByteArray(Charsets.UTF_8))

    private fun DataInputStream.readText(): String = readField().toString(Charsets.UTF_8)

    private fun DataOutputStream.writeField(value: ByteArray) {
        require(value.size <= MAX_FIELD_LENGTH)
        writeInt(value.size)
        write(value)
    }

    private fun DataInputStream.readField(limit: Int = MAX_FIELD_LENGTH): ByteArray {
        val length = readInt()
        if (length < 0 || length > limit) {
            throw InvalidCiphertextException("Session state field is out of bounds")
        }
        return ByteArray(length).also(::readFully)
    }

    private const val VERSION = 1
    private const val EXPECTED_ROOT_KEY_LENGTH = 32
    private const val MAX_KEY_LENGTH = 128
    private const val MAX_FIELD_LENGTH = 8192
}
