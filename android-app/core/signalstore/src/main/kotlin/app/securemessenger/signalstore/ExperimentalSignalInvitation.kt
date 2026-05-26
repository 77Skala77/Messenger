package app.securemessenger.signalstore

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

/**
 * Neutral experimental DTO only. These placeholders are not a verified PQXDH bundle and are
 * never accepted by the active v1 invitation/chat path.
 */
data class ExperimentalSignalPqxdhInvitation(
    val protocolVersion: Int,
    val receiveQueueId: String,
    val identityPublicKey: ByteArray,
    val signedPreKeyPublicKey: ByteArray,
    val signedPreKeySignaturePlaceholder: ByteArray,
    val oneTimePreKeyId: Int,
    val oneTimePreKeyPublicKeyPlaceholder: ByteArray,
    val kyberPreKeyId: Int,
    val kyberPreKeyPublicKeyPlaceholder: ByteArray,
) {
    init {
        require(receiveQueueId.isNotBlank())
        require(oneTimePreKeyId >= 0)
        require(kyberPreKeyId >= 0)
    }
}

object ExperimentalSignalInvitationCodec {
    const val FORMAT_ID: String = "INVITE_V2_SIGNAL_PQXDH_EXPERIMENTAL"
    const val PROTOCOL_VERSION: Int = 2
    private const val PREFIX = "smp-invite-v2-signal-pqxdh-experimental:"
    private const val MAX_FIELD_LENGTH = 16 * 1024

    fun encode(invitation: ExperimentalSignalPqxdhInvitation): String {
        require(invitation.protocolVersion == PROTOCOL_VERSION)
        val encoded = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeInt(invitation.protocolVersion)
                output.writeUtf8(invitation.receiveQueueId)
                output.writeBytesField(invitation.identityPublicKey)
                output.writeBytesField(invitation.signedPreKeyPublicKey)
                output.writeBytesField(invitation.signedPreKeySignaturePlaceholder)
                output.writeInt(invitation.oneTimePreKeyId)
                output.writeBytesField(invitation.oneTimePreKeyPublicKeyPlaceholder)
                output.writeInt(invitation.kyberPreKeyId)
                output.writeBytesField(invitation.kyberPreKeyPublicKeyPlaceholder)
            }
            buffer.toByteArray()
        }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(encoded)
    }

    fun decode(encoded: String): ExperimentalSignalPqxdhInvitation {
        require(encoded.startsWith(PREFIX)) { "Unsupported experimental invitation format" }
        val bytes = Base64.getUrlDecoder().decode(encoded.removePrefix(PREFIX))
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val version = input.readInt()
            require(version == PROTOCOL_VERSION) { "Unsupported experimental invitation version" }
            val invitation = ExperimentalSignalPqxdhInvitation(
                protocolVersion = version,
                receiveQueueId = input.readUtf8(),
                identityPublicKey = input.readBytesField(),
                signedPreKeyPublicKey = input.readBytesField(),
                signedPreKeySignaturePlaceholder = input.readBytesField(),
                oneTimePreKeyId = input.readInt().also { require(it >= 0) },
                oneTimePreKeyPublicKeyPlaceholder = input.readBytesField(),
                kyberPreKeyId = input.readInt().also { require(it >= 0) },
                kyberPreKeyPublicKeyPlaceholder = input.readBytesField(),
            )
            require(input.available() == 0) { "Trailing experimental invitation bytes" }
            invitation
        }
    }

    private fun DataOutputStream.writeUtf8(value: String) = writeBytesField(value.toByteArray(Charsets.UTF_8))

    private fun DataOutputStream.writeBytesField(bytes: ByteArray) {
        require(bytes.size in 1..MAX_FIELD_LENGTH)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readUtf8(): String = readBytesField().toString(Charsets.UTF_8)

    private fun DataInputStream.readBytesField(): ByteArray {
        val size = readInt()
        require(size in 1..MAX_FIELD_LENGTH) { "Experimental invitation field is out of bounds" }
        return ByteArray(size).also(::readFully)
    }
}
