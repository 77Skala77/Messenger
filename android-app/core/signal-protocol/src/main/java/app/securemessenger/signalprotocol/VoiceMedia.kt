package app.securemessenger.signalprotocol

import app.securemessenger.domain.RelayQueueCredentials
import app.securemessenger.signalstore.V2VoiceNote
import java.nio.ByteBuffer
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The recording of a voice message on its way through the relay (DEC-036).
 *
 * It is cut into equal pieces of [CHUNK_BYTES], the last one filled up with zeros, so the relay
 * learns only how many pieces there are. Each piece is encrypted with AES-256-GCM under the note's
 * key, a fresh random key for every recording that travels only inside the encrypted payload. The
 * nonce is the piece's index and the associated data names the index and the number of pieces, so
 * a piece cannot be moved to another place, repeated, or left out unnoticed; the digest of the whole
 * padded recording is checked once all pieces are here. A standard AEAD with a key used for one
 * recording only: no construction of our own (AGENTS.md).
 *
 * A piece as it travels: `"SMM1"`, version 1, its index (16 bits), then the ciphertext with its tag.
 */
object VoiceMedia {
    /**
     * A piece in base64 must stay within what the relay and the app take for one message
     * ([RELAY_MAX_CIPHERTEXT_CHARS]): 47 KiB of recording make 64,204 characters.
     */
    const val CHUNK_BYTES = 47 * 1024

    /** At 32 kbit/s fifteen minutes take about 77 pieces; the rest is room for the encoder. */
    const val MAX_CHUNKS = 96
    const val MAX_RECORDING_BYTES = CHUNK_BYTES * MAX_CHUNKS
    const val KEY_BYTES = 32
    const val DIGEST_BYTES = 32

    /** AAC-LC in ADTS frames, the only format so far. */
    const val CODEC_AAC_ADTS = 1

    /** Fifteen minutes (owner, DEC-036), with room for the encoder's last frames. */
    const val MAX_DURATION_MILLIS = 15 * 60 * 1000 + 2_000
    const val WAVEFORM_MAX_SAMPLES = 100
    const val WAVEFORM_MAX_VALUE = 31

    private val MAGIC = byteArrayOf(0x53, 0x4D, 0x4D, 0x31) // "SMM1"
    private const val VERSION = 1
    private const val TAG_BYTES = 16
    private const val HEADER_BYTES = 4 + 1 + 2

    /** Every piece on the relay has exactly this size. */
    const val PIECE_BYTES = HEADER_BYTES + CHUNK_BYTES + TAG_BYTES

    /** The relay's `maxCiphertextChars` and the app's `RelayResponseLimits.maxCiphertextChars`. */
    const val RELAY_MAX_CIPHERTEXT_CHARS = 64 * 1024

    private val AAD_DOMAIN = "smp-voice-piece-v1\u0000".toByteArray(Charsets.US_ASCII)
    private val DIGEST_DOMAIN = "smp-voice-recording-v1\u0000".toByteArray(Charsets.US_ASCII)

    /** A recording ready to travel: its pieces and the values the note carries. */
    class Sealed(val pieces: List<ByteArray>, val digest: ByteArray, val plaintextSize: Int)

    fun chunkCountFor(size: Int): Int = (size + CHUNK_BYTES - 1) / CHUNK_BYTES

    /** Cuts, pads and encrypts [recording] under [key]. */
    fun seal(recording: ByteArray, key: ByteArray): Sealed {
        require(recording.size in 1..MAX_RECORDING_BYTES) { "Invalid recording size" }
        require(key.size == KEY_BYTES) { "Invalid key size" }
        val count = chunkCountFor(recording.size)
        val padded = recording.copyOf(count * CHUNK_BYTES)
        try {
            val digest = sha256(DIGEST_DOMAIN, padded)
            val pieces = (0 until count).map { index ->
                val ciphertext = cipher(Cipher.ENCRYPT_MODE, key, index, count).doFinal(padded, index * CHUNK_BYTES, CHUNK_BYTES)
                V2Writer().raw(MAGIC).u8(VERSION).u16(index).raw(ciphertext).toByteArray()
            }
            return Sealed(pieces, digest, recording.size)
        } finally {
            padded.fill(0)
        }
    }

    /**
     * The index of a piece that decrypts under [note] at its own place, or null for anything else:
     * junk someone put in the queue, a damaged piece, or a piece of another recording.
     */
    fun checkPiece(piece: ByteArray, note: V2VoiceNote): Int? =
        try {
            val (index, plaintext) = openPiece(piece, note)
            plaintext.fill(0)
            index
        } catch (_: MalformedV2Exception) {
            null
        }

    /**
     * The recording from all of its pieces (in any order), or null if they are not exactly the
     * note's pieces, the whole does not match the note's digest, or the padding is not zeros.
     */
    fun open(pieces: List<ByteArray>, note: V2VoiceNote): ByteArray? {
        if (pieces.size != note.chunkCount) return null
        val padded = ByteArray(note.chunkCount * CHUNK_BYTES)
        val seen = BooleanArray(note.chunkCount)
        try {
            for (piece in pieces) {
                val (index, plaintext) = openPiece(piece, note)
                if (seen[index]) return null
                seen[index] = true
                plaintext.copyInto(padded, index * CHUNK_BYTES)
                plaintext.fill(0)
            }
            if (!sha256(DIGEST_DOMAIN, padded).contentEquals(note.digest)) return null
            for (position in note.plaintextSize until padded.size) {
                if (padded[position] != 0.toByte()) return null
            }
            return padded.copyOf(note.plaintextSize)
        } catch (_: MalformedV2Exception) {
            return null
        } finally {
            padded.fill(0)
        }
    }

    /** Whether a note keeps to the rules; the payload decoder and the sender both check it. */
    fun isAcceptable(note: V2VoiceNote): Boolean =
        note.queueReadSecret.size == RelayQueueCredentials.READ_SECRET_BYTES &&
            note.key.size == KEY_BYTES &&
            note.digest.size == DIGEST_BYTES &&
            note.plaintextSize in 1..MAX_RECORDING_BYTES &&
            note.chunkCount == chunkCountFor(note.plaintextSize) &&
            note.durationMillis in 1..MAX_DURATION_MILLIS &&
            note.waveform.size <= WAVEFORM_MAX_SAMPLES &&
            note.waveform.all { it in 0..WAVEFORM_MAX_VALUE } &&
            note.codec == CODEC_AAC_ADTS

    private fun openPiece(piece: ByteArray, note: V2VoiceNote): Pair<Int, ByteArray> {
        if (piece.size != PIECE_BYTES) throw MalformedV2Exception("Invalid piece size")
        val reader = V2Reader(piece)
        reader.expect(MAGIC)
        if (reader.u8() != VERSION) throw MalformedV2Exception("Unsupported piece version")
        val index = reader.u16()
        if (index >= note.chunkCount) throw MalformedV2Exception("Piece out of range")
        val ciphertext = reader.raw(CHUNK_BYTES + TAG_BYTES)
        reader.requireEnd()
        val plaintext = try {
            cipher(Cipher.DECRYPT_MODE, note.key, index, note.chunkCount).doFinal(ciphertext)
        } catch (_: AEADBadTagException) {
            throw MalformedV2Exception("Piece does not authenticate")
        }
        return index to plaintext
    }

    private fun cipher(mode: Int, key: ByteArray, index: Int, count: Int): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTES * 8, nonce(index)))
            updateAAD(AAD_DOMAIN + ByteBuffer.allocate(4).putShort(count.toShort()).putShort(index.toShort()).array())
        }

    /** Twelve bytes: four zeros and the index. Unique per piece; the key is used for one recording only. */
    private fun nonce(index: Int): ByteArray = ByteBuffer.allocate(12).putInt(0).putLong(index.toLong()).array()
}
