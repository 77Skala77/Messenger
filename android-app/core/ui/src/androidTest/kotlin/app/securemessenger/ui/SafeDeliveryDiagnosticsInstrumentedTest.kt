package app.securemessenger.ui

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.securemessenger.transport.RelayHealthStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafeDeliveryDiagnosticsInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = SafeDeliveryDiagnosticsStore(context) { 500L }

    @After
    fun wipe() {
        store.clearForLocalWipe()
    }

    @Test
    fun clearDiagnosticsRemovesTechnicalHistoryButDoesNotSilentlyUndoPairingConfirmation() {
        store.record(DeliveryTechnicalEvent.RELAY_HEALTH_OK)
        store.recordRelayHealth(RelayHealthStatus.AVAILABLE)
        store.markSuccessfulSync()
        store.setWorkSyncStatus(WorkSyncStatus.SUCCEEDED)
        store.confirmReciprocalPairing("contact-local-id")

        store.clearDiagnostics()

        assertTrue(store.events().isEmpty())
        assertNull(store.lastRelayHealthStatus())
        assertNull(store.lastSuccessfulSyncAtEpochMillis())
        assertEquals(WorkSyncStatus.UNKNOWN, store.workSyncStatus())
        assertTrue(store.isReciprocalPairingConfirmed("contact-local-id"))
    }
}
