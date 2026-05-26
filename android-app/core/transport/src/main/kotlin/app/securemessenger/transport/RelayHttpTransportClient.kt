package app.securemessenger.transport

import app.securemessenger.domain.RelayEncryptedBlob
import app.securemessenger.domain.TransportClient
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class RelayHttpTransportClient(
    private val relayBaseUrl: String,
    private val pollIntervalMillis: Long = 3_000L,
) : TransportClient {
    override suspend fun sendMessage(blob: RelayEncryptedBlob): Unit = withContext(Dispatchers.IO) {
        val request = JSONObject()
            .put("messageId", blob.messageId)
            .put("queueId", blob.queueId)
            .put("expiresAtEpochMillis", blob.expiresAtEpochMillis)
            .put("opaqueCiphertext", Base64.getEncoder().encodeToString(blob.opaqueCiphertext))
        request("POST", "/v1/messages", request.toString())
    }

    override suspend fun fetchMessages(queueId: String): List<RelayEncryptedBlob> = withContext(Dispatchers.IO) {
        val body = request("GET", "/v1/messages/${encodePathSegment(queueId)}")
        val messages = JSONArray(body)
        buildList {
            for (index in 0 until messages.length()) {
                val item = messages.getJSONObject(index)
                add(
                    RelayEncryptedBlob(
                        messageId = item.getString("messageId"),
                        queueId = queueId,
                        opaqueCiphertext = Base64.getDecoder().decode(item.getString("opaqueCiphertext")),
                        expiresAtEpochMillis = item.getLong("expiresAtEpochMillis"),
                    ),
                )
            }
        }
    }

    override suspend fun deleteMessage(messageId: String): Unit = withContext(Dispatchers.IO) {
        request("DELETE", "/v1/messages/${encodePathSegment(messageId)}")
    }

    override fun observeIncomingMessages(queueId: String): Flow<RelayEncryptedBlob> = flow {
        while (true) {
            fetchMessages(queueId).forEach { emit(it) }
            delay(pollIntervalMillis)
        }
    }

    private fun request(method: String, path: String, body: String? = null): String {
        val connection = URL(relayBaseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.setRequestProperty("Content-Type", "application/json")
        if (body != null) {
            connection.doOutput = true
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
        }
        val status = connection.responseCode
        if (status !in 200..299) {
            connection.errorStream?.close()
            connection.disconnect()
            error("Relay request failed with HTTP $status")
        }
        return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.also {
            connection.disconnect()
        }
    }

    private fun encodePathSegment(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
