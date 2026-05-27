package app.securemessenger.domain

enum class EncryptionMode(val suiteId: String) {
    STANDARD_AEAD("STANDARD_AEAD"),
    PARANOIA_C4_AEAD("PARANOIA_C4_AEAD"),
}

enum class VerificationStatus {
    VERIFIED,
    UNVERIFIED,
    KEY_CHANGED,
    UNKNOWN,
}

enum class MessageStatus {
    PENDING,
    SENT,
    RECEIVED,
    FAILED,
}

enum class DeviceApprovalStatus {
    PRIMARY_UNVERIFIED,
    VERIFIED,
    PENDING_APPROVAL,
    KEY_CHANGED,
    REVOKED,
}

enum class PairingStatus {
    WAITING_FOR_RETURN_INVITATION,
    READY_FOR_CONVERSATION,
    DEVICE_BLOCKED,
}

enum class DeliveryBlockReason {
    MISSING_PRIMARY_DEVICE,
    PENDING_APPROVAL,
    REVOKED,
    KEY_CHANGED,
}

data class DevicePublicIdentity(
    val deviceId: String,
    val encodedPublicKey: ByteArray,
    val fingerprint: String,
)

data class Invitation(
    val version: Int,
    val queueId: String,
    val identity: DevicePublicIdentity,
)

data class Contact(
    val contactId: String,
    val localDisplayName: String,
    val queueId: String,
    val identity: DevicePublicIdentity,
    val verificationStatus: VerificationStatus,
)

data class ContactDevice(
    val contactId: String,
    val queueId: String,
    val identity: DevicePublicIdentity,
    val approvalStatus: DeviceApprovalStatus,
    val firstSeenEpochMillis: Long,
)

fun pairingStatus(
    contact: Contact?,
    devices: List<ContactDevice>,
    reciprocalInvitationConfirmed: Boolean = false,
): PairingStatus {
    if (contact == null) return PairingStatus.WAITING_FOR_RETURN_INVITATION
    val primary = devices.firstOrNull { it.identity.deviceId == contact.identity.deviceId }
        ?: return PairingStatus.DEVICE_BLOCKED
    return when (primary.approvalStatus) {
        DeviceApprovalStatus.PENDING_APPROVAL,
        DeviceApprovalStatus.KEY_CHANGED,
        DeviceApprovalStatus.REVOKED,
        -> PairingStatus.DEVICE_BLOCKED
        DeviceApprovalStatus.PRIMARY_UNVERIFIED,
        DeviceApprovalStatus.VERIFIED,
        -> if (reciprocalInvitationConfirmed) {
            PairingStatus.READY_FOR_CONVERSATION
        } else {
            PairingStatus.WAITING_FOR_RETURN_INVITATION
        }
    }
}

fun deliveryBlockReason(contact: Contact, devices: List<ContactDevice>): DeliveryBlockReason? {
    val primary = devices.firstOrNull { it.identity.deviceId == contact.identity.deviceId }
        ?: return DeliveryBlockReason.MISSING_PRIMARY_DEVICE
    return when (primary.approvalStatus) {
        DeviceApprovalStatus.PENDING_APPROVAL -> DeliveryBlockReason.PENDING_APPROVAL
        DeviceApprovalStatus.REVOKED -> DeliveryBlockReason.REVOKED
        DeviceApprovalStatus.KEY_CHANGED -> DeliveryBlockReason.KEY_CHANGED
        DeviceApprovalStatus.PRIMARY_UNVERIFIED,
        DeviceApprovalStatus.VERIFIED,
        -> null
    }
}

data class ConversationSummary(
    val contact: Contact,
    val lastActivityEpochMillis: Long,
)

data class DisplayMessage(
    val id: String,
    val contactId: String,
    val plaintextInMemoryOnly: String,
    val outgoing: Boolean,
    val status: MessageStatus,
    val createdAtEpochMillis: Long,
)
