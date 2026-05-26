package app.securemessenger.domain

import kotlinx.coroutines.flow.Flow

data class RelayEncryptedBlob(
    val messageId: String,
    val queueId: String,
    val opaqueCiphertext: ByteArray,
    val expiresAtEpochMillis: Long,
)

interface TransportClient {
    suspend fun sendMessage(blob: RelayEncryptedBlob)
    suspend fun fetchMessages(queueId: String): List<RelayEncryptedBlob>
    suspend fun deleteMessage(messageId: String)
    fun observeIncomingMessages(queueId: String): Flow<RelayEncryptedBlob>
}
