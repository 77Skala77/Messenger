package app.securemessenger.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** A pending envelope as the relay holds it; [messageId] is assigned by the relay (DEC-024). */
class RelayEncryptedBlob(
    val messageId: String,
    val queueId: String,
    val opaqueCiphertext: ByteArray,
    val expiresAtEpochMillis: Long,
)

/**
 * Read access to one self-certifying relay queue (DEC-024). The queue ID is derived from the
 * 32-byte read secret, so senders learn only the ID and the relay can check a read without storing
 * anything. The secret is a bearer credential: never log it, never store it unencrypted.
 */
class RelayQueueCredentials private constructor(private val readSecret: ByteArray) {
    val queueId: String = queueIdFor(readSecret)

    /** The secret as a copy, e.g. for encrypted storage. The caller wipes it. */
    fun readSecret(): ByteArray = readSecret.copyOf()

    /** Value for `Authorization: Bearer ...`. */
    fun bearer(): String = BASE64_URL.encodeToString(readSecret)

    override fun toString(): String = "RelayQueueCredentials(redacted)"

    companion object {
        const val READ_SECRET_BYTES = 32
        private const val PREFIX = "q3-"
        private val DOMAIN = "smp-relay-queue-v1\u0000".toByteArray(Charsets.US_ASCII)
        private val QUEUE_ID = Regex("^q3-[A-Za-z0-9_-]{43}$")
        private val BASE64_URL = Base64.getUrlEncoder().withoutPadding()

        fun fromReadSecret(readSecret: ByteArray): RelayQueueCredentials {
            require(readSecret.size == READ_SECRET_BYTES) { "Invalid read secret" }
            return RelayQueueCredentials(readSecret.copyOf())
        }

        fun generate(random: SecureRandom = SecureRandom()): RelayQueueCredentials =
            RelayQueueCredentials(ByteArray(READ_SECRET_BYTES).also(random::nextBytes))

        /** True only for the self-certifying form `q3-` + 43 base64url characters. */
        fun isValidQueueId(value: String): Boolean = QUEUE_ID.matches(value)

        private fun queueIdFor(readSecret: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").run {
                update(DOMAIN)
                digest(readSecret)
            }
            return PREFIX + BASE64_URL.encodeToString(digest)
        }
    }
}

interface TransportClient {
    /** Anyone may send to a queue ID. Returns the relay-assigned message ID. */
    suspend fun sendMessage(queueId: String, opaqueCiphertext: ByteArray, expiresAtEpochMillis: Long): String

    /** Needs the queue's read secret. */
    suspend fun fetchMessages(queue: RelayQueueCredentials): List<RelayEncryptedBlob>

    /** Deletes a message of this queue only; needs the queue's read secret. */
    suspend fun deleteMessage(queue: RelayQueueCredentials, messageId: String)
}
