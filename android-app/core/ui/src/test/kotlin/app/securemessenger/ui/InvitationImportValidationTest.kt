package app.securemessenger.ui

import app.securemessenger.crypto.InvitationCodec
import app.securemessenger.domain.DevicePublicIdentity
import app.securemessenger.domain.Invitation
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InvitationImportValidationTest {
    @Test
    fun `empty contact name returns name specific error for valid invitation`() {
        val result = validateInvitationImport(validInvitationCode(), " ")

        assertEquals(
            InvitationImportValidationResult.Invalid(InvitationImportValidationError.EMPTY_CONTACT_NAME),
            result,
        )
    }

    @Test
    fun `empty invitation code returns invitation specific error`() {
        val result = validateInvitationImport(" ", "Alice")

        assertEquals(
            InvitationImportValidationResult.Invalid(InvitationImportValidationError.EMPTY_INVITATION_CODE),
            result,
        )
    }

    @Test
    fun `invalid invitation returns corrupted invitation error`() {
        val result = validateInvitationImport("smp-invite-v1:not valid base64", "Alice")

        assertEquals(
            InvitationImportValidationResult.Invalid(InvitationImportValidationError.INVALID_OR_CORRUPTED_INVITATION),
            result,
        )
    }

    @Test
    fun `unsupported invitation version returns version specific error`() {
        val result = validateInvitationImport(unsupportedVersionInvitationCode(), "Alice")

        assertEquals(
            InvitationImportValidationResult.Invalid(InvitationImportValidationError.UNSUPPORTED_INVITATION_VERSION),
            result,
        )
    }

    @Test
    fun `valid invitation returns trimmed code contact name and decoded invitation`() {
        val code = validInvitationCode()
        val result = validateInvitationImport("  $code  ", "  Alice  ")

        assertTrue(result is InvitationImportValidationResult.Valid)
        result as InvitationImportValidationResult.Valid
        assertEquals(code, result.trimmedInvitationCode)
        assertEquals("Alice", result.trimmedContactName)
        assertEquals("queue-1", result.invitation.queueId)
        assertEquals("device-1", result.invitation.identity.deviceId)
    }

    private fun validInvitationCode(): String =
        InvitationCodec.encode(
            Invitation(
                version = InvitationCodec.VERSION,
                queueId = "queue-1",
                identity = DevicePublicIdentity(
                    deviceId = "device-1",
                    encodedPublicKey = byteArrayOf(1, 2, 3, 4),
                    fingerprint = "01 02",
                ),
            ),
        )

    private fun unsupportedVersionInvitationCode(): String {
        val bytes = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeInt(InvitationCodec.VERSION + 1)
                output.writeField("queue-1".toByteArray(Charsets.UTF_8))
                output.writeField("device-1".toByteArray(Charsets.UTF_8))
                output.writeField(byteArrayOf(1, 2, 3, 4))
            }
            buffer.toByteArray()
        }
        return "smp-invite-v1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun DataOutputStream.writeField(bytes: ByteArray) {
        writeInt(bytes.size)
        write(bytes)
    }
}
