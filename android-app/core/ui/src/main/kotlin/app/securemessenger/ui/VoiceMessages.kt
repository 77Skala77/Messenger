package app.securemessenger.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.drawText
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.securemessenger.signalstore.V2MediaState
import app.securemessenger.signalstore.V2StoredMessage
import app.securemessenger.signalstore.V2VoiceInfo
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The play button of a voice bubble; the suffix is the local message ID. */
internal const val VOICE_PLAY_TAG_PREFIX = "voice_play_"
internal const val VOICE_ONCE_MARK_TAG_PREFIX = "voice_once_"
internal const val VOICE_WAVEFORM_TAG_PREFIX = "voice_wave_"
internal const val VOICE_UNHEARD_TAG_PREFIX = "voice_unheard_"
internal const val VOICE_RECORD_TAG = "voice_record"
internal const val VOICE_PANEL_TAG = "voice_panel"
internal const val VOICE_CANCEL_TAG = "voice_cancel"
internal const val VOICE_HINT_TAG = "voice_hint"
internal const val VOICE_TOOLTIP_TAG = "voice_tooltip"
internal const val VOICE_ONCE_TOGGLE_TAG = "voice_once_toggle"
internal const val VOICE_PAUSE_TAG = "voice_pause"
internal const val VOICE_DELETE_RECORDING_TAG = "voice_delete_recording"
internal const val VOICE_PREVIEW_PLAY_TAG = "voice_preview_play"
internal const val VOICE_TRIM_LEFT_TAG = "voice_trim_left"
internal const val VOICE_TRIM_RIGHT_TAG = "voice_trim_right"
internal const val VOICE_TRIM_CONFIRM_TAG = "voice_trim_confirm"

/** The player's name for the recording being made, listened to while paused. */
internal const val VOICE_PREVIEW_ID = "voice-preview"

/** The mark of a one-time recording: Telegram's "1" in a dashed ring. */
private const val ONCE_MARK = "1"

/** Telegram's numbers (`ChatActivityEnterView`, `ChatMessageCell`, `SeekBarWaveform`; DEC-033). */
private val PlayButtonSize = 44.dp
private val WaveformHeight = 24.dp
private val WaveformMaxWidth = 150.dp

/** The record circle (`RecordCircle`): 41 dp, 30 dp more at full voice; its soft halo 47 to 62 dp. */
private val RecordCircleRadius = 41.dp
private val RecordCircleWave = 30.dp
private val RecordHaloRadius = 47.dp
private val RecordHaloWave = 15.dp

/** The microphone button in the bar: a 38 dp circle (`audioVideoButtonContainer`). */
private val MicButtonSize = 38.dp
private val MicButtonRadius = 19.dp

/** The round glass buttons above the record circle (Telegram: radius 18 dp): pause, then "1" above it. */
private val RecordSideButton = 36.dp
private val PauseAboveCircle = 85.dp
private val OnceAboveCircle = 136.dp

/** The lock while held (`ControlsView`): 50 dp high at rest, 36 once pulled up, its bottom 60 dp above the circle's middle. */
private val LockPillRest = 50.dp
private val LockPillPulled = 36.dp
private val LockBottomAboveCircle = 60.dp
private val LockBob = 8.dp
private const val LOCK_BOB_MILLIS = 1_650

/** Moving the finger up this far locks the recording, so it goes on without holding. */
private val LockDistance = 57.dp

/** Slide-to-cancel takes 35 percent of the screen width, at most 140 dp. */
private val CancelDistanceMax = 140.dp
private const val CANCEL_SHARE = 0.35f

/** Letting go with less than this much of the slide left still cancels. */
private const val RELEASE_CANCEL_PROGRESS = 0.45f

/** Below this much of the slide left the lock shrinks away (`slideToCancelLockProgress`). */
private const val LOCK_HIDES_BELOW = 0.7f

/** How far the bubble's content leaves room around the bars: button, gap, paddings. */
private val VoiceBubbleChrome = 86.dp

/** After a pause the encoder hands over its last frames before the recording so far is listened to. */
private const val PAUSE_SETTLE_MILLIS = 250L

/** The circle turning into the recording in the bar when paused (`transformToSeekbar`, 580 ms). */
private const val PAUSE_MORPH_MILLIS = 580

/** The circle comes in over 300 ms: it grows to full size, back to 90 percent and full again. */
private const val CIRCLE_ENTER_MILLIS = 300

/** Locking: the buttons snap into place in 250 ms, the lock settles in 350 ms after 100 ms. */
private const val LOCK_SNAP_MILLIS = 250
private const val LOCK_SETTLE_MILLIS = 350
private const val LOCK_SETTLE_DELAY = 100

/** Telegram's red dot: 5 dp, fading out and in over 600 ms each way. */
private val RecordDotRadius = 5.dp
private const val RECORD_DOT_BLINK_MILLIS = 600

/** Telegram's bin animation (`chat_audio_record_delete_2`): 52 frames at 60 a second, 28 dp. */
private const val BIN_FRAMES = 52f
private const val BIN_MILLIS = 867
private val BinSize = 28.dp

/** The place of the dot, the bin and the bar's bin button at the start of the bar. */
private val RecordSlot = 44.dp

/**
 * The trim strip (`RecordedAudioPlayerView`): handles 11.33 dp in from the ends, a kept part of at
 * least 30 dp, a handle taken from 24 dp outside to 6 dp inside, the 20 dp label (24 dp with the sign
 * only, 30 dp plus the length), found 6 dp around; its length fades in 340 ms; after moving the end
 * it plays the last 1.5 s.
 */
private val TrimInset = 11.33.dp
private val TrimMinWidth = 30.dp
private val TrimHandleReach = 24.dp
private val TrimHandleNear = 6.dp
private val TrimBadgeHeight = 20.dp
private val TrimBadgeSmall = 24.dp
private val TrimBadgeChrome = 30.dp
private val TrimBadgeSlop = 6.dp
private const val TRIM_BADGE_MILLIS = 340
private const val TRIM_RIGHT_REPLAY_MILLIS = 1_500L

/** Telegram's `chat_recordedVoiceProgress`: the bars of the kept part not played yet. */
private val TrimBarColor = Color(0xFFB1DEFF)

/** The panel comes in (`RECORD_STATE_ENTER`): the timer and its text slide 20 dp in 150 ms. */
private const val RECORD_PANEL_ENTER_MILLIS = 150
private val RecordPanelEnterShift = 20.dp

/**
 * How a recording leaves (`updateRecordInterface`, Telegram's numbers): the timer goes 20 dp left in
 * 200 ms after 200 ms on a cancel, 40 dp right in 150 ms when sent; the bin plays, and 700 ms after a
 * cancel the dot's place shrinks away in 150 ms; the text field returns after 300 ms (150 ms when
 * sent) in 200 ms; the circle swells by 16 dp and then shrinks to nothing in 360 ms, after 490 ms on a
 * cancel; a cancel by the finger takes it back in 200 ms, shrinking to the 19 dp microphone.
 */
private const val TIMER_LEAVE_DELAY_CANCEL = 200L
private const val TIMER_LEAVE_MILLIS_CANCEL = 200
private const val TIMER_LEAVE_MILLIS_SEND = 150
private val TimerShiftCancel = (-20).dp
private val TimerShiftSend = 40.dp
private const val CANCEL_ICONS_DELAY = 700L
private const val ICONS_MILLIS = 150
private const val FIELD_BACK_DELAY_CANCEL = 300L
private const val FIELD_BACK_DELAY_SEND = 150L
private const val FIELD_BACK_MILLIS = 200
private const val CIRCLE_EXIT_MILLIS = 360
private const val CIRCLE_EXIT_DELAY_CANCEL = 490L
private val CircleExitSwell = 16.dp
private const val GESTURE_BACK_MILLIS = 200
private const val MIC_BACK_DELAY_GESTURE = 110L
private const val MIC_BACK_DELAY_SEND = 200L
private const val CONTROLS_OUT_DELAY_SEND = 200L

/** Telegram's tooltip next to the buttons (`HintView2`): 3.5 s on screen, in and out in 300 ms. */
private const val TOOLTIP_MILLIS = 3_500L
private const val TOOLTIP_FADE_MILLIS = 300
private val TooltipGap = 10.dp
private val TooltipArrowHeight = 6.dp
private val TooltipArrowHalfWidth = 7.dp
private val TooltipRadius = 8.dp
private val TooltipBackground = Color(0xE6282828)

/** How often the hints show at most (Telegram: `voicepausehint` up to 3, `voiceoncehint` below 3). */
private const val PAUSE_HINT_LIMIT = 3
private const val ONCE_HINT_LIMIT = 3
private const val HINT_PREFERENCES = "secure_messenger_voice_hints"
private const val PAUSE_HINT_KEY = "pause_hint"
private const val ONCE_HINT_KEY = "once_hint"

/** Telegram asks "Trim the recording?" before the first trim only (its "trimvoicehint"). */
private const val TRIM_HINT_KEY = "trim_hint"

/** Telegram's curves (`CubicBezierInterpolator`). */
private val EaseBoth = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val EaseOutQuint = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** How much of the slide to cancel is left: 1 at rest, 0 when it cancels. */
internal fun slideProgress(slideX: Float, cancelDistancePx: Float): Float =
    if (cancelDistancePx <= 0f) 1f else (1f + slideX / cancelDistancePx).coerceIn(0f, 1f)

/** How far the finger must slide to cancel, in pixels, on a screen [screenWidthPx] wide. */
internal fun cancelDistancePx(screenWidthPx: Float, maxPx: Float): Float = minOf(screenWidthPx * CANCEL_SHARE, maxPx)

/**
 * The record circle's size while it comes in (`RecordCircle.onDraw`): to full size by the middle of
 * the way, back to 90 percent at three quarters, full again at the end.
 */
internal fun recordCircleEnterScale(progress: Float): Float = when {
    progress <= 0.5f -> progress / 0.5f
    progress <= 0.75f -> 1f - (progress - 0.5f) / 0.25f * 0.1f
    else -> 0.9f + (progress - 0.75f) / 0.25f * 0.1f
}

/** Where a recording is: nothing, held by the finger, locked to go on by itself, or paused to listen. */
internal enum class RecordPhase { IDLE, HOLDING, LOCKED, PAUSED }

/** How a recording leaves the screen: thrown away by the finger, by "CANCEL" or the bin, or sent. */
internal enum class RecordExit(val millis: Long) {
    CANCEL_GESTURE(850),
    CANCEL(850),
    SEND(360),
}

/** Which round button a tooltip points at. */
internal enum class TooltipTarget { PAUSE, ONCE }

/**
 * A tooltip next to a record button (Telegram's `HintView2`); [key] tells two showings of the same text
 * apart, so pressing "1" again shows it again.
 */
internal class RecordTooltip(val target: TooltipTarget, val text: Int, val fire: Boolean, val key: Int)

/** A short note over the end of the chat; [fire] puts Telegram's flame of one-time messages in front. */
internal class ChatHint(val text: Int, val fire: Boolean = false)

/**
 * Recording a voice message from the input bar (Telegram's record mode, DEC-036). [begin] starts a
 * [VoiceRecorder], [lock] lets it go on without the finger, [pause] lets the microphone go and offers
 * the recording so far ([preview]) for listening, [resume] goes on, [toggleOnce] makes it one-time,
 * [finish] stops it and hands it to [onSend], [cancel] throws it away. A press too short to be a
 * recording, or one that could not start, ends in [onHint]. A recording that ends leaves the screen
 * with Telegram's animations ([exit]). Lives as long as the conversation screen.
 */
@Stable
internal class VoiceRecordController(
    private val scope: CoroutineScope,
    private val view: View,
    private val player: VoicePlayer,
) {
    var phase by mutableStateOf(RecordPhase.IDLE)
        private set

    /** Counts recordings, so each one comes in with its own animation. */
    var sessionKey by mutableIntStateOf(0)
        private set

    /** The finger's way from where it pressed, in pixels, never right or down. */
    var slideX by mutableFloatStateOf(0f)
    var slideY by mutableFloatStateOf(0f)
    var elapsedMillis by mutableLongStateOf(0L)
        private set
    var amplitude by mutableFloatStateOf(0f)
        private set

    /** A one-time recording (DEC-036): the recipient plays it once. */
    var once by mutableStateOf(false)
        private set

    /** What was recorded before the pause, to listen to; null while recording. */
    var preview by mutableStateOf<VoiceRecording?>(null)
        private set

    /**
     * Telegram's trim handles on the paused recording: the part kept, as shares (0 to 1) of [preview].
     * Sending keeps only that part; going on recording cuts it first ([confirmTrim] asks the first time).
     */
    var trimLeft by mutableFloatStateOf(0f)
    var trimRight by mutableFloatStateOf(1f)

    /** "Trim the recording?" is being asked before recording goes on. */
    var confirmTrim by mutableStateOf(false)
        private set

    /** How long [preview] is: the base of the handles. */
    val previewMillis: Long get() = recordedBefore

    /** The middle of the microphone button on the screen, where the record circle is drawn. */
    var micCenter by mutableStateOf(Offset.Unspecified)

    /** How the last recording leaves, while it does; [exitKey] counts the leavings. */
    var exit by mutableStateOf<RecordExit?>(null)
        private set
    var exitKey by mutableIntStateOf(0)
        private set

    /** What the leaving recording looked like: the slide left, the time shown, locked, paused. */
    var exitSlide by mutableFloatStateOf(1f)
        private set
    var exitMillis by mutableLongStateOf(0L)
        private set
    var exitLocked by mutableStateOf(false)
        private set
    var exitPaused by mutableStateOf(false)
        private set

    /** The tooltip next to the buttons, if one shows. */
    var tooltip by mutableStateOf<RecordTooltip?>(null)
        private set
    private var tooltipCount = 0

    var onSend: (VoiceRecording) -> Unit = { it.wipe() }
    var onHint: (ChatHint) -> Unit = {}

    private var recorder: VoiceRecorder? = null
    private var segmentStart = 0L
    private var recordedBefore = 0L
    private val hints by lazy { view.context.getSharedPreferences(HINT_PREFERENCES, Context.MODE_PRIVATE) }

    fun begin(locked: Boolean) {
        if (phase != RecordPhase.IDLE) return
        // A recording that is still leaving makes room at once.
        exit = null
        exitKey++
        val started = VoiceRecorder()
        recorder = started
        sessionKey++
        segmentStart = System.nanoTime()
        recordedBefore = 0L
        slideX = 0f
        slideY = 0f
        elapsedMillis = 0
        amplitude = 0f
        once = false
        tooltip = null
        phase = if (locked) RecordPhase.LOCKED else RecordPhase.HOLDING
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        scope.launch {
            if (!started.start() && recorder === started) {
                reset()
                onHint(ChatHint(R.string.voice_record_failed))
            }
        }
    }

    fun lock() {
        if (phase != RecordPhase.HOLDING) return
        phase = RecordPhase.LOCKED
        slideX = 0f
        slideY = 0f
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        // Telegram points at the pause button the first few times (`showPauseHint`).
        val shown = hints.getInt(PAUSE_HINT_KEY, 0)
        if (shown <= PAUSE_HINT_LIMIT) {
            hints.edit().putInt(PAUSE_HINT_KEY, shown + 1).apply()
            showTooltip(TooltipTarget.PAUSE, R.string.voice_pause_hint, fire = false)
        }
    }

    /** "1" (Telegram's `onceRect`): switched on it says what it means, each time; switched off the tooltip goes. */
    fun toggleOnce() {
        if (phase != RecordPhase.LOCKED && phase != RecordPhase.PAUSED) return
        once = !once
        if (once) showTooltip(TooltipTarget.ONCE, R.string.voice_once_on, fire = true) else tooltip = null
    }

    fun pause() {
        val current = recorder ?: return
        if (phase != RecordPhase.LOCKED) return
        current.pause()
        phase = RecordPhase.PAUSED
        amplitude = 0f
        // Once the pause was found, its tooltip has done its job.
        if (tooltip?.target == TooltipTarget.PAUSE) tooltip = null
        hints.edit().putInt(PAUSE_HINT_KEY, maxOf(hints.getInt(PAUSE_HINT_KEY, 0), PAUSE_HINT_LIMIT)).apply()
        scope.launch {
            delay(PAUSE_SETTLE_MILLIS)
            if (recorder === current && phase == RecordPhase.PAUSED) {
                recordedBefore = current.elapsedMillis
                elapsedMillis = recordedBefore
                preview = current.snapshot()
                trimLeft = 0f
                trimRight = 1f
            }
            // When the circle has become the recording in the bar, "1" says what it is (a few times).
            delay(PAUSE_MORPH_MILLIS - PAUSE_SETTLE_MILLIS)
            if (recorder === current && phase == RecordPhase.PAUSED && !once) {
                val shown = hints.getInt(ONCE_HINT_KEY, 0)
                if (shown < ONCE_HINT_LIMIT) {
                    hints.edit().putInt(ONCE_HINT_KEY, shown + 1).apply()
                    showTooltip(TooltipTarget.ONCE, R.string.voice_once_off, fire = false)
                }
            }
        }
    }

    /**
     * Records on. With part of the recording cut off by the handles it is trimmed first, as in
     * Telegram; the first time ("trimvoicehint") only after "Trim the recording?" is confirmed.
     */
    fun resume() {
        val current = recorder ?: return
        if (phase != RecordPhase.PAUSED) return
        if (preview != null && trimCuts(trimLeft, trimRight)) {
            if (hints.getBoolean(TRIM_HINT_KEY, true)) {
                confirmTrim = true
                return
            }
            if (!current.trim(trimFromMillis(), trimToMillis())) return
        }
        goOn(current)
    }

    /** "Trim" in the question: trims, never asks again, and records on. */
    fun confirmTrimAndResume() {
        val current = recorder ?: return
        confirmTrim = false
        if (phase != RecordPhase.PAUSED) return
        hints.edit().putBoolean(TRIM_HINT_KEY, false).apply()
        if (current.trim(trimFromMillis(), trimToMillis())) goOn(current)
    }

    fun dismissTrim() {
        confirmTrim = false
    }

    private fun goOn(current: VoiceRecorder) {
        dropPreview()
        recordedBefore = current.elapsedMillis
        elapsedMillis = recordedBefore
        segmentStart = System.nanoTime()
        trimLeft = 0f
        trimRight = 1f
        current.resume()
        phase = RecordPhase.LOCKED
    }

    private fun trimFromMillis(): Long = (trimLeft * recordedBefore).toLong()

    /** The right handle at the end keeps the end, with whatever the encoder still held. */
    private fun trimToMillis(): Long = if (trimRight >= 0.99f) Long.MAX_VALUE else (trimRight * recordedBefore).toLong()

    /**
     * Throws the recording away. [gestureProgress] is how much of the slide was left when the finger
     * cancelled (Telegram's cancel by gesture), null for "CANCEL", the bin, or Back.
     */
    fun cancel(gestureProgress: Float? = null) {
        val current = recorder ?: return
        leave(if (gestureProgress != null) RecordExit.CANCEL_GESTURE else RecordExit.CANCEL, gestureProgress ?: 1f)
        current.cancel()
    }

    fun finish() {
        val current = recorder ?: return
        val oneTime = once
        // Sent while paused with handles moved: only the part between them goes (Telegram's crop).
        val cut = phase == RecordPhase.PAUSED && preview != null && trimCuts(trimLeft, trimRight)
        val from = trimFromMillis()
        val to = trimToMillis()
        leave(RecordExit.SEND, 1f)
        scope.launch {
            val stopped = current.stop()
            val recording = if (cut && stopped != null) trimmed(stopped, from, to) else stopped
            when {
                recording == null -> onHint(ChatHint(R.string.voice_hold_hint))
                recording.durationMillis < VoiceRules.MIN_RECORD_MILLIS -> {
                    recording.wipe()
                    onHint(ChatHint(R.string.voice_hold_hint))
                }
                else -> onSend(VoiceRecording(recording.bytes, recording.durationMillis, recording.waveform, oneTime))
            }
        }
    }

    /** The part of [recording] from [from] to [to] ms; the original is zeroed. Null if nothing is left. */
    private fun trimmed(recording: VoiceRecording, from: Long, to: Long): VoiceRecording? {
        val total = recording.durationMillis.toLong().coerceAtLeast(1)
        val end = minOf(to, total)
        val kept = trimAdtsInPlace(recording.bytes, recording.bytes.size, from, end)
        val result = if (kept.bytes == 0) {
            null
        } else {
            VoiceRecording(
                recording.bytes.copyOf(kept.bytes),
                (end - from).coerceAtLeast(0).toInt(),
                sliceWaveform(recording.waveform, from.toFloat() / total, end.toFloat() / total),
            )
        }
        recording.wipe()
        return result
    }

    /** Called on every frame while recording: the timer, the circle, and the stop at the limit. */
    fun frame() {
        val current = recorder ?: return
        if (phase == RecordPhase.HOLDING || phase == RecordPhase.LOCKED) {
            elapsedMillis = minOf(recordedBefore + (System.nanoTime() - segmentStart) / 1_000_000, VoiceRules.MAX_RECORD_MILLIS)
            amplitude = current.amplitude
        }
        if (current.reachedLimit) finish()
    }

    private fun showTooltip(target: TooltipTarget, text: Int, fire: Boolean) {
        val shown = RecordTooltip(target, text, fire, ++tooltipCount)
        tooltip = shown
        scope.launch {
            delay(TOOLTIP_MILLIS)
            if (tooltip === shown) tooltip = null
        }
    }

    /** The recording stops being one: remember how it looked, so it can leave the way Telegram's does. */
    private fun leave(kind: RecordExit, slide: Float) {
        exitMillis = elapsedMillis
        exitLocked = phase == RecordPhase.LOCKED || phase == RecordPhase.PAUSED
        exitPaused = phase == RecordPhase.PAUSED
        exitSlide = slide
        reset()
        exit = kind
        val key = ++exitKey
        scope.launch {
            delay(kind.millis)
            if (exitKey == key) exit = null
        }
    }

    private fun dropPreview() {
        if (player.activeId == VOICE_PREVIEW_ID) player.release()
        preview?.wipe()
        preview = null
    }

    private fun reset() {
        dropPreview()
        recorder = null
        phase = RecordPhase.IDLE
        slideX = 0f
        slideY = 0f
        amplitude = 0f
        elapsedMillis = 0
        once = false
        tooltip = null
        trimLeft = 0f
        trimRight = 1f
        confirmTrim = false
    }
}

/**
 * Keeps the controller's timer and circle moving while it records. Its frames count as an endless
 * animation, so a test waiting for the screen to settle is not held up by it.
 */
@Composable
internal fun VoiceRecordTicker(controller: VoiceRecordController) {
    val recording = controller.phase != RecordPhase.IDLE
    LaunchedEffect(recording) {
        while (recording) {
            withInfiniteAnimationFrameNanos { }
            controller.frame()
        }
    }
}

/**
 * The microphone at the end of the input bar while the field is empty: Telegram's 38 dp blue circle
 * with a white microphone. Held, it records: slide left to cancel, slide up to lock. Locked, the big
 * circle over it is the send button; paused, it is a send button itself. A plain tap only shows how it
 * works. Without the microphone permission it asks for it. For accessibility (and tests) its click
 * starts a locked recording, and clicks again to send. After a recording it comes back the way
 * Telegram's does: at once after a cancel by the finger, after the bin after a cancel, soon after sending.
 */
@Composable
internal fun VoiceRecordButton(controller: VoiceRecordController, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.messenger
    val context = LocalContext.current
    val density = LocalDensity.current
    val lockPx = with(density) { LockDistance.toPx() }
    val cancelPx = cancelDistancePx(
        context.resources.displayMetrics.widthPixels.toFloat(),
        with(density) { CancelDistanceMax.toPx() },
    )
    val cancelDistance by rememberUpdatedState(cancelPx)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) controller.onHint(ChatHint(R.string.voice_permission_needed))
    }
    val permitted = {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    val askPermission by rememberUpdatedState { permission.launch(Manifest.permission.RECORD_AUDIO) }
    val recordLabel = stringResource(R.string.voice_record)
    val sendLabel = stringResource(R.string.v2_send)
    val phase = controller.phase
    val exit = controller.exit
    val mic = remember { Animatable(1f) }
    LaunchedEffect(phase, controller.exitKey) {
        when {
            phase != RecordPhase.IDLE -> mic.snapTo(0f)
            exit == RecordExit.CANCEL_GESTURE -> {
                mic.snapTo(0f)
                delay(MIC_BACK_DELAY_GESTURE)
                mic.animateTo(1f, tween(ICONS_MILLIS))
            }
            exit == RecordExit.CANCEL -> {
                mic.snapTo(0f)
                delay(CANCEL_ICONS_DELAY)
                mic.animateTo(1f, tween(ICONS_MILLIS))
            }
            exit == RecordExit.SEND -> {
                mic.snapTo(0f)
                delay(MIC_BACK_DELAY_SEND)
                mic.animateTo(1f, tween(ICONS_MILLIS))
            }
            else -> mic.snapTo(1f)
        }
    }
    Box(
        modifier
            .size(48.dp)
            .onGloballyPositioned { coordinates ->
                val origin = coordinates.positionInRoot()
                controller.micCenter = Offset(origin.x + coordinates.size.width / 2f, origin.y + coordinates.size.height / 2f)
            }
            .testTag(VOICE_RECORD_TAG)
            .semantics {
                role = Role.Button
                contentDescription = if (phase == RecordPhase.IDLE || phase == RecordPhase.HOLDING) recordLabel else sendLabel
                onClick {
                    when {
                        controller.phase == RecordPhase.LOCKED || controller.phase == RecordPhase.PAUSED -> controller.finish()
                        controller.phase != RecordPhase.IDLE -> Unit
                        permitted() -> controller.begin(locked = true)
                        else -> askPermission()
                    }
                    true
                }
            }
            .pointerInput(controller) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    when {
                        controller.phase == RecordPhase.LOCKED || controller.phase == RecordPhase.PAUSED -> {
                            if (waitForUpOrCancellation() != null) controller.finish()
                            return@awaitEachGesture
                        }
                        controller.phase != RecordPhase.IDLE -> return@awaitEachGesture
                        !permitted() -> {
                            askPermission()
                            waitForUpOrCancellation()
                            return@awaitEachGesture
                        }
                    }
                    controller.begin(locked = false)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            if (controller.phase == RecordPhase.HOLDING) {
                                val left = slideProgress(controller.slideX, cancelDistance)
                                if (left < RELEASE_CANCEL_PROGRESS) controller.cancel(gestureProgress = left) else controller.finish()
                            }
                            break
                        }
                        change.consume()
                        if (controller.phase != RecordPhase.HOLDING) continue
                        controller.slideX = (change.position.x - down.position.x).coerceAtMost(0f)
                        controller.slideY = (change.position.y - down.position.y).coerceAtMost(0f)
                        when {
                            -controller.slideX >= cancelDistance -> controller.cancel(gestureProgress = 0f)
                            -controller.slideY >= lockPx -> controller.lock()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (phase == RecordPhase.PAUSED) {
            // Paused, the big circle has gone into the bar: this is the send button (Telegram's `sendButton`).
            Canvas(Modifier.size(MicButtonSize)) {
                drawCircle(colors.fab)
                drawSendArrow(colors.fabIcon)
            }
        } else if (mic.value > 0f) {
            Canvas(
                Modifier
                    .size(MicButtonSize)
                    .graphicsLayer {
                        alpha = mic.value
                        // After "CANCEL" Telegram lets the microphone grow back from nothing.
                        val grow = if (exit == RecordExit.CANCEL) mic.value else 1f
                        scaleX = grow
                        scaleY = grow
                    },
            ) {
                drawCircle(colors.fab)
                drawMicrophone(colors.fabIcon)
            }
        }
    }
}

/**
 * Everything of a recording that floats over the chat (Telegram's `RecordCircle` and `ControlsView`),
 * drawn around the microphone button. The big blue circle with its soft halo follows the finger to the
 * left and shrinks while it slides (to 70 percent), the lock above it climbs with the finger and goes
 * away once the slide is past 30 percent; locked, the lock becomes the pause button, "1" snaps in
 * above it, and the circle's microphone turns into the send arrow; paused, the circle goes into the
 * bar. Next to "1" and pause a tooltip may point at them. When the recording leaves, the circle goes
 * the way Telegram's does: back into the microphone after a cancel by the finger, swelling and
 * shrinking away after "CANCEL" (once the bin is done) or after sending.
 */
@Composable
internal fun VoiceRecordOverlay(controller: VoiceRecordController, glass: GlassSource, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.messenger
    val phase = controller.phase
    val exit = controller.exit
    // The microphone's place on the screen, made relative to this screen (which may be sliding).
    val center = controller.micCenter.let { if (it.isSpecified()) it - glass.origin else it }
    val density = LocalDensity.current
    val context = LocalContext.current
    val lockPx = with(density) { LockDistance.toPx() }
    val cancelPx = cancelDistancePx(context.resources.displayMetrics.widthPixels.toFloat(), with(density) { CancelDistanceMax.toPx() })
    val locked = phase == RecordPhase.LOCKED || phase == RecordPhase.PAUSED

    val enter = remember(controller.sessionKey) { Animatable(0f) }
    LaunchedEffect(controller.sessionKey) { enter.animateTo(1f, tween(CIRCLE_ENTER_MILLIS, easing = LinearEasing)) }
    val snapIn = remember(controller.sessionKey) { Animatable(if (phase == RecordPhase.LOCKED) 1f else 0f) }
    LaunchedEffect(controller.sessionKey, locked) {
        if (locked) snapIn.animateTo(1f, tween(LOCK_SNAP_MILLIS, easing = EaseOutQuint))
    }
    val pausedT by animateFloatAsState(if (phase == RecordPhase.PAUSED) 1f else 0f, tween(PAUSE_MORPH_MILLIS, easing = EaseBoth), label = "paused")
    val slideTarget = if (phase == RecordPhase.HOLDING) slideProgress(controller.slideX, cancelPx) else 1f
    val slide by animateFloatAsState(slideTarget, if (phase == RecordPhase.HOLDING) snap() else tween(200), label = "slide")
    val lockShown by animateFloatAsState(if (slide < LOCK_HIDES_BELOW) 0f else 1f, tween(130), label = "lock shown")
    val level by animateFloatAsState(controller.amplitude, tween(100), label = "record level")
    val bob by rememberInfiniteTransition(label = "lock bob")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(LOCK_BOB_MILLIS), RepeatMode.Reverse), label = "lock bob value")
    // While held, how far the lock was pulled; once locked it settles where the pause button lives.
    val pulled = if (phase == RecordPhase.HOLDING) (-controller.slideY).coerceIn(0f, lockPx) else 0f
    val lastLock = remember(controller.sessionKey) { floatArrayOf(0f) }
    val pillAbove = remember(controller.sessionKey) { Animatable(0f) }
    val pauseAbove = with(density) { PauseAboveCircle.toPx() }
    LaunchedEffect(controller.sessionKey, locked) {
        if (locked) {
            pillAbove.snapTo(if (lastLock[0] > 0f) lastLock[0] else pauseAbove)
            delay(LOCK_SETTLE_DELAY.toLong())
            pillAbove.animateTo(pauseAbove, tween(LOCK_SETTLE_MILLIS))
        }
    }

    val leave = remember(controller.exitKey) { Animatable(if (exit == RecordExit.CANCEL_GESTURE) controller.exitSlide else 0f) }
    val controlsOut = remember(controller.exitKey) { Animatable(0f) }
    LaunchedEffect(controller.exitKey, exit) {
        when (exit) {
            RecordExit.CANCEL_GESTURE -> {
                launch { controlsOut.animateTo(1f, tween(ICONS_MILLIS)) }
                leave.animateTo(1f, tween(GESTURE_BACK_MILLIS, easing = EaseBoth))
            }
            RecordExit.CANCEL -> {
                launch {
                    delay(CANCEL_ICONS_DELAY)
                    controlsOut.animateTo(1f, tween(ICONS_MILLIS))
                }
                delay(CIRCLE_EXIT_DELAY_CANCEL)
                leave.animateTo(1f, tween(CIRCLE_EXIT_MILLIS, easing = LinearEasing))
            }
            RecordExit.SEND -> {
                launch {
                    delay(CONTROLS_OUT_DELAY_SEND)
                    controlsOut.animateTo(1f, tween(ICONS_MILLIS))
                }
                leave.animateTo(1f, tween(CIRCLE_EXIT_MILLIS, easing = LinearEasing))
            }
            null -> Unit
        }
    }
    if (!center.isSpecified() || (phase == RecordPhase.IDLE && exit == null)) return
    val showsSend = if (exit != null) controller.exitLocked else locked

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val sc = recordCircleEnterScale(enter.value)
            var cx = center.x
            var radius: Float
            var alpha = 1f
            var halo: Float
            when (exit) {
                RecordExit.CANCEL_GESTURE -> {
                    val p = leave.value
                    cx = center.x - cancelPx * (1f - p)
                    radius = max(RecordCircleRadius.toPx() * 0.7f * EaseOut.transform(1f - p), MicButtonRadius.toPx())
                    if (p > 0.7f) alpha = 1f - (p - 0.7f) / 0.3f
                    halo = 0f
                }
                RecordExit.CANCEL, RecordExit.SEND -> {
                    val t = leave.value
                    val swell = EaseBoth.transform((t / 0.6f).coerceAtMost(1f))
                    val shrink = EaseBoth.transform(((t - 0.6f) / 0.4f).coerceIn(0f, 1f))
                    radius = if (controller.exitPaused) 0f else (RecordCircleRadius.toPx() + CircleExitSwell.toPx() * swell) * (1f - shrink)
                    if (t > 0.6f) alpha = 1f - (t - 0.6f) / 0.4f
                    halo = 1f - swell
                }
                null -> {
                    cx = center.x - cancelPx * (1f - slide)
                    radius = (RecordCircleRadius.toPx() + RecordCircleWave.toPx() * level) * sc * (0.7f + 0.3f * slide) * (1f - pausedT)
                    alpha = 1f - pausedT
                    halo = sc * (if (slide > 0.7f) 1f else slide / 0.7f) * (1f - pausedT)
                }
            }
            if (radius <= 0f || alpha <= 0f) return@Canvas
            val circleCenter = Offset(cx, center.y)
            if (halo > 0f) {
                drawCircle(
                    colors.fab.copy(alpha = 0.2f * alpha),
                    radius = (RecordHaloRadius.toPx() + RecordHaloWave.toPx() * level) * halo,
                    center = circleCenter,
                )
            }
            drawCircle(colors.fab.copy(alpha = colors.fab.alpha * alpha), radius = radius, center = circleCenter)
            translateTo(circleCenter) {
                val icon = colors.fabIcon.copy(alpha = alpha)
                val sending = if (exit == null) snapIn.value else if (showsSend) 1f else 0f
                if (sending < 1f) scaleAround(1f - sending * 0.4f) { drawMicrophone(icon.copy(alpha = icon.alpha * (1f - sending))) }
                if (sending > 0f) scaleAround(0.6f + sending * 0.4f) { drawSendArrow(icon.copy(alpha = icon.alpha * sending)) }
            }
        }
        val controlsAlpha = 1f - controlsOut.value
        // Held: the lock, climbing with the finger, gone once the slide is past 30 percent. Let go to
        // send, it fades with the other controls; cancelled by the finger, it is already gone.
        val holding = phase == RecordPhase.HOLDING || (exit == RecordExit.SEND && !controller.exitLocked)
        if (holding && controlsAlpha > 0f && lockShown > 0f) {
            val moveProgress = 1f - pulled / lockPx
            val height = LockPillPulled + (LockPillRest - LockPillPulled) * moveProgress
            val bottomAbove = with(density) { LockBottomAboveCircle.toPx() + pulled + LockBob.toPx() * bob * moveProgress }
            val centerAbove = bottomAbove + with(density) { height.toPx() } / 2f
            SideEffect { lastLock[0] = centerAbove }
            val dropPx = with(density) { 72.dp.toPx() } * (1f - lockShown)
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (center.x - RecordSideButton.toPx() / 2f).roundToInt(),
                            (center.y - bottomAbove - height.toPx() + dropPx).roundToInt(),
                        )
                    }
                    .size(width = RecordSideButton, height = height)
                    .graphicsLayer {
                        alpha = controlsAlpha
                        scaleX = lockShown
                        scaleY = lockShown
                    }
                    .glass(glass, RecordSideButton / 2),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) { drawLock(colors.glassIcon, open = moveProgress) }
            }
        }
        // Locked or paused: pause (or, paused, the microphone to go on) and "1" above it.
        val showsControls = locked || (exit != null && controller.exitLocked)
        if (showsControls && controlsAlpha > 0f) {
            val paused = phase == RecordPhase.PAUSED || (exit != null && controller.exitPaused)
            val settling = pillAbove.value.takeIf { it > 0f } ?: lastLock[0].takeIf { it > 0f } ?: pauseAbove
            RecordSideButton(
                center = center,
                above = if (exit == null) with(density) { settling.toDp() } else PauseAboveCircle,
                glass = glass,
                filled = false,
                scale = 1f,
                alpha = controlsAlpha,
                description = stringResource(if (paused) R.string.voice_resume_recording else R.string.voice_pause_recording),
                onClick = { if (phase == RecordPhase.PAUSED) controller.resume() else controller.pause() },
                modifier = Modifier.testTag(VOICE_PAUSE_TAG),
            ) { tint ->
                when {
                    paused -> drawMicrophone(tint)
                    snapIn.value < 1f -> {
                        drawLock(tint.copy(alpha = tint.alpha * (1f - snapIn.value)), open = 0f)
                        drawPause(tint.copy(alpha = tint.alpha * snapIn.value))
                    }
                    else -> drawPause(tint)
                }
            }
            RecordSideButton(
                center = center,
                above = OnceAboveCircle,
                glass = glass,
                filled = controller.once,
                scale = snapIn.value,
                alpha = controlsAlpha,
                description = stringResource(R.string.voice_once),
                onClick = controller::toggleOnce,
                modifier = Modifier.testTag(VOICE_ONCE_TOGGLE_TAG),
            ) { tint -> drawOnceMark(tint, fill = null) }
        }
        RecordTooltipView(controller.tooltip.takeIf { exit == null }, center)
    }
}

/** A round glass button [above] the record circle; [filled] in the accent color when switched on. */
@Composable
private fun RecordSideButton(
    center: Offset,
    above: Dp,
    glass: GlassSource,
    filled: Boolean,
    scale: Float,
    alpha: Float,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: DrawScope.(Color) -> Unit,
) {
    val colors = MaterialTheme.messenger
    Box(
        modifier
            .offset {
                IntOffset(
                    (center.x - RecordSideButton.toPx() / 2f).roundToInt(),
                    (center.y - above.toPx() - RecordSideButton.toPx() / 2f).roundToInt(),
                )
            }
            .size(RecordSideButton)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }
            .then(if (filled) Modifier.background(colors.fab, CircleShape) else Modifier.glass(glass, RecordSideButton / 2))
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Canvas(Modifier.fillMaxSize()) { icon(if (filled) colors.fabIcon else colors.glassIcon) }
    }
}

/**
 * Telegram's tooltip at a record button (`HintView2`, pointing right): a dark rounded box (90 percent
 * #282828, radius 8 dp, padding 11 by 6 dp) with an arrow 6 dp long at its end, white 14 sp text broken
 * into two even lines, the flame in front for "one-time". It points at the middle of its button from
 * 10 dp away, comes in and goes out in 300 ms (fading and growing from 75 percent around the arrow).
 */
@Composable
private fun BoxScope.RecordTooltipView(tooltip: RecordTooltip?, center: Offset) {
    var shown by remember { mutableStateOf<RecordTooltip?>(null) }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(tooltip?.key) {
        if (tooltip != null) {
            if (shown != null && shown !== tooltip) appear.snapTo(0f)
            shown = tooltip
            appear.animateTo(1f, tween(TOOLTIP_FADE_MILLIS, easing = EaseOutQuint))
        } else if (shown != null) {
            appear.animateTo(0f, tween(TOOLTIP_FADE_MILLIS, easing = EaseOutQuint))
            shown = null
        }
    }
    val current = shown ?: return
    val density = LocalDensity.current
    val text = stringResource(current.text)
    val style = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.sp, color = Color.White)
    val measurer = rememberTextMeasurer()
    // Telegram's `cutInFancyHalf`: as narrow as two even lines allow.
    val maxWidth = remember(text, density) {
        val single = measurer.measure(text, style).size.width
        with(density) { (single / 2f + 12.dp.toPx()).toDp().coerceIn(120.dp, 260.dp) }
    }
    val above = if (current.target == TooltipTarget.ONCE) OnceAboveCircle else PauseAboveCircle
    Row(
        Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(Constraints())
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val right = center.x - RecordSideButton.toPx() / 2f - TooltipGap.toPx()
                    val middle = center.y - above.toPx()
                    placeable.place((right - placeable.width).roundToInt(), (middle - placeable.height / 2f).roundToInt())
                }
            }
            .graphicsLayer {
                alpha = appear.value
                val grow = 0.75f + 0.25f * appear.value
                scaleX = grow
                scaleY = grow
                transformOrigin = TransformOrigin(1f, 0.5f)
            }
            .drawBehind {
                val arrow = TooltipArrowHeight.toPx()
                val radius = TooltipRadius.toPx()
                val body = Path().apply {
                    addRoundRect(RoundRect(0f, 0f, size.width - arrow, size.height, CornerRadius(radius)))
                    val middle = size.height / 2f
                    moveTo(size.width - arrow, middle - TooltipArrowHalfWidth.toPx())
                    lineTo(size.width, middle)
                    lineTo(size.width - arrow, middle + TooltipArrowHalfWidth.toPx())
                    close()
                }
                drawPath(body, TooltipBackground)
            }
            .padding(start = 11.dp, end = 11.dp + TooltipArrowHeight, top = 6.dp, bottom = 7.dp)
            .testTag(VOICE_TOOLTIP_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (current.fire) {
            Canvas(Modifier.size(22.dp)) { drawFlame(Color.White) }
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = style, modifier = Modifier.widthIn(max = maxWidth))
    }
}

/**
 * The input bar's content while recording, in place of the text field: the blinking red dot and the
 * timer, and "Slide to cancel" that follows the finger and fades out on the way; once locked,
 * "CANCEL". Paused: the bin that throws the recording away, and the recording so far, to listen to.
 */
@Composable
internal fun VoiceRecordPanel(controller: VoiceRecordController, player: VoicePlayer, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val cancelPx = cancelDistancePx(context.resources.displayMetrics.widthPixels.toFloat(), with(density) { CancelDistanceMax.toPx() })
    Row(
        modifier.heightIn(min = 44.dp).testTag(VOICE_PANEL_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (controller.phase == RecordPhase.PAUSED) {
            PausedRecording(controller, player)
        } else {
            RecordingNow(controller, cancelPx)
        }
    }
}

/**
 * While recording: the blinking dot, the timer, and "Slide to cancel" or, locked, "CANCEL". They come
 * in as Telegram's do: the dot grows from nothing, the timer and the text slide 20 dp in from the
 * right while they appear, in 150 ms.
 */
@Composable
private fun RowScope.RecordingNow(controller: VoiceRecordController, cancelPx: Float) {
    val colors = MaterialTheme.messenger
    val enter = remember(controller.sessionKey) { Animatable(0f) }
    LaunchedEffect(controller.sessionKey) { enter.animateTo(1f, tween(RECORD_PANEL_ENTER_MILLIS)) }
    val enterShift = with(LocalDensity.current) { RecordPanelEnterShift.toPx() }
    val entering = Modifier.graphicsLayer {
        alpha = enter.value
        translationX = enterShift * (1f - enter.value)
    }
    run {
        Box(Modifier.size(RecordSlot).graphicsLayer { scaleX = enter.value; scaleY = enter.value }, contentAlignment = Alignment.Center) { BlinkingDot() }
        RecordTimer(controller.elapsedMillis, entering)
        Box(Modifier.weight(1f).then(entering), contentAlignment = Alignment.Center) {
            if (controller.phase == RecordPhase.LOCKED) {
                Text(
                    stringResource(R.string.voice_cancel),
                    style = RecordCancelStyle,
                    color = colors.link,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(role = Role.Button) { controller.cancel() }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag(VOICE_CANCEL_TAG),
                )
            } else {
                SlideToCancel(progress = slideProgress(controller.slideX, cancelPx), shift = controller.slideX)
            }
        }
        // Room for the round button, which stays where the microphone was.
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun RecordTimer(millis: Long, modifier: Modifier = Modifier) {
    val time = formatRecordingTime(millis)
    val timeDescription = stringResource(R.string.voice_recording, formatVoiceDuration(millis))
    Text(
        time,
        style = RecordTimeStyle,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = modifier.semantics { contentDescription = timeDescription },
    )
}

/** "‹ Slide to cancel", moved with the finger and fading as it goes. */
@Composable
private fun SlideToCancel(progress: Float, shift: Float, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.messenger
    Row(
        modifier.graphicsLayer {
            translationX = shift
            alpha = progress
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(width = 10.dp, height = 14.dp)) {
            val stroke = 1.6.dp.toPx()
            drawLine(colors.hint, Offset(size.width * 0.8f, size.height * 0.15f), Offset(size.width * 0.25f, size.height / 2), stroke, StrokeCap.Round)
            drawLine(colors.hint, Offset(size.width * 0.25f, size.height / 2), Offset(size.width * 0.8f, size.height * 0.85f), stroke, StrokeCap.Round)
        }
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.voice_slide_to_cancel), style = RecordSlideStyle, color = colors.hint, maxLines = 1)
    }
}

/** Telegram's red dot (`RecordDot`): 5 dp, fading out in 600 ms and back in 600 ms while it records. */
@Composable
private fun BlinkingDot() {
    val colors = MaterialTheme.messenger
    val blink = rememberInfiniteTransition(label = "record dot")
    val dotAlpha by blink.animateFloat(1f, 0f, infiniteRepeatable(tween(RECORD_DOT_BLINK_MILLIS, easing = LinearEasing), RepeatMode.Reverse), label = "record dot alpha")
    Canvas(Modifier.size(BinSize)) { drawCircle(colors.recordDot.copy(alpha = dotAlpha), radius = RecordDotRadius.toPx()) }
}

/**
 * Paused (Telegram's recorded panel): the bin, then the recording so far as Telegram shows it: a blue
 * strip with the bars, trim handles at its ends and a label with play and the length ([TrimStrip]).
 */
@Composable
private fun RowScope.PausedRecording(controller: VoiceRecordController, player: VoicePlayer) {
    val colors = MaterialTheme.messenger
    val preview = controller.preview
    val active = player.activeId == VOICE_PREVIEW_ID
    val progress = if (active && player.durationMillis > 0) (player.positionMillis.toFloat() / player.durationMillis).coerceIn(0f, 1f) else 0f
    val deleteLabel = stringResource(R.string.voice_delete_recording)
    val behind = colors.glassTint.copy(alpha = 1f)
    Box(
        Modifier
            .size(RecordSlot)
            .clip(CircleShape)
            .clickable(role = Role.Button) { controller.cancel() }
            .semantics { contentDescription = deleteLabel }
            .testTag(VOICE_DELETE_RECORDING_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(BinSize)) { drawRecordBin(BIN_FRAMES, colors.hint, colors.hint, behind) }
    }
    TrimStrip(controller, player, preview, progress.takeIf { active }, Modifier.weight(1f).padding(end = 6.dp))
}

/** Which part of the trim strip a finger holds. */
private enum class TrimPart { BADGE, LEFT, RIGHT, SEEK }

/**
 * Telegram's recorded voice with trim handles (`RecordedAudioPlayerView`): the part kept lies between
 * two small white handles, the parts cut off are darker with faint bars; in the middle of the kept part
 * a darker label plays or pauses it and says how long it is (just the sign when it does not fit). The
 * handles are dragged; a drag between them moves the place played from. Playing stays inside the
 * handles. What is kept is [VoiceRecordController.trimLeft] to [VoiceRecordController.trimRight].
 */
@Composable
private fun TrimStrip(controller: VoiceRecordController, player: VoicePlayer, preview: VoiceRecording?, position: Float?, modifier: Modifier) {
    val colors = MaterialTheme.messenger
    val density = LocalDensity.current
    val active = player.activeId == VOICE_PREVIEW_ID
    val playing = active && player.playing
    var widthPx by remember { mutableIntStateOf(0) }
    var hold by remember { mutableStateOf<Float?>(null) }
    var everPlayed by remember(preview) { mutableStateOf(false) }
    LaunchedEffect(playing) { if (playing) everPlayed = true }
    val insetPx = with(density) { TrimInset.toPx() }
    val minSpan = trimMinimumSpan(controller.previewMillis, widthPx - 2 * insetPx, with(density) { TrimMinWidth.toPx() })
    val left = controller.trimLeft
    val right = controller.trimRight
    // Playing ends at the right handle and starts again at the left one.
    LaunchedEffect(playing, position, left, right) {
        val at = position ?: return@LaunchedEffect
        if (!playing) return@LaunchedEffect
        if (at >= right) {
            player.pause()
            player.seekTo(left)
        } else if (at < left - 0.005f) {
            player.seekTo(left)
        }
    }
    val toggle: () -> Unit = {
        when {
            preview == null -> Unit
            !active -> player.play(VOICE_PREVIEW_ID, preview.bytes.copyOf(), controller.trimLeft)
            player.playing -> player.pause()
            else -> {
                val at = if (player.durationMillis > 0) player.positionMillis.toFloat() / player.durationMillis else 0f
                if (at < controller.trimLeft || at >= controller.trimRight) player.seekTo(controller.trimLeft)
                player.resume()
            }
        }
    }
    val lengthText = formatVoiceDuration(((right - left) * controller.previewMillis).toLong().coerceAtLeast(1_000L))
    val measurer = rememberTextMeasurer()
    val badgeStyle = MessengerType.MessageTime.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
    val text = remember(lengthText, badgeStyle) { measurer.measure(lengthText, badgeStyle) }
    val geometry = trimGeometry(widthPx.toFloat(), insetPx, left, right, text.size.width.toFloat(), density)
    val showText by animateFloatAsState(if (geometry.textFits) 1f else 0f, tween(TRIM_BADGE_MILLIS, easing = EaseOutQuint), label = "trim length")
    val badgeWidth = androidx.compose.ui.util.lerp(with(density) { TrimBadgeSmall.toPx() }, geometry.badgeFull, showText)
    val current by rememberUpdatedState(TrimInput(geometry, badgeWidth, minSpan, toggle))
    val playLabel = stringResource(if (playing) R.string.voice_pause else R.string.voice_play)
    val startLabel = stringResource(R.string.voice_trim_start)
    val endLabel = stringResource(R.string.voice_trim_end)
    val darker = androidx.compose.ui.graphics.lerp(colors.fab, Color.Black, 0.3f)
    Box(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .onSizeChanged { widthPx = it.width }
            .pointerInput(preview != null) {
                if (preview == null) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val input = current
                    val g = input.geometry
                    val x = down.position.x
                    val reach = TrimHandleReach.toPx()
                    val near = TrimHandleNear.toPx()
                    val badgeHalf = input.badgeWidth / 2f + TrimBadgeSlop.toPx()
                    val part = when {
                        abs(x - g.center) <= badgeHalf && abs(down.position.y - size.height / 2f) <= TrimBadgeHeight.toPx() / 2f + TrimBadgeSlop.toPx() -> TrimPart.BADGE
                        x >= g.leftX - reach && x <= g.leftX + near -> TrimPart.LEFT
                        x >= g.rightX - near && x <= g.rightX + reach -> TrimPart.RIGHT
                        x > g.leftX + near && x < g.rightX - near -> TrimPart.SEEK
                        else -> return@awaitEachGesture
                    }
                    down.consume()
                    // Handles and the place are dragged sideways: the swipe back leaves this touch alone.
                    SwipeBackGuard.claim()
                    val wasPlaying = player.activeId == VOICE_PREVIEW_ID && player.playing
                    if (part != TrimPart.BADGE && wasPlaying) player.pause()
                    if (part == TrimPart.SEEK) {
                        hold = if (player.activeId == VOICE_PREVIEW_ID && player.durationMillis > 0) {
                            (player.positionMillis.toFloat() / player.durationMillis).coerceIn(controller.trimLeft, controller.trimRight)
                        } else {
                            controller.trimLeft
                        }
                    }
                    var released = false
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) {
                                released = true
                                break
                            }
                            val fraction = ((change.position.x - insetPx) / (size.width - 2 * insetPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
                            when (part) {
                                TrimPart.LEFT -> controller.trimLeft = fraction.coerceIn(0f, (controller.trimRight - current.minSpan).coerceAtLeast(0f))
                                TrimPart.RIGHT -> controller.trimRight = fraction.coerceIn((controller.trimLeft + current.minSpan).coerceAtMost(1f), 1f)
                                TrimPart.SEEK -> hold = fraction.coerceIn(controller.trimLeft, controller.trimRight)
                                TrimPart.BADGE -> Unit
                            }
                        }
                    } finally {
                        val seekTo = hold
                        hold = null
                        if (released) {
                            val loaded = player.activeId == VOICE_PREVIEW_ID && player.durationMillis > 0
                            when (part) {
                                TrimPart.BADGE -> current.toggle()
                                TrimPart.LEFT -> if (loaded && everPlayed) {
                                    player.seekTo(controller.trimLeft)
                                    player.resume()
                                }
                                TrimPart.RIGHT -> if (loaded && everPlayed) {
                                    val back = TRIM_RIGHT_REPLAY_MILLIS.toFloat() / player.durationMillis
                                    player.seekTo(maxOf(controller.trimLeft, controller.trimRight - back))
                                    player.resume()
                                }
                                TrimPart.SEEK -> {
                                    val target = seekTo ?: controller.trimLeft
                                    if (loaded) {
                                        player.seekTo(target)
                                        player.resume()
                                    } else {
                                        preview.let { player.play(VOICE_PREVIEW_ID, it.bytes.copyOf(), target) }
                                    }
                                }
                            }
                        }
                    }
                }
            },
    ) {
        val bars = preview?.waveform ?: ByteArray(0)
        Canvas(Modifier.fillMaxSize()) {
            drawTrimStrip(
                bars = bars,
                geometry = geometry,
                badgeWidth = badgeWidth,
                progress = hold ?: position,
                lit = active || hold != null || everPlayed,
                text = text,
                textAlpha = showText,
                playing = playing,
                selected = colors.fab,
                darker = darker,
            )
        }
        // The label and the handles as the screen reader (and the tests) know them.
        val badgeWidthDp = with(density) { badgeWidth.toDp() }
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((geometry.center - badgeWidth / 2f).roundToInt(), 0) }
                .size(width = badgeWidthDp, height = TrimBadgeHeight)
                .semantics {
                    role = Role.Button
                    contentDescription = playLabel
                    onClick {
                        toggle()
                        true
                    }
                }
                .testTag(VOICE_PREVIEW_PLAY_TAG),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((geometry.leftX - TrimHandleReach.toPx()).roundToInt(), 0) }
                .size(width = TrimHandleReach + TrimHandleNear, height = 34.dp)
                .semantics { contentDescription = startLabel }
                .testTag(VOICE_TRIM_LEFT_TAG),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((geometry.rightX - TrimHandleNear.toPx()).roundToInt(), 0) }
                .size(width = TrimHandleReach + TrimHandleNear, height = 34.dp)
                .semantics { contentDescription = endLabel }
                .testTag(VOICE_TRIM_RIGHT_TAG),
        )
    }
}

/** What the trim strip's gesture reads, always the newest. */
private class TrimInput(val geometry: TrimGeometry, val badgeWidth: Float, val minSpan: Float, val toggle: () -> Unit)

/** Where the handles and the label of the trim strip are, in pixels. */
private class TrimGeometry(val width: Float, val leftX: Float, val rightX: Float, val center: Float, val badgeFull: Float, val textFits: Boolean)

private fun trimGeometry(width: Float, inset: Float, left: Float, right: Float, textWidth: Float, density: androidx.compose.ui.unit.Density): TrimGeometry {
    val span = (width - 2 * inset).coerceAtLeast(0f)
    val leftX = inset + span * left.coerceIn(0f, 1f)
    val rightX = inset + span * right.coerceIn(0f, 1f)
    val badgeFull = with(density) { TrimBadgeChrome.toPx() } + textWidth
    val fits = badgeFull <= rightX - leftX - with(density) { 8.dp.toPx() }
    return TrimGeometry(width, leftX, rightX, (leftX + rightX) / 2f, badgeFull, fits)
}

/** Draws the trim strip as Telegram's `RecordedAudioPlayerView.drawIn` does. */
private fun DrawScope.drawTrimStrip(
    bars: ByteArray,
    geometry: TrimGeometry,
    badgeWidth: Float,
    progress: Float?,
    lit: Boolean,
    text: androidx.compose.ui.text.TextLayoutResult,
    textAlpha: Float,
    playing: Boolean,
    selected: Color,
    darker: Color,
) {
    val left = geometry.leftX
    val right = geometry.rightX
    drawRect(darker)
    // Faint bars all along, then the kept part on top in its own color.
    val heights = trimBarHeights(bars, ((size.width - 27.dp.toPx()) / 3.dp.toPx()).toInt())
    val barsLeft = 14.dp.toPx()
    fun drawBars(color: Color) {
        heights.forEachIndexed { index, value ->
            val h = 2.dp.toPx() + (12.dp.toPx() - 2.dp.toPx()) * value
            drawRoundRect(color, Offset(barsLeft + index * 3.dp.toPx(), center.y - h / 2f), Size(2.dp.toPx(), h), CornerRadius(1.dp.toPx()))
        }
    }
    drawBars(TrimBarColor.copy(alpha = TrimBarColor.alpha * 0.3f))
    drawRect(selected, Offset(left, 0f), Size((right - left).coerceAtLeast(0f), size.height))
    val at = (progress ?: 1f)
    val progressX = (barsLeft - 1.dp.toPx() + (size.width - 27.dp.toPx()) * at).coerceIn(left, right)
    if (progressX < right) clipRect(progressX, 0f, right, size.height) { drawBars(TrimBarColor) }
    if (progressX > left) clipRect(left, 0f, progressX, size.height) { drawBars(if (lit) Color.White else TrimBarColor) }
    // The handles: 1.67 by 10.67 dp, 5.33 dp outside the kept part.
    val handleHeight = 10.67.dp.toPx()
    val handleWidth = 7.dp.toPx() - 5.33.dp.toPx()
    drawRoundRect(Color.White, Offset(left - 7.dp.toPx(), center.y - handleHeight / 2f), Size(handleWidth, handleHeight), CornerRadius(handleWidth / 2f))
    drawRoundRect(Color.White, Offset(right + 5.33.dp.toPx(), center.y - handleHeight / 2f), Size(handleWidth, handleHeight), CornerRadius(handleWidth / 2f))
    // The label: a darker pill with play or pause, and the length when it fits.
    val badgeHeight = TrimBadgeHeight.toPx()
    val badgeLeft = geometry.center - badgeWidth / 2f
    drawRoundRect(darker, Offset(badgeLeft, center.y - badgeHeight / 2f), Size(badgeWidth, badgeHeight), CornerRadius(badgeHeight / 2f))
    val sign = 12.dp.toPx()
    val signLeft = androidx.compose.ui.util.lerp(geometry.center - sign / 2f, badgeLeft + 6.dp.toPx(), textAlpha)
    translateTo(Offset(signLeft + sign / 2f, center.y)) { scaleAround(0.5f) { if (playing) drawPause(Color.White) else drawPlay(Color.White) } }
    if (textAlpha > 0f) {
        drawText(text, topLeft = Offset(badgeLeft + 21.66.dp.toPx(), center.y - text.size.height / 2f), alpha = textAlpha)
    }
}

/** How far the text field stands aside for the bin while a recording leaves. */
private val FieldShift = 28.dp

/**
 * The text field of the bar, coming back after a recording left it the way Telegram's does: it fades
 * in after 300 ms (150 ms when sent) in 200 ms beside the bin, and slides into its place as the bin
 * shrinks away. [field] gets the modifier that moves it.
 */
@Composable
internal fun RowScope.FieldAfterRecording(controller: VoiceRecordController?, field: @Composable (Modifier) -> Unit) {
    val exit = controller?.exit
    val alpha = remember { Animatable(1f) }
    val aside = remember { Animatable(0f) }
    LaunchedEffect(controller?.exitKey) {
        val leaving = controller?.exit
        if (leaving == null) {
            alpha.snapTo(1f)
            aside.snapTo(0f)
            return@LaunchedEffect
        }
        alpha.snapTo(0f)
        aside.snapTo(1f)
        launch {
            delay(if (leaving == RecordExit.SEND) FIELD_BACK_DELAY_SEND else FIELD_BACK_DELAY_CANCEL)
            alpha.animateTo(1f, tween(FIELD_BACK_MILLIS))
        }
        delay(if (leaving == RecordExit.SEND) MIC_BACK_DELAY_SEND else CANCEL_ICONS_DELAY)
        aside.animateTo(0f, tween(ICONS_MILLIS))
    }
    val shift = with(LocalDensity.current) { FieldShift.toPx() }
    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
        field(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    this.alpha = alpha.value
                    translationX = shift * aside.value
                },
        )
        if (controller != null && exit != null) VoiceRecordLeaving(controller)
    }
}

/**
 * What stays of a recording in the bar while it leaves, over the returning text field: the bin playing
 * (or, when sent, the dot shrinking away), and the timer and its text going (Telegram's timings).
 */
@Composable
internal fun VoiceRecordLeaving(controller: VoiceRecordController, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.messenger
    val exit = controller.exit ?: return
    val cancel = exit != RecordExit.SEND
    val bin = remember(controller.exitKey) { Animatable(0f) }
    val shrink = remember(controller.exitKey) { Animatable(1f) }
    val timer = remember(controller.exitKey) { Animatable(0f) }
    LaunchedEffect(controller.exitKey) {
        if (cancel) {
            launch { bin.animateTo(1f, tween(BIN_MILLIS, easing = LinearEasing)) }
            launch {
                delay(TIMER_LEAVE_DELAY_CANCEL)
                timer.animateTo(1f, tween(TIMER_LEAVE_MILLIS_CANCEL))
            }
            delay(CANCEL_ICONS_DELAY)
            shrink.animateTo(0f, tween(ICONS_MILLIS))
        } else {
            launch { timer.animateTo(1f, tween(TIMER_LEAVE_MILLIS_SEND)) }
            delay(MIC_BACK_DELAY_SEND)
            shrink.animateTo(0f, tween(ICONS_MILLIS))
        }
    }
    val shift = with(LocalDensity.current) { (if (cancel) TimerShiftCancel else TimerShiftSend).toPx() } * timer.value
    Row(modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(RecordSlot).graphicsLayer { scaleX = shrink.value; scaleY = shrink.value }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(BinSize)) {
                if (cancel) {
                    drawRecordBin(bin.value * BIN_FRAMES, colors.hint, colors.recordDot, colors.glassTint.copy(alpha = 1f))
                } else {
                    drawCircle(colors.recordDot, radius = RecordDotRadius.toPx())
                }
            }
        }
        if (!controller.exitPaused) {
            RecordTimer(
                controller.exitMillis,
                Modifier.graphicsLayer {
                    alpha = 1f - timer.value
                    translationX = shift
                },
            )
        }
    }
}

/**
 * The bin of a cancelled recording, as Telegram animates it (`chat_audio_record_delete_2`, [frame] 0 to
 * 52 at 60 a second): it grows out of the red dot in grey, its lid sinks in, the bin turns red, the lid
 * jumps up and settles with a little wobble, and three lines rise into place on it. Drawn in a 100 by
 * 100 grid, like the animation. [slit] is the color behind the bin, for the lines.
 */
private fun DrawScope.drawRecordBin(frame: Float, grey: Color, red: Color, slit: Color) {
    val unit = size.minDimension / 100f
    val color = if (frame < 14f) grey else red
    val grow = 0.35f + 0.65f * EaseOut.transform((frame / 9f).coerceIn(0f, 1f))
    val lidY = keyframes(frame, floatArrayOf(9f, 0f, 14f, 14f, 25f, -6f, 29f, 0f, 33f, -3f, 37f, 0f))
    val lidTurn = keyframes(frame, floatArrayOf(33f, 0f, 37f, 5f, 41f, -5f, 47f, 0f))
    val lines = ((frame - 12f) / 7f).coerceIn(0f, 1f)
    val linesY = keyframes(frame, floatArrayOf(15f, 6f, 23f, 0f))
    scale(grow, grow, center) {
        val body = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 33f * unit,
                    top = 42f * unit,
                    right = 67f * unit,
                    bottom = 84f * unit,
                    topLeftCornerRadius = CornerRadius(2f * unit),
                    topRightCornerRadius = CornerRadius(2f * unit),
                    bottomRightCornerRadius = CornerRadius(7f * unit),
                    bottomLeftCornerRadius = CornerRadius(7f * unit),
                ),
            )
        }
        drawPath(body, color)
        if (lines > 0f) {
            for (x in floatArrayOf(42f, 50f, 58f)) {
                drawLine(
                    slit.copy(alpha = slit.alpha * lines),
                    Offset(x * unit, (52f + linesY) * unit),
                    Offset(x * unit, (74f + linesY) * unit),
                    strokeWidth = 4f * unit,
                    cap = StrokeCap.Round,
                )
            }
        }
        rotate(lidTurn, Offset(50f * unit, (33f + lidY) * unit)) {
            drawRoundRect(color, Offset(27f * unit, (30f + lidY) * unit), Size(46f * unit, 7f * unit), CornerRadius(3.5f * unit))
            drawRoundRect(color, Offset(41f * unit, (23f + lidY) * unit), Size(18f * unit, 9f * unit), CornerRadius(3f * unit))
        }
    }
}

/** The value at [frame] of pairs (frame, value), straight in between, held before the first and after the last. */
private fun keyframes(frame: Float, pairs: FloatArray): Float {
    if (frame <= pairs[0]) return pairs[1]
    var i = 0
    while (i + 2 < pairs.size) {
        val f0 = pairs[i]
        val v0 = pairs[i + 1]
        val f1 = pairs[i + 2]
        val v1 = pairs[i + 3]
        if (frame <= f1) return v0 + (v1 - v0) * ((frame - f0) / (f1 - f0))
        i += 2
    }
    return pairs[pairs.size - 1]
}

/** A short note over the end of the chat: how recording works, or why it did not; with a flame for one-time. */
@Composable
internal fun VoiceHint(hint: ChatHint, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.messenger
    Row(
        modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .widthIn(max = 420.dp)
            .background(colors.chip, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag(VOICE_HINT_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hint.fire) {
            Canvas(Modifier.size(22.dp)) { drawFlame(colors.chipText) }
            Spacer(Modifier.width(12.dp))
        }
        Text(stringResource(hint.text), style = MessengerType.DateChip, color = colors.chipText)
    }
}

/**
 * A voice message inside its bubble (Telegram's voice cell): the round play button, the loudness bars
 * that fill as it plays (a tap plays or pauses, a drag along them moves the place, as in Telegram), the
 * length or the place reached, and the time.
 * Ours can be played as soon as it is recorded; the contact's once its recording is here. While it is
 * fetched the button turns; one that never came says so. A one-time recording carries Telegram's "1"
 * until it is played; the contact's plays only once ([onPlay] knows), without jumping.
 */
@Composable
internal fun VoiceBubbleContent(
    message: V2StoredMessage,
    voice: V2VoiceInfo,
    player: VoicePlayer,
    maxWidth: Dp,
    onPlay: (fraction: Float) -> Unit,
    /** The bubble's own long press (its menu); the bars take the press, so they pass it on. */
    onLongPress: (() -> Unit)?,
    meta: @Composable () -> Unit,
) {
    val colors = MaterialTheme.messenger
    val outgoing = message.outgoing
    val active = player.activeId == message.messageId
    val played = voice.state == V2MediaState.PLAYED
    val playable = voice.state == V2MediaState.READY || (outgoing && voice.state == V2MediaState.UPLOADING)
    // While it plays it can be paused, even once a one-time recording is marked played.
    val kind = when {
        active && player.playing -> PlayKind.PAUSE
        active -> PlayKind.PLAY
        played -> PlayKind.PLAYED
        voice.state == V2MediaState.UNAVAILABLE -> PlayKind.UNAVAILABLE
        !playable -> PlayKind.LOADING
        else -> PlayKind.PLAY
    }
    // The contact's one-time recording cannot be jumped in: it plays from the start, once.
    val seekable = !(voice.once && !outgoing)
    // Telegram's seek bar: a place dragged to on a message that is not playing stays and is where it starts.
    var startAt by remember(message.messageId) { mutableFloatStateOf(0f) }
    var scrubbing by remember(message.messageId) { mutableStateOf<Float?>(null) }
    LaunchedEffect(active) { if (active) startAt = 0f }
    val progress = scrubbing ?: when {
        active && player.durationMillis > 0 -> (player.positionMillis.toFloat() / player.durationMillis).coerceIn(0f, 1f)
        active -> 0f
        else -> startAt
    }
    val toggle: () -> Unit = {
        when {
            !active -> onPlay(if (seekable) startAt else 0f)
            player.playing -> player.pause()
            else -> player.resume()
        }
    }
    // The bars' gesture outlives recompositions: it calls the newest of these.
    val tap by rememberUpdatedState(toggle)
    val longPress by rememberUpdatedState(onLongPress)
    val dragEnd by rememberUpdatedState<(Float) -> Unit> { fraction ->
        if (active) player.seekTo(fraction) else startAt = fraction
    }
    val place by rememberUpdatedState(progress)
    val slopPx = pixelsInCm(VoiceRules.SEEK_START_CM, LocalContext.current.resources.displayMetrics.xdpi)
    val description = stringResource(
        when (kind) {
            PlayKind.PAUSE -> R.string.voice_pause
            PlayKind.LOADING -> R.string.voice_downloading
            PlayKind.UNAVAILABLE -> R.string.voice_unavailable
            PlayKind.PLAYED -> R.string.voice_played
            PlayKind.PLAY -> if (voice.once) R.string.voice_once else R.string.voice_play
        },
    )
    val waveformWidth = minOf(WaveformMaxWidth, (maxWidth - VoiceBubbleChrome).coerceAtLeast(60.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        Box {
            VoicePlayButton(
                kind = kind,
                outgoing = outgoing,
                onClick = if (playable || active) toggle else null,
                description = description,
                modifier = Modifier.testTag(VOICE_PLAY_TAG_PREFIX + message.messageId),
            )
            if (voice.once && !played) {
                OnceMark(outgoing, Modifier.align(Alignment.BottomEnd).offset(x = 5.dp, y = 5.dp).testTag(VOICE_ONCE_MARK_TAG_PREFIX + message.messageId))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.width(waveformWidth)) {
            VoiceWaveform(
                waveform = voice.waveform,
                progress = if (played && !active) 0f else progress,
                outgoing = outgoing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WaveformHeight)
                    .graphicsLayer { alpha = if (played && !active) 0.4f else 1f }
                    .pointerInput(playable || active, seekable, slopPx) {
                        val usable = playable || active
                        detectVoiceSeek(
                            slopPx = slopPx,
                            seekable = seekable && usable,
                            place = { place },
                            onTap = { if (usable) tap() },
                            onLongPress = { longPress?.invoke() },
                            onDrag = { scrubbing = it },
                            onDragEnd = { fraction ->
                                scrubbing = null
                                if (fraction != null) dragEnd(fraction)
                            },
                        )
                    }
                    .testTag(VOICE_WAVEFORM_TAG_PREFIX + message.messageId),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val shownMillis = scrubbing?.let { (it * (if (active) player.durationMillis else voice.durationMillis.toLong())).toLong() }
                val label = when {
                    shownMillis != null -> formatVoiceDuration(shownMillis)
                    played && !active -> stringResource(R.string.voice_played)
                    voice.state == V2MediaState.UNAVAILABLE -> stringResource(R.string.voice_unavailable)
                    !active && startAt > 0f -> formatVoiceDuration((startAt * voice.durationMillis).toLong())
                    active && player.positionMillis > 0 -> formatVoiceDuration(player.positionMillis)
                    else -> formatVoiceDuration(voice.durationMillis.toLong())
                }
                Text(
                    label,
                    style = MessengerType.MessageTime,
                    color = if (outgoing) colors.timeOutgoing else colors.timeIncoming,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // Telegram's "not played yet" (`isContentUnread`, DEC-039): a 3 dp dot in the bars'
                // played color, 6 dp after the length. The contact's goes as soon as it plays here; ours
                // when the contact's app says it was played.
                val unheard = !voice.listened && voice.state != V2MediaState.UNAVAILABLE && voice.state != V2MediaState.PLAYED &&
                    !(active && !outgoing)
                if (unheard) {
                    val dot = if (outgoing) colors.waveOutgoingPlayed else colors.waveIncomingPlayed
                    val unheardLabel = stringResource(R.string.voice_unheard)
                    Canvas(
                        Modifier
                            .padding(start = 3.dp)
                            .size(6.dp)
                            .semantics { contentDescription = unheardLabel }
                            .testTag(VOICE_UNHEARD_TAG_PREFIX + message.messageId),
                    ) { drawCircle(dot) }
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                meta()
            }
        }
    }
}

private enum class PlayKind { PLAY, PAUSE, LOADING, UNAVAILABLE, PLAYED }

private enum class SeekStart { TAP, DRAG, GONE }

/**
 * Telegram's touch on the bars (`SeekBarWaveform.onTouch`, `ChatMessageCell.checkAudioMotionEvent`): a
 * tap plays or pauses; a finger moved sideways by [slopPx] (0.2 cm) drags the place along from where
 * it was ([place]), shown as it goes ([onDrag]) and taken on release ([onDragEnd]; null when the gesture
 * was lost). From then on the drag holds the moves, so neither the reply swipe nor the swipe back
 * starts; a move up or down first leaves it to the list. A long press without moving opens the menu.
 */
private suspend fun PointerInputScope.detectVoiceSeek(
    slopPx: Float,
    seekable: Boolean,
    place: () -> Float,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float?) -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    val from = place()
    // Null when the finger stayed down without moving: a long press.
    val start: SeekStart? = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        var decided: SeekStart? = null
        while (decided == null) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
            val dx = if (change == null) 0f else abs(change.position.x - down.position.x)
            val dy = if (change == null) 0f else abs(change.position.y - down.position.y)
            decided = when {
                change == null || change.isConsumed -> SeekStart.GONE
                !change.pressed -> SeekStart.TAP.also { change.consume() }
                seekable && dx > slopPx && dx > dy -> SeekStart.DRAG.also {
                    change.consume()
                    // The drag is ours: the swipe back leaves it alone.
                    SwipeBackGuard.claim()
                }
                dy > viewConfiguration.touchSlop || (!seekable && dx > viewConfiguration.touchSlop) -> SeekStart.GONE
                else -> null
            }
        }
        decided
    }
    when (start) {
        null -> {
            onLongPress()
            // The rest of the press belongs to the menu.
            do {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
        SeekStart.TAP -> onTap()
        SeekStart.GONE -> Unit
        SeekStart.DRAG -> {
            var fraction = from
            var ended = false
            try {
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    fraction = seekFraction(from, change.position.x - down.position.x, size.width.toFloat())
                    change.consume()
                    if (!change.pressed) {
                        ended = true
                        onDragEnd(fraction)
                        break
                    }
                    onDrag(fraction)
                }
            } finally {
                if (!ended) onDragEnd(null)
            }
        }
    }
}

@Composable
private fun VoicePlayButton(kind: PlayKind, outgoing: Boolean, onClick: (() -> Unit)?, description: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.messenger
    val background = if (outgoing) colors.voiceButtonOutgoing else colors.voiceButtonIncoming
    val icon = if (outgoing) colors.voiceIconOutgoing else colors.voiceIconIncoming
    val faded = kind == PlayKind.UNAVAILABLE || kind == PlayKind.PLAYED
    Box(
        modifier
            .size(PlayButtonSize)
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { contentDescription = description },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(if (faded) background.copy(alpha = 0.4f) else background)
            when (kind) {
                PlayKind.PLAY -> drawPlay(icon)
                PlayKind.UNAVAILABLE -> drawPlay(icon.copy(alpha = 0.7f))
                PlayKind.PLAYED -> drawCheck(icon)
                PlayKind.PAUSE -> drawPause(icon)
                PlayKind.LOADING -> Unit
            }
        }
        // Only a recording on its way turns; the others draw nothing on every frame.
        if (kind == PlayKind.LOADING) LoadingRing(icon)
    }
}

@Composable
private fun LoadingRing(color: Color) {
    val turning = rememberInfiniteTransition(label = "voice loading")
    val angle by turning.animateFloat(0f, 360f, infiniteRepeatable(tween(1_000, easing = LinearEasing)), label = "voice loading angle")
    Canvas(Modifier.fillMaxSize()) {
        val inset = 12.dp.toPx()
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - 2 * inset, size.height - 2 * inset),
            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/**
 * Telegram's mark of a one-time recording (`ChatMessageCell.drawVoiceOnce`): a 20 dp circle in the
 * play button's color at its lower right, set apart by a ring of the bubble's color, with "1" in a
 * dashed ring.
 */
@Composable
private fun OnceMark(outgoing: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.messenger
    val button = if (outgoing) colors.voiceButtonOutgoing else colors.voiceButtonIncoming
    val bubble = if (outgoing) colors.bubbleOutgoing else colors.bubbleIncoming
    val icon = if (outgoing) colors.voiceIconOutgoing else colors.voiceIconIncoming
    Box(modifier.size(23.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(bubble)
            drawCircle(button, radius = 10.dp.toPx())
            drawOnceMark(icon, fill = null, ringDiameter = 14.dp.toPx(), textScale = 0.8f)
        }
    }
}

/**
 * Telegram's bars: every 3 dp one of 2 dp, mirrored around the middle, played ones in the accent (or in
 * [playedColor] and [unplayedColor] where the bars sit on a colored strip).
 */
@Composable
private fun VoiceWaveform(
    waveform: ByteArray,
    progress: Float,
    outgoing: Boolean,
    modifier: Modifier = Modifier,
    unplayedColor: Color? = null,
    playedColor: Color? = null,
) {
    val colors = MaterialTheme.messenger
    val unplayed = unplayedColor ?: if (outgoing) colors.waveOutgoing else colors.waveIncoming
    val played = playedColor ?: if (outgoing) colors.waveOutgoingPlayed else colors.waveIncomingPlayed
    Canvas(modifier) {
        val step = VoiceRules.BAR_STEP_DP.dp.toPx()
        val width = VoiceRules.BAR_WIDTH_DP.dp.toPx()
        val half = VoiceRules.BAR_HALF_HEIGHT_DP.dp.toPx()
        val bars = waveformBars(waveform, (size.width / step).toInt())
        val playedUntil = progress * size.width
        val middle = size.height / 2
        bars.forEachIndexed { index, value ->
            val x = index * step
            val height = value * half
            drawRoundRect(
                color = if (x + width / 2 <= playedUntil) played else unplayed,
                topLeft = Offset(x, middle - height - width / 2),
                size = Size(width, 2 * height + width),
                cornerRadius = CornerRadius(width / 2),
            )
        }
    }
}

private fun Offset.isSpecified(): Boolean = this != Offset.Unspecified && x.isFinite() && y.isFinite()

/** Draws [block] as if the middle of the canvas were at [point]. */
private inline fun DrawScope.translateTo(point: Offset, block: DrawScope.() -> Unit) {
    val dx = point.x - center.x
    val dy = point.y - center.y
    drawContext.transform.translate(dx, dy)
    block()
    drawContext.transform.translate(-dx, -dy)
}

/** Draws [block] shrunk around the middle of the canvas. */
private inline fun DrawScope.scaleAround(factor: Float, block: DrawScope.() -> Unit) {
    drawContext.transform.scale(factor, factor, center)
    block()
    drawContext.transform.scale(1f / factor, 1f / factor, center)
}

private fun DrawScope.drawPlay(color: Color) {
    val unit = 1.dp.toPx()
    // A rounded triangle, nudged right so it looks centred, like Telegram's.
    val path = Path().apply {
        moveTo(center.x - 5f * unit, center.y - 8f * unit)
        lineTo(center.x + 9f * unit, center.y)
        lineTo(center.x - 5f * unit, center.y + 8f * unit)
        close()
    }
    drawPath(path, color)
    drawPath(path, color, style = Stroke(width = 2f * unit, join = StrokeJoin.Round))
}

private fun DrawScope.drawPause(color: Color) {
    val unit = 1.dp.toPx()
    val barSize = Size(4f * unit, 14f * unit)
    val radius = CornerRadius(1.5f * unit)
    drawRoundRect(color, Offset(center.x - 6f * unit, center.y - 7f * unit), barSize, radius)
    drawRoundRect(color, Offset(center.x + 2f * unit, center.y - 7f * unit), barSize, radius)
}

/** A tick: the contact's one-time recording was played (or ours was played by the contact). */
private fun DrawScope.drawCheck(color: Color) {
    val unit = 1.dp.toPx()
    val stroke = 2.2f * unit
    drawLine(color, Offset(center.x - 7f * unit, center.y), Offset(center.x - 2f * unit, center.y + 5f * unit), stroke, StrokeCap.Round)
    drawLine(color, Offset(center.x - 2f * unit, center.y + 5f * unit), Offset(center.x + 7f * unit, center.y - 5f * unit), stroke, StrokeCap.Round)
}

/** A microphone: the capsule, the stand around it, the stem and the foot. */
private fun DrawScope.drawMicrophone(color: Color) {
    val unit = 1.dp.toPx()
    val stroke = 1.8f * unit
    val top = center.y - 9f * unit
    drawRoundRect(
        color,
        topLeft = Offset(center.x - 3.5f * unit, top),
        size = Size(7f * unit, 12f * unit),
        cornerRadius = CornerRadius(3.5f * unit),
    )
    drawArc(
        color,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(center.x - 6.5f * unit, top + 2f * unit),
        size = Size(13f * unit, 13f * unit),
        style = Stroke(stroke, cap = StrokeCap.Round),
    )
    drawLine(color, Offset(center.x, top + 15f * unit), Offset(center.x, top + 18f * unit), stroke, StrokeCap.Round)
    drawLine(color, Offset(center.x - 3f * unit, top + 18f * unit), Offset(center.x + 3f * unit, top + 18f * unit), stroke, StrokeCap.Round)
}

/**
 * The send arrow of the locked circle. Telegram draws a paper plane here; a white paper plane in a blue
 * circle is Telegram's logo, which this app never uses (DEC-033), so it stays an arrow.
 */
private fun DrawScope.drawSendArrow(color: Color) {
    val unit = 1.dp.toPx()
    val stroke = 2.4f * unit
    val shaft = 9f * unit
    val head = 7f * unit
    val tip = Offset(center.x, center.y - shaft)
    drawLine(color, Offset(center.x, center.y + shaft), tip, stroke, StrokeCap.Round)
    drawLine(color, Offset(center.x - head, center.y - shaft + head), tip, stroke, StrokeCap.Round)
    drawLine(color, Offset(center.x + head, center.y - shaft + head), tip, stroke, StrokeCap.Round)
}

/**
 * Telegram's padlock (`ControlsView`): a 12 dp body with 3 dp corners under an 8 dp shackle of 1.7 dp,
 * set 2 dp above the middle. At rest ([open] 1) the shackle stands 2 dp open and the lock leans 9
 * degrees; pulled up it closes and stands straight.
 */
private fun DrawScope.drawLock(color: Color, open: Float) {
    val unit = 1.dp.toPx()
    rotate(9f * open, center) {
        val bodyTop = center.y - 2f * unit
        drawRoundRect(color, Offset(center.x - 6f * unit, bodyTop), Size(12f * unit, 12f * unit), CornerRadius(3f * unit))
        val lift = 2f * unit * open
        drawArc(
            color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(center.x - 4f * unit, bodyTop - 8f * unit - lift),
            size = Size(8f * unit, 8f * unit),
            style = Stroke(1.7f * unit),
        )
        drawLine(color, Offset(center.x + 4f * unit, bodyTop - 4f * unit - lift), Offset(center.x + 4f * unit, bodyTop), 1.7f * unit, StrokeCap.Round)
        drawLine(color, Offset(center.x - 4f * unit, bodyTop - 4f * unit - lift), Offset(center.x - 4f * unit, bodyTop - 2f * unit - lift), 1.7f * unit, StrokeCap.Round)
    }
}

/**
 * "1" in a dashed ring (Telegram's `PeriodDrawable` of one-time messages): the ring [ringDiameter]
 * wide, 1.5 dp, the digit drawn as strokes so no font is needed.
 */
private fun DrawScope.drawOnceMark(color: Color, fill: Color?, ringDiameter: Float = 20.dp.toPx(), textScale: Float = 1f) {
    val unit = 1.dp.toPx()
    val radius = ringDiameter / 2f
    if (fill != null) drawCircle(fill, radius)
    val dash = 2.2f * unit
    drawCircle(
        color,
        radius = radius - 0.75f * unit,
        style = Stroke(1.5f * unit, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.8f))),
    )
    // The digit one: a stem and a short flag.
    val h = 4.5f * unit * textScale
    val stroke = 1.6f * unit * textScale
    drawLine(color, Offset(center.x + 0.5f * unit, center.y - h), Offset(center.x + 0.5f * unit, center.y + h), stroke, StrokeCap.Round)
    drawLine(color, Offset(center.x + 0.5f * unit, center.y - h), Offset(center.x - 2.2f * unit * textScale, center.y - h + 2.2f * unit * textScale), stroke, StrokeCap.Round)
}

/** Telegram's flame of one-time messages, drawn: a drop that ends in a lick of fire, with a small one inside. */
private fun DrawScope.drawFlame(color: Color) {
    val w = size.width
    val h = size.height
    val outer = Path().apply {
        moveTo(w * 0.52f, h * 0.04f)
        cubicTo(w * 0.62f, h * 0.30f, w * 0.92f, h * 0.42f, w * 0.88f, h * 0.68f)
        cubicTo(w * 0.85f, h * 0.88f, w * 0.68f, h * 0.98f, w * 0.50f, h * 0.98f)
        cubicTo(w * 0.30f, h * 0.98f, w * 0.12f, h * 0.86f, w * 0.12f, h * 0.64f)
        cubicTo(w * 0.12f, h * 0.48f, w * 0.24f, h * 0.38f, w * 0.30f, h * 0.30f)
        cubicTo(w * 0.32f, h * 0.42f, w * 0.38f, h * 0.48f, w * 0.44f, h * 0.50f)
        cubicTo(w * 0.40f, h * 0.32f, w * 0.44f, h * 0.16f, w * 0.52f, h * 0.04f)
        close()
    }
    drawPath(outer, color)
}

private val RecordTimeStyle = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.sp, fontFeatureSettings = "tnum")
private val RecordSlideStyle = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.sp)
private val RecordCancelStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, letterSpacing = 0.sp)
