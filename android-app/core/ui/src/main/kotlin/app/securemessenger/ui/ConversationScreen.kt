package app.securemessenger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.securemessenger.signalstore.V2Contact
import app.securemessenger.signalstore.V2ContactState
import app.securemessenger.signalstore.V2MessageStatus
import app.securemessenger.signalstore.V2StoredMessage
import java.time.ZoneId
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val CONVERSATION_LIST_TAG = "conversation_list"
internal const val CONVERSATION_HEADER_TAG = "conversation_header"

/** The tag of a message bubble; the suffix is the local message ID. */
internal const val MESSAGE_TAG_PREFIX = "message_"
internal const val MESSAGE_INPUT_TAG = "v2_message_input"
internal const val MESSAGE_SEND_TAG = "v2_send"
internal const val SCROLL_DOWN_TAG = "scroll_down_button"
internal const val SCROLL_DOWN_COUNT_TAG = "scroll_down_count"
internal const val FLOATING_DATE_TAG = "floating_date"

/** A bubble is at most the screen width minus this much (no avatars in a 1:1 chat), notes 2.3. */
private const val BUBBLE_SCREEN_MARGIN_DP = 80

/** On a wide screen (tablet, phone on its side) bubbles stop here, so lines stay readable. */
private const val BUBBLE_MAX_DP = 480
private const val BUBBLE_MIN_DP = 160

/** Between the last line of text and the time that shares it. */
private const val META_GAP_DP = 8

/**
 * The conversation (step 6e): a header with the contact, the messages as bubbles over the wallpaper
 * with date pills, and the input bar. The contact's safety number is one tap away on the header.
 * Nothing here sends anything about reading; ticks show the delivery state of our own texts only.
 */
@Composable
internal fun V2Conversation(
    contact: V2Contact,
    messages: List<V2StoredMessage>,
    onSend: (String) -> Unit,
    onBack: () -> Unit,
    onOpenSafety: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val zone = remember { ZoneId.systemDefault() }
    val entries = remember(messages, zone) { conversationEntries(messages, zone) }
    val blocked = contact.state == V2ContactState.KEY_CHANGED
    Scaffold(topBar = { ConversationHeader(contact, onBack, onOpenSafety) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ChatWallpaper(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.fillMaxSize()) {
                    MessageList(entries, zone)
                    if (entries.isEmpty()) EmptyConversationNote(Modifier.align(Alignment.Center))
                }
            }
            MessageInputBar(
                draft = draft,
                onDraftChange = { draft = it },
                enabled = !blocked,
                onSend = {
                    onSend(draft)
                    draft = ""
                },
            )
        }
    }
}

@Composable
private fun ConversationHeader(contact: V2Contact, onBack: () -> Unit, onOpenSafety: () -> Unit) {
    val colors = MaterialTheme.messenger
    val blocked = contact.state == V2ContactState.KEY_CHANGED
    // An active contact says whether its safety number was compared (DEC-030); the other states say themselves.
    val checked = contact.state == V2ContactState.ACTIVE && contact.safetyNumberChecked
    val subtitle = when {
        checked -> stringResource(R.string.conversation_subtitle_verified)
        contact.state == V2ContactState.ACTIVE -> stringResource(R.string.conversation_subtitle_unverified)
        else -> stateLabel(contact.state)
    }
    TelegramTopBar(
        onBack = onBack,
        titleContent = {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag(CONVERSATION_HEADER_TAG)
                    .clickable(onClickLabel = stringResource(R.string.conversation_open_safety), onClick = onOpenSafety),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(contact.localDisplayName, contact.contactId, size = MessengerDimens.AvatarHeader)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(contact.localDisplayName, style = MessengerType.HeaderTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        subtitle,
                        style = MessengerType.HeaderSubtitle,
                        color = when {
                            blocked -> MaterialTheme.colorScheme.error
                            checked -> colors.link
                            else -> colors.hint
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    )
}

/** A chat with no messages yet says so on a pill over the wallpaper, like Telegram's empty chat. */
@Composable
private fun EmptyConversationNote(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.messenger
    Text(
        stringResource(R.string.conversation_empty),
        style = MessengerType.DateChip,
        color = colors.chipText,
        textAlign = TextAlign.Center,
        modifier = modifier
            .padding(horizontal = 32.dp)
            .background(colors.chip, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/** Messages that appear in the first moments after the screen opened are the history, not news: no entrance. */
private const val HISTORY_GRACE_NANOS = 800_000_000L

/** The scroll-down button waits until the list is scrolled this far into the newest message. */
private const val SCROLL_DOWN_THRESHOLD_DP = 48

/**
 * Newest message at the bottom, where the list also starts; a new one scrolls into view if the
 * person is near the end. The motion is Telegram's (DEC-033): new messages grow from 90 percent while
 * fading in and the rest slide to make room (250 ms), a date pill floats at the top while scrolling,
 * and a round button with a count takes the person back to the newest message.
 */
@Composable
private fun MessageList(entries: List<ConversationEntry>, zone: ZoneId) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val newestFirst = remember(entries) { entries.asReversed() }
    val newest = newestFirst.firstOrNull()
    val newestMessage = newestFirst.firstNotNullOfOrNull { it as? ConversationEntry.Bubble }?.message
    val thresholdPx = with(density) { SCROLL_DOWN_THRESHOLD_DP.dp.roundToPx() }
    val showDown by remember(thresholdPx) {
        derivedStateOf { scrollDownButtonVisible(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, thresholdPx) }
    }
    // Texts that came in while the person was reading higher up (Telegram's `newUnreadMessageCount`).
    var arrivedBelow by remember { mutableIntStateOf(0) }
    var firstKey by remember { mutableStateOf(true) }
    LaunchedEffect(newest?.key) {
        if (firstKey) {
            firstKey = false
            return@LaunchedEffect
        }
        val ours = newestMessage?.outgoing == true
        if (ours || listState.firstVisibleItemIndex <= 2) {
            listState.animateScrollToItem(0)
        } else if (newestMessage != null) {
            arrivedBelow++
        }
    }
    LaunchedEffect(showDown) { if (!showDown) arrivedBelow = 0 }

    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val patterns = rememberDatePatterns()
    val nowMillis = remember { System.currentTimeMillis() }
    val maxBubble = (LocalConfiguration.current.screenWidthDp - BUBBLE_SCREEN_MARGIN_DP).coerceIn(BUBBLE_MIN_DP, BUBBLE_MAX_DP).dp
    val openedAt = remember { System.nanoTime() }
    val seen = remember { mutableSetOf<String>() }

    // The floating date: the day of the oldest message in view, shown while scrolling.
    val topMillis by remember(newestFirst) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf null
            when (val entry = newestFirst.getOrNull(last.index)) {
                is ConversationEntry.Day -> entry.timeMillis
                is ConversationEntry.Bubble -> entry.message.createdAtEpochMillis
                null -> null
            }
        }
    }
    var dateShown by remember { mutableStateOf(false) }
    var firstHide by remember { mutableStateOf(true) }
    val scrolling = listState.isScrollInProgress
    LaunchedEffect(scrolling) {
        if (scrolling) {
            dateShown = true
        } else if (dateShown) {
            delay(if (firstHide) TelegramMotion.FLOATING_DATE_FIRST_HIDE_MILLIS else TelegramMotion.FLOATING_DATE_HIDE_MILLIS)
            firstHide = false
            dateShown = false
        }
    }
    val dateAlpha by animateFloatAsState(
        if (dateShown) 1f else 0f,
        tween(TelegramMotion.FLOATING_DATE_FADE_MILLIS),
        label = "floating date",
    )

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag(CONVERSATION_LIST_TAG),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        ) {
            items(newestFirst, key = { it.key }) { entry ->
                // A message that arrives later grows in; the history that was there at the start does not.
                val enter = remember(entry.key) {
                    val isNews = entry is ConversationEntry.Bubble && entry.key !in seen && System.nanoTime() - openedAt > HISTORY_GRACE_NANOS
                    Animatable(if (isNews) 0f else 1f)
                }
                LaunchedEffect(entry.key) {
                    seen += entry.key
                    if (enter.value < 1f) {
                        enter.animateTo(1f, tween(TelegramMotion.LIST_MOVE_MILLIS, easing = TelegramMotion.AccelerateDecelerate))
                    }
                }
                Box(
                    Modifier.animateItem(
                        fadeInSpec = tween(TelegramMotion.LIST_MOVE_MILLIS, easing = TelegramMotion.AccelerateDecelerate),
                        placementSpec = tween(TelegramMotion.LIST_MOVE_MILLIS, easing = TelegramMotion.ListMove),
                        fadeOutSpec = null,
                    ),
                ) {
                    when (entry) {
                        is ConversationEntry.Day -> DateChip(formatDateChip(entry.timeMillis, nowMillis, zone, locale, patterns))
                        is ConversationEntry.Bubble -> MessageBubble(
                            message = entry.message,
                            position = entry.position,
                            time = formatMessageTime(entry.message.createdAtEpochMillis, zone, locale),
                            maxWidth = maxBubble,
                            enterProgress = { enter.value },
                        )
                    }
                }
            }
        }
        topMillis?.let { millis ->
            if (dateAlpha > 0f) {
                DateChip(
                    text = formatDateChip(millis, nowMillis, zone, locale, patterns),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = (TelegramMotion.FloatingDateTop - 7.dp).coerceAtLeast(0.dp))
                        .graphicsLayer { alpha = dateAlpha }
                        .testTag(FLOATING_DATE_TAG),
                )
            }
        }
        ScrollDownButton(
            visible = showDown,
            count = arrivedBelow,
            onClick = {
                arrivedBelow = 0
                scope.launch { listState.animateScrollToItem(0) }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 12.dp),
        )
    }
}

/**
 * The round button that takes the person to the newest message (`ChatActivitySideControlsButtonsLayout`):
 * it fades in from 70 percent size, rising 80 dp, in 280 ms, and a counter of the texts that arrived
 * meanwhile sits above it.
 */
@Composable
private fun ScrollDownButton(visible: Boolean, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(
        if (visible) 1f else 0f,
        tween(TelegramMotion.SIDE_BUTTON_MILLIS, easing = TelegramMotion.Decelerate),
        label = "scroll down",
    )
    if (progress <= 0f) return
    val colors = MaterialTheme.messenger
    val description = stringResource(R.string.scroll_down_description)
    val slidePx = with(LocalDensity.current) { TelegramMotion.SideButtonSlide.toPx() }
    Box(
        modifier
            .graphicsLayer {
                alpha = progress
                val scale = TelegramMotion.SIDE_BUTTON_MIN_SCALE + (1f - TelegramMotion.SIDE_BUTTON_MIN_SCALE) * progress
                scaleX = scale
                scaleY = scale
                translationY = slidePx * (1f - progress)
            },
    ) {
        Box(
            Modifier
                .size(TelegramMotion.SideButtonSize)
                .shadow(3.dp, CircleShape)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
                .testTag(SCROLL_DOWN_TAG),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(24.dp)) {
                val stroke = 2.dp.toPx()
                val half = 6.dp.toPx()
                val tip = Offset(center.x, center.y + half / 2)
                drawLine(colors.hint, Offset(center.x - half, center.y - half / 2), tip, stroke, StrokeCap.Round)
                drawLine(colors.hint, Offset(center.x + half, center.y - half / 2), tip, stroke, StrokeCap.Round)
            }
        }
        AnimatedUnreadBadge(
            count,
            Modifier.align(Alignment.TopCenter).offset(y = -(MessengerDimens.BadgeHeight / 2)).testTag(SCROLL_DOWN_COUNT_TAG),
        )
    }
}

/** One bubble (notes 2.1 to 2.3): its colors and shape from the series, the time and ticks at the bottom end. */
@Composable
private fun MessageBubble(
    message: V2StoredMessage,
    position: SeriesPosition,
    time: String,
    maxWidth: Dp,
    enterProgress: () -> Float = { 1f },
) {
    val colors = MaterialTheme.messenger
    val outgoing = message.outgoing
    val shape = remember(outgoing, position) { BubbleShape(outgoing, position.tail, position.nearTop, position.nearBottom) }
    val strip = MessengerDimens.BubbleTailStrip.dp
    Row(
        Modifier.fillMaxWidth().padding(bottom = if (position.tail) 6.dp else 2.dp),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    // A new message grows from 90 percent around its own middle.
                    val scale = TelegramMotion.LIST_ENTER_SCALE + (1f - TelegramMotion.LIST_ENTER_SCALE) * enterProgress()
                    scaleX = scale
                    scaleY = scale
                }
                .testTag(MESSAGE_TAG_PREFIX + message.messageId)
                .semantics(mergeDescendants = true) {}
                .widthIn(max = maxWidth)
                .background(if (outgoing) colors.bubbleOutgoing else colors.bubbleIncoming, shape)
                .padding(
                    start = 11.dp + if (outgoing) 0.dp else strip,
                    end = 11.dp + if (outgoing) strip else 0.dp,
                    top = 6.dp,
                    bottom = 5.dp,
                ),
        ) {
            BubbleContent(message.text, MessengerType.MessageText, colors.messageText) {
                MessageMeta(time, outgoing, message.status)
            }
        }
    }
}

@Composable
private fun MessageMeta(time: String, outgoing: Boolean, status: V2MessageStatus) {
    val colors = MaterialTheme.messenger
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            time,
            style = MessengerType.MessageTime,
            color = if (outgoing) colors.timeOutgoing else colors.timeIncoming,
            maxLines = 1,
        )
        tickKindFor(status)?.let { kind ->
            MessageTicks(kind, if (kind == TickKind.CLOCK) colors.clockOutgoing else colors.tickOutgoing)
        }
    }
}

/**
 * The text and the time block of a bubble, laid out by [planBubble]: the time shares the last line
 * when it fits and goes under the text when it does not. The text is measured once here to learn its
 * longest and last line; the text child then gets exactly that width, so it wraps the same way.
 */
@Composable
private fun BubbleContent(text: String, style: TextStyle, color: Color, meta: @Composable () -> Unit) {
    val measurer = rememberTextMeasurer()
    // The same merged style for measuring and for drawing, or the two would disagree about line breaks.
    val resolved = LocalTextStyle.current.merge(style)
    Layout(content = {
        Text(text, style = resolved, color = color)
        meta()
    }) { measurables, constraints ->
        val metaPlaceable = measurables[1].measure(Constraints())
        val measured = measurer.measure(text, resolved, constraints = Constraints(maxWidth = constraints.maxWidth))
        var textWidth = 0
        for (line in 0 until measured.lineCount) textWidth = maxOf(textWidth, ceil(measured.getLineRight(line)).toInt())
        val lastLineWidth = if (measured.lineCount == 0) 0 else ceil(measured.getLineRight(measured.lineCount - 1)).toInt()
        val textPlaceable = measurables[0].measure(Constraints(maxWidth = minOf(textWidth, constraints.maxWidth)))
        val plan = planBubble(
            textWidth = textPlaceable.width,
            textHeight = textPlaceable.height,
            lastLineWidth = lastLineWidth,
            metaWidth = metaPlaceable.width,
            metaHeight = metaPlaceable.height,
            maxWidth = constraints.maxWidth,
            gap = META_GAP_DP.dp.roundToPx(),
        )
        layout(plan.width, plan.height) {
            textPlaceable.placeRelative(0, 0)
            metaPlaceable.placeRelative(plan.metaX, plan.metaY)
        }
    }
}

/**
 * The input bar (notes 2.10): at least 44 dp high, 18 sp text, growing to a few lines; the round
 * send button appears at the end only when there is something to send. A blocked contact (changed
 * key) can be written to by nobody: the field is off.
 */
@Composable
private fun MessageInputBar(draft: String, onDraftChange: (String) -> Unit, enabled: Boolean, onSend: () -> Unit) {
    val colors = MaterialTheme.messenger
    val canSend = enabled && draft.isNotBlank()
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        BasicTextField(
            value = draft,
            onValueChange = onDraftChange,
            enabled = enabled,
            textStyle = MessengerType.InputText.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(colors.fab),
            maxLines = INPUT_MAX_LINES,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.weight(1f).testTag(MESSAGE_INPUT_TAG),
            decorationBox = { field ->
                Box(Modifier.heightIn(min = 44.dp).padding(horizontal = 4.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                    if (draft.isEmpty()) Text(stringResource(R.string.v2_message_hint), style = MessengerType.InputText, color = colors.hint)
                    field()
                }
            },
        )
        AnimatedVisibility(
            visible = canSend,
            enter = fadeIn(tween(TelegramMotion.SEND_BUTTON_MILLIS, easing = TelegramMotion.AccelerateDecelerate)) +
                scaleIn(tween(TelegramMotion.SEND_BUTTON_MILLIS, easing = TelegramMotion.AccelerateDecelerate), initialScale = 0.1f),
            exit = fadeOut(tween(TelegramMotion.SEND_BUTTON_MILLIS, easing = TelegramMotion.AccelerateDecelerate)) +
                scaleOut(tween(TelegramMotion.SEND_BUTTON_MILLIS, easing = TelegramMotion.AccelerateDecelerate), targetScale = 0.1f),
        ) {
            SendButton(onSend)
        }
    }
}

private const val INPUT_MAX_LINES = 6

/** A round button with an arrow, drawn: no icon set is needed for it. */
@Composable
private fun SendButton(onClick: () -> Unit) {
    val colors = MaterialTheme.messenger
    val description = stringResource(R.string.v2_send)
    Box(
        Modifier
            .size(48.dp)
            .testTag(MESSAGE_SEND_TAG)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(36.dp)) {
            drawCircle(colors.fab)
            val stroke = 2.dp.toPx()
            val shaft = 7.dp.toPx()
            val head = 5.dp.toPx()
            val tip = Offset(center.x, center.y - shaft)
            drawLine(colors.fabIcon, Offset(center.x, center.y + shaft), tip, stroke, StrokeCap.Round)
            drawLine(colors.fabIcon, Offset(center.x - head, center.y - shaft + head), tip, stroke, StrokeCap.Round)
            drawLine(colors.fabIcon, Offset(center.x + head, center.y - shaft + head), tip, stroke, StrokeCap.Round)
        }
    }
}
