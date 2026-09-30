package app.securemessenger.transport

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.securemessenger.domain.RelayQueueCredentials
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The HTTP transport against a live local relay (API v3, DEC-024; `node server/relay/src/server.mjs`
 * on the host and `adb reverse tcp:8787 tcp:8787`). Skipped, not failed, only when the health check
 * cannot reach a relay. Carries opaque test bytes only; the protocol above it is tested elsewhere.
 */
@RunWith(AndroidJUnit4::class)
class RelayHttpTransportInstrumentedTest {
    private lateinit var transport: RelayHttpTransportClient

    @Before
    fun connect() = runBlocking {
        val preferences = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("relay_transport_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val settings = RelayServerSettings(preferences)
        settings.saveUrl("http://127.0.0.1:8787")
        transport = RelayHttpTransportClient(settings.currentUrl())
        assumeTrue(
            "Start the local Node relay and run adb reverse tcp:8787 tcp:8787",
            transport.checkHealth() == RelayHealthStatus.AVAILABLE,
        )
    }

    @Test
    fun healthStatesAreDistinguished() = runBlocking {
        assertEquals(RelayHealthStatus.UNAVAILABLE, RelayHttpTransportClient("http://127.0.0.1:8787/not-an-endpoint").checkHealth())
        assertEquals(RelayHealthStatus.CONNECTION_ERROR, RelayHttpTransportClient("http://127.0.0.1:1").checkHealth())
    }

    @Test
    fun opaqueBlobRoundTripsThroughItsQueueAndIsDeleted() = runBlocking {
        val queue = RelayQueueCredentials.generate()
        val payload = ByteArray(300) { (it * 13).toByte() }

        val messageId = transport.sendMessage(queue.queueId, payload, System.currentTimeMillis() + 60_000)

        val fetched = transport.fetchMessages(queue).single()
        assertEquals(messageId, fetched.messageId)
        assertArrayEquals(payload, fetched.opaqueCiphertext)
        transport.deleteMessage(queue, messageId)
        assertTrue(transport.fetchMessages(queue).isEmpty())
    }

    /** DEC-024: knowing a queue ID lets you send, never read or delete. */
    @Test
    fun anotherQueuesSecretCannotReadOrDelete() = runBlocking {
        val queue = RelayQueueCredentials.generate()
        val stranger = RelayQueueCredentials.generate()
        val messageId = transport.sendMessage(queue.queueId, byteArrayOf(1, 2, 3), System.currentTimeMillis() + 60_000)

        // The client type cannot even express a foreign secret for this queue; an attacker would send
        // one by hand.
        assertEquals(403, rawStatus("GET", "/v1/queues/${queue.queueId}/messages", stranger.bearer()))
        assertEquals(403, rawStatus("DELETE", "/v1/queues/${queue.queueId}/messages/$messageId", stranger.bearer()))
        assertEquals(403, rawStatus("GET", "/v1/queues/${queue.queueId}/messages", null))
        // The stranger's own queue cannot reach this message either.
        transport.deleteMessage(stranger, messageId)
        assertEquals(messageId, transport.fetchMessages(queue).single().messageId)
        transport.deleteMessage(queue, messageId)
    }

    private fun rawStatus(method: String, path: String, bearer: String?): Int {
        val connection = URL("http://127.0.0.1:8787$path").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            if (bearer != null) connection.setRequestProperty("Authorization", "Bearer $bearer")
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }
}
