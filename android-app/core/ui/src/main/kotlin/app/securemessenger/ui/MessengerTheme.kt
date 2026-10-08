package app.securemessenger.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** A change of theme fades over this long (Telegram reveals the new theme over about 400 ms; here the colors cross-fade). */
internal const val THEME_FADE_MILLIS = 300

/**
 * The app's theme (DEC-027, step 6c; DEC-038): Telegram's classic light look, or its default dark look
 * "Dark Blue", chosen by [mode]. Dynamic color stays off, so the bubbles look the same on every phone. Text is set in
 * Source Sans 3 (DEC-033), presses show a light gray ripple like Telegram's list selector, and a
 * change between light and dark fades.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessengerTheme(mode: AppThemeMode = AppThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    // 0 is the light look, 1 the dark one; the first composition starts at its target, so only a change fades.
    val darkness by animateFloatAsState(if (dark) 1f else 0f, tween(THEME_FADE_MILLIS), label = "theme")
    val scheme = lerp(LightColorScheme, DarkColorScheme, darkness)
    val messengerColors = lerp(LightMessengerColors, DarkMessengerColors, darkness)
    val typography = remember { messengerTypography() }
    // Telegram's list selector: 6 percent black on light, 6 percent white on dark.
    val ripple = remember(dark) {
        RippleConfiguration(color = if (dark) Color.White else Color.Black)
    }
    ApplySystemBars(dark = dark)
    CompositionLocalProvider(
        LocalMessengerColors provides messengerColors,
        LocalRippleConfiguration provides ripple,
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography) {
            // Text without a color of its own takes the theme's text color everywhere, also where no
            // Material surface sets one (on a dark ground it was black before: the owner's report of
            // 2026-10-07, settings in the dark theme).
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
        }
    }
}

/**
 * The app draws edge to edge, like Telegram 12 (`MainActivity` makes the system bars clear): the
 * screen shows under the status bar and the navigation bar (each screen keeps its content clear of
 * them), and their icons follow the app's own theme, not the phone's. Android's scrim under the
 * navigation buttons is turned off, so the chat's wallpaper goes down to the edge. The window belongs
 * to the activity, so it is looked up through the view; with no activity (previews) nothing happens.
 */
@Composable
private fun ApplySystemBars(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        window.isNavigationBarContrastEnforced = false
        window.isStatusBarContrastEnforced = false
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
