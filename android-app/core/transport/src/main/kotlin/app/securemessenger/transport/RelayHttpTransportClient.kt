package app.securemessenger.transport

import app.securemessenger.domain.RelayEncryptedBlob
import app.securemessenger.domain.RelayQueueCredentials
import app.securemessenger.domain.TransportClient
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Relay API v3 (DEC-024): self-certifying queues, relay-assigned message IDs.
 *
 * Every response is untrusted (Phase 5, DEC-026): its size and reading time are bounded, and each
 * pending message is parsed on its own, so one item the app cannot read is skipped instead of
 * blocking the queue. Skipped items are never passed on; the relay itself refuses them on upload.
 */
class RelayHttpTransportClient(
    private val relayBaseUrl: String,
    private val limits: RelayResponseLimits = RelayResponseLimits(),
) : TransportClient {
    suspend fun checkHealth(): RelayHealthStatus = withContext(Dispatchers.IO) {
        try {
            val body = request("GET", "/health")
            if (JSONObject(body).optString("status") == "ok") {
                RelayHealthStatus.AVAILABLE
            } else {
                RelayHealthStatus.UNAVAILABLE
            }
        } catch (_: RelayHttpStatusException) {
            RelayHealthStatus.UNAVAILABLE
        } catch (_: Exception) {
            RelayHealthStatus.CONNECTION_ERROR
        }
    }

    override suspend fun sendMessage(queueId: String, opaqueCiphertext: ByteArray, expiresAtEpochMillis: Long): String =
        withContext(Dispatchers.IO) {
            val request = JSONObject()
                .put("expiresAtEpochMillis", expiresAtEpochMillis)
                .put("opaqueCiphertext", Base64.getEncoder().encodeToString(opaqueCiphertext))
            val response = request("POST", queuePath(queueId), request.toString())
            parsed { JSONObject(response).getString("messageId") }
        }

    override suspend fun fetchMessages(queue: RelayQueueCredentials): List<RelayEncryptedBlob> = withContext(Dispatchers.IO) {
        val body = request("GET", queuePath(queue.queueId), bearer = queue.bearer())
        val messages = parsed { JSONArray(body) }
        buildList {
            for (index in 0 until minOf(messages.length(), limits.maxMessages)) {
                parseMessage(messages.optJSONObject(index), queue.queueId)?.let(::add)
            }
        }
    }

    override suspend fun deleteMessage(queue: RelayQueueCredentials, messageId: String): Unit = withContext(Dispatchers.IO) {
        request("DELETE", queuePath(queue.queueId) + "/" + encodePathSegment(messageId), bearer = queue.bearer())
    }

    private fun queuePath(queueId: String): String {
        require(RelayQueueCredentials.isValidQueueId(queueId)) { "Invalid queue ID" }
        return "/v1/queues/$queueId/messages"
    }

    /** One pending message, or null if any field is missing or malformed. */
    private fun parseMessage(item: JSONObject?, queueId: String): RelayEncryptedBlob? {
        if (item == null) return null
        val messageId = item.opt("messageId") as? String ?: return null
        val encoded = item.opt("opaqueCiphertext") as? String ?: return null
        val expiresAt = (item.opt("expiresAtEpochMillis") as? Number)?.toLong() ?: return null
        if (!MESSAGE_ID.matches(messageId) || expiresAt <= 0 || encoded.isEmpty() || encoded.length > limits.maxCiphertextChars) {
            return null
        }
        val ciphertext = try {
            Base64.getDecoder().decode(encoded)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return RelayEncryptedBlob(messageId, queueId, ciphertext, expiresAt)
    }

    /** The bearer secret goes only into the Authorization header, never into the URL or logs. */
    private fun request(method: String, path: String, body: String? = null, bearer: String? = null): String {
        val connection = URL(relayBaseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.setRequestProperty("Content-Type", "application/json")
            if (bearer != null) connection.setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                connection.errorStream?.close()
                throw RelayHttpStatusException(status)
            }
            connection.inputStream.use(::readBounded)
        } finally {
            connection.disconnect()
        }
    }

    /** Reads at most [RelayResponseLimits.maxResponseBytes], within [RelayResponseLimits.maxReadMillis]. */
    private fun readBounded(input: InputStream): String {
        val deadline = System.nanoTime() + limits.maxReadMillis * 1_000_000
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > limits.maxResponseBytes) throw RelayResponseException("Relay response is too large")
            output.write(buffer, 0, count)
            if (System.nanoTime() > deadline) throw RelayResponseException("Relay response took too long")
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private inline fun <T> parsed(block: () -> T): T =
        try {
            block()
        } catch (failure: JSONException) {
            throw RelayResponseException("Relay response is not valid", failure)
        }

    private fun encodePathSegment(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        /** Relay-assigned IDs (DEC-024): 16 random bytes in base64url. */
        val MESSAGE_ID = Regex("^[A-Za-z0-9_-]{22}$")
    }
}

/**
 * Bounds on what the app accepts from the relay. A full page (DEC-026: at most 100 messages of at
 * most 64 KB of base64 each) stays well inside them.
 */
class RelayResponseLimits(
    val maxResponseBytes: Int = 8 * 1024 * 1024,
    val maxMessages: Int = 200,
    val maxCiphertextChars: Int = 64 * 1024,
    val maxReadMillis: Long = 30_000,
)

/** The relay answered, but not with something the app can use. Treated like a network failure. */
class RelayResponseException(message: String, cause: Throwable? = null) : IOException(message, cause)

private class RelayHttpStatusException(val status: Int) :
    IllegalStateException("Relay returned an unsuccessful HTTP status")
