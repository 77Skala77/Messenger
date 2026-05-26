package app.securemessenger.signalstore

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class SignalStoreSpikeTest {
    @Test
    fun `signal session adapter remains disabled by default`() {
        val adapter = DisabledSignalSessionManagerAdapter()

        assertFalse(SignalStoreFeatureFlags.SIGNAL_BACKED_SESSIONS_ENABLED)
        assertFalse(adapter.enabled)
        assertThrows(SignalSpikeDisabledException::class.java) {
            adapter.createExperimentalSession(sampleInvitation())
        }
    }

    @Test
    fun `experimental signal invitation v2 serializes neutral placeholder bundle`() {
        val original = sampleInvitation()
        val encoded = ExperimentalSignalInvitationCodec.encode(original)
        val restored = ExperimentalSignalInvitationCodec.decode(encoded)

        assertEquals(ExperimentalSignalInvitationCodec.FORMAT_ID, "INVITE_V2_SIGNAL_PQXDH_EXPERIMENTAL")
        assertEquals(original.protocolVersion, restored.protocolVersion)
        assertEquals(original.receiveQueueId, restored.receiveQueueId)
        assertEquals(original.oneTimePreKeyId, restored.oneTimePreKeyId)
        assertEquals(original.kyberPreKeyId, restored.kyberPreKeyId)
        assertArrayEquals(original.identityPublicKey, restored.identityPublicKey)
        assertArrayEquals(original.signedPreKeyPublicKey, restored.signedPreKeyPublicKey)
        assertArrayEquals(original.signedPreKeySignaturePlaceholder, restored.signedPreKeySignaturePlaceholder)
        assertArrayEquals(original.oneTimePreKeyPublicKeyPlaceholder, restored.oneTimePreKeyPublicKeyPlaceholder)
        assertArrayEquals(original.kyberPreKeyPublicKeyPlaceholder, restored.kyberPreKeyPublicKeyPlaceholder)
    }

    @Test
    fun `signalstore spike does not add logging calls`() {
        File("src/main/kotlin").walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { source ->
                val content = source.readText()
                assertFalse(source.path, content.contains("android.util.Log"))
                assertFalse(source.path, Regex("""\bprintln\(""").containsMatchIn(content))
                assertFalse(source.path, Regex("""\bprint\(""").containsMatchIn(content))
            }
    }

    private fun sampleInvitation() = ExperimentalSignalPqxdhInvitation(
        protocolVersion = ExperimentalSignalInvitationCodec.PROTOCOL_VERSION,
        receiveQueueId = "experimental-receive-queue",
        identityPublicKey = byteArrayOf(1, 2, 3),
        signedPreKeyPublicKey = byteArrayOf(4, 5, 6),
        signedPreKeySignaturePlaceholder = byteArrayOf(7, 8),
        oneTimePreKeyId = 11,
        oneTimePreKeyPublicKeyPlaceholder = byteArrayOf(9, 10),
        kyberPreKeyId = 12,
        kyberPreKeyPublicKeyPlaceholder = byteArrayOf(11, 12),
    )
}
