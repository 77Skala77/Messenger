package app.securemessenger.ui

import app.securemessenger.crypto.InvitationCodec
import app.securemessenger.domain.Invitation

enum class InvitationImportValidationError {
    EMPTY_CONTACT_NAME,
    EMPTY_INVITATION_CODE,
    INVALID_OR_CORRUPTED_INVITATION,
    UNSUPPORTED_INVITATION_VERSION,
}

sealed class InvitationImportValidationResult {
    data class Valid(
        val trimmedInvitationCode: String,
        val trimmedContactName: String,
        val invitation: Invitation,
    ) : InvitationImportValidationResult()

    data class Invalid(val error: InvitationImportValidationError) : InvitationImportValidationResult()
}

fun validateInvitationImport(
    encoded: String,
    localContactName: String,
    decoder: (String) -> Invitation = InvitationCodec::decode,
): InvitationImportValidationResult {
    val trimmedName = localContactName.trim()
    if (trimmedName.isBlank()) {
        return InvitationImportValidationResult.Invalid(InvitationImportValidationError.EMPTY_CONTACT_NAME)
    }
    val trimmedCode = encoded.trim()
    if (trimmedCode.isBlank()) {
        return InvitationImportValidationResult.Invalid(InvitationImportValidationError.EMPTY_INVITATION_CODE)
    }
    val invitation = try {
        decoder(trimmedCode)
    } catch (error: Exception) {
        val validationError = if (error.message?.contains("Unsupported invitation version") == true) {
            InvitationImportValidationError.UNSUPPORTED_INVITATION_VERSION
        } else {
            InvitationImportValidationError.INVALID_OR_CORRUPTED_INVITATION
        }
        return InvitationImportValidationResult.Invalid(validationError)
    }
    return InvitationImportValidationResult.Valid(trimmedCode, trimmedName, invitation)
}
