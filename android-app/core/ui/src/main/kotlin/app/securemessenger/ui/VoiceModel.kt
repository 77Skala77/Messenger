package app.securemessenger.ui

import app.securemessenger.signalprotocol.VoiceMedia
import java.util.Locale

/**
 * The plain rules behind voice messages (DEC-036), kept apart from Android so they can be tested on
 * the JVM: how lengths are written, how the loudness bars are made and drawn, and the frame header of
 * the recording's format.
 */
internal object VoiceRules {
    /** The longest recording (owner, DEC-036); the recorder stops and sends at this length. */
    const val MAX_RECORD_MILLIS = 15 * 60 * 1000L

    /** A press shorter than this records nothing: it was a tap, and the hint says how it works. */
    const val MIN_RECORD_MILLIS = 700L

    /** The recorder stops this far below the largest recording a note may announce. */
    const val SIZE_MARGIN_BYTES = 64 * 1024

    /** Below this peak (of 32,767) a recording counts as silence, so its noise is not drawn as speech (Telegram: 2500). */
    const val QUIET_PEAK = 2_500

    /** Telegram's bars: one every 3 dp, 2 dp wide, at most 7 dp above and below the middle. */
    const val BAR_STEP_DP = 3f
    const val BAR_WIDTH_DP = 2f
    const val BAR_HALF_HEIGHT_DP = 7f

    /** A finger moved this far sideways on the bars drags the place instead of tapping (`SeekBarWaveform`: 0.2 cm). */
    const val SEEK_START_CM = 0.2f
}

/**
 * Where a drag on the bars puts the place (Telegram's `thumbX = x - thumbDX`): the place it started
 * from, moved by as much of the bars' width as the finger went, never past either end.
 */
internal fun seekFraction(startFraction: Float, dxPx: Float, widthPx: Float): Float =
    if (widthPx <= 0f) startFraction.coerceIn(0f, 1f) else (startFraction + dxPx / widthPx).coerceIn(0f, 1f)

/** A voice message's length, or the place reached while playing: "00:07", "12:34" (Telegram's long duration). */
internal fun formatVoiceDuration(millis: Long): String {
    val seconds = (millis.coerceAtLeast(0) / 1000).toInt()
    val hours = seconds / 3600
    return if (hours == 0) {
        String.format(Locale.US, "%02d:%02d", seconds / 60 % 60, seconds % 60)
    } else {
        String.format(Locale.US, "%d:%02d:%02d", hours, seconds / 60 % 60, seconds % 60)
    }
}

/** The timer while recording: "0:07,4" (minutes, seconds, tenths), like Telegram's. */
internal fun formatRecordingTime(millis: Long): String {
    val safe = millis.coerceAtLeast(0)
    val seconds = safe / 1000
    return String.format(Locale.US, "%d:%02d,%d", seconds / 60, seconds % 60, (safe % 1000) / 100)
}

/**
 * At most [VoiceMedia.WAVEFORM_MAX_SAMPLES] values of 0 to 31 from the loudness peaks of a recording
 * (one per stretch the recorder read). Each value is the loudest peak of its share of the recording,
 * measured against 1.8 times the average of those (as Telegram's waveform does), so speech fills the
 * bars and the loudest moments reach the top; silence stays flat.
 */
internal fun compressWaveform(peaks: IntArray, maxSamples: Int = VoiceMedia.WAVEFORM_MAX_SAMPLES): ByteArray {
    if (peaks.isEmpty() || maxSamples <= 0) return ByteArray(0)
    val count = minOf(maxSamples, peaks.size)
    val buckets = IntArray(count) { index ->
        val from = (index.toLong() * peaks.size / count).toInt()
        val to = maxOf(from + 1, ((index + 1).toLong() * peaks.size / count).toInt())
        var loudest = 0
        for (position in from until to) loudest = maxOf(loudest, peaks[position].coerceAtLeast(0))
        loudest
    }
    val reference = maxOf(VoiceRules.QUIET_PEAK.toDouble(), 1.8 * buckets.average())
    return ByteArray(count) { index ->
        (buckets[index] * VoiceMedia.WAVEFORM_MAX_VALUE / reference).toInt().coerceIn(0, VoiceMedia.WAVEFORM_MAX_VALUE).toByte()
    }
}

/**
 * The heights of [count] bars, 0 to 1, from a note's values: each bar shows the value at its place,
 * so a short recording's few values are spread over the whole width. No values (a damaged or empty
 * note) give flat bars.
 */
internal fun waveformBars(waveform: ByteArray, count: Int): FloatArray {
    if (count <= 0) return FloatArray(0)
    if (waveform.isEmpty()) return FloatArray(count)
    return FloatArray(count) { bar ->
        val index = (bar.toLong() * waveform.size / count).toInt().coerceIn(0, waveform.lastIndex)
        waveform[index].toInt().coerceIn(0, VoiceMedia.WAVEFORM_MAX_VALUE) / VoiceMedia.WAVEFORM_MAX_VALUE.toFloat()
    }
}

/**
 * The trim strip's bars (`RecordedAudioPlayerView.checkWaveform`): [count] heights from 0 to 1, each
 * bar's value placed between the lowest and the highest bar shown, so even a quiet recording fills
 * the strip. All equal (or no values) gives the lowest bars.
 */
internal fun trimBarHeights(waveform: ByteArray, count: Int): FloatArray {
    if (count <= 0) return FloatArray(0)
    if (waveform.isEmpty()) return FloatArray(count)
    val values = IntArray(count) { index -> waveform[(index.toLong() * waveform.size / count).toInt().coerceIn(0, waveform.lastIndex)].toInt() }
    val low = values.min()
    val high = values.max()
    if (high <= low) return FloatArray(count)
    return FloatArray(count) { index -> (values[index] - low).toFloat() / (high - low) }
}

/** How many bars fit into [widthDp]: one every [VoiceRules.BAR_STEP_DP]. */
internal fun waveformBarCount(widthDp: Float): Int = (widthDp / VoiceRules.BAR_STEP_DP).toInt().coerceAtLeast(0)

/**
 * The ADTS sampling-frequency index of [sampleRate] (ISO/IEC 14496-3), or -1 for a rate ADTS cannot
 * name. The recorder uses 16 kHz, or 44.1 kHz where the phone cannot record at 16 kHz.
 */
internal fun adtsSampleRateIndex(sampleRate: Int): Int = ADTS_RATES.indexOf(sampleRate)

/**
 * The 7-byte ADTS header in front of one AAC-LC frame of [payloadBytes] (no checksum): this is what
 * makes the bare frames of the encoder a stream that players read, with no container file around it.
 */
internal fun adtsHeader(payloadBytes: Int, sampleRate: Int, channels: Int = 1): ByteArray {
    val rateIndex = adtsSampleRateIndex(sampleRate)
    require(rateIndex >= 0) { "Unsupported sample rate" }
    require(channels in 1..7) { "Unsupported channel count" }
    val frameBytes = payloadBytes + ADTS_HEADER_BYTES
    require(payloadBytes >= 0 && frameBytes <= ADTS_MAX_FRAME_BYTES) { "Invalid frame size" }
    // Profile field = audio object type minus one: AAC-LC (2) is 1.
    val profile = 1
    return byteArrayOf(
        0xFF.toByte(),
        // Syncword end, MPEG-4, layer 0, no checksum.
        0xF1.toByte(),
        ((profile shl 6) or (rateIndex shl 2) or (channels shr 2)).toByte(),
        (((channels and 3) shl 6) or (frameBytes shr 11)).toByte(),
        ((frameBytes shr 3) and 0xFF).toByte(),
        (((frameBytes and 7) shl 5) or 0x1F).toByte(),
        0xFC.toByte(),
    )
}

internal const val ADTS_HEADER_BYTES = 7
private const val ADTS_MAX_FRAME_BYTES = (1 shl 13) - 1

/** Every AAC frame holds this many samples, so a frame lasts 64 ms at 16 kHz. */
internal const val AAC_FRAME_SAMPLES = 1024

private val ADTS_RATES = intArrayOf(96_000, 88_200, 64_000, 48_000, 44_100, 32_000, 24_000, 22_050, 16_000, 12_000, 11_025, 8_000, 7_350)

/**
 * Cuts a recording to the stretch from [fromMillis] to [toMillis] (Telegram's trim while paused), in
 * place: of the ADTS frames in the first [count] bytes of [bytes], those starting inside the stretch
 * move to the front; everything after them is zeroed, so no cut-off sound stays in memory. Reading
 * stops at the first frame whose header does not check out. Returns the new length in bytes and the
 * number of frames removed before the stretch ([TrimResult.framesBefore]).
 */
internal fun trimAdtsInPlace(bytes: ByteArray, count: Int, fromMillis: Long, toMillis: Long): TrimResult {
    var read = 0
    var write = 0
    var index = 0
    var removed = 0
    var before = 0
    while (read + ADTS_HEADER_BYTES <= count) {
        val first = bytes[read].toInt() and 0xFF
        val second = bytes[read + 1].toInt() and 0xFF
        if (first != 0xFF || (second and 0xF0) != 0xF0) break
        val rateIndex = (bytes[read + 2].toInt() shr 2) and 0x0F
        if (rateIndex >= ADTS_RATES.size) break
        val length = ((bytes[read + 3].toInt() and 0x03) shl 11) or
            ((bytes[read + 4].toInt() and 0xFF) shl 3) or
            ((bytes[read + 5].toInt() and 0xFF) ushr 5)
        if (length < ADTS_HEADER_BYTES || read + length > count) break
        val startMillis = index.toLong() * AAC_FRAME_SAMPLES * 1000 / ADTS_RATES[rateIndex]
        if (startMillis >= fromMillis && startMillis < toMillis) {
            if (write != read) System.arraycopy(bytes, read, bytes, write, length)
            write += length
        } else {
            removed++
            if (startMillis < fromMillis) before++
        }
        read += length
        index++
    }
    bytes.fill(0, write, count)
    return TrimResult(write, index - removed, removed, before)
}

/** What [trimAdtsInPlace] left: the length in bytes, the frames kept, and how many went in all and before the stretch. */
internal class TrimResult(val bytes: Int, val framesKept: Int, val framesRemoved: Int, val framesBefore: Int)

/** The loudness bars of the part from [left] to [right] (0 to 1) of a recording; at least one value. */
internal fun sliceWaveform(waveform: ByteArray, left: Float, right: Float): ByteArray {
    if (waveform.isEmpty()) return waveform
    val from = (left.coerceIn(0f, 1f) * waveform.size).toInt().coerceIn(0, waveform.lastIndex)
    val to = kotlin.math.ceil(right.coerceIn(0f, 1f) * waveform.size).toInt().coerceIn(from + 1, waveform.size)
    return waveform.copyOfRange(from, to)
}

/** The trim handles' rules (`RecordedAudioPlayerView`): what is kept spans at least a second and 30 dp. */
internal fun trimMinimumSpan(durationMillis: Long, widthPx: Float, thirtyDpPx: Float): Float {
    val bySecond = if (durationMillis > 0) 1000f / durationMillis else 1f
    val byWidth = if (widthPx > 0f) thirtyDpPx / widthPx else 1f
    return maxOf(bySecond, byWidth).coerceIn(0f, 1f)
}

/** Whether handles at [left] and [right] cut anything off (Telegram: more than 1 percent). */
internal fun trimCuts(left: Float, right: Float): Boolean = left > 0.01f || right < 0.99f
