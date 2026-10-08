package app.securemessenger.ui

import app.securemessenger.signalprotocol.VoiceMedia
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** DEC-036: the plain rules behind voice messages, as Telegram shows them. */
class VoiceModelTest {
    @Test
    fun `lengths read like Telegram's`() {
        assertEquals("00:00", formatVoiceDuration(0))
        assertEquals("00:07", formatVoiceDuration(7_999))
        assertEquals("01:05", formatVoiceDuration(65_000))
        assertEquals("15:00", formatVoiceDuration(VoiceRules.MAX_RECORD_MILLIS))
        assertEquals("1:00:01", formatVoiceDuration(3_601_000))
        assertEquals("00:00", formatVoiceDuration(-5))

        assertEquals("0:00,0", formatRecordingTime(0))
        assertEquals("0:07,4", formatRecordingTime(7_456))
        assertEquals("14:59,9", formatRecordingTime(899_999))
    }

    @Test
    fun `the bars of a recording fit the note's rules`() {
        // A quarter of the stretches loud, the rest quiet speech.
        val speech = IntArray(1_000) { index -> if ((index / 10) % 4 == 0) 20_000 else 2_000 }
        val bars = compressWaveform(speech)
        assertEquals(VoiceMedia.WAVEFORM_MAX_SAMPLES, bars.size)
        assertTrue(bars.all { it in 0..VoiceMedia.WAVEFORM_MAX_VALUE })
        assertTrue("loud moments reach the top", bars.any { it.toInt() == VoiceMedia.WAVEFORM_MAX_VALUE })
        assertTrue("quiet speech still shows", bars.filter { it < VoiceMedia.WAVEFORM_MAX_VALUE }.all { it in 3..8 })

        // A short recording keeps one value per stretch; silence stays flat; nothing gives nothing.
        assertEquals(3, compressWaveform(intArrayOf(100, 9_000, 100)).size)
        assertTrue(compressWaveform(IntArray(50) { 40 }).all { it.toInt() <= 3 })
        assertEquals(0, compressWaveform(IntArray(0)).size)
    }

    @Test
    fun `bars spread a note's values over the width`() {
        val waveform = byteArrayOf(0, 31, 15)
        val bars = waveformBars(waveform, 6)
        assertEquals(6, bars.size)
        assertEquals(0f, bars[0], 0f)
        assertEquals(1f, bars[2], 0f)
        assertEquals(15f / 31f, bars[5], 0.0001f)
        // A damaged value is held to the range; no values give flat bars.
        assertEquals(1f, waveformBars(byteArrayOf(99), 2)[1], 0f)
        assertTrue(waveformBars(ByteArray(0), 4).all { it == 0f })
        assertEquals(0, waveformBars(waveform, 0).size)
        assertEquals(50, waveformBarCount(150f))
    }

    /** The header players use to read the recording: checked against the ADTS layout bit by bit. */
    @Test
    fun `an ADTS header names AAC-LC, the rate, mono and the frame length`() {
        val header = adtsHeader(payloadBytes = 200, sampleRate = 16_000)
        assertEquals(ADTS_HEADER_BYTES, header.size)
        val bits = header.joinToString("") { (it.toInt() and 0xFF).toString(2).padStart(8, '0') }
        assertEquals("111111111111", bits.substring(0, 12)) // syncword
        assertEquals("0", bits.substring(12, 13)) // MPEG-4
        assertEquals("00", bits.substring(13, 15)) // layer
        assertEquals("1", bits.substring(15, 16)) // no checksum
        assertEquals(1, bits.substring(16, 18).toInt(2)) // AAC-LC
        assertEquals(8, bits.substring(18, 22).toInt(2)) // 16 kHz
        assertEquals(1, bits.substring(23, 26).toInt(2)) // one channel
        assertEquals(207, bits.substring(30, 43).toInt(2)) // frame length with the header
        assertEquals(0x7FF, bits.substring(43, 54).toInt(2)) // variable bit rate
        assertEquals(0, bits.substring(54, 56).toInt(2)) // one raw data block

        assertEquals(4, adtsSampleRateIndex(44_100))
        assertEquals(-1, adtsSampleRateIndex(12_345))
        assertThrows(IllegalArgumentException::class.java) { adtsHeader(100, 12_345) }
        assertThrows(IllegalArgumentException::class.java) { adtsHeader(9_000, 16_000) }
        assertArrayEquals(adtsHeader(0, 44_100).copyOfRange(0, 2), byteArrayOf(0xFF.toByte(), 0xF1.toByte()))
    }

    @Test
    fun `slide to cancel ends at its distance`() {
        assertEquals(1f, slideProgress(0f, 100f), 0f)
        assertEquals(0.5f, slideProgress(-50f, 100f), 0f)
        assertEquals(0f, slideProgress(-150f, 100f), 0f)
        assertEquals(1f, slideProgress(-50f, 0f), 0f)
        assertEquals(140f, cancelDistancePx(1_000f, 140f), 0f)
        assertEquals(35f, cancelDistancePx(100f, 140f), 0.001f)
    }
}
