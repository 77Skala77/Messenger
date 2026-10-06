package app.securemessenger.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/*
 * The motion of Telegram for Android's screen transitions, translated from its sources
 * (`ui/ActionBar/ActionBarLayout.java`, GPL v. 2 or later, and `ui/Components/CubicBezierInterpolator.java`;
 * see THIRD_PARTY_NOTICES.md and DEC-033). The numbers, curves and decisions are theirs; only the
 * language is ours.
 */
object TelegramMotion {
    /** A screen opens (and closes) in this long: `startLayoutAnimation`, 150.0f. */
    const val SCREEN_MILLIS = 150

    /** The opening screen slides in from this far on the end side while it fades in (`dp(48)`). */
    val SlideOffset = 48.dp

    /** The swipe back starts after the finger moved this far sideways: 0.4 cm (`getPixelsInCM(0.4f, true)`). */
    const val SWIPE_START_CM = 0.4f

    /** ... and only if the move is mostly horizontal: `Math.abs(dx) / 3 > dy`. */
    const val SWIPE_AXIS_RATIO = 3f

    /** A fling to the right at least this fast (px per second) closes the screen from any distance. */
    const val FLING_VELOCITY = 3500f

    /** Letting go closer than this share of the width, without a fling, puts the screen back. */
    const val SWIPE_CLOSE_SHARE = 1f / 3f

    /** Closing after a swipe: 200 ms for the full width, at least 50 ms. */
    const val FINISH_BASE_MILLIS = 200f
    const val FINISH_MIN_MILLIS = 50f

    /** Putting the screen back after a swipe: 320 ms for the full width, at least 120 ms. */
    const val CANCEL_BASE_MILLIS = 320f
    const val CANCEL_MIN_MILLIS = 120f

    /** The screen below is dimmed by a black scrim: alpha 120 of 255 at most, scaled by how much of it is covered. */
    const val SCRIM_ALPHA = 120f / 255f
    const val SCRIM_MAX_COVER = 0.8f

    /** The shadow on the edge of the sliding screen: full while at least 20 dp of the screen below shows. */
    val ShadowFadeDistance = 20.dp

    /** Our own edge shadow (Telegram uses a drawable): this wide, 20 percent black at the edge. */
    val ShadowWidth = 8.dp
    const val SHADOW_ALPHA = 0.2f

    // CubicBezierInterpolator constants, in CSS order (x1, y1, x2, y2).
    val Default = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    val EaseOutQuint = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val EaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
    val EaseBoth = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
    val EaseOutBack = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val StandardDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    // --- Lists: `ui/recyclerview/ChatListItemAnimator.java` (messages), `DialogsItemAnimator.java` (chats) ---

    /** A message or a chat row moves, and a new message enters, in 250 ms (`DEFAULT_DURATION`). */
    const val LIST_MOVE_MILLIS = 250

    /** The curve of those moves: `DEFAULT_INTERPOLATOR`. */
    val ListMove = CubicBezierEasing(0.19919473f, 0.010644531f, 0.27920937f, 0.91025391f)

    /** A new message grows from 90 percent of its size while it fades in (`setScaleX(0.9f)`). */
    const val LIST_ENTER_SCALE = 0.9f

    /** Chat rows fade in and out in 180 ms (`deleteDuration`, `changeDuration`), decelerating. */
    const val DIALOG_FADE_MILLIS = 180

    /** The send button scales between 10 and 100 percent while it fades (`ChatActivityEnterView`). */
    const val SEND_BUTTON_MILLIS = 150

    /** An alert dialog grows from 80 percent while it fades in. */
    const val DIALOG_OPEN_MILLIS = 150

    // --- `ui/Components/CounterView.java`: the unread counter ---

    /** From nothing to a number: 220 ms with an overshoot (tension 2). */
    const val COUNTER_IN_MILLIS = 220

    /** From a number to nothing: 150 ms, the default curve. */
    const val COUNTER_OUT_MILLIS = 150

    /** One number replaced by another: 430 ms, the digits roll 13 dp and cross-fade in the first half. */
    const val COUNTER_REPLACE_MILLIS = 430
    val CounterRoll = 13.dp

    // --- `ui/Components/chat/layouts/ChatActivitySideControlsButtonsLayout.java`: the scroll-down button ---

    /** The button fades, grows from 70 percent and slides 80 dp in 280 ms, decelerating. */
    const val SIDE_BUTTON_MILLIS = 280
    const val SIDE_BUTTON_MIN_SCALE = 0.7f
    val SideButtonSlide = 80.dp

    /** The button is 44 dp (the height it takes in the stack). */
    val SideButtonSize = 44.dp

    // --- `ui/ChatActivity.java`: the floating date while scrolling ---

    /** The date pill fades in and out in 150 ms, 4 dp from the top. */
    const val FLOATING_DATE_FADE_MILLIS = 150
    val FloatingDateTop = 4.dp

    /** It hides this long after scrolling stopped: 500 ms (1000 ms the first time). */
    const val FLOATING_DATE_HIDE_MILLIS = 500L
    const val FLOATING_DATE_FIRST_HIDE_MILLIS = 1000L

    // --- `ui/ActionBar/ActionBarPopupWindow.java`: popup menus ---

    /** A menu opens in 150 ms plus 16 ms for every item; its panel grows from the top (or bottom) corner. */
    const val POPUP_BASE_MILLIS = 150
    const val POPUP_PER_ITEM_MILLIS = 16

    /** It closes in 150 ms, sliding 5 dp toward its corner while fading. */
    const val POPUP_DISMISS_MILLIS = 150
    val PopupDismissSlide = 5.dp

    /** Its items fade in one after another and slide in from 6 dp above; the cascade overlaps by 4 steps. */
    val PopupItemSlide = 6.dp
    const val POPUP_CASCADE_OVERLAP = 4f

    val PopupItemHeight = 48.dp
    val PopupPadding = 8.dp
    val PopupCorner = 12.dp

    /** Android's `OvershootInterpolator(tension)`; the counter appears with tension 2. */
    fun overshoot(tension: Float = 2f): Easing = Easing { t ->
        val x = t - 1f
        x * x * ((tension + 1f) * x + tension) + 1f
    }

    /** Android's `DecelerateInterpolator(factor)`; the screens use 1.5 (`decelerateInterpolator`). */
    fun decelerate(factor: Float): Easing = Easing { x ->
        if (factor == 1f) 1f - (1f - x) * (1f - x) else 1f - (1f - x).pow(2f * factor)
    }

    val ScreenEasing: Easing = decelerate(1.5f)

    /** `DecelerateInterpolator()` with the default factor: chat rows and the scroll-down button. */
    val Decelerate: Easing = decelerate(1f)

    /** Android's `AccelerateDecelerateInterpolator`, which finishing a swipe uses. */
    val AccelerateDecelerate: Easing = Easing { x -> (cos((x + 1.0) * PI) / 2.0).toFloat() + 0.5f }
}

/** Pixels in [cm] centimetres on a screen of [xdpi] dots per inch (`AndroidUtilities.getPixelsInCM`). */
fun pixelsInCm(cm: Float, xdpi: Float): Float = cm * max(1f, xdpi / 2.54f)

/** The swipe back begins: far enough sideways and clearly more sideways than up or down. */
fun shouldStartBackSwipe(dx: Float, dy: Float, minDistancePx: Float): Boolean =
    dx >= minDistancePx && abs(dx) / TelegramMotion.SWIPE_AXIS_RATIO > dy

/**
 * The finger came up at [x] (distance dragged, in px) on a screen [width] px wide, moving at
 * ([velocityX], [velocityY]) px per second: put the screen back (true) or close it (false). Closer
 * than a third of the width goes back, unless it was a fast fling to the right.
 */
fun shouldCancelBackSwipe(x: Float, width: Float, velocityX: Float, velocityY: Float): Boolean =
    x < width * TelegramMotion.SWIPE_CLOSE_SHARE &&
        (velocityX < TelegramMotion.FLING_VELOCITY || abs(velocityX) < abs(velocityY))

/** A fling to the right on a swipe that was never started by distance still closes the screen. */
fun isClosingFling(velocityX: Float, velocityY: Float): Boolean =
    velocityX >= TelegramMotion.FLING_VELOCITY && velocityX > abs(velocityY)

/** How long the screen takes to slide the rest of the way out after the finger came up at [x]. */
fun backFinishMillis(x: Float, width: Float): Int =
    max(TelegramMotion.FINISH_BASE_MILLIS / width * (width - x), TelegramMotion.FINISH_MIN_MILLIS).toInt()

/** How long the screen takes to slide back to its place after the finger came up at [x]. */
fun backCancelMillis(x: Float, width: Float): Int =
    max(TelegramMotion.CANCEL_BASE_MILLIS / width * x, TelegramMotion.CANCEL_MIN_MILLIS).toInt()

/** The alpha (0 to 1) of the black scrim over the screen below while the top one is at [translationX]. */
fun backScrimAlpha(translationX: Float, width: Float): Float {
    val cover = min(max((width - translationX) / width, 0f), TelegramMotion.SCRIM_MAX_COVER)
    return TelegramMotion.SCRIM_ALPHA * cover
}

/**
 * Where item [index] of [count] is while a popup menu opens at [fraction] (0 to 1): items start one
 * after another and each takes [overlap] steps (`AndroidUtilities.cascade`).
 */
fun cascade(fraction: Float, index: Int, count: Int, overlap: Float): Float {
    val step = 1f / (count - 1 + overlap)
    return min(max((fraction - step * index) / (step * overlap), 0f), 1f)
}

/**
 * The scroll-down button shows when the newest message is not in view: the list (newest at index 0)
 * is scrolled by at least one item, or by more than [thresholdPx] into the first one.
 */
fun scrollDownButtonVisible(firstVisibleIndex: Int, firstVisibleOffsetPx: Int, thresholdPx: Int): Boolean =
    firstVisibleIndex > 0 || firstVisibleOffsetPx > thresholdPx

/** The share (0 to 1) of the edge shadow that shows: it fades out when less than [fadeDistancePx] of the screen below is left. */
fun backShadowShare(translationX: Float, width: Float, fadeDistancePx: Float): Float =
    min(max((width - translationX) / fadeDistancePx, 0f), 1f)
