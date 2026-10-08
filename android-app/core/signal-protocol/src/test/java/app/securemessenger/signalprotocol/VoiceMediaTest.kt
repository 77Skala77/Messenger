package app.securemessenger.signalprotocol

import app.securemessenger.domain.RelayQueueCredentials
import app.securemessenger.signalstore.V2VoiceNote
import java.security.SecureRandom
import java.util.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DEC-036: the recording of a voice message travels as equal pieces, each encrypted under the
 * recording's own key and bound to its place; nothing but the exact pieces gives the recording back.
 */
class VoiceMediaTest {
    private val secure = SecureRandom()

    @Test
    fun aRecordingComesBackExactlyFromItsPiecesInAnyOrder() {
        listOf(1, VoiceMedia.CHUNK_BYTES, VoiceMedia.CHUNK_BYTES + 1, 150_000).forEach { size ->
            val recording = ByteArray(size).also(Random(size.toLong())::nextBytes)
            val (note, pieces) = sealed(recording)
            assertEquals(VoiceMedia.chunkCountFor(size), pieces.size)
            assertTrue("every piece has the same size", pieces.all { it.size == VoiceMedia.PIECE_BYTES })
            assertArrayEquals(recording, VoiceMedia.open(pieces.reversed(), note))
            pieces.forEachIndexed { index, piece -> assertEquals(index, VoiceMedia.checkPiece(piece, note)) }
        }
    }

    /** A piece the relay refused, or the app skipped on fetching, would hold its note back for good. */
    @Test
    fun aPieceInBase64FitsWhatTheRelayAndTheAppTakeForOneMessage() {
        val (_, pieces) = sealed(ByteArray(10).also(Random(3)::nextBytes))
        val encoded = java.util.Base64.getEncoder().encodeToString(pieces.single())
        assertTrue(encoded.length <= VoiceMedia.RELAY_MAX_CIPHERTEXT_CHARS)
        assertEquals(64 * 1024, VoiceMedia.RELAY_MAX_CIPHERTEXT_CHARS)
        // Fifteen minutes at 32 kbit/s (about 4.1 KB a second with the frame headers) fit.
        assertTrue(15 * 60 * 4_200 <= VoiceMedia.MAX_RECORDING_BYTES)
    }

    /**
     * The limit is the relay's and the transport's own, not a number made up here. Read from the
     * repository, so it runs on the computer only (on a phone the files are not there: skipped).
     */
    @Test
    fun theRelayAndTheTransportTakeExactlyThatLimit() {
        val relay = java.io.File("../../server/relay/src/relay-store.mjs")
        val transport = java.io.File("../transport/src/main/kotlin/app/securemessenger/transport/RelayHttpTransportClient.kt")
        org.junit.Assume.assumeTrue(relay.exists() && transport.exists())
        assertTrue(relay.readText().contains("maxCiphertextChars = 64 * 1024,"))
        assertTrue(transport.readText().contains("val maxCiphertextChars: Int = 64 * 1024,"))
    }

    @Test
    fun aChangedMovedForeignMissingOrRepeatedPieceIsRefused() {
        val recording = ByteArray(120_000).also(Random(7)::nextBytes)
        val (note, pieces) = sealed(recording)

        // One bit changed anywhere in the ciphertext.
        val changed = pieces[1].copyOf().also { it[100] = (it[100].toInt() xor 1).toByte() }
        assertNull(VoiceMedia.checkPiece(changed, note))
        assertNull(VoiceMedia.open(listOf(pieces[0], changed, pieces[2]), note))
        // A piece relabelled as another place: the index is authenticated.
        val moved = pieces[0].copyOf().also { it[6] = 1 }
        assertNull(VoiceMedia.checkPiece(moved, note))
        // A piece of another recording, even of the same size.
        val (_, foreign) = sealed(ByteArray(120_000).also(Random(8)::nextBytes))
        assertNull(VoiceMedia.checkPiece(foreign[1], note))
        // Left out, or one place twice.
        assertNull(VoiceMedia.open(pieces.dropLast(1), note))
        assertNull(VoiceMedia.open(listOf(pieces[0], pieces[0], pieces[2]), note))
        // Junk of any size.
        assertNull(VoiceMedia.checkPiece(ByteArray(0), note))
        assertNull(VoiceMedia.checkPiece(ByteArray(VoiceMedia.PIECE_BYTES), note))
        // A note whose digest is not the recording's.
        assertNull(VoiceMedia.open(pieces, note.copy(digest = ByteArray(VoiceMedia.DIGEST_BYTES))))
        // A note that says there are more pieces: the associated data names the count.
        assertNull(VoiceMedia.checkPiece(pieces[0], note.copy(plaintextSize = VoiceMedia.CHUNK_BYTES * 3 + 1, chunkCount = 4)))
    }

    @Test
    fun aNoteKeepsToItsRules() {
        val (note, _) = sealed(ByteArray(1_000).also(Random(9)::nextBytes))
        assertTrue(VoiceMedia.isAcceptable(note))
        listOf(
            note.copy(chunkCount = 2),
            note.copy(plaintextSize = 0, chunkCount = 0),
            note.copy(plaintextSize = VoiceMedia.MAX_RECORDING_BYTES + 1, chunkCount = VoiceMedia.MAX_CHUNKS + 1),
            note.copy(durationMillis = 0),
            note.copy(durationMillis = VoiceMedia.MAX_DURATION_MILLIS + 1),
            note.copy(waveform = ByteArray(VoiceMedia.WAVEFORM_MAX_SAMPLES + 1)),
            note.copy(waveform = byteArrayOf(32)),
            note.copy(waveform = byteArrayOf(-1)),
            note.copy(codec = 2),
            note.copy(key = ByteArray(16)),
            note.copy(queueReadSecret = ByteArray(31)),
        ).forEach { bad -> assertFalse(VoiceMedia.isAcceptable(bad)) }
        // Fifteen minutes is allowed (owner, DEC-036).
        assertTrue(VoiceMedia.isAcceptable(note.copy(durationMillis = 15 * 60 * 1000)))
    }

    private fun sealed(recording: ByteArray): Pair<V2VoiceNote, List<ByteArray>> {
        val key = ByteArray(VoiceMedia.KEY_BYTES).also(secure::nextBytes)
        val sealed = VoiceMedia.seal(recording, key)
        val note = V2VoiceNote(
            queueReadSecret = RelayQueueCredentials.generate(secure).readSecret(),
            key = key,
            digest = sealed.digest,
            plaintextSize = sealed.plaintextSize,
            chunkCount = sealed.pieces.size,
            durationMillis = 7_000,
            waveform = ByteArray(40) { (it % 32).toByte() },
            codec = VoiceMedia.CODEC_AAC_ADTS,
        )
        return note to sealed.pieces
    }

    private fun V2VoiceNote.copy(
        queueReadSecret: ByteArray = this.queueReadSecret,
        key: ByteArray = this.key,
        digest: ByteArray = this.digest,
        plaintextSize: Int = this.plaintextSize,
        chunkCount: Int = this.chunkCount,
        durationMillis: Int = this.durationMillis,
        waveform: ByteArray = this.waveform,
        codec: Int = this.codec,
    ) = V2VoiceNote(queueReadSecret, key, digest, plaintextSize, chunkCount, durationMillis, waveform, codec)
}
