package app.securemessenger.ui

import android.content.Context

/** The owner's choice (DEC-027): follow the system, or always light, or always dark. */
enum class AppThemeMode(val storedValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        /** Anything unknown or missing means "follow the system". */
        fun fromStored(value: String?): AppThemeMode = entries.firstOrNull { it.storedValue == value } ?: SYSTEM
    }
}

/** Keeps the theme choice in ordinary preferences, like the language: it is not a secret. */
object ThemeController {
    private const val PREFERENCES = "secure_messenger_theme"
    private const val SELECTED_MODE = "selected_mode"

    fun selected(context: Context): AppThemeMode =
        AppThemeMode.fromStored(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(SELECTED_MODE, null))

    fun select(context: Context, mode: AppThemeMode) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(SELECTED_MODE, mode.storedValue)
            .apply()
    }
}
