package app.securemessenger.ui

import android.content.Context
import android.content.SharedPreferences
import android.view.HapticFeedbackConstants
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.securemessenger.signalprotocol.ConversationV2Service
import kotlin.math.hypot
import kotlinx.coroutines.launch

internal const val SELECTION_CLOSE_TAG = "chat_selection_close"
internal const val SELECTION_COUNT_TAG = "chat_selection_count"
internal const val SELECTION_PIN_TAG = "chat_selection_pin"
internal const val SELECTION_MUTE_TAG = "chat_selection_mute"
internal const val SELECTION_DELETE_TAG = "chat_selection_delete"
internal const val SELECTION_MORE_TAG = "chat_selection_more"
internal const val SELECTION_READ_TAG = "chat_selection_read"
internal const val SELECTION_CLEAR_TAG = "chat_selection_clear"
internal const val CONFIRM_CHAT_ACTION_TAG = "confirm_chat_action"
internal const val SELECTED_CHECK_TAG = "chat_selected_check"

/**
 * What the chat list can do to chats (DEC-035). All of it stays on this phone; deleting removes the
 * contact with its keys (owner decision). Empty by default, for screens shown without a pipeline.
 */
internal class ChatListActions(
    val pin: (Collection<String>, Boolean) -> Unit = { _, _ -> },
    /** null unmutes. */
    val mute: (Collection<String>, Long?) -> Unit = { _, _ -> },
    val markRead: (Collection<String>) -> Unit = {},
    val clearHistory: (Collection<String>) -> Unit = {},
    val delete: (Collection<String>) -> Unit = {},
)

/** What a swipe to the left on a chat row does, chosen in the settings like Telegram's "Swipe gesture". */
internal enum class ChatSwipeAction(@StringRes val label: Int) {
    PIN(R.string.chat_swipe_pin),
    READ(R.string.chat_swipe_read),
    MUTE(R.string.chat_swipe_mute),
    DELETE(R.string.chat_swipe_delete),
}

/** The person's choice of [ChatSwipeAction]; pinning by default. A local preference, no chat data in it. */
internal object ChatSwipeSetting {
    private const val PREFERENCES = "secure_messenger_chat_list"
    private const val ACTION = "swipe_action"

    fun get(context: Context): ChatSwipeAction {
        val stored = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(ACTION, null)
        return ChatSwipeAction.entries.firstOrNull { it.name == stored } ?: ChatSwipeAction.PIN
    }

    fun set(context: Context, action: ChatSwipeAction) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString(ACTION, action.name).apply()
    }

    /**
     * The choice as Compose state for the chat list: a change made in the settings applies to the very
     * next swipe, not only once the list happens to be drawn again.
     */
    @Composable
    fun rememberChosen(context: Context): ChatSwipeAction {
        var chosen by remember { mutableStateOf(get(context)) }
        DisposableEffect(context) {
            val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            // Android keeps listeners weakly; this effect holds it until it is disposed.
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == ACTION) chosen = get(context) }
            preferences.registerOnSharedPreferenceChangeListener(listener)
            onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        return chosen
    }
}

/** How a swipe looks for one chat: Telegram's band color, icon and word. */
internal class SwipeLook(val color: Color, @DrawableRes val icon: Int, @StringRes val label: Int)

/** Telegram's colors for the band (`chats_archiveBackground`, `dialogSwipeRemove`) and the reveal (`chats_archivePinBackground`). */
private val SwipeRemove = Color(0xFFE56555)
private val SwipeReveal = Color(0xFF9FAAB3)

/** The look of [action] on a chat in its present state, or null when it would do nothing (read with nothing unread). */
@Composable
internal fun swipeLook(action: ChatSwipeAction, pinned: Boolean, muted: Boolean, unread: Boolean): SwipeLook? {
    val accent = MaterialTheme.colorScheme.primary
    return when (action) {
        ChatSwipeAction.PIN ->
            if (pinned) SwipeLook(accent, R.drawable.tg_chats_unpin, R.string.chat_action_unpin)
            else SwipeLook(accent, R.drawable.tg_chats_pin, R.string.chat_action_pin)
        ChatSwipeAction.READ -> if (unread) SwipeLook(accent, R.drawable.tg_msg_markread, R.string.chat_action_read) else null
        ChatSwipeAction.MUTE ->
            if (muted) SwipeLook(accent, R.drawable.tg_msg_unmute, R.string.chat_action_unmute)
            else SwipeLook(accent, R.drawable.tg_msg_mute, R.string.chat_action_mute)
        ChatSwipeAction.DELETE -> SwipeLook(SwipeRemove, R.drawable.tg_msg_delete, R.string.chat_action_delete)
    }
}

/** A swipe counts once the row was pulled this far to the left, in parts of its width (`getSwipeThreshold`). */
internal const val SWIPE_THRESHOLD = 0.45f

/** Or once it was flung left at least this fast, in pixels a second (`getSwipeEscapeVelocity`). */
internal const val SWIPE_ESCAPE_VELOCITY = 3_500f

/** The row slides back in this long (`ANIMATION_TYPE_SWIPE_CANCEL`). */
private const val SWIPE_BACK_MILLIS = 200

/** The reveal circle grows in this long once the row passed the threshold. */
private const val SWIPE_REVEAL_MILLIS = 300

/** Whether a swipe that ended at [offsetPx] (negative: to the left) with [velocityX] runs the action. */
internal fun swipeCounts(offsetPx: Float, widthPx: Float, velocityX: Float): Boolean =
    widthPx > 0f && (-offsetPx >= widthPx * SWIPE_THRESHOLD || velocityX <= -SWIPE_ESCAPE_VELOCITY)

/**
 * Telegram's swipe on a chat row (`DialogsActivity.SwipeController`, `DialogCell`, DEC-033): the row
 * follows the finger to the left over a band in the action's color with its icon and word 43 dp from
 * the edge; past 45 percent of the width a gray circle grows out of the icon to say "let go now", and
 * the phone ticks once. Let go there, or fling, and [onAction] runs; the row slides back in 200 ms.
 * With [look] null the row does not move.
 */
@Composable
internal fun SwipeActionRow(look: SwipeLook?, onAction: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    // The gesture outlives recompositions: it must run the action for the chat as it is now (pinned or
    // not, muted or not), never the one it was when the gesture started listening (owner's report).
    val action by rememberUpdatedState(onAction)
    var width by remember { mutableIntStateOf(0) }
    val passed by remember { derivedStateOf { width > 0 && -offset.value >= width * SWIPE_THRESHOLD } }
    LaunchedEffect(Unit) {
        snapshotFlow { passed }.collect { if (it) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    }
    val reveal by animateFloatAsState(if (passed) 1f else 0f, tween(SWIPE_REVEAL_MILLIS, easing = TelegramMotion.EaseIn), label = "swipe reveal")
    val iconScale by animateFloatAsState(if (passed) 1.15f else 1f, tween(SWIPE_REVEAL_MILLIS, easing = TelegramMotion.overshoot(2f)), label = "swipe icon")
    val iconOffset = with(LocalDensity.current) { 43.dp.toPx() }
    Box(
        modifier
            .onSizeChanged { width = it.width }
            .clipToBounds()
            .pointerInput(look != null) {
                if (look == null) return@pointerInput
                val velocity = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { velocity.resetTracking() },
                    onDragEnd = {
                        val counts = swipeCounts(offset.value, width.toFloat(), velocity.calculateVelocity().x)
                        if (counts) action()
                        scope.launch { offset.animateTo(0f, tween(SWIPE_BACK_MILLIS, easing = TelegramMotion.Decelerate)) }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f, tween(SWIPE_BACK_MILLIS, easing = TelegramMotion.Decelerate)) } },
                ) { change, dragAmount ->
                    velocity.addPosition(change.uptimeMillis, change.position)
                    change.consume()
                    val next = (offset.value + dragAmount).coerceIn(-width.toFloat(), 0f)
                    scope.launch { offset.snapTo(next) }
                }
            },
    ) {
        if (look != null && offset.value < 0f) {
            Box(Modifier.matchParentSize().background(look.color)) {
                Canvas(Modifier.matchParentSize()) {
                    if (reveal > 0f) {
                        val center = Offset(size.width - iconOffset, size.height / 2f)
                        drawCircle(SwipeReveal, radius = hypot(center.x, size.height) * reveal, center = center)
                    }
                }
                Column(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .width(86.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TelegramIcon(look.icon, tint = Color.White, size = 28.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(look.label), color = Color.White, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
        Box(
            Modifier
                .graphicsLayer { translationX = offset.value }
                .background(MaterialTheme.colorScheme.surface),
        ) { content() }
    }
}

/**
 * The top bar while chats are selected (Telegram's action mode): a cross that ends the selection, the
 * count, which rolls like the unread counter, and what can be done to all of them at once.
 */
@Composable
internal fun ChatSelectionBar(
    count: Int,
    allPinned: Boolean,
    allMuted: Boolean,
    anyUnread: Boolean,
    onClose: () -> Unit,
    onPin: () -> Unit,
    onMute: () -> Unit,
    onDelete: () -> Unit,
    onRead: () -> Unit,
    onClear: () -> Unit,
) {
    var more by remember { mutableStateOf(false) }
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    TelegramTopBar(
        titleContent = {
            TopBarAction(
                onClick = onClose,
                description = stringResource(R.string.selection_close),
                modifier = Modifier.width(54.dp).testTag(SELECTION_CLOSE_TAG),
            ) { CloseIcon(tint) }
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(tween(TelegramMotion.COUNTER_REPLACE_MILLIS / 2)) { if (up) it else -it } + fadeIn()) togetherWith
                        (slideOutVertically(tween(TelegramMotion.COUNTER_REPLACE_MILLIS / 2)) { if (up) -it else it } + fadeOut())
                },
                modifier = Modifier.weight(1f).padding(start = 18.dp),
                label = "selected count",
            ) { shown ->
                Text(
                    shown.toString(),
                    style = MessengerType.TopBarTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag(SELECTION_COUNT_TAG),
                )
            }
        },
        actions = {
            TopBarAction(
                onClick = onPin,
                description = stringResource(if (allPinned) R.string.chat_action_unpin else R.string.chat_action_pin),
                modifier = Modifier.testTag(SELECTION_PIN_TAG),
            ) { TelegramIcon(if (allPinned) R.drawable.tg_msg_unpin else R.drawable.tg_msg_pin, tint = tint) }
            TopBarAction(
                onClick = onMute,
                description = stringResource(if (allMuted) R.string.chat_action_unmute else R.string.chat_action_mute),
                modifier = Modifier.testTag(SELECTION_MUTE_TAG),
            ) { TelegramIcon(if (allMuted) R.drawable.tg_msg_unmute else R.drawable.tg_msg_mute, tint = tint) }
            TopBarAction(
                onClick = onDelete,
                description = stringResource(R.string.chat_action_delete),
                modifier = Modifier.testTag(SELECTION_DELETE_TAG),
            ) { TelegramIcon(R.drawable.tg_msg_delete, tint = tint) }
            Box {
                TopBarAction(
                    onClick = { more = true },
                    description = stringResource(R.string.more_options),
                    modifier = Modifier.testTag(SELECTION_MORE_TAG),
                ) { MoreIcon(tint) }
                val items = if (anyUnread) 2 else 1
                TelegramPopupMenu(expanded = more, onDismissRequest = { more = false }, itemCount = items) {
                    var index = 0
                    if (anyUnread) {
                        Item(
                            index = index++,
                            icon = R.drawable.tg_msg_markread,
                            text = stringResource(R.string.chat_action_read),
                            onClick = {
                                more = false
                                onRead()
                            },
                            modifier = Modifier.testTag(SELECTION_READ_TAG),
                        )
                    }
                    Item(
                        index = index,
                        icon = R.drawable.tg_msg_clear,
                        text = stringResource(R.string.chat_action_clear),
                        onClick = {
                            more = false
                            onClear()
                        },
                        modifier = Modifier.testTag(SELECTION_CLEAR_TAG),
                    )
                }
            }
        },
    )
}

/** A cross, drawn: the selection's way out (Telegram's `BackDrawable` in its closed state). */
@Composable
internal fun CloseIcon(color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val half = 6.5.dp.toPx()
        val stroke = 2.dp.toPx()
        drawLine(color, Offset(center.x - half, center.y - half), Offset(center.x + half, center.y + half), stroke, StrokeCap.Round)
        drawLine(color, Offset(center.x + half, center.y - half), Offset(center.x - half, center.y + half), stroke, StrokeCap.Round)
    }
}

/**
 * The round check on the avatar of a selected chat (Telegram's `CheckBox2`): accent with a white
 * tick and a ring in the row's color, popping in and out.
 */
@Composable
internal fun SelectedCheck(selected: Boolean, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val ring = MaterialTheme.colorScheme.surface
    AnimatedVisibility(
        visible = selected,
        modifier = modifier,
        enter = scaleIn(tween(TelegramMotion.COUNTER_IN_MILLIS, easing = TelegramMotion.overshoot(2f))),
        exit = scaleOut(tween(TelegramMotion.COUNTER_OUT_MILLIS)),
    ) {
        Canvas(
            Modifier
                .size(22.dp)
                .border(2.dp, ring, CircleShape)
                .background(accent, CircleShape)
                .testTag(SELECTED_CHECK_TAG),
        ) {
            val stroke = 2.dp.toPx()
            val a = Offset(size.width * 0.3f, size.height * 0.52f)
            val b = Offset(size.width * 0.45f, size.height * 0.66f)
            val c = Offset(size.width * 0.72f, size.height * 0.36f)
            drawLine(Color.White, a, b, stroke, StrokeCap.Round)
            drawLine(Color.White, b, c, stroke, StrokeCap.Round)
        }
    }
}

/** What the chat list asks before it deletes or clears (DEC-035). */
internal enum class ChatConfirm { DELETE, CLEAR }

/** The question before deleting chats (with their contacts and keys) or clearing their history. */
@Composable
internal fun ChatConfirmDialog(kind: ChatConfirm, count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    TelegramAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    kind == ChatConfirm.DELETE && count == 1 -> stringResource(R.string.delete_chat_title)
                    kind == ChatConfirm.DELETE -> pluralStringResource(R.plurals.delete_chats_title, count, count)
                    count == 1 -> stringResource(R.string.clear_history_title)
                    else -> pluralStringResource(R.plurals.clear_histories_title, count, count)
                },
            )
        },
        text = {
            Text(stringResource(if (kind == ChatConfirm.DELETE) R.string.delete_chats_text else R.string.clear_history_text))
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(CONFIRM_CHAT_ACTION_TAG)) {
                Text(
                    stringResource(if (kind == ChatConfirm.DELETE) R.string.chat_action_delete else R.string.clear_history_confirm),
                    color = SwipeRemove,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

internal const val CHAT_MENU_TAG = "chat_menu"
internal const val CHAT_MENU_MUTE_TAG = "chat_menu_mute"
internal const val CHAT_MENU_CLEAR_TAG = "chat_menu_clear"
internal const val CHAT_MENU_DELETE_TAG = "chat_menu_delete"

/** The tag of a choice in the mute menu; the suffix is the [MuteFor] name in lower case. */
internal const val MUTE_FOR_TAG_PREFIX = "mute_for_"

/** How long a chat's menu can mute it for (Telegram's mute menu). */
internal enum class MuteFor(private val millis: Long, @StringRes val label: Int) {
    HOUR(60 * 60_000L, R.string.mute_1h),
    EIGHT_HOURS(8 * 60 * 60_000L, R.string.mute_8h),
    TWO_DAYS(2 * 24 * 60 * 60_000L, R.string.mute_2d),
    FOREVER(0L, R.string.mute_forever),
    ;

    /** Until when a mute chosen at [nowEpochMillis] lasts. */
    fun until(nowEpochMillis: Long): Long = if (this == FOREVER) ConversationV2Service.MUTED_FOREVER else nowEpochMillis + millis
}

private enum class ChatMenuPage { MAIN, MUTE }

/**
 * The three dots in a chat's top bar (Telegram's chat menu, DEC-035): mute notifications (for an
 * hour, eight hours, two days, or for good) or let them through again, clear the history, delete
 * the chat with its contact. The last two ask first ([onClear], [onDelete] open the question).
 */
@Composable
internal fun ConversationMenu(muted: Boolean, onMute: (Long?) -> Unit, onClear: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf<ChatMenuPage?>(null) }
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Box {
        TopBarAction(
            onClick = { open = ChatMenuPage.MAIN },
            description = stringResource(R.string.more_options),
            modifier = Modifier.testTag(CHAT_MENU_TAG),
        ) { MoreIcon(tint) }
        TelegramPopupMenu(expanded = open == ChatMenuPage.MAIN, onDismissRequest = { open = null }, itemCount = 3) {
            Item(
                index = 0,
                icon = if (muted) R.drawable.tg_msg_unmute else R.drawable.tg_msg_mute,
                text = stringResource(if (muted) R.string.chat_menu_unmute else R.string.chat_menu_mute),
                onClick = {
                    if (muted) {
                        open = null
                        onMute(null)
                    } else {
                        open = ChatMenuPage.MUTE
                    }
                },
                modifier = Modifier.testTag(CHAT_MENU_MUTE_TAG),
            )
            Item(
                index = 1,
                icon = R.drawable.tg_msg_clear,
                text = stringResource(R.string.chat_action_clear),
                onClick = {
                    open = null
                    onClear()
                },
                modifier = Modifier.testTag(CHAT_MENU_CLEAR_TAG),
            )
            Item(
                index = 2,
                icon = R.drawable.tg_msg_delete,
                text = stringResource(R.string.chat_menu_delete),
                onClick = {
                    open = null
                    onDelete()
                },
                modifier = Modifier.testTag(CHAT_MENU_DELETE_TAG),
            )
        }
        TelegramPopupMenu(expanded = open == ChatMenuPage.MUTE, onDismissRequest = { open = null }, itemCount = MuteFor.entries.size) {
            MuteFor.entries.forEachIndexed { index, option ->
                Item(
                    index = index,
                    icon = if (option == MuteFor.FOREVER) R.drawable.tg_msg_mute else R.drawable.tg_msg_mute_period,
                    text = stringResource(option.label),
                    onClick = {
                        open = null
                        onMute(option.until(System.currentTimeMillis()))
                    },
                    modifier = Modifier.testTag(MUTE_FOR_TAG_PREFIX + option.name.lowercase()),
                )
            }
        }
    }
}

/** Muting from the list or a swipe is for good, as in Telegram; the chat's own menu offers shorter times. */
internal const val MUTE_FROM_LIST = ConversationV2Service.MUTED_FOREVER

/** Arranges the parts of the selection rules so tests can check them without a screen. */
internal object ChatSelection {
    /** Pin all unless every selected chat is pinned already; then unpin them all (Telegram's rule). */
    fun pinAll(selectedPinned: List<Boolean>): Boolean = !selectedPinned.all { it }

    /** Mute all unless every selected chat is muted already; then unmute them all. */
    fun muteAll(selectedMuted: List<Boolean>): Boolean = !selectedMuted.all { it }
}

/** The small icons of a chat row: muted after the name, pinned where the counter would be. */
@Composable
internal fun RowStateIcon(@DrawableRes icon: Int, description: String, modifier: Modifier = Modifier) {
    TelegramIcon(
        icon,
        tint = MaterialTheme.messenger.listDate,
        size = 18.dp,
        modifier = modifier.semantics { contentDescription = description },
    )
}
