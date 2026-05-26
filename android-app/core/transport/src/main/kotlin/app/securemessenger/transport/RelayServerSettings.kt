package app.securemessenger.transport

import android.content.Context
import android.content.SharedPreferences
import java.net.URI

enum class RelayHealthStatus {
    AVAILABLE,
    UNAVAILABLE,
    CONNECTION_ERROR,
}

/**
 * The relay address is local configuration rather than protocol state or a secret.
 * A release transport still needs an HTTPS/deployment review before any real use.
 */
class RelayServerSettings(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
    )

    fun currentUrl(): String =
        preferences.getString(RELAY_URL, DEFAULT_EMULATOR_URL) ?: DEFAULT_EMULATOR_URL

    fun saveUrl(value: String): String? {
        val normalized = normalize(value) ?: return null
        preferences.edit().putString(RELAY_URL, normalized).apply()
        return normalized
    }

    fun reset() {
        preferences.edit().remove(RELAY_URL).apply()
    }

    companion object {
        const val DEFAULT_EMULATOR_URL = "http://10.0.2.2:8787"
        private const val PREFERENCES_NAME = "secure_messenger_relay"
        private const val RELAY_URL = "relay_url"

        fun normalize(value: String): String? {
            val trimmed = value.trim().trimEnd('/')
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
            if (uri.scheme !in setOf("http", "https")) return null
            if (uri.host.isNullOrBlank() || uri.rawUserInfo != null) return null
            if (uri.rawQuery != null || uri.rawFragment != null) return null
            if (!uri.rawPath.isNullOrBlank()) return null
            return trimmed
        }
    }
}
