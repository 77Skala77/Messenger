package app.securemessenger.ui

import android.content.Context
import java.util.UUID

/**
 * The v2 protocol is the app's only chat path, and it is available in debug builds only until the
 * independent review of Phase 5 (DEC-021): `enable_v2_protocol` is `false` in the main resources and
 * `true` only in the debug resources. A release build never constructs the pipeline.
 */
object V2Availability {
    fun isAvailable(context: Context): Boolean = context.resources.getBoolean(R.bool.enable_v2_protocol)
}

/** Receive queue of the v2 pipeline. */
class V2QueueIdStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun getOrCreateQueueId(): String =
        preferences.getString(QUEUE_ID, null) ?: "v2-${UUID.randomUUID()}".also {
            preferences.edit().putString(QUEUE_ID, it).apply()
        }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val PREFERENCES = "secure_messenger_v2_queue"
        const val QUEUE_ID = "local_queue_id"
    }
}
