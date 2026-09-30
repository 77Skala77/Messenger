package app.securemessenger.domain

import java.security.MessageDigest
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayQueueCredentialsTest {
    /** The same derivation as the relay (`queueIdForReadSecret` in server/relay/src/relay-store.mjs). */
    @Test
    fun queueIdIsTheDomainSeparatedHashOfTheReadSecret() {
        val secret = ByteArray(32) { it.toByte() }
        val credentials = RelayQueueCredentials.fromReadSecret(secret)
        val digest = MessageDigest.getInstance("SHA-256").run {
            update("smp-relay-queue-v1\u0000".toByteArray())
            digest(secret)
        }
        assertEquals("q3-" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest), credentials.queueId)
        // Pinned value, identical to the vector in server/relay/test/relay.test.mjs.
        assertEquals(SHARED_VECTOR, credentials.queueId)
        assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(secret), credentials.bearer())
        assertTrue(RelayQueueCredentials.isValidQueueId(credentials.queueId))
    }

    @Test
    fun onlyTheSelfCertifyingFormIsAValidQueueId() {
        assertFalse(RelayQueueCredentials.isValidQueueId("v2-00000000-0000-0000-0000-000000000000"))
        assertFalse(RelayQueueCredentials.isValidQueueId("inv2-AAAAAAAAAAAAAAAAAAAAAA"))
        assertFalse(RelayQueueCredentials.isValidQueueId("q3-short"))
        assertFalse(RelayQueueCredentials.isValidQueueId("q3-" + "A".repeat(42) + "="))
        assertThrows(IllegalArgumentException::class.java) { RelayQueueCredentials.fromReadSecret(ByteArray(16)) }
    }

    @Test
    fun generatedQueuesDifferAndNeverPrintTheSecret() {
        val first = RelayQueueCredentials.generate()
        val second = RelayQueueCredentials.generate()
        assertNotEquals(first.queueId, second.queueId)
        assertFalse(first.toString().contains(first.bearer()))
    }

    private companion object {
        /** Read secret 0x00..0x1f. */
        const val SHARED_VECTOR = "q3--3ndGxCkLNwXdS9yA7nYaH2uIosiRXqmYXeHybuC5Po"
    }
}
