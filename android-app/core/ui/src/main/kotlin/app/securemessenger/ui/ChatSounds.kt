package app.securemessenger.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool

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
        if (!enabled(context)) return
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val soundPool = ensure(context)
        if (loaded < 2) return
        soundPool.play(if (outgoing) outId else inId, 1f, 1f, 1, 0, 1f)
    }
}
