package app.securemessenger.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** What a popup menu hands to its items: how far its opening has got. */
class TelegramPopupScope internal constructor(private val progress: () -> Float, private val count: Int) {
    /** One row of the menu, 48 dp high, 16 sp; it fades in and slides down from 6 dp above in its turn. */
    @Composable
    fun Item(index: Int, text: String, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
        val slidePx = with(LocalDensity.current) { TelegramMotion.PopupItemSlide.toPx() }
        Row(
            modifier
                .fillMaxWidth()
                .heightIn(min = TelegramMotion.PopupItemHeight)
                .graphicsLayer {
                    val at = cascade(progress(), index, count, TelegramMotion.POPUP_CASCADE_OVERLAP)
                    alpha = at
                    translationY = -slidePx * (1f - at)
                }
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                TelegramIcon(icon)
                Spacer(Modifier.width(16.dp))
            }
            Text(text, style = MessengerType.MenuItem, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}

/**
 * Telegram's popup menu (`ActionBarPopupWindow`, DEC-033). It belongs to the composable it is
 * placed in and opens by it, at its end corner: downward from the top, or upward from the bottom
 * ([fromBottom]). The panel grows from that corner while fading in (150 ms plus 16 ms an item) and
 * its [itemCount] items cascade in; closing slides it 5 dp toward the corner while fading.
 */
@Composable
fun TelegramPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    itemCount: Int,
    modifier: Modifier = Modifier,
    fromBottom: Boolean = false,
    content: @Composable TelegramPopupScope.() -> Unit,
) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = expanded
    if (!state.currentState && !state.targetState) return
    val density = LocalDensity.current
    val transition = updateTransition(state, label = "popup menu")
    val opening = state.targetState
    val progress by transition.animateFloat(
        transitionSpec = {
            if (targetState) {
                tween(TelegramMotion.POPUP_BASE_MILLIS + TelegramMotion.POPUP_PER_ITEM_MILLIS * itemCount, easing = TelegramMotion.AccelerateDecelerate)
            } else {
                tween(TelegramMotion.POPUP_DISMISS_MILLIS, easing = TelegramMotion.AccelerateDecelerate)
            }
        },
        label = "popup progress",
    ) { if (it) 1f else 0f }
    val slidePx = with(density) { TelegramMotion.PopupDismissSlide.toPx() }
    val margin = with(density) { 8.dp.roundToPx() }
    val position = remember(fromBottom, margin) { EndCornerPosition(fromBottom, margin) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Popup(popupPositionProvider = position, onDismissRequest = onDismissRequest, properties = PopupProperties(focusable = true)) {
        Surface(
            modifier = modifier.graphicsLayer {
                transformOrigin = TransformOrigin(1f, if (fromBottom) 1f else 0f)
                alpha = progress
                if (opening) {
                    scaleY = progress
                } else {
                    translationY = (if (fromBottom) slidePx else -slidePx) * (1f - progress)
                }
            },
            shape = RoundedCornerShape(TelegramMotion.PopupCorner),
            color = if (dark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            Column(
                Modifier.width(IntrinsicSize.Max).widthIn(min = 180.dp).padding(vertical = TelegramMotion.PopupPadding),
            ) {
                TelegramPopupScope({ progress }, itemCount).content()
            }
        }
    }
}

/** The menu's end edge on the anchor's end edge, below the anchor's top (or above its bottom), kept on screen. */
private class EndCornerPosition(private val fromBottom: Boolean, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(margin, maxOf(margin, windowSize.width - popupContentSize.width - margin))
        val y = if (fromBottom) anchorBounds.top - popupContentSize.height - margin else anchorBounds.top + margin
        return IntOffset(x, y.coerceIn(0, maxOf(0, windowSize.height - popupContentSize.height)))
    }
}
