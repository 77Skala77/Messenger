package app.securemessenger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import app.securemessenger.signalstore.V2StoredMessage
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The one-time player's parts, for tests. */
internal const val ONCE_PLAYER_TAG = "once_player"
internal const val ONCE_PLAYER_CLOSE_TAG = "once_player_close"

/**
 * A one-time voice message being played in Telegram's own player (`SecretVoicePlayer`): [message] as
 * it was before playing, and where its bubble stood on the screen ([bounds], in root coordinates).
 * [incoming]: the contact's, which is gone once played; ours only shows.
 */
@Stable
internal class OnceSession(val message: V2StoredMessage, val bounds: Rect, val incoming: Boolean)

/** Telegram's numbers (`SecretVoicePlayer`). */
private object OnceRules {
    /** Opening and the closing that deletes: 520 ms; just closing: 330 ms; the dust 1.5 s. */
    const val OPEN_MILLIS = 520
    const val CLOSE_MILLIS = 330
    const val DUST_MILLIS = 1_500L

    /** The bubble's middle moves toward the screen's, kept between 30 and 70 percent of it, 78 percent of the way. */
    const val CENTER_MIN = 0.3f
    const val CENTER_MAX = 0.7f
    const val CENTER_SHARE = 0.78f

    /** Telegram blurs a fourteenth-size copy of the screen; this radius comes close. */
    val Blur = 28.dp

    /** If the sound never starts (the phone could not play it), the player closes after this. */
    const val START_TIMEOUT_MILLIS = 5_000L
}

private val EaseOutQuintOnce = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/**
 * Telegram's player of a one-time voice message (owner, 2026-10-07, from a recording of Telegram):
 * the chat goes out of focus behind it, the bubble stays where it was and moves a little toward the
 * middle, a dark note above it says it can be heard once, and "Delete and close" (ours: "Close")
 * waits at the bottom. When the sound ends, or the person closes it, the contact's bubble falls apart
 * into dust while the chat comes back; ours goes back to its place. [bubble] draws the message's
 * bubble (it keeps its picture in the [BubblePictures] it is given, for the dust).
 */
@Composable
internal fun OnceVoicePlayer(
    session: OnceSession,
    glass: GlassSource,
    player: VoicePlayer,
    onClosed: () -> Unit,
    bubble: @Composable (BubblePictures) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dark = MaterialTheme.colorScheme.background.luminanceBelowHalf()
    val open = remember(session) { Animatable(0f) }
    val pictures = remember(session) { BubblePictures() }
    val dust = remember(session) { MessageDust() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var heightPx by remember { mutableStateOf(0) }
    var noteHeight by remember { mutableStateOf(0f) }
    var closing by remember(session) { mutableStateOf(false) }
    var scattered by remember(session) { mutableStateOf(false) }
    val messageId = session.message.messageId

    val close: () -> Unit = {
        if (!closing) {
            closing = true
            if (player.activeId == messageId) player.release()
            scope.launch {
                if (session.incoming) {
                    // The bubble falls apart where it stands while the chat comes back into focus.
                    pictures.capture(messageId)?.let { (image, topLeft) ->
                        dust.scatter(image, topLeft - origin, density.density)
                    }
                    scattered = true
                    launch { open.animateTo(0f, tween(OnceRules.OPEN_MILLIS, easing = EaseOutQuintOnce)) }
                    delay(OnceRules.DUST_MILLIS)
                } else {
                    open.animateTo(0f, tween(OnceRules.CLOSE_MILLIS, easing = EaseOutQuintOnce))
                }
                onClosed()
            }
        }
    }
    BackHandler(onBack = close)
    BlockSwipeBack(true)
    LaunchedEffect(session) { open.animateTo(1f, tween(OnceRules.OPEN_MILLIS, easing = EaseOutQuintOnce)) }
    // It closes by itself once the sound is over (or never started).
    LaunchedEffect(session) {
        var started = false
        var waited = 0L
        while (!closing) {
            if (player.activeId == messageId) started = true
            if (started && player.activeId != messageId) break
            if (!started && waited >= OnceRules.START_TIMEOUT_MILLIS) break
            delay(50)
            waited += 50
        }
        close()
    }

    val screenHeight = heightPx.toFloat()
    val bounds = session.bounds
    val startTop = bounds.top - origin.y
    val center = (bounds.center.y - origin.y).coerceIn(screenHeight * OnceRules.CENTER_MIN, screenHeight * OnceRules.CENTER_MAX)
    val targetTop = screenHeight / 2f + OnceRules.CENTER_SHARE * (center - screenHeight / 2f) - bounds.height / 2f
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                origin = it.positionInRoot()
                heightPx = it.size.height
            }
            // Nothing behind it can be touched while it plays.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
            .testTag(ONCE_PLAYER_TAG),
    ) {
        // The chat, out of focus.
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = open.value
                    val radius = OnceRules.Blur.toPx()
                    renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                },
        ) {
            val shift = glass.origin - origin
            translate(shift.x, shift.y) { drawLayer(glass.layer) }
            drawRect(Color.Black.copy(alpha = if (dark) 0.10f else 0.04f))
        }
        if (!scattered && heightPx > 0) {
            val top = lerp(startTop, targetTop, open.value)
            // The note sits above the bubble: the column starts its height higher.
            Column(Modifier.fillMaxWidth().offset { IntOffset(0, (top - noteHeight).roundToInt()) }) {
                Box(Modifier.onSizeChanged { noteHeight = it.height.toFloat() }.graphicsLayer { alpha = open.value }) {
                    OnceNote(
                        text = stringResource(if (session.incoming) R.string.voice_once_hint_in else R.string.voice_once_hint_out),
                        incoming = session.incoming,
                        modifier = Modifier.offset { IntOffset(0, (-8).dp.roundToPx()) },
                    )
                }
                bubble(pictures)
            }
        }
        MessageDustLayer(dust, Modifier.fillMaxSize())
        Text(
            stringResource(if (session.incoming) R.string.voice_once_close_delete else R.string.voice_once_close),
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 18.dp)
                .graphicsLayer { alpha = open.value }
                .clip(RoundedCornerShape(50))
                .background(if (dark) Color(0x20FFFFFF) else Color(0x2E000000))
                .clickable(role = Role.Button, enabled = !closing, onClick = close)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag(ONCE_PLAYER_CLOSE_TAG),
        )
    }
}

/**
 * The dark note above the bubble (`HintView2`, rounding 12, 14 sp, its arrow 34 dp from the bubble's
 * start), at most 60 percent of the screen wide.
 */
@Composable
private fun OnceNote(text: String, incoming: Boolean, modifier: Modifier = Modifier) {
    val maxWidth = (LocalConfiguration.current.screenWidthDp * 0.6f).dp
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = if (incoming) Alignment.BottomStart else Alignment.BottomEnd) {
        Column(modifier, horizontalAlignment = if (incoming) Alignment.Start else Alignment.End) {
            Text(
                text,
                style = TextStyle(fontSize = 14.sp, lineHeight = 18.sp),
                color = Color.White,
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .clip(RoundedCornerShape(12.dp))
                    .background(OnceNoteColor)
                    .padding(horizontal = 11.dp, vertical = 7.dp),
            )
            Canvas(Modifier.padding(start = 28.dp, end = 28.dp).size(width = 14.dp, height = 6.dp)) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(path, OnceNoteColor)
            }
        }
    }
}

/** `HintView2`'s background. */
private val OnceNoteColor = Color(0xE6282828)
