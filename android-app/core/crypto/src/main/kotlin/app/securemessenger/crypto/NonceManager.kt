package app.securemessenger.crypto

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

data class NonceState(val randomPrefix: ByteArray, val lastCounter: Long)

interface NonceStateStore {
    fun read(scope: String): NonceState?
    fun write(scope: String, state: NonceState)
}

interface NonceManager {
    fun generateNonce(key: ByteArray): ByteArray
}

class InMemoryNonceStateStore : NonceStateStore {
    private val states = ConcurrentHashMap<String, NonceState>()

    override fun read(scope: String): NonceState? = states[scope]

    override fun write(scope: String, state: NonceState) {
        states[scope] = state
    }
}

class PersistentCounterNonceManager(
    private val store: NonceStateStore,
    private val random: SecureRandom = SecureRandom(),
) : NonceManager {
    private val observedNonces = ConcurrentHashMap.newKeySet<String>()

    @Synchronized
    override fun generateNonce(key: ByteArray): ByteArray {
        require(key.isNotEmpty()) { "Key must not be empty" }
        val scope = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(key))
        val prior = store.read(scope)
        val prefix = prior?.randomPrefix?.copyOf() ?: ByteArray(4).also(random::nextBytes)
        val nextCounter = (prior?.lastCounter ?: 0L) + 1L
        check(nextCounter > 0) { "Nonce counter exhausted" }

        // Persist before encryption, so a crash cannot make the same nonce available again.
        store.write(scope, NonceState(prefix.copyOf(), nextCounter))
        val nonce = ByteBuffer.allocate(12).put(prefix).putLong(nextCounter).array()
        val identity = "$scope:${Base64.getEncoder().encodeToString(nonce)}"
        if (!observedNonces.add(identity)) {
            throw NonceReuseException()
        }
        return nonce
    }
}
