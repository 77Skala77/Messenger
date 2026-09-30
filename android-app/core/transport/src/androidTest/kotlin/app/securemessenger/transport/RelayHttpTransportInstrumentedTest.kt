package app.securemessenger.transport

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.securemessenger.domain.RelayEncryptedBlob
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The HTTP transport against a live local relay (`node server/relay/src/server.mjs` on the host and
 * `adb reverse tcp:8787 tcp:8787`). Skipped, not failed, when no relay is reachable. Carries opaque
 * test bytes only; the v2 protocol above it is tested in :core:signal-protocol and :core:ui.
 */
@RunWith(AndroidJUnit4::class)
class RelayHttpTransportInstrumentedTest {
    @Test
    fun opaqueBlobRoundTripsThroughTheRelayAndIsDeleted() = runBlocking {
        val preferences = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("relay_transport_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val settings = RelayServerSettings(preferences)
        settings.saveUrl("http://127.0.0.1:8787")
        val transport = RelayHttpTransportClient(settings.currentUrl())
        val queue = "transport-test-${UUID.randomUUID()}"
        try {
            transport.fetchMessages(queue)
        } catch (unavailable: Exception) {
            assumeNoException("Start the local Node relay and run adb reverse tcp:8787 tcp:8787", unavailable)
        }

        assertEquals(RelayHealthStatus.AVAILABLE, transport.checkHealth())
        assertEquals(RelayHealthStatus.UNAVAILABLE, RelayHttpTransportClient("http://127.0.0.1:8787/not-an-endpoint").checkHealth())
        assertEquals(RelayHealthStatus.CONNECTION_ERROR, RelayHttpTransportClient("http://127.0.0.1:1").checkHealth())

        val payload = ByteArray(300) { (it * 13).toByte() }
        val messageId = UUID.randomUUID().toString()
        transport.sendMessage(RelayEncryptedBlob(messageId, queue, payload, System.currentTimeMillis() + 60_000))

        val fetched = transport.fetchMessages(queue).single()
        assertEquals(messageId, fetched.messageId)
        assertArrayEquals(payload, fetched.opaqueCiphertext)

        transport.deleteMessage(messageId)
        assertTrue(transport.fetchMessages(queue).isEmpty())
    }
}
