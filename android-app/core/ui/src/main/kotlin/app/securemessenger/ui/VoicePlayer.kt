package app.securemessenger.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaDataSource
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Plays voice messages (DEC-036), one at a time, from memory: the decrypted recording is handed to
 * Android's player through a [MediaDataSource], never through a file, and zeroed when it stops. Its
 * state is Compose state, so bubbles follow it. Used on the main thread only.
 */
internal class VoicePlayer(context: Context) {
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)

    /** The message being played or paused, or null. */
    var activeId by mutableStateOf<String?>(null)
        private set

    /** True while sound comes out; false while paused or getting ready. */
    var playing by mutableStateOf(false)
        private set
    var positionMillis by mutableLongStateOf(0L)
        private set
    var durationMillis by mutableLongStateOf(0L)
        private set

    private var player: MediaPlayer? = null
    private var source: MemorySource? = null
    private var focus: AudioFocusRequest? = null

    /**
     * Plays [recording] (taken over: it is zeroed when playing ends) for [messageId], from
     * [startFraction] of its length; [onFailure] if the phone cannot play it.
     */
    fun play(messageId: String, recording: ByteArray, startFraction: Float = 0f, onFailure: () -> Unit = {}) {
        release()
        val memory = MemorySource(recording)
        val media = MediaPlayer()
        source = memory
        player = media
        activeId = messageId
        try {
            media.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            media.setDataSource(memory)
            media.setOnPreparedListener { prepared ->
                if (player !== prepared) return@setOnPreparedListener
                durationMillis = prepared.duration.toLong().coerceAtLeast(0)
                if (startFraction > 0f && durationMillis > 0) prepared.seekTo((durationMillis * startFraction.coerceIn(0f, 1f)).toInt())
                requestFocus()
                prepared.start()
                playing = true
            }
            media.setOnCompletionListener { done -> if (player === done) release() }
            media.setOnErrorListener { failed, _, _ ->
                if (player === failed) {
                    release()
                    onFailure()
                }
                true
            }
            media.prepareAsync()
        } catch (_: Exception) {
            release()
            onFailure()
        }
    }

    fun pause() {
        val media = player ?: return
        if (playing) {
            runCatching { media.pause() }
            playing = false
            abandonFocus()
        }
    }

    fun resume() {
        val media = player ?: return
        if (!playing && durationMillis > 0) {
            requestFocus()
            media.start()
            playing = true
        }
    }

    /** Jumps to [fraction] of the message being played. */
    fun seekTo(fraction: Float) {
        val media = player ?: return
        if (durationMillis <= 0) return
        val target = (durationMillis * fraction.coerceIn(0f, 1f)).toInt()
        media.seekTo(target)
        positionMillis = target.toLong()
    }

    /** Reads where the player is; the screen calls it on every frame while playing. */
    fun tick() {
        val media = player ?: return
        if (playing) positionMillis = runCatching { media.currentPosition.toLong() }.getOrDefault(positionMillis)
    }

    /** Stops, frees the player and zeroes the recording in memory. */
    fun release() {
        player?.let { media ->
            player = null
            runCatching { media.stop() }
            media.release()
        }
        source?.wipe()
        source = null
        abandonFocus()
        activeId = null
        playing = false
        positionMillis = 0
        durationMillis = 0
    }

    private fun requestFocus() {
        val request = focus ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause()
            }
            .build()
            .also { focus = it }
        audio?.requestAudioFocus(request)
    }

    private fun abandonFocus() {
        focus?.let { audio?.abandonAudioFocusRequest(it) }
    }

    /** The recording as the player reads it: straight from memory. */
    private class MemorySource(private val bytes: ByteArray) : MediaDataSource() {
        @Volatile
        private var wiped = false

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            synchronized(this) {
                if (wiped || position >= bytes.size) return -1
                val count = minOf(size.toLong(), bytes.size - position).toInt()
                System.arraycopy(bytes, position.toInt(), buffer, offset, count)
                return count
            }
        }

        override fun getSize(): Long = bytes.size.toLong()

        override fun close() = Unit

        fun wipe() = synchronized(this) {
            wiped = true
            bytes.fill(0)
        }
    }
}
