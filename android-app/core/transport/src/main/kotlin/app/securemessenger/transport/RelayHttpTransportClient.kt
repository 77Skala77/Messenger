package app.securemessenger.transport

import app.securemessenger.domain.RelayEncryptedBlob
import app.securemessenger.domain.RelayQueueCredentials
import app.securemessenger.domain.TransportClient
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Relay API v3 (DEC-024): self-certifying queues, relay-assigned message IDs. */
class RelayHttpTransportClient(
    private val relayBaseUrl: String,
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
            JSONObject(response).getString("messageId")
        }

    override suspend fun fetchMessages(queue: RelayQueueCredentials): List<RelayEncryptedBlob> = withContext(Dispatchers.IO) {
        val body = request("GET", queuePath(queue.queueId), bearer = queue.bearer())
        val messages = JSONArray(body)
        buildList {
            for (index in 0 until messages.length()) {
                val item = messages.getJSONObject(index)
                add(
                    RelayEncryptedBlob(
                        messageId = item.getString("messageId"),
                        queueId = queue.queueId,
                        opaqueCiphertext = Base64.getDecoder().decode(item.getString("opaqueCiphertext")),
                        expiresAtEpochMillis = item.getLong("expiresAtEpochMillis"),
                    ),
                )
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
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun encodePathSegment(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}

private class RelayHttpStatusException(val status: Int) :
    IllegalStateException("Relay returned an unsuccessful HTTP status")
