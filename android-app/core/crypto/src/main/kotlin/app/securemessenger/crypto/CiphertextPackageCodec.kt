package app.securemessenger.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

object CiphertextPackageCodec {
    fun encode(value: CiphertextPackage): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(value.version)
                output.writeField(value.suiteId.toByteArray(Charsets.UTF_8))
                output.writeField(value.nonce)
                output.writeField(value.ciphertext)
                output.writeField(value.tag)
                output.writeField(value.aadHash)
            }
            bytes.toByteArray()
        }

    fun decode(bytes: ByteArray): CiphertextPackage = try {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val value = CiphertextPackage(
                version = input.readInt(),
                suiteId = input.readField().toString(Charsets.UTF_8),
                nonce = input.readField(),
                ciphertext = input.readField(MAX_CIPHERTEXT_LENGTH),
                tag = input.readField(),
                aadHash = input.readField(),
            )
            if (input.available() != 0) {
                throw InvalidCiphertextException("Trailing ciphertext package bytes")
            }
            value
        }
    } catch (error: CryptoException) {
        throw error
    } catch (error: Exception) {
        throw InvalidCiphertextException("Malformed ciphertext package", error)
    }

    private fun DataOutputStream.writeField(value: ByteArray) {
        require(value.size <= MAX_CIPHERTEXT_LENGTH)
        writeInt(value.size)
        write(value)
    }

    private fun DataInputStream.readField(limit: Int = MAX_FIELD_LENGTH): ByteArray {
        val length = readInt()
        if (length < 0 || length > limit) {
            throw InvalidCiphertextException("Ciphertext field is out of bounds")
        }
        return ByteArray(length).also(::readFully)
    }

    private const val MAX_FIELD_LENGTH = 8192
    private const val MAX_CIPHERTEXT_LENGTH = 16 * 1024 * 1024
}
