package app.securemessenger.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.SoundPool
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * The two short sounds of a conversation, taken from Telegram for Android (`sound_out`, `sound_in`;
 * DEC-033): one when we send a text, one when a text arrives while the chat is open. They never play
 * when the phone is on silent or vibrate, and a switch in the settings turns them off. They carry no
 * message content and never leave the phone.
 */
internal object ChatSounds {
    private const val PREFERENCES = "secure_messenger_sounds"
    private const val ENABLED = "chat_sounds"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(ENABLED, true)

    fun set(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean(ENABLED, enabled).apply()
    }

    private var pool: SoundPool? = null
    private var outId = 0
    private var inId = 0
    private var loaded = 0

    private fun ensure(context: Context): SoundPool {
        pool?.let { return it }
        val created = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        val app = context.applicationContext
        created.setOnLoadCompleteListener { _, _, status -> if (status == 0) loaded++ }
        outId = created.load(app, R.raw.tg_sound_out, 1)
        inId = created.load(app, R.raw.tg_sound_in, 1)
        pool = created
        return created
    }

    /** Loads the sounds ahead of the first text, so the first one is not late. */
    fun prepare(context: Context) {
        if (enabled(context)) ensure(context)
    }

    fun playSent(context: Context) = play(context, outgoing = true)

    fun playReceived(context: Context) = play(context, outgoing = false)

    private fun play(context: Context, outgoing: Boolean) {
        if (!audible(context)) return
        val soundPool = ensure(context)
        if (loaded < 2) return
        soundPool.play(if (outgoing) outId else inId, 1f, 1f, 1, 0, 1f)
    }

    private fun audible(context: Context): Boolean {
        if (!enabled(context)) return false
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audio.ringerMode == AudioManager.RINGER_MODE_NORMAL
    }

    private var snap: AudioTrack? = null

    /**
     * A finger snap as a message falls apart into dust (owner's wish, 2026-10-07: the effect is named
     * after Thanos). Our own sound, made here from noise and a tone ([fingerSnapSamples]); no film or
     * other recording is used. Same rules as the other sounds: off on silent, vibrate or with the switch.
     */
    fun playSnap(context: Context) {
        if (!audible(context)) return
        val track = snap ?: runCatching { createSnap() }.getOrNull()?.also { snap = it } ?: return
        runCatching {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    private fun createSnap(): AudioTrack {
        val samples = fingerSnapSamples(SNAP_RATE)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SNAP_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
        track.write(samples, 0, samples.size)
        return track
    }

    private const val SNAP_RATE = 44_100
}

/**
 * A finger snap, made from scratch: a sharp crack (noise through a resonance near 2.6 kHz, rising in
 * 1.5 ms and gone in some 20 ms), a short click on top and a dull body at 180 Hz, about 0.14 s in all,
 * its loudest point at 80 percent of full scale. The noise has a fixed seed, so it is the same every time.
 */
internal fun fingerSnapSamples(sampleRate: Int): ShortArray {
    val count = (sampleRate * 0.14).toInt()
    val random = java.util.Random(7)
    val resonance = 0.93
    val angle = 2.0 * PI * 2_600.0 / sampleRate
    val a1 = 2.0 * resonance * cos(angle)
    val a2 = -resonance * resonance
    var y1 = 0.0
    var y2 = 0.0
    val raw = DoubleArray(count) { index ->
        val t = index.toDouble() / sampleRate
        val noise = random.nextDouble() * 2.0 - 1.0
        val crackEnvelope = if (t < 0.0015) t / 0.0015 else exp(-(t - 0.0015) / 0.018)
        val crack = noise * crackEnvelope + a1 * y1 + a2 * y2
        y2 = y1
        y1 = crack
        val click = noise * exp(-t / 0.004) * 0.5
        val body = sin(2.0 * PI * 180.0 * t) * exp(-t / 0.03) * min(1.0, t / 0.002) * 0.35
        crack * 0.12 + click + body
    }
    val peak = raw.maxOf { abs(it) }.takeIf { it > 0.0 } ?: 1.0
    return ShortArray(count) { index -> (raw[index] / peak * 0.8 * Short.MAX_VALUE).toInt().toShort() }
}
