package app.securemessenger.ui

import android.content.Context
import app.securemessenger.domain.DeviceApprovalStatus
import app.securemessenger.domain.PairingStatus
import app.securemessenger.transport.RelayHealthStatus
import java.security.MessageDigest

enum class DeliveryTechnicalEvent {
    RELAY_HEALTH_OK,
    RELAY_HEALTH_FAILED,
    OUTGOING_QUEUED,
    OUTGOING_SENT,
    OUTGOING_RETRY_SCHEDULED,
    OUTGOING_RETRY_STARTED,
    OUTGOING_RETRY_SUCCEEDED,
    OUTGOING_RETRY_FAILED,
    INCOMING_FETCH_STARTED,
    INCOMING_ACCEPTED,
    INCOMING_DUPLICATE_SUPPRESSED,
    DEVICE_BLOCKED_SEND,
    PAIRING_INCOMPLETE,
    PAIRING_READY,
    MANUAL_SYNC_STARTED,
    MANUAL_SYNC_FINISHED,
}

enum class WorkSyncStatus {
    UNKNOWN,
    SCHEDULED,
    RUNNING,
    SUCCEEDED,
    RETRY_PENDING,
}

data class TechnicalDeliveryEvent(
    val type: DeliveryTechnicalEvent,
    val timestampEpochMillis: Long,
)

class TechnicalEventBuffer(
    initialEvents: List<TechnicalDeliveryEvent> = emptyList(),
    private val maximumEvents: Int = MAX_TECHNICAL_EVENTS,
) {
    private val events = initialEvents.takeLast(maximumEvents).toMutableList()

    fun append(event: TechnicalDeliveryEvent) {
        events += event
        while (events.size > maximumEvents) events.removeAt(0)
    }

    fun entries(): List<TechnicalDeliveryEvent> = events.toList()

    fun clear() = events.clear()
}

data class SafeDiagnosticsSnapshot(
    val relayUrl: String = "",
    val relayHealthStatus: RelayHealthStatus? = null,
    val lastSuccessfulSyncAtEpochMillis: Long? = null,
    val pendingOutgoingCount: Int = 0,
    val failedOutgoingCount: Int = 0,
    val nextRetryAtEpochMillis: Long? = null,
    val contactCount: Int = 0,
    val localQueueFingerprint: String? = null,
    val selectedContactQueueFingerprint: String? = null,
    val pairingStatus: PairingStatus? = null,
    val primaryDeviceState: DeviceApprovalStatus? = null,
    val workSyncStatus: WorkSyncStatus = WorkSyncStatus.UNKNOWN,
    val events: List<TechnicalDeliveryEvent> = emptyList(),
) {
    companion object {
        fun create(
            relayUrl: String,
            relayHealthStatus: RelayHealthStatus?,
            lastSuccessfulSyncAtEpochMillis: Long?,
            pendingOutgoingCount: Int,
            failedOutgoingCount: Int,
            nextRetryAtEpochMillis: Long?,
            contactCount: Int,
            localQueueId: String?,
            selectedContactQueueId: String?,
            pairingStatus: PairingStatus?,
            primaryDeviceState: DeviceApprovalStatus?,
            workSyncStatus: WorkSyncStatus,
            events: List<TechnicalDeliveryEvent>,
        ): SafeDiagnosticsSnapshot =
            SafeDiagnosticsSnapshot(
                relayUrl = relayUrl,
                relayHealthStatus = relayHealthStatus,
                lastSuccessfulSyncAtEpochMillis = lastSuccessfulSyncAtEpochMillis,
                pendingOutgoingCount = pendingOutgoingCount,
                failedOutgoingCount = failedOutgoingCount,
                nextRetryAtEpochMillis = nextRetryAtEpochMillis,
                contactCount = contactCount,
                localQueueFingerprint = localQueueId?.let(RedactedIdentifier::fingerprint),
                selectedContactQueueFingerprint = selectedContactQueueId?.let(RedactedIdentifier::fingerprint),
                pairingStatus = pairingStatus,
                primaryDeviceState = primaryDeviceState,
                workSyncStatus = workSyncStatus,
                events = events.takeLast(MAX_TECHNICAL_EVENTS),
            )
    }
}

object RedactedIdentifier {
    fun fingerprint(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return "sha256:" + digest.take(6).joinToString("") { "%02x".format(it) }
    }
}

/**
 * Persists status codes and timestamps only; no API accepts payload or cryptographic bytes.
 */
class SafeDeliveryDiagnosticsStore(
    context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun record(type: DeliveryTechnicalEvent) {
        val buffer = TechnicalEventBuffer(events())
        buffer.append(TechnicalDeliveryEvent(type, now()))
        preferences.edit().putString(EVENT_LOG, encode(buffer.entries())).apply()
    }

    fun events(): List<TechnicalDeliveryEvent> =
        preferences.getString(EVENT_LOG, null)?.let(::decode).orEmpty()

    fun recordRelayHealth(status: RelayHealthStatus) {
        preferences.edit().putString(LAST_RELAY_HEALTH, status.name).apply()
    }

    fun lastRelayHealthStatus(): RelayHealthStatus? =
        preferences.getString(LAST_RELAY_HEALTH, null)
            ?.let { stored -> runCatching { RelayHealthStatus.valueOf(stored) }.getOrNull() }

    fun clearRelayHealth() {
        preferences.edit().remove(LAST_RELAY_HEALTH).apply()
    }

    fun markSuccessfulSync() {
        preferences.edit()
            .putLong(LAST_SUCCESSFUL_SYNC, now())
            .putString(WORK_STATUS, WorkSyncStatus.SUCCEEDED.name)
            .apply()
    }

    fun lastSuccessfulSyncAtEpochMillis(): Long? =
        if (preferences.contains(LAST_SUCCESSFUL_SYNC)) preferences.getLong(LAST_SUCCESSFUL_SYNC, 0) else null

    fun setWorkSyncStatus(status: WorkSyncStatus) {
        preferences.edit().putString(WORK_STATUS, status.name).apply()
    }

    fun workSyncStatus(): WorkSyncStatus =
        runCatching {
            WorkSyncStatus.valueOf(preferences.getString(WORK_STATUS, WorkSyncStatus.UNKNOWN.name)!!)
        }.getOrDefault(WorkSyncStatus.UNKNOWN)

    fun confirmReciprocalPairing(contactId: String) {
        val confirmed = preferences.getStringSet(CONFIRMED_PAIRINGS, emptySet()).orEmpty().toMutableSet()
        confirmed += RedactedIdentifier.fingerprint(contactId)
        preferences.edit().putStringSet(CONFIRMED_PAIRINGS, confirmed).apply()
    }

    fun isReciprocalPairingConfirmed(contactId: String): Boolean =
        RedactedIdentifier.fingerprint(contactId) in
            preferences.getStringSet(CONFIRMED_PAIRINGS, emptySet()).orEmpty()

    fun clearDiagnostics() {
        preferences.edit()
            .remove(EVENT_LOG)
            .remove(LAST_RELAY_HEALTH)
            .remove(LAST_SUCCESSFUL_SYNC)
            .remove(WORK_STATUS)
            .apply()
    }

    fun clearForLocalWipe() {
        preferences.edit().clear().apply()
    }

    private fun encode(events: List<TechnicalDeliveryEvent>): String =
        events.joinToString("\n") { "${it.timestampEpochMillis}|${it.type.name}" }

    private fun decode(encoded: String): List<TechnicalDeliveryEvent> =
        encoded.lineSequence().mapNotNull { line ->
            val parts = line.split('|', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val timestamp = parts[0].toLongOrNull() ?: return@mapNotNull null
            val type = runCatching { DeliveryTechnicalEvent.valueOf(parts[1]) }.getOrNull()
                ?: return@mapNotNull null
            TechnicalDeliveryEvent(type, timestamp)
        }.toList().takeLast(MAX_TECHNICAL_EVENTS)

    private companion object {
        const val PREFERENCES = "secure_messenger_safe_diagnostics"
        const val EVENT_LOG = "technical_events"
        const val LAST_RELAY_HEALTH = "last_relay_health"
        const val LAST_SUCCESSFUL_SYNC = "last_successful_sync"
        const val WORK_STATUS = "work_sync_status"
        const val CONFIRMED_PAIRINGS = "confirmed_pairings"
    }
}

data class DiagnosticReportLabels(
    val title: String,
    val safetyNotice: String,
    val relayUrl: String,
    val relayHealth: String,
    val lastSuccessfulSync: String,
    val pendingOutgoing: String,
    val failedOutgoing: String,
    val nextRetryAt: String,
    val contacts: String,
    val localQueue: String,
    val contactQueue: String,
    val pairing: String,
    val primaryDevice: String,
    val workSync: String,
    val events: String,
    val unavailable: String,
)

object SafeDiagnosticReport {
    fun render(
        snapshot: SafeDiagnosticsSnapshot,
        labels: DiagnosticReportLabels,
        relayHealthValue: String,
        pairingValue: String,
        primaryDeviceValue: String,
        workSyncValue: String,
        eventValues: Map<DeliveryTechnicalEvent, String>,
    ): String = buildString {
        appendLine(labels.title)
        appendLine(labels.safetyNotice)
        appendLine("${labels.relayUrl}: ${snapshot.relayUrl.ifBlank { labels.unavailable }}")
        appendLine("${labels.relayHealth}: $relayHealthValue")
        appendLine("${labels.lastSuccessfulSync}: ${snapshot.lastSuccessfulSyncAtEpochMillis ?: labels.unavailable}")
        appendLine("${labels.pendingOutgoing}: ${snapshot.pendingOutgoingCount}")
        appendLine("${labels.failedOutgoing}: ${snapshot.failedOutgoingCount}")
        appendLine("${labels.nextRetryAt}: ${snapshot.nextRetryAtEpochMillis ?: labels.unavailable}")
        appendLine("${labels.contacts}: ${snapshot.contactCount}")
        appendLine("${labels.localQueue}: ${snapshot.localQueueFingerprint ?: labels.unavailable}")
        appendLine("${labels.contactQueue}: ${snapshot.selectedContactQueueFingerprint ?: labels.unavailable}")
        appendLine("${labels.pairing}: $pairingValue")
        appendLine("${labels.primaryDevice}: $primaryDeviceValue")
        appendLine("${labels.workSync}: $workSyncValue")
        appendLine(labels.events)
        snapshot.events.forEach { event ->
            appendLine("${event.timestampEpochMillis}: ${eventValues[event.type] ?: event.type.name}")
        }
    }
}

const val MAX_TECHNICAL_EVENTS = 100
