package app.securemessenger.ui

import android.content.Context
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * The owner's choice (2026-10-06, DEC-032): screen security is off by default and can be switched on
 * in settings. On, Android blocks screenshots and screen recording of the app and hides its content
 * in the list of recent apps. The setting is not a secret, so ordinary preferences hold it.
 */
object ScreenSecurity {
    private const val PREFERENCES = "secure_messenger_privacy"
    private const val ENABLED = "screen_security"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(ENABLED, false)

    fun set(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean(ENABLED, enabled).commit()
    }
}

/** Applies [enabled] to the window of the activity this screen is in, and undoes it when it leaves. */
@Composable
internal fun SecureWindowEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        val window = view.context.findActivity()?.window
        if (enabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose { if (enabled) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
