package app.securemessenger.ui

import app.securemessenger.domain.DeviceApprovalStatus
import app.securemessenger.domain.PairingStatus
import app.securemessenger.transport.RelayHealthStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TwoDeviceQaPreflightTest {
    @Test
    fun `physical phone warns for loopback and bind-only relay addresses`() {
        assertEquals(
            RelayClientAddressWarning.PHONE_LOOPBACK,
            relayClientAddressWarning("http://127.0.0.1:8787", isPhysicalDevice = true),
        )
        assertEquals(
            RelayClientAddressWarning.PHONE_LOOPBACK,
            relayClientAddressWarning("http://localhost:8787", isPhysicalDevice = true),
        )
        assertEquals(
            RelayClientAddressWarning.UNSPECIFIED_BIND_ADDRESS,
            relayClientAddressWarning("http://0.0.0.0:8787", isPhysicalDevice = true),
        )
    }

    @Test
    fun `emulator development URL is not incorrectly blocked`() {
        assertNull(relayClientAddressWarning("http://10.0.2.2:8787", isPhysicalDevice = true))
        assertNull(relayClientAddressWarning("http://127.0.0.1:8787", isPhysicalDevice = false))
    }

    @Test
    fun `preflight reports address health pairing device and retry failures`() {
        assertEquals(
            TwoDevicePreflightResult.RELAY_URL_MISSING,
            evaluateTwoDevicePreflight(readySnapshot().copy(relayUrl = "")),
        )
        assertEquals(
            TwoDevicePreflightResult.PHONE_LOOPBACK_URL,
            evaluateTwoDevicePreflight(readySnapshot().copy(relayUrl = "http://localhost:8787")),
        )
        assertEquals(
            TwoDevicePreflightResult.UNSPECIFIED_RELAY_URL,
            evaluateTwoDevicePreflight(readySnapshot().copy(relayUrl = "http://0.0.0.0:8787")),
        )
        assertEquals(
            TwoDevicePreflightResult.RELAY_UNAVAILABLE,
            evaluateTwoDevicePreflight(readySnapshot().copy(relayHealthStatus = RelayHealthStatus.UNAVAILABLE)),
        )
        assertEquals(
            TwoDevicePreflightResult.PAIRING_INCOMPLETE,
            evaluateTwoDevicePreflight(readySnapshot().copy(pairingStatus = PairingStatus.WAITING_FOR_RETURN_INVITATION)),
        )
        assertEquals(
            TwoDevicePreflightResult.DEVICE_BLOCKED,
            evaluateTwoDevicePreflight(readySnapshot().copy(primaryDeviceState = DeviceApprovalStatus.REVOKED)),
        )
        assertEquals(
            TwoDevicePreflightResult.RETRY_PENDING,
            evaluateTwoDevicePreflight(readySnapshot().copy(failedOutgoingCount = 1)),
        )
        assertEquals(TwoDevicePreflightResult.READY, evaluateTwoDevicePreflight(readySnapshot()))
    }

    @Test
    fun `safe preflight summary exposes statuses only and omits configured URL content`() {
        val snapshot = readySnapshot().copy(relayUrl = "http://private_key-nonce-ciphertext-queue-id-probe:8787")

        val summary = SafeTwoDevicePreflightSummary.render(snapshot, evaluateTwoDevicePreflight(snapshot))

        listOf("private_key", "nonce", "ciphertext", "queue-id-probe", "http://").forEach { forbidden ->
            assertFalse(summary.contains(forbidden))
        }
    }

    private fun readySnapshot() = TwoDevicePreflightSnapshot(
        relayUrl = "http://192.168.0.87:8787",
        isPhysicalDevice = true,
        relayHealthStatus = RelayHealthStatus.AVAILABLE,
        contactCount = 1,
        pairingStatus = PairingStatus.READY_FOR_CONVERSATION,
        primaryDeviceState = DeviceApprovalStatus.PRIMARY_UNVERIFIED,
        pendingOutgoingCount = 0,
        failedOutgoingCount = 0,
        nextRetryAtEpochMillis = null,
        workSyncStatus = WorkSyncStatus.SUCCEEDED,
    )
}
