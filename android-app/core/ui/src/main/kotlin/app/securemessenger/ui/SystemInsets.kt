package app.securemessenger.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable

/**
 * The app draws edge to edge (under the status bar and the navigation bar, like Telegram 12): what a
 * screen with a text field keeps its content clear of, the system bars and the keyboard. With the
 * keyboard open its height counts at the bottom, as the window's resizing did before.
 */
@Composable
internal fun keyboardAwareInsets(): WindowInsets = WindowInsets.systemBars.union(WindowInsets.ime)
