package app.securemessenger.ui

import app.securemessenger.domain.DeviceApprovalStatus
import app.securemessenger.domain.PairingStatus
import app.securemessenger.transport.RelayHealthStatus
import java.net.URI

enum class RelayClientAddressWarning {
    PHONE_LOOPBACK,
    UNSPECIFIED_BIND_ADDRESS,
}

enum class RelayTestMode {
    LAN_RELAY,
    PUBLIC_RELAY,
}

enum class TwoDevicePreflightResult {
    READY,
    RELAY_URL_MISSING,
    PHONE_LOOPBACK_URL,
    UNSPECIFIED_RELAY_URL,
    RELAY_UNAVAILABLE,
    PAIRING_INCOMPLETE,
    DEVICE_BLOCKED,
    RETRY_PENDING,
}

data class TwoDevicePreflightSnapshot(
    val relayUrl: String,
    val isPhysicalDevice: Boolean,
    val relayHealthStatus: RelayHealthStatus?,
    val contactCount: Int,
    val pairingStatus: PairingStatus?,
    val primaryDeviceState: DeviceApprovalStatus?,
    val pendingOutgoingCount: Int,
    val failedOutgoingCount: Int,
    val nextRetryAtEpochMillis: Long?,
    val workSyncStatus: WorkSyncStatus,
)

fun relayTestMode(url: String): RelayTestMode? {
    val scheme = runCatching { URI(url.trim()).scheme?.lowercase() }.getOrNull() ?: return null
    return when (scheme) {
        "http" -> RelayTestMode.LAN_RELAY
        "https" -> RelayTestMode.PUBLIC_RELAY
        else -> null
    }
}

fun relayClientAddressWarning(url: String, isPhysicalDevice: Boolean): RelayClientAddressWarning? {
    if (!isPhysicalDevice) return null
    val host = runCatching { URI(url.trim()).host?.lowercase() }.getOrNull() ?: return null
    return when (host) {
        "127.0.0.1", "localhost", "::1" -> RelayClientAddressWarning.PHONE_LOOPBACK
        "0.0.0.0" -> RelayClientAddressWarning.UNSPECIFIED_BIND_ADDRESS
        else -> null
    }
}

fun evaluateTwoDevicePreflight(snapshot: TwoDevicePreflightSnapshot): TwoDevicePreflightResult = when {
    snapshot.relayUrl.isBlank() -> TwoDevicePreflightResult.RELAY_URL_MISSING
    relayClientAddressWarning(snapshot.relayUrl, snapshot.isPhysicalDevice) ==
        RelayClientAddressWarning.PHONE_LOOPBACK -> TwoDevicePreflightResult.PHONE_LOOPBACK_URL
    relayClientAddressWarning(snapshot.relayUrl, snapshot.isPhysicalDevice) ==
        RelayClientAddressWarning.UNSPECIFIED_BIND_ADDRESS -> TwoDevicePreflightResult.UNSPECIFIED_RELAY_URL
    snapshot.relayHealthStatus != RelayHealthStatus.AVAILABLE -> TwoDevicePreflightResult.RELAY_UNAVAILABLE
    snapshot.contactCount == 0 -> TwoDevicePreflightResult.PAIRING_INCOMPLETE
    snapshot.pairingStatus == PairingStatus.DEVICE_BLOCKED ||
        snapshot.primaryDeviceState in setOf(
            DeviceApprovalStatus.PENDING_APPROVAL,
            DeviceApprovalStatus.REVOKED,
            DeviceApprovalStatus.KEY_CHANGED,
        ) -> TwoDevicePreflightResult.DEVICE_BLOCKED
    snapshot.pairingStatus != PairingStatus.READY_FOR_CONVERSATION ->
        TwoDevicePreflightResult.PAIRING_INCOMPLETE
    snapshot.primaryDeviceState == null -> TwoDevicePreflightResult.DEVICE_BLOCKED
    snapshot.failedOutgoingCount > 0 || snapshot.pendingOutgoingCount > 0 ->
        TwoDevicePreflightResult.RETRY_PENDING
    else -> TwoDevicePreflightResult.READY
}

/**
 * Testable technical summary boundary. It deliberately has no payload or queue identifier fields.
 */
object SafeTwoDevicePreflightSummary {
    fun render(snapshot: TwoDevicePreflightSnapshot, result: TwoDevicePreflightResult): String = buildString {
        appendLine("preflight=${result.name}")
        appendLine("relay_configured=${snapshot.relayUrl.isNotBlank()}")
        appendLine("relay_mode=${relayTestMode(snapshot.relayUrl)?.name ?: "UNKNOWN"}")
        appendLine("relay_health=${snapshot.relayHealthStatus?.name ?: "UNKNOWN"}")
        appendLine("contacts=${snapshot.contactCount}")
        appendLine("pairing=${snapshot.pairingStatus?.name ?: "UNKNOWN"}")
        appendLine("primary_device=${snapshot.primaryDeviceState?.name ?: "UNKNOWN"}")
        appendLine("pending=${snapshot.pendingOutgoingCount}")
        appendLine("failed_retry=${snapshot.failedOutgoingCount}")
        appendLine("next_retry=${snapshot.nextRetryAtEpochMillis ?: "NONE"}")
        appendLine("work_sync=${snapshot.workSyncStatus.name}")
    }
}
