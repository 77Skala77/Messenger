package app.securemessenger.crypto

import app.securemessenger.domain.Invitation
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SerializationAndInvitationTest {
    @Test
    fun `ciphertext package serializes without losing bytes`() {
        val original = StandardAead().encrypt(ByteArray(32) { 7 }, "payload".toByteArray(), "aad".toByteArray())
        val restored = CiphertextPackageCodec.decode(CiphertextPackageCodec.encode(original))

        assertEquals(original.version, restored.version)
        assertEquals(original.suiteId, restored.suiteId)
        assertArrayEquals(original.nonce, restored.nonce)
        assertArrayEquals(original.ciphertext, restored.ciphertext)
        assertArrayEquals(original.tag, restored.tag)
        assertArrayEquals(original.aadHash, restored.aadHash)
    }

    @Test
    fun `contact QR invitation exports and imports public material`() {
        val identity = SimplifiedSessionManager(StandardAead()).createIdentity().publicIdentity
        val original = Invitation(InvitationCodec.VERSION, "queue-12345678", identity)
        val restored = InvitationCodec.decode(InvitationCodec.encode(original))

        assertEquals(original.queueId, restored.queueId)
        assertEquals(identity.deviceId, restored.identity.deviceId)
        assertArrayEquals(identity.encodedPublicKey, restored.identity.encodedPublicKey)
        assertEquals(identity.fingerprint, restored.identity.fingerprint)
    }

    @Test
    fun `paranoia c4 remains disabled without audited primitives`() {
        assertThrows(ExperimentalSuiteDisabledException::class.java) {
            ParanoiaC4Aead().encrypt(ByteArray(32), ByteArray(1), ByteArray(0))
        }
    }
}
