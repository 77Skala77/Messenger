package app.securemessenger.ui

import app.securemessenger.domain.DeviceApprovalStatus
import app.securemessenger.domain.PairingStatus
import app.securemessenger.transport.RelayHealthStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeDeliveryDiagnosticsTest {
    @Test
    fun `report contains redacted queue fingerprints and no message or cryptographic fields`() {
        val rawLocalQueueId = "queue-local-plaintext-probe-private-key-probe-full-identifier"
        val rawContactQueueId = "queue-contact-nonce-probe-ciphertext-probe-full-identifier"
        val snapshot = SafeDiagnosticsSnapshot.create(
            relayUrl = "http://192.168.1.45:8787",
            relayHealthStatus = RelayHealthStatus.AVAILABLE,
            lastSuccessfulSyncAtEpochMillis = 25L,
            pendingOutgoingCount = 1,
            failedOutgoingCount = 0,
            nextRetryAtEpochMillis = null,
            contactCount = 1,
            localQueueId = rawLocalQueueId,
            selectedContactQueueId = rawContactQueueId,
            pairingStatus = PairingStatus.READY_FOR_CONVERSATION,
            primaryDeviceState = DeviceApprovalStatus.VERIFIED,
            workSyncStatus = WorkSyncStatus.SUCCEEDED,
            events = listOf(TechnicalDeliveryEvent(DeliveryTechnicalEvent.OUTGOING_SENT, 24L)),
        )

        val report = SafeDiagnosticReport.render(
            snapshot = snapshot,
            labels = labels(),
            relayHealthValue = "available",
            pairingValue = "ready",
            primaryDeviceValue = "verified",
            workSyncValue = "done",
            lastErrorValue = "none",
            eventValues = mapOf(DeliveryTechnicalEvent.OUTGOING_SENT to "OUTGOING_SENT"),
        )

        assertTrue(report.contains(RedactedIdentifier.fingerprint(rawLocalQueueId)))
        assertTrue(report.contains(RedactedIdentifier.fingerprint(rawContactQueueId)))
        assertFalse(report.contains(rawLocalQueueId))
        assertFalse(report.contains(rawContactQueueId))
        listOf("plaintext-probe", "private-key-probe", "nonce-probe", "ciphertext-probe").forEach { forbidden ->
            assertFalse(report.contains(forbidden))
        }
    }

    @Test
    fun `diagnostics use human time grouping and last error classification`() {
        val events = listOf(
            TechnicalDeliveryEvent(DeliveryTechnicalEvent.RELAY_HEALTH_OK, 3_723_000L),
            TechnicalDeliveryEvent(DeliveryTechnicalEvent.DELIVERY_RECEIPT_RECEIVED, 3_724_000L),
            TechnicalDeliveryEvent(DeliveryTechnicalEvent.DEVICE_BLOCKED_SEND, 3_725_000L),
        )
        val snapshot = SafeDiagnosticsSnapshot.create(
            relayUrl = "http://192.168.1.45:8787",
            relayHealthStatus = RelayHealthStatus.AVAILABLE,
            lastSuccessfulSyncAtEpochMillis = null,
            pendingOutgoingCount = 0,
            failedOutgoingCount = 0,
            nextRetryAtEpochMillis = null,
            contactCount = 1,
            localQueueId = null,
            selectedContactQueueId = null,
            pairingStatus = PairingStatus.READY_FOR_CONVERSATION,
            primaryDeviceState = DeviceApprovalStatus.VERIFIED,
            workSyncStatus = WorkSyncStatus.SUCCEEDED,
            events = events,
        )
        val report = SafeDiagnosticReport.render(
            snapshot = snapshot,
            labels = labels(),
            relayHealthValue = "available",
            pairingValue = "ready",
            primaryDeviceValue = "verified",
            workSyncValue = "done",
            lastErrorValue = "blocked",
            eventValues = DeliveryTechnicalEvent.entries.associateWith { it.name },
        )

        assertEquals(DeliveryEventGroup.RELAY, DeliveryDiagnosticsClassifier.group(DeliveryTechnicalEvent.RELAY_HEALTH_OK))
        assertEquals(
            DeliveryEventGroup.RECEIPTS,
            DeliveryDiagnosticsClassifier.group(DeliveryTechnicalEvent.DELIVERY_RECEIPT_RECEIVED),
        )
        assertEquals(DeliveryLastError.DEVICE_BLOCKED, snapshot.lastError)
        assertTrue(Regex("\\d{2}:\\d{2}:\\d{2}").containsMatchIn(report))
    }

    @Test
    fun `technical event log keeps only last one hundred event types and timestamps`() {
        val buffer = TechnicalEventBuffer()

        repeat(105) { index ->
            buffer.append(TechnicalDeliveryEvent(DeliveryTechnicalEvent.OUTGOING_QUEUED, index.toLong()))
        }

        assertEquals(100, buffer.entries().size)
        assertEquals(5L, buffer.entries().first().timestampEpochMillis)
        assertEquals(104L, buffer.entries().last().timestampEpochMillis)
    }

    private fun labels() = DiagnosticReportLabels(
        title = "diagnostics",
        safetyNotice = "safe-only",
        relayUrl = "url",
        relayHealth = "health",
        lastSuccessfulSync = "sync",
        pendingOutgoing = "pending",
        failedOutgoing = "failed",
        nextRetryAt = "retry",
        contacts = "contacts",
        localQueue = "local",
        contactQueue = "contact",
        pairing = "pairing",
        primaryDevice = "device",
        workSync = "worker",
        lastError = "last-error",
        events = "events",
        unavailable = "none",
    )
}
