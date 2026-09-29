package app.securemessenger.ui

import android.content.Context
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Hidden QA switch for the v2 protocol (DEC-019 O3 amendment). Off by default. It has an effect
 * only when `allowed` is true, and `allowed` is the debug-only `enable_debug_qa_tools` resource,
 * so a release build can neither turn it on nor read the stored value.
 */
object V2QaSwitch {
    private const val PREFERENCES = "secure_messenger_v2_qa"
    private const val ENABLED = "v2_enabled"

    private val state = MutableStateFlow(false)
    private var loaded = false
    private val alwaysOff = MutableStateFlow(false)

    /** The value that actually takes effect. Pure so it can be tested without Android. */
    fun effective(allowed: Boolean, stored: Boolean): Boolean = allowed && stored

    @Synchronized
    fun flow(context: Context, allowed: Boolean): StateFlow<Boolean> {
        if (!allowed) return alwaysOff
        if (!loaded) {
            state.value = preferences(context).getBoolean(ENABLED, false)
            loaded = true
        }
        return state
    }

    @Synchronized
    fun set(context: Context, allowed: Boolean, enabled: Boolean) {
        if (!allowed) return
        preferences(context).edit().putBoolean(ENABLED, enabled).apply()
        loaded = true
        state.value = enabled
    }

    /** Local wipe: the switch goes back to its default. */
    @Synchronized
    fun clear(context: Context) {
        preferences(context).edit().clear().apply()
        loaded = true
        state.value = false
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}

/** Receive queue of the v2 pipeline. Separate from the legacy queue so the two never read each other's blobs. */
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
