package app.securemessenger.transport

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.securemessenger.domain.RelayQueueCredentials
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.Base64
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 5 (DEC-026): the relay's answers are untrusted. A tiny HTTP server inside the test plays a
 * hostile relay, so neither a live relay nor `adb reverse` is needed. Whatever it sends, the client
 * returns only well-formed items, refuses oversized or slow answers, and never runs out of memory.
 */
@RunWith(AndroidJUnit4::class)
class RelayHostileResponseInstrumentedTest {
    private val queue = RelayQueueCredentials.generate()

    @Test
    fun unreadableItemsAreSkippedAndTheOthersArrive() = runBlocking {
        val body = JSONArray()
            .put(item(id(1), byteArrayOf(1, 2, 3)))
            .put(JSONObject().put("messageId", id(2)).put("opaqueCiphertext", "not base64!").put("expiresAtEpochMillis", FUTURE))
            .put(JSONObject().put("messageId", "../../health").put("opaqueCiphertext", "AQID").put("expiresAtEpochMillis", FUTURE))
            .put(JSONObject().put("opaqueCiphertext", "AQID").put("expiresAtEpochMillis", FUTURE))
            .put(JSONObject().put("messageId", id(3)).put("opaqueCiphertext", 5).put("expiresAtEpochMillis", FUTURE))
            .put(JSONObject().put("messageId", id(4)).put("opaqueCiphertext", "AQID").put("expiresAtEpochMillis", "soon"))
            .put(JSONObject().put("messageId", id(5)).put("opaqueCiphertext", "").put("expiresAtEpochMillis", FUTURE))
            .put(JSONObject().put("messageId", id(6)).put("opaqueCiphertext", "A".repeat(70_000)).put("expiresAtEpochMillis", FUTURE))
            .put("just a string")
            .put(JSONObject.NULL)
            .put(item(id(7), byteArrayOf(4, 5)))

        withHostileRelay(body.toString()) { url ->
            val fetched = RelayHttpTransportClient(url).fetchMessages(queue)
            assertEquals(listOf(id(1), id(7)), fetched.map { it.messageId })
            assertArrayEquals(byteArrayOf(1, 2, 3), fetched.first().opaqueCiphertext)
            assertEquals(queue.queueId, fetched.first().queueId)
        }
    }

    @Test
    fun anAnswerThatIsNotAListIsARelayError() = runBlocking {
        for (body in listOf("{}", "not json", "", "[1, 2", "null")) {
            withHostileRelay(body) { url ->
                assertThrows(body, IOException::class.java) { runBlocking { RelayHttpTransportClient(url).fetchMessages(queue) } }
            }
        }
    }

    @Test
    fun anOversizedAnswerIsRefusedWithoutReadingItAll() = runBlocking {
        // 9 MB: more than any real page (100 messages of at most 64 KB) can be.
        withHostileRelay("[\"" + "A".repeat(9 * 1024 * 1024) + "\"]") { url ->
            assertThrows(IOException::class.java) { runBlocking { RelayHttpTransportClient(url).fetchMessages(queue) } }
        }
    }

    @Test
    fun aHugePageIsCappedAndASlowAnswerIsCutOff() = runBlocking {
        val many = JSONArray().also { array -> repeat(500) { array.put(item(id(it), byteArrayOf(it.toByte()))) } }
        withHostileRelay(many.toString()) { url ->
            assertEquals(RelayResponseLimits().maxMessages, RelayHttpTransportClient(url).fetchMessages(queue).size)
        }
        withHostileRelay("[" + " ".repeat(64 * 1024) + "]", dripMillis = 300) { url ->
            val client = RelayHttpTransportClient(url, RelayResponseLimits(maxReadMillis = 1_000))
            assertThrows(IOException::class.java) { runBlocking { client.fetchMessages(queue) } }
        }
    }

    /** Serves [body] to every request, all at once or in 1 KB pieces every [dripMillis]. */
    private suspend fun withHostileRelay(body: String, dripMillis: Long = 0, block: suspend (String) -> Unit) {
        val server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        val bytes = body.toByteArray(Charsets.UTF_8)
        val worker = thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (_: IOException) {
                    break
                }
                socket.use { connection ->
                    try {
                        val request = connection.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                        while (request.readLine().orEmpty().isNotEmpty()) Unit
                        val output = connection.getOutputStream()
                        val head = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                        output.write(head.toByteArray(Charsets.ISO_8859_1))
                        var offset = 0
                        while (offset < bytes.size) {
                            val end = if (dripMillis == 0L) bytes.size else minOf(bytes.size, offset + 1024)
                            output.write(bytes, offset, end - offset)
                            output.flush()
                            offset = end
                            if (dripMillis > 0) Thread.sleep(dripMillis)
                        }
                    } catch (_: IOException) {
                        // The client hung up, as it should on a hostile answer.
                    }
                }
            }
        }
        try {
            block("http://127.0.0.1:${server.localPort}")
        } finally {
            server.close()
            worker.join(5_000)
        }
    }

    private fun item(messageId: String, ciphertext: ByteArray) = JSONObject()
        .put("messageId", messageId)
        .put("opaqueCiphertext", Base64.getEncoder().encodeToString(ciphertext))
        .put("expiresAtEpochMillis", FUTURE)

    /** A relay-shaped message ID: 22 base64url characters. */
    private fun id(index: Int) = "%022d".format(index)

    private companion object {
        const val FUTURE = 4_000_000_000_000L
    }
}
