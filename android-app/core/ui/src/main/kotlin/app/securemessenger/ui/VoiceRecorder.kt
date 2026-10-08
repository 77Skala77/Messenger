package app.securemessenger.ui

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Process
import app.securemessenger.signalprotocol.VoiceMedia
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A finished recording, in memory only: AAC-LC frames with ADTS headers, its length and its loudness
 * bars; [once] for a one-time voice message (DEC-036).
 */
internal class VoiceRecording(val bytes: ByteArray, val durationMillis: Int, val waveform: ByteArray, val once: Boolean = false) {
    fun wipe() = bytes.fill(0)
}

/**
 * Records a voice message (DEC-036) straight into memory: the microphone's 16-bit sound goes into the
 * phone's AAC encoder, and the encoded frames, each behind its ADTS header, into one buffer reserved
 * at the largest size a note may announce, so it never grows and leaves copies behind. Nothing is
 * written to a file at any point: no unencrypted copy of a recording is ever on the flash. The buffer
 * is zeroed when the recording is handed over or thrown away.
 *
 * One recording per object: [start] opens the microphone and records on a thread of its own; [pause]
 * lets the microphone go (Android's indicator goes off) and [resume] takes it again, the recording
 * running on seamlessly; [snapshot] gives what was recorded so far, for listening while paused;
 * [stop] ends it and returns the recording, [cancel] ends it and throws it away. [elapsedMillis] and
 * [amplitude] are read by the screen on every frame. The recorder stops by itself at
 * [VoiceRules.MAX_RECORD_MILLIS] (or close to the largest size) and then says so in [reachedLimit].
 */
internal class VoiceRecorder {
    /** How much sound was recorded so far. */
    @Volatile
    var elapsedMillis: Long = 0
        private set

    /** The loudness of the last moment, 0 to 1, for the pulsing circle. */
    @Volatile
    var amplitude: Float = 0f
        private set

    @Volatile
    var reachedLimit: Boolean = false
        private set

    private val lock = Any()
    private var stopRequested = false
    private var pauseRequested = false
    private var discard = false
    private var thread: Thread? = null

    @Volatile
    private var failed = false
    private val output = WipeableBuffer(VoiceMedia.MAX_RECORDING_BYTES)
    private val peaks = IntList()

    /** Samples cut away by [trim]: the time and the limit count only what is kept. */
    @Volatile
    private var removedSamples = 0L

    /** Samples fed so far and their rate, written by the recording thread. */
    @Volatile
    private var fedSamples = 0L

    @Volatile
    private var sampleRate = 0

    /**
     * Opens the microphone and the encoder and starts recording; false if the phone could not (no
     * microphone permission, the microphone in use, no AAC encoder) or the recording was already
     * stopped while it opened. The caller has checked the microphone permission.
     */
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        val microphone = try {
            openMicrophone()
        } catch (_: Exception) {
            return@withContext false
        }
        val (record, rate) = microphone
        val encoder = try {
            openEncoder(rate)
        } catch (_: Exception) {
            record.release()
            return@withContext false
        }
        try {
            record.startRecording()
        } catch (_: Exception) {
            close(record, encoder)
            return@withContext false
        }
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            close(record, encoder)
            return@withContext false
        }
        synchronized(lock) {
            if (stopRequested) {
                close(record, encoder)
                return@withContext false
            }
            thread = Thread({ recordLoop(record, encoder, rate) }, "voice-recorder").also { it.start() }
        }
        true
    }

    /** Lets the microphone go; the recording so far stays and goes on with [resume]. */
    fun pause() {
        synchronized(lock) { pauseRequested = true }
    }

    fun resume() {
        synchronized(lock) { pauseRequested = false }
    }

    /**
     * While paused, cuts what was recorded so far to the stretch from [fromMillis] to [toMillis]
     * (Telegram's trim before recording on): the cut-off sound is zeroed in the buffer, and the time,
     * the limit and the bars go on from the part kept. False when not paused or nothing would be left.
     */
    fun trim(fromMillis: Long, toMillis: Long): Boolean {
        if (!synchronized(lock) { pauseRequested && !stopRequested }) return false
        val kept = output.trim(fromMillis, toMillis)
        if (kept.bytes == 0) return false
        val frames = kept.framesKept + kept.framesRemoved
        if (frames > 0) peaks.keep(kept.framesBefore.toFloat() / frames, (kept.framesBefore + kept.framesKept).toFloat() / frames)
        removedSamples += kept.framesRemoved.toLong() * AAC_FRAME_SAMPLES
        val rate = sampleRate
        if (rate > 0) elapsedMillis = (fedSamples - removedSamples).coerceAtLeast(0) * 1000 / rate
        return true
    }

    /**
     * What was recorded so far, as a copy, for listening while paused; null if nothing yet. The
     * encoder may still hold the last tenth of a second, which the finished recording will have.
     */
    fun snapshot(): VoiceRecording? {
        val bytes = output.toByteArray()
        if (bytes.isEmpty()) return null
        return VoiceRecording(bytes, elapsedMillis.toInt(), compressWaveform(peaks.toArray()))
    }

    /** Ends the recording and returns it; null if nothing usable was recorded. */
    suspend fun stop(): VoiceRecording? = withContext(Dispatchers.IO) {
        val running = synchronized(lock) {
            stopRequested = true
            thread
        } ?: return@withContext null
        running.join(STOP_TIMEOUT_MILLIS)
        if (running.isAlive || failed) {
            synchronized(lock) { discard = true }
            if (!running.isAlive) output.wipe()
            return@withContext null
        }
        val bytes = output.toByteArray()
        output.wipe()
        if (bytes.isEmpty()) return@withContext null
        VoiceRecording(bytes, elapsedMillis.toInt(), compressWaveform(peaks.toArray()))
    }

    /** Ends the recording and throws it away. Returns at once; the thread wipes the buffer on its way out. */
    fun cancel() {
        val running = synchronized(lock) {
            stopRequested = true
            discard = true
            thread
        }
        if (running == null || !running.isAlive) output.wipe()
    }

    @SuppressLint("MissingPermission") // The screen asks for RECORD_AUDIO before it starts a recorder.
    private fun openMicrophone(): Pair<AudioRecord, Int> {
        for (rate in SAMPLE_RATES) {
            val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minimum <= 0) continue
            // A fifth of a second of sound, so a slow moment of the encoder loses nothing.
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minimum, rate / 5 * BYTES_PER_SAMPLE),
            )
            if (record.state == AudioRecord.STATE_INITIALIZED) return record to rate
            record.release()
        }
        throw IllegalStateException("The microphone cannot be opened")
    }

    private fun openEncoder(rate: Int): MediaCodec {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, FRAME_SAMPLES * BYTES_PER_SAMPLE * 4)
        }
        val name = MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format)
            ?: throw IllegalStateException("No AAC encoder")
        val codec = MediaCodec.createByCodecName(name)
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        } catch (failure: Exception) {
            codec.release()
            throw failure
        }
        return codec
    }

    /**
     * The recording thread: microphone to encoder to buffer, until stopped or at the limit. While
     * paused the microphone is stopped and nothing is fed; the time stamps count the sound fed, so the
     * pieces before and after a pause join without a gap.
     */
    private fun recordLoop(microphone: AudioRecord, encoder: MediaCodec, rate: Int) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val samples = ShortArray(FRAME_SAMPLES)
        val frame = ByteArray(MAX_FRAME_BYTES)
        val info = MediaCodec.BufferInfo()
        val sizeLimit = VoiceMedia.MAX_RECORDING_BYTES - VoiceRules.SIZE_MARGIN_BYTES
        var fed = 0L
        var inputDone = false
        var outputDone = false
        var idleAfterEnd = 0
        var listening = true
        sampleRate = rate
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val (stop, pause) = synchronized(lock) { stopRequested to pauseRequested }
                    val atLimit = (fed - removedSamples) * 1000 / rate >= VoiceRules.MAX_RECORD_MILLIS || output.size() >= sizeLimit
                    if (pause && !stop && !atLimit) {
                        if (listening) {
                            runCatching { microphone.stop() }
                            listening = false
                            amplitude = 0f
                        }
                        outputDone = drain(encoder, info, frame, rate, 0L)
                        Thread.sleep(PAUSE_POLL_MILLIS)
                        continue
                    }
                    if (!listening && !stop && !atLimit) {
                        microphone.startRecording()
                        listening = true
                    }
                    val index = encoder.dequeueInputBuffer(TIMEOUT_MICROS)
                    if (index >= 0) {
                        val buffer = checkNotNull(encoder.getInputBuffer(index))
                        buffer.clear()
                        if (atLimit || stop) {
                            if (atLimit) reachedLimit = true
                            encoder.queueInputBuffer(index, 0, 0, micros(fed, rate), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val wanted = minOf(samples.size, buffer.remaining() / BYTES_PER_SAMPLE)
                            val read = microphone.read(samples, 0, wanted)
                            if (read < 0) throw IllegalStateException("The microphone stopped")
                            measure(samples, read)
                            buffer.order(ByteOrder.nativeOrder()).asShortBuffer().put(samples, 0, read)
                            encoder.queueInputBuffer(index, 0, read * BYTES_PER_SAMPLE, micros(fed, rate), 0)
                            fed += read
                            fedSamples = fed
                            elapsedMillis = (fed - removedSamples).coerceAtLeast(0) * 1000 / rate
                        }
                    }
                }
                if (drain(encoder, info, frame, rate, if (inputDone) TIMEOUT_MICROS else 0L)) {
                    outputDone = true
                } else if (inputDone && ++idleAfterEnd > MAX_IDLE_AFTER_END) {
                    // The encoder should end soon after the last input; do not wait for it forever.
                    outputDone = true
                }
            }
        } catch (_: Exception) {
            failed = true
        } finally {
            samples.fill(0)
            frame.fill(0)
            close(microphone, encoder)
            amplitude = 0f
            if (failed || synchronized(lock) { discard }) output.wipe()
        }
    }

    /**
     * Takes what the encoder has ready, each frame behind its ADTS header, into the buffer. True once
     * the end of the stream came out. [timeoutMicros] is how long to wait for the first frame.
     */
    private fun drain(encoder: MediaCodec, info: MediaCodec.BufferInfo, frame: ByteArray, rate: Int, timeoutMicros: Long): Boolean {
        var wait = timeoutMicros
        while (true) {
            val index = encoder.dequeueOutputBuffer(info, wait)
            wait = 0L
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) return false
            if (index < 0) continue
            val encoded = checkNotNull(encoder.getOutputBuffer(index))
            val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
            if (!isConfig && info.size in 1..MAX_FRAME_BYTES) {
                encoded.position(info.offset)
                encoded.limit(info.offset + info.size)
                encoded.get(frame, 0, info.size)
                output.write(adtsHeader(info.size, rate))
                output.write(frame, 0, info.size)
            }
            encoder.releaseOutputBuffer(index, false)
            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return true
        }
    }

    /** The loudness of the samples just read: the peak for the bars, a smoothed level for the circle. */
    private fun measure(samples: ShortArray, count: Int) {
        var peak = 0
        var squares = 0.0
        for (index in 0 until count) {
            val value = samples[index].toInt()
            peak = maxOf(peak, abs(value))
            squares += value.toDouble() * value
        }
        peaks.add(peak)
        val level = if (count == 0) 0f else (sqrt(squares / count) / LOUD_RMS).toFloat().coerceIn(0f, 1f)
        amplitude = amplitude * 0.5f + level * 0.5f
    }

    private fun close(microphone: AudioRecord, encoder: MediaCodec) {
        runCatching { microphone.stop() }
        microphone.release()
        runCatching { encoder.stop() }
        encoder.release()
    }

    private fun micros(samples: Long, rate: Int): Long = samples * 1_000_000L / rate

    /** A byte buffer whose content can be zeroed; reserved at full size, so it never copies itself. */
    private class WipeableBuffer(capacity: Int) : ByteArrayOutputStream(capacity) {
        @Synchronized
        fun wipe() {
            buf.fill(0)
            count = 0
        }

        /** Keeps the frames of the stretch, in place; the rest is zeroed. */
        @Synchronized
        fun trim(fromMillis: Long, toMillis: Long): TrimResult {
            val result = trimAdtsInPlace(buf, count, fromMillis, toMillis)
            count = result.bytes
            return result
        }
    }

    /** Peaks as plain ints, one per stretch read (about 15 a second); read while recording for [snapshot]. */
    private class IntList {
        private var values = IntArray(1024)
        private var size = 0

        @Synchronized
        fun add(value: Int) {
            if (size == values.size) values = values.copyOf(size * 2)
            values[size++] = value
        }

        @Synchronized
        fun toArray(): IntArray = values.copyOf(size)

        /** Keeps the share from [from] to [to] (0 to 1) of the peaks, in their order. */
        @Synchronized
        fun keep(from: Float, to: Float) {
            val start = (from.coerceIn(0f, 1f) * size).toInt().coerceIn(0, size)
            val end = kotlin.math.ceil(to.coerceIn(0f, 1f) * size).toInt().coerceIn(start, size)
            System.arraycopy(values, start, values, 0, end - start)
            size = end - start
        }
    }

    private companion object {
        /** 16 kHz is plenty for a voice; 44.1 kHz is the rate every phone must offer. */
        val SAMPLE_RATES = intArrayOf(16_000, 44_100)
        const val BIT_RATE = 32_000
        const val BYTES_PER_SAMPLE = 2

        /** One AAC frame's worth of sound. */
        const val FRAME_SAMPLES = 1024
        const val MAX_FRAME_BYTES = 8 * 1024
        const val TIMEOUT_MICROS = 10_000L
        const val MAX_IDLE_AFTER_END = 300
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val PAUSE_POLL_MILLIS = 20L

        /** A level of speech close to the microphone: the circle is fully open from here. */
        const val LOUD_RMS = 3_000.0
    }
}
