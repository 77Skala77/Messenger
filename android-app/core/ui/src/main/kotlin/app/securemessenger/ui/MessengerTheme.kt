package app.securemessenger.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * The app's theme (DEC-027, step 6c): Telegram's classic light look, or its Night look, chosen by
 * [mode]. Dynamic color stays off, so the bubbles look the same on every phone.
 */
@Composable
fun MessengerTheme(mode: AppThemeMode = AppThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val scheme = if (dark) DarkColorScheme else LightColorScheme
    ApplySystemBars(dark = dark, color = scheme.background.toArgb())
    CompositionLocalProvider(LocalMessengerColors provides if (dark) DarkMessengerColors else LightMessengerColors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/**
 * Makes the status bar match the screen, with icons that stay readable on it. The window belongs
 * to the activity, so it is looked up through the view; with no activity (previews) nothing happens.
 */
@Composable
private fun ApplySystemBars(dark: Boolean, color: Int) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        @Suppress("DEPRECATION")
        window.statusBarColor = color
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
