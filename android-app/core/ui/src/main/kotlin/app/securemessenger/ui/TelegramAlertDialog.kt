package app.securemessenger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Telegram's alert dialog (`AlertDialog.java` look, DEC-033): a 10 dp rounded card that grows from
 * 80 percent while fading in over 150 ms, title 20 sp medium, text 16 sp, the buttons at the end in
 * the accent color. It replaces Material's `AlertDialog`; the callers and their test tags are the same.
 */
@Composable
fun TelegramAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(TelegramMotion.DIALOG_OPEN_MILLIS, easing = TelegramMotion.Decelerate)) }
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = modifier
                .widthIn(max = 320.dp)
                .graphicsLayer {
                    val scale = 0.8f + 0.2f * progress.value
                    scaleX = scale
                    scaleY = scale
                    alpha = progress.value
                },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp)) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    ProvideTextStyle(MessengerType.DialogTitle) { title() }
                    Column(Modifier.padding(top = 12.dp)) { ProvideTextStyle(MessengerType.DialogText) { text() } }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}
