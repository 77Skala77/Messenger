package app.securemessenger.ui

import app.securemessenger.signalstore.V2ChatSummary
import app.securemessenger.signalstore.V2Contact
import app.securemessenger.signalstore.V2ContactState
import kotlin.math.abs
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner's wishes of 2026-10-07 (with DEC-039): Telegram's trim of a paused recording, the drag along a
 * voice message's bars, finer dust, the snap, and the counter on the Chats tab.
 */
class VoiceTrimTest {
    /** [count] ADTS frames of 16 kHz, each with a payload of [payload] bytes all set to its number plus one. */
    private fun frames(count: Int, payload: Int = 10): ByteArray =
        (0 until count).fold(ByteArray(0)) { all, index -> all + adtsHeader(payload, 16_000) + ByteArray(payload) { (index + 1).toByte() } }

    private fun frameNumbers(bytes: ByteArray, length: Int, payload: Int = 10): List<Int> =
        (0 until length / (payload + ADTS_HEADER_BYTES)).map { bytes[it * (payload + ADTS_HEADER_BYTES) + ADTS_HEADER_BYTES].toInt() }

    @Test
    fun `a trim keeps the frames that start inside the stretch and zeroes the rest`() {
        // Frames of 1,024 samples at 16 kHz last 64 ms: frame 3 starts at 192 ms, frame 6 at 384 ms.
        val bytes = frames(10)
        val result = trimAdtsInPlace(bytes, bytes.size, fromMillis = 150, toMillis = 384)
        assertEquals(listOf(4, 5, 6), frameNumbers(bytes, result.bytes))
        assertEquals(3, result.framesKept)
        assertEquals(7, result.framesRemoved)
        assertEquals(3, result.framesBefore)
        assertTrue("nothing cut off stays in memory", bytes.copyOfRange(result.bytes, bytes.size).all { it == 0.toByte() })
    }

    @Test
    fun `a trim to the end keeps everything from the start on`() {
        val bytes = frames(5)
        val result = trimAdtsInPlace(bytes, bytes.size, 0, Long.MAX_VALUE)
        assertEquals(listOf(1, 2, 3, 4, 5), frameNumbers(bytes, result.bytes))
        assertEquals(0, result.framesRemoved)
    }

    @Test
    fun `a trim stops at a damaged header and keeps nothing after it`() {
        val bytes = frames(4)
        bytes[2 * 17] = 0x12 // The third frame's sync word.
        val result = trimAdtsInPlace(bytes, bytes.size, 0, Long.MAX_VALUE)
        assertEquals(listOf(1, 2), frameNumbers(bytes, result.bytes))
        assertTrue(bytes.copyOfRange(result.bytes, bytes.size).all { it == 0.toByte() })
        assertEquals(0, trimAdtsInPlace(ByteArray(3), 3, 0, 100).bytes)
    }

    @Test
    fun `the bars and the handles follow Telegram's rules`() {
        assertArrayEquals(byteArrayOf(3, 4, 5, 6), sliceWaveform(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), 0.2f, 0.6f))
        assertArrayEquals("never empty", byteArrayOf(10), sliceWaveform(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), 1f, 1f))
        assertArrayEquals(ByteArray(0), sliceWaveform(ByteArray(0), 0f, 1f))

        // At least a second and 30 dp are kept.
        assertEquals(0.1f, trimMinimumSpan(10_000, 1_000f, 30f), 0.0001f)
        assertEquals(0.15f, trimMinimumSpan(10_000, 200f, 30f), 0.0001f)
        assertTrue(trimCuts(0.02f, 1f))
        assertTrue(trimCuts(0f, 0.98f))
        assertFalse(trimCuts(0.005f, 0.995f))

        val heights = trimBarHeights(byteArrayOf(4, 8, 12), 3)
        assertArrayEquals(floatArrayOf(0f, 0.5f, 1f), heights, 0.0001f)
        assertArrayEquals("flat stays low", floatArrayOf(0f, 0f), trimBarHeights(byteArrayOf(5, 5), 2), 0f)
    }

    @Test
    fun `a drag along the bars moves the place from where it was`() {
        assertEquals(0.5f, seekFraction(0.25f, 50f, 200f), 0.0001f)
        assertEquals(0f, seekFraction(0.1f, -500f, 200f), 0f)
        assertEquals(1f, seekFraction(0.9f, 500f, 200f), 0f)
        assertEquals(0.3f, seekFraction(0.3f, 10f, 0f), 0f)
    }

    @Test
    fun `dust grains are as fine as Telegram's unless a bubble is too big`() {
        // 0.4 dp at a density of 3 is 1.2 px: two pixels.
        assertEquals(2, dustCellPx(300, 100, 3f))
        assertEquals(1, dustCellPx(100, 50, 1f))
        // A big bubble stays within the number of grains.
        val cell = dustCellPx(1_000, 1_000, 3f)
        assertTrue((1_000 / cell) * (1_000 / cell) <= DustRules.MAX_GRAINS)
    }

    @Test
    fun `the snap is short, never clips and fades out`() {
        val samples = fingerSnapSamples(44_100)
        assertEquals((44_100 * 0.14).toInt(), samples.size)
        val peak = samples.maxOf { abs(it.toInt()) }
        assertTrue(peak in 26_000..26_300)
        assertTrue("silent at the end", samples.takeLast(200).all { abs(it.toInt()) < 400 })
        assertArrayEquals("the same every time", samples, fingerSnapSamples(44_100))
    }

    @Test
    fun `the chats tab counts unread chats that are not muted and the requests`() {
        fun chat(id: String, unread: Int, mutedUntil: Long? = null) = V2ChatSummary(
            V2Contact(id, id, byteArrayOf(1), "q", "aa", "bb", V2ContactState.ACTIVE, 1, mutedUntilEpochMillis = mutedUntil),
            null,
            unread,
        )
        val state = ConversationV2UiState(chats = listOf(chat("a", 3), chat("b", 0), chat("c", 2, mutedUntil = Long.MAX_VALUE)))
        assertEquals(1, homeUnreadCount(state, 100))
        assertEquals(2, homeUnreadCount(ConversationV2UiState(chats = listOf(chat("a", 1), chat("c", 2, mutedUntil = 50))), 100))
    }
}
