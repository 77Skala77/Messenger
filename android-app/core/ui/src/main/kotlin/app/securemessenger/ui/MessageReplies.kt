package app.securemessenger.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.securemessenger.signalstore.V2StoredMessage
import kotlin.math.abs
import kotlinx.coroutines.launch

internal const val REPLY_PANEL_TAG = "reply_panel"
internal const val REPLY_PANEL_CANCEL_TAG = "reply_panel_cancel"
internal const val MESSAGE_MENU_REPLY_TAG = "message_menu_reply"

/** The tag of a quote inside a bubble; the suffix is the local ID of the message that quotes. */
internal const val QUOTE_TAG_PREFIX = "quote_"

/** How many characters of a quoted text are ever looked at: a quote shows one line. */
private const val QUOTE_MAX_CHARS = 200

/**
 * What a quote shows (DEC-035): whether the quoted text is the person's own, and its first line; or,
 * when this phone does not have it (cleared, or never arrived), [text] null and no author.
 */
internal data class QuoteInfo(val authorIsMe: Boolean, val text: String?)

/** The quote for a text that answers [replyToInnerIdHex], looked up in this phone's own copy of the chat. */
internal fun quoteOf(replyToInnerIdHex: String?, byInnerId: Map<String, V2StoredMessage>): QuoteInfo? {
    val id = replyToInnerIdHex ?: return null
    val quoted = byInnerId[id] ?: return QuoteInfo(authorIsMe = false, text = null)
    return QuoteInfo(authorIsMe = quoted.outgoing, text = quoteLine(quoted.text))
}

/** A text on one line, as a quote shows it: runs of spaces and line breaks become one space. */
internal fun quoteLine(text: String): String =
    text.take(QUOTE_MAX_CHARS).split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

/**
 * Telegram's swipe to reply (`ChatActivity`, its sliding view; DEC-035): a bubble pulled to the left
 * follows the finger, at most 80 dp. From 20 dp on a round reply sign grows in at the right side and
 * a ring around it fills; at 50 dp the phone ticks once, and letting go there answers the message
 * ([onReply]). The bubble then slides back in 180 ms. A drag that starts up or down stays the list's;
 * a drag to the right is never this one's (that is the swipe back).
 */
@Composable
internal fun ReplySwipe(
    enabled: Boolean,
    outgoing: Boolean,
    onReply: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current
    val startPx = pixelsInCm(TelegramMotion.SWIPE_START_CM, LocalContext.current.resources.displayMetrics.xdpi)
    val maxPx = with(density) { TelegramMotion.ReplySwipeMax.toPx() }
    val triggerPx = with(density) { TelegramMotion.ReplySwipeTrigger.toPx() }
    val showPx = with(density) { TelegramMotion.ReplyIconShow.toPx() }
    val halfIconPx = with(density) { TelegramMotion.ReplyIconSize.toPx() / 2f }
    val reply by rememberUpdatedState(onReply)
    Box(
        modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var trackingFrom: Float? = null
                var ticked = false
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (trackingFrom != null) {
                            if (-offset.value >= triggerPx) reply()
                            scope.launch { offset.animateTo(0f, tween(TelegramMotion.REPLY_SWIPE_BACK_MILLIS, easing = TelegramMotion.Decelerate)) }
                        }
                        return@awaitEachGesture
                    }
                    val from = trackingFrom
                    if (from == null) {
                        // Someone else (the list scrolling, the swipe back) took the gesture first.
                        if (change.isConsumed) return@awaitEachGesture
                        val dx = change.position.x - down.position.x
                        val dy = abs(change.position.y - down.position.y)
                        if (shouldStartReplySwipe(dx, dy, startPx)) {
                            // Like Telegram, the bubble starts moving from here, not from the first touch.
                            trackingFrom = change.position.x
                            change.consume()
                        } else if (dy > viewConfiguration.touchSlop || dx > viewConfiguration.touchSlop) {
                            return@awaitEachGesture
                        }
                    } else {
                        change.consume()
                        val next = replySwipeOffset(change.position.x - from, maxPx)
                        val beyond = -next >= triggerPx
                        if (beyond && !ticked) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        ticked = beyond
                        scope.launch { offset.snapTo(next) }
                    }
                }
                // The gesture was cancelled without the finger coming up.
                scope.launch { offset.animateTo(0f, tween(TelegramMotion.REPLY_SWIPE_BACK_MILLIS, easing = TelegramMotion.Decelerate)) }
            }
        },
    ) {
        Box(Modifier.graphicsLayer { translationX = offset.value }) { content() }
        val pulled = -offset.value
        if (pulled > showPx) {
            val progress = ((pulled - showPx) / (triggerPx - showPx)).coerceIn(0f, 1f)
            ReplySign(
                progress = progress,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .graphicsLayer {
                        // Telegram puts its middle at the right edge plus the pull (half of it for one's own bubbles).
                        translationX = halfIconPx + offset.value * (if (outgoing) 0.5f else 1f)
                        val scale = 0.4f + 0.6f * progress
                        scaleX = scale
                        scaleY = scale
                        alpha = progress
                    },
            )
        }
    }
}

/** The round reply sign of the swipe: the chat's pill color, a ring that fills up to the trigger, and the arrow. */
@Composable
private fun ReplySign(progress: Float, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.messenger
    Box(modifier.size(TelegramMotion.ReplyIconSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(TelegramMotion.ReplyIconSize)) {
            drawCircle(colors.chip)
            if (progress < 1f) {
                val stroke = 2.dp.toPx()
                drawArc(
                    color = colors.chipText,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke),
                )
            }
        }
        TelegramIcon(R.drawable.tg_ic_ab_reply, tint = colors.chipText, size = 20.dp)
    }
}

/**
 * The quote at the top of a bubble that answers another text (Telegram's reply block): a bar, the
 * author ("You" or the contact's name) and the quoted text's first line, in the bubble's accent (green
 * in one's own bubbles, blue in the other's). A tap jumps to the quoted text.
 */
@Composable
internal fun BubbleQuote(quote: QuoteInfo, contactName: String, outgoing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.messenger
    val accent = if (outgoing) colors.tickOutgoing else colors.link
    Row(
        modifier
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .height(IntrinsicSize.Min),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
        Column(Modifier.padding(horizontal = 7.dp, vertical = 3.dp)) {
            if (quote.text != null) {
                Text(
                    if (quote.authorIsMe) stringResource(R.string.reply_you) else contactName,
                    style = MessengerType.QuoteName,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(quote.text, style = MessengerType.QuoteText, color = colors.messageText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text(stringResource(R.string.reply_unavailable), style = MessengerType.QuoteText, color = colors.hint, maxLines = 1)
            }
        }
    }
}

/** What the input bar shows above the field while the person answers a text. */
internal data class ReplyTarget(val author: String, val text: String)

/**
 * The panel above the input field while answering (Telegram's field panel): the reply arrow and a bar
 * in the accent color, the author and the text being answered, and a cross that cancels. It slides
 * open and closed; while it closes it keeps showing what it showed.
 */
@Composable
internal fun ReplyPanel(target: ReplyTarget?, onCancel: () -> Unit) {
    val colors = MaterialTheme.messenger
    val accent = colors.link
    val last = remember { arrayOfNulls<ReplyTarget>(1) }
    if (target != null) last[0] = target
    AnimatedVisibility(
        visible = target != null,
        enter = expandVertically(tween(TelegramMotion.LIST_MOVE_MILLIS)) + fadeIn(tween(TelegramMotion.LIST_MOVE_MILLIS)),
        exit = shrinkVertically(tween(TelegramMotion.LIST_MOVE_MILLIS)) + fadeOut(tween(TelegramMotion.LIST_MOVE_MILLIS)),
    ) {
        val shown = target ?: last[0] ?: return@AnimatedVisibility
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(MaterialTheme.colorScheme.surface)
                .testTag(REPLY_PANEL_TAG),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TelegramIcon(R.drawable.tg_ic_ab_reply, tint = accent, modifier = Modifier.padding(horizontal = 14.dp))
            Box(Modifier.width(2.dp).height(34.dp).background(accent))
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(shown.author, style = MessengerType.QuoteName, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(shown.text, style = MessengerType.QuoteText, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TopBarAction(
                onClick = onCancel,
                description = stringResource(R.string.reply_cancel),
                modifier = Modifier.height(48.dp).testTag(REPLY_PANEL_CANCEL_TAG),
            ) { CloseIcon(colors.hint) }
        }
    }
}
