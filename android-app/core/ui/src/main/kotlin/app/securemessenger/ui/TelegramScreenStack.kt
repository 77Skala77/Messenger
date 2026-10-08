package app.securemessenger.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * The screens of one flow, bottom first; the last one is on screen. Pushing opens a screen, popping
 * closes it (and Android's Back, the app's Back button and the swipe back all pop).
 */
@Stable
class NavStack<T : Any>(root: T) {
    var items: List<T> by mutableStateOf(listOf(root))
        private set

    val top: T get() = items.last()
    val canPop: Boolean get() = items.size > 1

    fun push(item: T) {
        if (items.last() != item) items = items + item
    }

    fun pop(): Boolean {
        if (items.size <= 1) return false
        items = items.dropLast(1)
        return true
    }

    fun popToRoot() {
        if (items.size > 1) items = items.take(1)
    }

    /** Replaces the whole stack (for example "contacts, the new chat, its safety number" after adding a contact). */
    fun set(newItems: List<T>) {
        require(newItems.isNotEmpty()) { "A stack always has its root" }
        items = newItems.toList()
    }
}

@Composable
fun <T : Any> rememberNavStack(root: T): NavStack<T> = remember { NavStack(root) }

/**
 * A stack of enum screens that outlives a rebuilt activity (a language change, or Android restoring
 * the app): it is saved by the entries' names. An unknown name (a screen that no longer exists) is left
 * out; with nothing left, the stack starts again at [root].
 */
@Composable
inline fun <reified T : Enum<T>> rememberSaveableNavStack(root: T): NavStack<T> =
    rememberSaveable(saver = navStackSaver(enumValues<T>())) { NavStack(root) }

fun <T : Enum<T>> navStackSaver(entries: Array<T>): Saver<NavStack<T>, ArrayList<String>> = Saver(
    save = { stack -> ArrayList(stack.items.map { it.name }) },
    restore = { names ->
        val items = names.mapNotNull { name -> entries.firstOrNull { it.name == name } }
        if (items.isEmpty()) null else NavStack(items.first()).also { it.set(items) }
    },
)

/**
 * True for the screen that is on show, false for one hidden beneath another or on its way out. Screens
 * with effects that must run only while seen (marking texts as seen, DEC-029) read it.
 */
val LocalScreenVisible = compositionLocalOf { true }

/**
 * Keeps the swipe back off touches that belong to something on the screen (Telegram's
 * `requestDisallowInterceptTouchEvent`): a drag along a voice message's bars or a trim handle
 * [claim]s the touch it is in, and while a recording runs (or a one-time voice message plays)
 * the screen [block]s the swipe altogether. The owner's phone left the chat on such a drag
 * (2026-10-07). Used on the main thread only.
 */
object SwipeBackGuard {
    /** True once something claimed the touch going on now; forgotten when the next finger goes down. */
    var claimed: Boolean = false
        private set
    private var blocks = 0

    val blocked: Boolean get() = blocks > 0

    fun claim() {
        claimed = true
    }

    internal fun newTouch() {
        claimed = false
    }

    internal fun block() {
        blocks++
    }

    internal fun unblock() {
        blocks = (blocks - 1).coerceAtLeast(0)
    }
}

/** No swipe back while [active] (a recording, a one-time voice message playing). */
@Composable
internal fun BlockSwipeBack(active: Boolean) {
    if (!active) return
    androidx.compose.runtime.DisposableEffect(Unit) {
        SwipeBackGuard.block()
        onDispose { SwipeBackGuard.unblock() }
    }
}

/** What changed between two stacks, decided once per new stack. */
private class StackMove<T>(val kind: Kind, val leaving: T?) {
    enum class Kind { NONE, PUSH, POP }

    companion object {
        fun <T> between(old: List<T>, new: List<T>, committedBySwipe: Boolean): StackMove<T> = when {
            old == new -> StackMove(Kind.NONE, null)
            new.size > old.size -> StackMove(Kind.PUSH, null)
            // A swipe has already slid the screen out: nothing is left to animate.
            new.size < old.size -> if (committedBySwipe) StackMove(Kind.NONE, null) else StackMove(Kind.POP, old.last())
            else -> StackMove(Kind.NONE, null)
        }
    }
}

/** Plain bookkeeping that must outlive recompositions without causing them. */
private class StackBook<T>(var committed: List<T>) {
    var swipeCommitted = false
}

/**
 * Telegram's screen stack (`ActionBarLayout`, DEC-033): the new screen fades in while sliding 48 dp
 * from the end side in 150 ms; closing reverses it; and a swipe to the right anywhere on the screen
 * drags the top screen along with the finger, over the screen below (dimmed, with a shadow on the
 * edge), and closes it or puts it back by the same rules (distance, speed, durations).
 *
 * [onSwipeBack] is called after the screen slid out; the caller pops. Touches are ignored while a
 * screen is moving. Screens keep their saved state while buried; the screen below the top is kept
 * composed (hidden) so a swipe can show it at once.
 */
@Composable
fun <T : Any> TelegramScreenStack(
    stack: List<T>,
    onSwipeBack: () -> Unit,
    modifier: Modifier = Modifier,
    swipeBackEnabled: Boolean = true,
    content: @Composable (T) -> Unit,
) {
    require(stack.isNotEmpty()) { "A stack always has its root" }
    // The swipe gesture outlives recompositions; it always calls the caller's current lambda.
    val swipedBack by rememberUpdatedState(onSwipeBack)
    val density = LocalDensity.current
    val xdpi = LocalContext.current.resources.displayMetrics.xdpi
    val scope = rememberCoroutineScope()
    val saveable = rememberSaveableStateHolder()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val book = remember { StackBook(stack) }
    val move = remember(stack) {
        StackMove.between(book.committed, stack, book.swipeCommitted).also { book.swipeCommitted = false }
    }
    SideEffect { book.committed = stack }

    // The screen opening or closing: progress 0 to 1 with Telegram's curve.
    var screenProgress by remember(move) { mutableFloatStateOf(if (move.kind == StackMove.Kind.NONE) 1f else 0f) }
    var finishedMove by remember { mutableStateOf<Any?>(null) }
    val moving = move.kind != StackMove.Kind.NONE && finishedMove !== move
    androidx.compose.runtime.LaunchedEffect(move) {
        if (move.kind == StackMove.Kind.NONE) return@LaunchedEffect
        animate(0f, 1f, animationSpec = tween(TelegramMotion.SCREEN_MILLIS, easing = TelegramMotion.ScreenEasing)) { value, _ ->
            screenProgress = value
        }
        screenProgress = 1f
        finishedMove = move
    }

    // The swipe back: how far the finger dragged the top screen, and for which stack.
    var dragPx by remember { mutableFloatStateOf(0f) }
    var dragForSize by remember { mutableIntStateOf(-1) }
    var swiping by remember { mutableStateOf(false) }
    // The finger is up and the screen is sliding the rest of the way: touches wait.
    var finishing by remember { mutableStateOf(false) }
    var widthPx by remember { mutableIntStateOf(0) }
    val slideOffsetPx = with(density) { TelegramMotion.SlideOffset.toPx() }
    val shadowWidthPx = with(density) { TelegramMotion.ShadowWidth.toPx() }
    val shadowFadePx = with(density) { TelegramMotion.ShadowFadeDistance.toPx() }
    val minSwipePx = pixelsInCm(TelegramMotion.SWIPE_START_CM, xdpi)
    // While the finger drags, the gesture itself holds the touches; blocking then would end it.
    val blocked = moving || finishing

    val top = stack.last()
    val below = stack.getOrNull(stack.size - 2)
    val pushing = moving && move.kind == StackMove.Kind.PUSH
    val popping = moving && move.kind == StackMove.Kind.POP
    val belowVisible = pushing || swiping

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { widthPx = it.width }
            .pointerInput(stack.size > 1 && swipeBackEnabled, blocked) {
                if (blocked) {
                    // A screen is moving: its touches are not for the screens.
                    awaitEachGesture {
                        while (true) {
                            val event = awaitPointerEventInitial()
                            event.changes.forEach { it.consume() }
                            if (event.changes.none { it.pressed }) break
                        }
                    }
                    return@pointerInput
                }
                if (stack.size <= 1 || !swipeBackEnabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    SwipeBackGuard.newTouch()
                    val pointerId = down.id
                    var startX = down.position.x
                    val startY = down.position.y
                    var tracking = false
                    val velocity = VelocityTracker()
                    velocity.addPosition(down.uptimeMillis, down.position)
                    while (true) {
                        val event = awaitPointerEventInitial()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        velocity.addPosition(change.uptimeMillis, change.position)
                        if (!change.pressed) {
                            val v = velocity.calculateVelocity()
                            val width = widthPx.toFloat()
                            val free = !SwipeBackGuard.claimed && !SwipeBackGuard.blocked
                            if (!tracking && free && isClosingFling(v.x, v.y) && width > 0f) {
                                tracking = true
                                dragForSize = stack.size
                                swiping = true
                                dragPx = max(0f, change.position.x - startX)
                            }
                            if (tracking && width > 0f) {
                                val x = dragPx
                                val cancel = shouldCancelBackSwipe(x, width, v.x, v.y)
                                change.consume()
                                finishing = true
                                scope.launch {
                                    if (cancel) {
                                        animate(
                                            x, 0f,
                                            animationSpec = tween(backCancelMillis(x, width), easing = TelegramMotion.AccelerateDecelerate),
                                        ) { value, _ -> dragPx = value }
                                    } else {
                                        animate(
                                            x, width,
                                            animationSpec = tween(backFinishMillis(x, width), easing = TelegramMotion.AccelerateDecelerate),
                                        ) { value, _ -> dragPx = value }
                                        book.swipeCommitted = true
                                        swipedBack()
                                    }
                                    dragForSize = -1
                                    dragPx = 0f
                                    swiping = false
                                    finishing = false
                                }
                            } else {
                                // Let go without ever tracking (or with no width yet): nothing moved.
                                swiping = false
                            }
                            break
                        }
                        val dx = max(0f, change.position.x - startX)
                        val dy = abs(change.position.y - startY)
                        val free = !SwipeBackGuard.claimed && !SwipeBackGuard.blocked
                        if (!tracking && free && shouldStartBackSwipe(dx, dy, minSwipePx)) {
                            tracking = true
                            startX = change.position.x
                            dragForSize = stack.size
                            swiping = true
                            focus.clearFocus()
                            keyboard?.hide()
                        } else if (tracking) {
                            dragPx = max(0f, change.position.x - startX)
                        }
                        if (tracking) change.consume()
                    }
                }
            },
    ) {
        // The screens on show, bottom to top: the one below (shown only while the top one opens over it
        // or is dragged off it), the top one, and the one just closed. They are emitted in one keyed
        // loop, so a screen that changes its part (top to below on opening, below to top on closing)
        // keeps its composition and state: emitted from different places, it was built anew each time,
        // which lost the main screen's tab and the chat's scroll and flashed a fresh copy (2026-10-07).
        val layers = buildList {
            if (below != null) add(below to LayerPart.BELOW)
            add(top to LayerPart.TOP)
            val leaving = move.leaving
            if (popping && leaving != null) add(leaving to LayerPart.LEAVING)
        }
        for ((screen, part) in layers) {
            key(screen.toString()) {
                // Dimming and the edge shadow while the top screen is dragged away.
                if (part == LayerPart.TOP && swiping && widthPx > 0) {
                    Canvas(Modifier.fillMaxSize()) {
                        val tx = if (dragForSize == stack.size) dragPx else 0f
                        drawRect(Color.Black.copy(alpha = backScrimAlpha(tx, size.width)))
                        val share = backShadowShare(tx, size.width, shadowFadePx)
                        if (share > 0f && tx > 0f) {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = TelegramMotion.SHADOW_ALPHA * share),
                                    startX = tx - shadowWidthPx,
                                    endX = tx,
                                ),
                                topLeft = Offset(tx - shadowWidthPx, 0f),
                                size = androidx.compose.ui.geometry.Size(shadowWidthPx, size.height),
                            )
                        }
                    }
                }
                // One call for every part, so the screen's composition stays when its part changes.
                ScreenLayer(
                    key = screen.toString(),
                    visible = part == LayerPart.TOP,
                    hidden = part == LayerPart.BELOW && !belowVisible,
                    saveable = saveable,
                    modifier = when (part) {
                        LayerPart.BELOW -> Modifier
                        // Opening (fading and sliding in) or following the finger.
                        LayerPart.TOP -> Modifier.graphicsLayer {
                            if (pushing) {
                                alpha = screenProgress
                                translationX = slideOffsetPx * (1f - screenProgress)
                            } else if (dragForSize == stack.size) {
                                translationX = dragPx
                            }
                        }
                        // Just closed: fading and sliding out over the one below it.
                        LayerPart.LEAVING -> Modifier.graphicsLayer {
                            alpha = 1f - screenProgress
                            translationX = slideOffsetPx * screenProgress
                        }
                    },
                ) { content(screen) }
            }
        }
    }
}

/** The part a screen plays in the stack at the moment. */
private enum class LayerPart { BELOW, TOP, LEAVING }

/** Reads the next pointer event in the initial pass, before the screens below see it. */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.awaitPointerEventInitial() =
    awaitPointerEvent(PointerEventPass.Initial)

@Composable
private fun ScreenLayer(
    key: String,
    visible: Boolean,
    hidden: Boolean,
    saveable: androidx.compose.runtime.saveable.SaveableStateHolder,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val layer = if (hidden) {
        // Composed so a swipe can show it at once, but never drawn, read or touched. Not drawing at
        // all (rather than a clear layer) makes the screen draw afresh the moment it shows: pictures
        // it records of itself (the glass, DEC-037) are then current. A clear layer kept them stale,
        // and the owner saw the screen below black or flickering during a swipe back, and an old copy
        // of the chat when a page over it closed (2026-10-07).
        modifier
            .fillMaxSize()
            .drawWithContent { }
            .clearAndSetSemantics { }
            .pointerInput(Unit) {
                awaitEachGesture {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                }
            }
    } else if (!visible) {
        // Visible but not the screen the person is on (beneath the opening one, or on its way out).
        modifier.fillMaxSize().clearAndSetSemantics { }
    } else {
        modifier.fillMaxSize()
    }
    // A flow inside a hidden screen is hidden too.
    val shown = visible && LocalScreenVisible.current
    Box(layer) {
        saveable.SaveableStateProvider(key) {
            CompositionLocalProvider(LocalScreenVisible provides shown) { content() }
        }
    }
}
