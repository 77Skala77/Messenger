package app.securemessenger.crypto

import app.securemessenger.domain.DevicePublicIdentity
import app.securemessenger.domain.Invitation
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Base64

object InvitationCodec {
    fun encode(invitation: Invitation): String {
        val bytes = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeInt(invitation.version)
                output.writeUtf8(invitation.queueId)
                output.writeUtf8(invitation.identity.deviceId)
                output.writeBytesField(invitation.identity.encodedPublicKey)
            }
            buffer.toByteArray()
        }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun decode(encoded: String): Invitation {
        require(encoded.startsWith(PREFIX)) { "Unsupported invitation format" }
        val decoded = Base64.getUrlDecoder().decode(encoded.removePrefix(PREFIX))
        return DataInputStream(ByteArrayInputStream(decoded)).use { input ->
            val version = input.readInt()
            require(version == VERSION) { "Unsupported invitation version" }
            val queueId = input.readUtf8()
            val deviceId = input.readUtf8()
            val publicKey = input.readBytesField()
            require(input.available() == 0) { "Trailing invitation bytes" }
            Invitation(
                version = version,
                queueId = queueId,
                identity = DevicePublicIdentity(
                    deviceId = deviceId,
                    encodedPublicKey = publicKey,
                    fingerprint = fingerprint(publicKey),
                ),
            )
        }
    }

    fun fingerprint(publicKey: ByteArray): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(publicKey)
        return hash.joinToString(separator = " ") { byte -> "%02X".format(byte) }
    }

    private fun DataOutputStream.writeUtf8(value: String) = writeBytesField(value.toByteArray(Charsets.UTF_8))

    private fun DataOutputStream.writeBytesField(bytes: ByteArray) {
        require(bytes.size <= MAX_FIELD_LENGTH)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readUtf8(): String = readBytesField().toString(Charsets.UTF_8)

    private fun DataInputStream.readBytesField(): ByteArray {
        val size = readInt()
        require(size in 1..MAX_FIELD_LENGTH) { "Invitation field is out of bounds" }
        return ByteArray(size).also(::readFully)
    }

    const val VERSION = 1
    private const val PREFIX = "smp-invite-v1:"
    private const val MAX_FIELD_LENGTH = 8192
}
