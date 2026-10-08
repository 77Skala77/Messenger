package app.securemessenger.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
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

/** A message's line while selecting; the suffix is the local message ID. */
internal const val MESSAGE_SELECT_TAG_PREFIX = "message_select_"
internal const val MESSAGES_SELECTION_CLOSE_TAG = "messages_selection_close"
internal const val MESSAGES_SELECTION_COUNT_TAG = "messages_selection_count"
internal const val MESSAGES_SELECTION_REPLY_TAG = "messages_selection_reply"
internal const val MESSAGES_SELECTION_DELETE_TAG = "messages_selection_delete"
internal const val MESSAGE_INPUT_TAG = "v2_message_input"
internal const val MESSAGE_SEND_TAG = "v2_send"
internal const val SCROLL_DOWN_TAG = "scroll_down_button"
internal const val SCROLL_DOWN_COUNT_TAG = "scroll_down_count"
internal const val FLOATING_DATE_TAG = "floating_date"
internal const val MESSAGE_MENU_DELETE_TAG = "message_menu_delete"
internal const val DELETE_MESSAGE_CONFIRM_TAG = "delete_message_confirm"
internal const val DELETE_MESSAGE_FOR_BOTH_TAG = "delete_message_for_both"

/** A bubble is at most the screen width minus this much (no avatars in a 1:1 chat), notes 2.3. */
private const val BUBBLE_SCREEN_MARGIN_DP = 80

/** On a wide screen (tablet, phone on its side) bubbles stop here, so lines stay readable. */
private const val BUBBLE_MAX_DP = 480
private const val BUBBLE_MIN_DP = 160

/** Between the last line of text and the time that shares it. */
private const val META_GAP_DP = 8

/** Telegram's glass header (`ActionBar.setupGlass`): 56 dp high, pills of 46 dp, 6 dp from the edges. */
private val HeaderHeight = 56.dp
private val HeaderPill = 46.dp
private val HeaderInset = 6.dp

/** Telegram's input island (`ChatInputViewsContainer`): radius 22 dp, 7 dp from the sides, 9 dp from the bottom. */
private val InputRadius = 22.dp
private val InputSideInset = 7.dp
private val InputBottomInset = 9.dp

/** What a chat's own menu does (DEC-035); all of it stays on this phone. Empty for screens shown without a pipeline. */
internal class ConversationActions(
    /** null lets notifications through again. */
    val mute: (Long?) -> Unit = {},
    val clearHistory: () -> Unit = {},
    val delete: () -> Unit = {},
)

/**
 * The conversation (step 6e, glass look of Telegram 12): the chat fills the whole screen, under the
 * status bar and the navigation bar, and the header (back, the contact, the menu) and the input bar
 * float over it as glass. The contact's safety number is one tap away on the header. Nothing here
 * sends anything about reading; ticks show the delivery state of our own texts only. The one signal
 * about listening: playing the contact's voice message calls [onVoicePlayed] (DEC-039, owner: like
 * Telegram), which marks it listened and tells the contact once; until then a dot shows on both phones.
 * A text can be answered (DEC-035): swipe its bubble to the left, or press it long; the answer then
 * carries a quote of it. [onSend] gets the text and the inner ID of the text it answers, or null.
 * With the field empty, the microphone records a voice message (DEC-036) for [onSendVoice], which
 * takes the recording over; [loadVoice] gives a voice message's recording for playing, or null, and
 * [playOnce] the contact's one-time recording, once. A long press also offers "Delete" (DEC-037):
 * [onDeleteMessages] gets the message and whether the contact's copy goes too.
 */
@Composable
internal fun V2Conversation(
    contact: V2Contact,
    messages: List<V2StoredMessage>,
    onSend: (text: String, replyToInnerIdHex: String?) -> Unit,
    onBack: () -> Unit,
    onOpenSafety: () -> Unit,
    actions: ConversationActions = ConversationActions(),
    onSendVoice: (recording: VoiceRecording, replyToInnerIdHex: String?) -> Unit = { recording, _ -> recording.wipe() },
    loadVoice: suspend (messageId: String) -> ByteArray? = { null },
    playOnce: suspend (messageId: String) -> ByteArray? = { null },
    onDeleteMessages: (messageIds: List<String>, forBoth: Boolean) -> Unit = { _, _ -> },
    onVoicePlayed: (messageId: String) -> Unit = {},
) {
    var draft by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current
    LaunchedEffect(Unit) { ChatSounds.prepare(context) }
    val zone = remember { ZoneId.systemDefault() }
    // What the list shows: the messages, a moment behind when some go, so that the bubbles of messages
    // the contact deleted for both of us (DEC-037) can still be pictured and fall apart here too.
    var shown by remember(contact.contactId) { mutableStateOf(messages) }
    val entries = remember(shown, zone) { conversationEntries(shown, zone) }
    val blocked = contact.state == V2ContactState.KEY_CHANGED
    // The text being answered; once it is gone (the history was cleared) it can no longer be answered.
    var replyingTo by remember(contact.contactId) { mutableStateOf<V2StoredMessage?>(null) }
    LaunchedEffect(messages) {
        val target = replyingTo ?: return@LaunchedEffect
        if (messages.none { it.messageId == target.messageId }) replyingTo = null
    }
    val inputFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val you = stringResource(R.string.reply_you)
    // A blocked contact (changed key) can be written to by nobody, so nothing can be answered either.
    val startReply: ((V2StoredMessage) -> Unit)? = if (blocked) {
        null
    } else {
        { message ->
            replyingTo = message
            runCatching { inputFocus.requestFocus() }
            keyboard?.show()
        }
    }
    // Voice messages (DEC-036): one player and one recorder for the screen, stopped when it goes.
    val player = remember { VoicePlayer(context) }
    val recorder = remember { VoiceRecordController(scope, view, player) }
    var hint by remember { mutableStateOf<ChatHint?>(null) }
    LaunchedEffect(hint) {
        if (hint != null) {
            delay(VOICE_HINT_MILLIS)
            hint = null
        }
    }
    SideEffect {
        recorder.onHint = { hint = it }
        recorder.onSend = { recording ->
            onSendVoice(recording, replyingTo?.innerMessageIdHex)
            ChatSounds.playSent(context)
            scope.launch { WallpaperMotion.next() }
            replyingTo = null
        }
    }
    VoiceRecordTicker(recorder)
    LaunchedEffect(player.playing) {
        while (player.playing) {
            withInfiniteAnimationFrameNanos { }
            player.tick()
        }
    }
    // Leaving the app stops the microphone and the sound at once; nothing records in the background.
    LifecycleStartEffect(Unit) {
        onStopOrDispose {
            recorder.cancel()
            player.pause()
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    BackHandler(enabled = recorder.phase != RecordPhase.IDLE) { recorder.cancel() }
    // While recording (and listening to or trimming a paused recording) the swipe back is off.
    BlockSwipeBack(recorder.phase != RecordPhase.IDLE)
    LaunchedEffect(blocked) { if (blocked) recorder.cancel() }
    // Deleting a message (DEC-037): its picture falls apart while the message itself goes.
    val dust = remember { MessageDust() }
    val dustBubbles = remember { BubblePictures() }
    // A one-time voice message playing in its own player, over the chat.
    var onceSession by remember { mutableStateOf<OnceSession?>(null) }
    val playVoice: (V2StoredMessage, Float) -> Unit = { message, fraction ->
        val voice = message.voice
        scope.launch {
            // The contact's one-time recording is handed over once and deleted in the same step.
            val oneTime = voice?.once == true && !message.outgoing
            val recording = if (oneTime) playOnce(message.messageId) else loadVoice(message.messageId)
            if (recording == null) {
                hint = ChatHint(R.string.voice_unavailable)
            } else {
                // A one-time recording plays in Telegram's own player over the chat (owner, 2026-10-07);
                // if its bubble is not on screen, the short note says what it is.
                val bounds = if (voice?.once == true) dustBubbles.bounds(message.messageId) else null
                if (bounds != null) {
                    onceSession = OnceSession(message, bounds, incoming = oneTime)
                } else if (voice?.once == true) {
                    hint = ChatHint(if (oneTime) R.string.voice_once_hint_in else R.string.voice_once_hint_out, fire = true)
                }
                player.play(message.messageId, recording, if (oneTime) 0f else fraction) { hint = ChatHint(R.string.voice_unavailable) }
                // The contact's ordinary recording: marked listened, "played" goes once (DEC-039).
                if (!message.outgoing && voice != null && !voice.once && !voice.listened) onVoicePlayed(message.messageId)
            }
        }
    }
    var deleting by remember { mutableStateOf<V2StoredMessage?>(null) }
    val voiceLabel = stringResource(R.string.voice_message)
    val glass = rememberGlassSource()
    // Messages whose dust is already flying (deleted here); the list lets them go without more dust.
    val dusted = remember { HashSet<String>() }
    // Lets [ids] fall apart where they stand, with the snap, and then deletes them (DEC-037).
    val scatterAndDelete: (List<String>, Boolean) -> Unit = { ids, forBoth ->
        if (player.activeId in ids) player.release()
        if (replyingTo?.messageId in ids) replyingTo = null
        ChatSounds.playSnap(context)
        scope.launch {
            ids.forEach { id ->
                dustBubbles.capture(id)?.let { (image, topLeft) -> dust.scatter(image, topLeft - glass.origin, density.density) }
                dusted += id
            }
            onDeleteMessages(ids, forBoth)
        }
    }
    // Messages that go while the chat is open and were not deleted here (the contact deleted them for
    // both of us) fall apart too, as on the phone that deleted them; a whole history at once does not.
    LaunchedEffect(messages) {
        val present = messages.mapTo(HashSet()) { it.messageId }
        val gone = shown.map { it.messageId }.filter { it !in present && !dusted.remove(it) }
        if (gone.isNotEmpty() && gone.size <= REMOTE_DUST_MAX) {
            var fell = false
            gone.forEach { id ->
                dustBubbles.capture(id)?.let { (image, topLeft) ->
                    dust.scatter(image, topLeft - glass.origin, density.density)
                    fell = true
                }
            }
            if (fell) ChatSounds.playSnap(context)
            if (player.activeId in gone) player.release()
        }
        shown = messages
    }
    // Selecting messages (Telegram's action mode, owner 2026-10-07): a long press starts it with that
    // message; then a tap anywhere on a message's line ticks it or takes the tick away; the cross or
    // Back ends it. Selected messages can be deleted together, a single one also answered.
    var selected by remember(contact.contactId) { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(messages) { selected = selected.filter { id -> messages.any { it.messageId == id } } }
    val selecting = selected.isNotEmpty()
    BackHandler(enabled = selecting) { selected = emptyList() }
    var deletingSelected by remember { mutableStateOf(false) }
    val toggleSelected: (V2StoredMessage) -> Unit = { message ->
        selected = if (message.messageId in selected) selected - message.messageId else selected + message.messageId
    }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var bottomBlock by remember { mutableIntStateOf(0) }
    val bottomInset = with(density) { bottomBlock.toDp() }
    Box(Modifier.fillMaxSize()) {
        // The chat, under everything; the glass panes show it through them.
        Box(Modifier.fillMaxSize().glassSource(glass)) {
            ChatWallpaper(Modifier.fillMaxSize()) {
                BubbleGradientBox(Modifier.fillMaxSize()) {
                    MessageList(
                        entries = entries,
                        zone = zone,
                        contactName = contact.localDisplayName,
                        onReply = startReply,
                        onDelete = { deleting = it },
                        player = player,
                        onPlayVoice = playVoice,
                        topPadding = topInset + HeaderHeight,
                        bottomPadding = bottomInset,
                        bubbles = dustBubbles,
                        recording = recorder.phase != RecordPhase.IDLE,
                        selected = selected.toSet(),
                        onSelect = toggleSelected,
                        onStartSelect = { message -> if (message.messageId !in selected) selected = selected + message.messageId },
                    )
                    if (entries.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyConversationNote() }
                    }
                }
            }
            MessageDustLayer(dust, Modifier.fillMaxSize())
        }
        if (selecting) {
            val single = selected.singleOrNull()?.let { id -> messages.firstOrNull { it.messageId == id } }
            GlassSelectionHeader(
                glass = glass,
                count = selected.size,
                onClose = { selected = emptyList() },
                onDelete = { deletingSelected = true },
                onReply = if (single != null && startReply != null) {
                    {
                        selected = emptyList()
                        startReply(single)
                    }
                } else {
                    null
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        } else {
            GlassHeader(glass, contact, onBack, onOpenSafety, actions, Modifier.align(Alignment.TopCenter))
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { bottomBlock = it.height }
                .navigationBarsPadding()
                .imePadding()
                .padding(start = InputSideInset, end = InputSideInset, bottom = InputBottomInset),
        ) {
            // While messages are selected the input bar steps aside (Telegram shows its actions instead);
            // it keeps its room, so the list does not jump.
            val inputAlpha by animateFloatAsState(if (selecting) 0f else 1f, tween(TelegramMotion.DIALOG_FADE_MILLIS), label = "input while selecting")
            Box(Modifier.graphicsLayer { alpha = inputAlpha }) {
                GlassInputBar(
                    glass = glass,
                    draft = draft,
                    onDraftChange = { draft = it },
                    enabled = !blocked,
                    focusRequester = inputFocus,
                    reply = replyingTo?.let {
                        ReplyTarget(if (it.outgoing) you else contact.localDisplayName, if (it.voice != null) voiceLabel else quoteLine(it.text))
                    },
                    onCancelReply = { replyingTo = null },
                    onSend = {
                        onSend(draft, replyingTo?.innerMessageIdHex)
                        ChatSounds.playSent(context)
                        // Telegram turns the wallpaper's colors one step on with each message of ours.
                        scope.launch { WallpaperMotion.next() }
                        draft = ""
                        replyingTo = null
                    },
                    // A blocked contact (changed key) gets no voice messages either.
                    recorder = if (blocked) null else recorder,
                    player = player,
                )
                if (selecting) {
                    Box(Modifier.matchParentSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } })
                }
            }
        }
        // Hints float just above the input bar without moving the messages.
        hint?.let { VoiceHint(it, Modifier.align(Alignment.BottomStart).padding(bottom = bottomInset)) }
        VoiceRecordOverlay(recorder, glass)
        onceSession?.let { session ->
            val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
            val maxBubble = (LocalConfiguration.current.screenWidthDp - BUBBLE_SCREEN_MARGIN_DP).coerceIn(BUBBLE_MIN_DP, BUBBLE_MAX_DP).dp
            OnceVoicePlayer(session, glass, player, onClosed = { onceSession = null }) { pictures ->
                MessageBubble(
                    message = session.message,
                    position = SeriesPosition(nearTop = false, nearBottom = false, tail = true, dateChipBefore = false),
                    time = formatMessageTime(session.message.createdAtEpochMillis, zone, locale),
                    maxWidth = maxBubble,
                    player = player,
                    pictures = pictures,
                )
            }
        }
    }
    if (recorder.confirmTrim) {
        TelegramAlertDialog(
            onDismissRequest = recorder::dismissTrim,
            title = { Text(stringResource(R.string.voice_trim_title)) },
            text = { Text(stringResource(R.string.voice_trim_text)) },
            confirmButton = {
                TextButton(onClick = recorder::confirmTrimAndResume, modifier = Modifier.testTag(VOICE_TRIM_CONFIRM_TAG)) {
                    Text(stringResource(R.string.voice_trim_confirm))
                }
            },
            dismissButton = { TextButton(onClick = recorder::dismissTrim) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (deletingSelected && selected.isNotEmpty()) {
        DeleteMessageDialog(
            contactName = contact.localDisplayName,
            canDeleteForBoth = !blocked,
            count = selected.size,
            onConfirm = { forBoth ->
                deletingSelected = false
                val ids = selected
                selected = emptyList()
                scatterAndDelete(ids, forBoth)
            },
            onDismiss = { deletingSelected = false },
        )
    }
    deleting?.let { target ->
        DeleteMessageDialog(
            contactName = contact.localDisplayName,
            canDeleteForBoth = !blocked,
            onConfirm = { forBoth ->
                deleting = null
                scatterAndDelete(listOf(target.messageId), forBoth)
            },
            onDismiss = { deleting = null },
        )
    }
}

/** How long a hint about recording stays. */
private const val VOICE_HINT_MILLIS = 2_500L

/** More messages than this going at once (a cleared history) just go, without dust. */
private const val REMOTE_DUST_MAX = 30

/**
 * The header while messages are selected (Telegram 12's action mode in glass): a round cross that
 * ends selecting, "N selected" in a pill, and a pill with "Reply" (one message only) and "Delete".
 */
@Composable
private fun GlassSelectionHeader(
    glass: GlassSource,
    count: Int,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onReply: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val closeLabel = stringResource(R.string.selection_close)
    val onSurface = MaterialTheme.colorScheme.onSurface
    Box(modifier.fillMaxWidth().statusBarsPadding().height(HeaderHeight)) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = HeaderInset)
                .size(HeaderPill)
                .glass(glass, HeaderPill / 2)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClose)
                .semantics { contentDescription = closeLabel }
                .testTag(MESSAGES_SELECTION_CLOSE_TAG),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(24.dp)) {
                val stroke = 2.dp.toPx()
                val half = 6.dp.toPx()
                drawLine(onSurface, Offset(center.x - half, center.y - half), Offset(center.x + half, center.y + half), stroke, StrokeCap.Round)
                drawLine(onSurface, Offset(center.x + half, center.y - half), Offset(center.x - half, center.y + half), stroke, StrokeCap.Round)
            }
        }
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = HeaderInset + HeaderPill + HeaderInset)
                .height(HeaderPill)
                .glass(glass, HeaderPill / 2)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.messages_selected, count),
                style = GlassTitleStyle,
                color = onSurface,
                maxLines = 1,
                modifier = Modifier.testTag(MESSAGES_SELECTION_COUNT_TAG),
            )
        }
        Row(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = HeaderInset)
                .height(HeaderPill)
                .glass(glass, HeaderPill / 2)
                .clip(RoundedCornerShape(HeaderPill / 2))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onReply != null) {
                SelectionAction(R.drawable.tg_ic_ab_reply, stringResource(R.string.reply_action), onReply, MESSAGES_SELECTION_REPLY_TAG)
            }
            SelectionAction(R.drawable.tg_msg_delete, stringResource(R.string.message_delete), onDelete, MESSAGES_SELECTION_DELETE_TAG)
        }
    }
}

@Composable
private fun SelectionAction(@DrawableRes icon: Int, label: String, onClick: () -> Unit, tag: String) {
    Box(
        Modifier
            .size(HeaderPill - 8.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) { TelegramIcon(icon, tint = MaterialTheme.colorScheme.onSurface) }
}

/**
 * Telegram 12's glass header (`ActionBar` in glass mode): a round back button, the contact in a pill
 * (avatar, name, and whether the safety number was compared), and a round menu button, all floating
 * over the chat below the status bar.
 */
@Composable
private fun GlassHeader(
    glass: GlassSource,
    contact: V2Contact,
    onBack: () -> Unit,
    onOpenSafety: () -> Unit,
    actions: ConversationActions,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.messenger
    val blocked = contact.state == V2ContactState.KEY_CHANGED
    // An active contact says whether its safety number was compared (DEC-030); the other states say themselves.
    val checked = contact.state == V2ContactState.ACTIVE && contact.safetyNumberChecked
    val subtitle = when {
        checked -> stringResource(R.string.conversation_subtitle_verified)
        contact.state == V2ContactState.ACTIVE -> stringResource(R.string.conversation_subtitle_unverified)
        else -> stateLabel(contact.state)
    }
    val muted = contact.isMuted(System.currentTimeMillis())
    var confirm by remember { mutableStateOf<ChatConfirm?>(null) }
    val backLabel = stringResource(R.string.back)
    Box(modifier.fillMaxWidth().statusBarsPadding().height(HeaderHeight)) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = HeaderInset)
                .size(HeaderPill)
                .glass(glass, HeaderPill / 2)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = backLabel }
                .testTag(TOP_BAR_BACK_TAG),
            contentAlignment = Alignment.Center,
        ) { BackArrowIcon(MaterialTheme.colorScheme.onSurface) }
        Row(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = HeaderInset + HeaderPill + HeaderInset)
                .wrapContentWidth()
                .height(HeaderPill)
                .glass(glass, HeaderPill / 2)
                .clip(RoundedCornerShape(HeaderPill / 2))
                .clickable(onClickLabel = stringResource(R.string.conversation_open_safety), onClick = onOpenSafety)
                .testTag(CONVERSATION_HEADER_TAG)
                .padding(start = 2.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(contact.localDisplayName, contact.contactId, size = MessengerDimens.AvatarHeader)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        contact.localDisplayName,
                        style = GlassTitleStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (muted) {
                        Spacer(Modifier.width(4.dp))
                        TelegramIcon(
                            R.drawable.tg_list_mute,
                            tint = colors.hint,
                            size = 16.dp,
                            description = stringResource(R.string.chat_muted_description),
                        )
                    }
                }
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
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = HeaderInset)
                .size(HeaderPill)
                .glass(glass, HeaderPill / 2),
            contentAlignment = Alignment.Center,
        ) {
            ConversationMenu(
                muted = muted,
                onMute = actions.mute,
                onClear = { confirm = ChatConfirm.CLEAR },
                onDelete = { confirm = ChatConfirm.DELETE },
            )
        }
    }
    confirm?.let { kind ->
        ChatConfirmDialog(
            kind = kind,
            count = 1,
            onConfirm = {
                confirm = null
                if (kind == ChatConfirm.DELETE) actions.delete() else actions.clearHistory()
            },
            onDismiss = { confirm = null },
        )
    }
}

/** The title in the glass header: 17 sp medium (`ActionBar` in glass mode). */
private val GlassTitleStyle = TextStyle(fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, lineHeight = 21.sp, letterSpacing = 0.sp)

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

/**
 * "Delete message" (Telegram's dialog, DEC-037): sure? and, for a contact that can still be reached,
 * "Also delete for <name>". Without the tick only this phone's copy goes.
 */
@Composable
private fun DeleteMessageDialog(
    contactName: String,
    canDeleteForBoth: Boolean,
    onConfirm: (forBoth: Boolean) -> Unit,
    onDismiss: () -> Unit,
    count: Int = 1,
) {
    var forBoth by remember { mutableStateOf(false) }
    TelegramAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (count == 1) stringResource(R.string.delete_message_title) else pluralStringResource(R.plurals.delete_messages_title, count, count))
        },
        text = {
            Column {
                Text(stringResource(if (count == 1) R.string.delete_message_text else R.string.delete_messages_text))
                if (canDeleteForBoth) {
                    Row(
                        Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(role = Role.Checkbox) { forBoth = !forBoth }
                            .testTag(DELETE_MESSAGE_FOR_BOTH_TAG),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = forBoth,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.messenger.fab),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.delete_message_for, contactName))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(forBoth && canDeleteForBoth) }, modifier = Modifier.testTag(DELETE_MESSAGE_CONFIRM_TAG)) {
                Text(stringResource(R.string.message_delete), color = SwipeRemove)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
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
 * and a round button with a count takes the person back to the newest message. A bubble swiped to
 * the left, or pressed long, is answered ([onReply]; null when nothing can be sent); a quote in a
 * bubble jumps to the text it quotes and lights it up for a moment (DEC-035). The list runs under
 * the glass header and input bar: [topPadding] and [bottomPadding] keep its ends clear of them.
 */
@Composable
private fun MessageList(
    entries: List<ConversationEntry>,
    zone: ZoneId,
    contactName: String,
    onReply: ((V2StoredMessage) -> Unit)?,
    onDelete: (V2StoredMessage) -> Unit,
    player: VoicePlayer,
    onPlayVoice: (message: V2StoredMessage, fraction: Float) -> Unit,
    topPadding: Dp,
    bottomPadding: Dp,
    bubbles: BubblePictures,
    recording: Boolean,
    /** The selected messages (Telegram's action mode); empty when nothing is selected. */
    selected: Set<String> = emptySet(),
    /** Ticks a message or takes its tick away; a long press on a message starts selecting with it. */
    onSelect: ((V2StoredMessage) -> Unit)? = null,
    /** A long press anywhere on a message's line: adds it to the selection (never takes it away). */
    onStartSelect: ((V2StoredMessage) -> Unit)? = null,
) {
    val selecting = selected.isNotEmpty()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
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
    // A text that arrives in an open chat makes Telegram's short "in" sound, wherever it sorts in
    // (by its sender's time it may land above our own latest, DEC-034). What loads in the first
    // moments after the screen opened is the history: no sound for it.
    val openedAt = remember { System.nanoTime() }
    var knownIncoming by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(newestFirst) {
        val incoming = newestFirst.mapNotNullTo(HashSet()) { entry ->
            (entry as? ConversationEntry.Bubble)?.takeIf { !it.message.outgoing }?.key
        }
        val settled = System.nanoTime() - openedAt > HISTORY_GRACE_NANOS
        if (settled && incoming.any { it !in knownIncoming }) ChatSounds.playReceived(context)
        knownIncoming = incoming
    }
    // Quotes are looked up in this phone's own copy of the chat (DEC-035).
    val byInnerId = remember(entries) {
        entries.mapNotNull { (it as? ConversationEntry.Bubble)?.message }.associateBy { it.innerMessageIdHex }
    }
    var highlighted by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(highlighted) {
        if (highlighted != null) {
            delay(TelegramMotion.QUOTE_HIGHLIGHT_MILLIS)
            highlighted = null
        }
    }
    val jumpTo: (String) -> Unit = { innerId ->
        val index = newestFirst.indexOfFirst { it is ConversationEntry.Bubble && it.message.innerMessageIdHex == innerId }
        if (index >= 0) {
            highlighted = newestFirst[index].key
            scope.launch { listState.animateScrollToItem(index) }
        }
    }

    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val patterns = rememberDatePatterns()
    val nowMillis = remember { System.currentTimeMillis() }
    val maxBubble = (LocalConfiguration.current.screenWidthDp - BUBBLE_SCREEN_MARGIN_DP).coerceIn(BUBBLE_MIN_DP, BUBBLE_MAX_DP).dp
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
            // Reversed: the bottom padding keeps the newest message above the input bar.
            // No side padding here: each message's line spans the whole width (its long press and its
            // selection tint reach the edges, as in Telegram); the line keeps its bubble 8 dp in.
            contentPadding = PaddingValues(top = topPadding + 6.dp, bottom = bottomPadding + 6.dp),
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
                        is ConversationEntry.Bubble -> {
                            val message = entry.message
                            ReplySwipe(
                                enabled = onReply != null && !selecting,
                                outgoing = message.outgoing,
                                onReply = { onReply?.invoke(message) },
                            ) {
                                MessageBubble(
                                    message = message,
                                    position = entry.position,
                                    time = formatMessageTime(message.createdAtEpochMillis, zone, locale),
                                    maxWidth = maxBubble,
                                    enterProgress = { enter.value },
                                    quote = quoteOf(message.replyToInnerIdHex, byInnerId),
                                    contactName = contactName,
                                    onQuoteClick = { message.replyToInnerIdHex?.let(jumpTo) },
                                    onReply = onReply?.let { reply -> { reply(message) } },
                                    onDelete = { onDelete(message) },
                                    highlighted = highlighted == entry.key,
                                    player = player,
                                    onPlayVoice = { fraction -> onPlayVoice(message, fraction) },
                                    pictures = bubbles,
                                    selecting = selecting,
                                    selected = message.messageId in selected,
                                    onSelect = onSelect?.let { select -> { select(message) } },
                                    onStartSelect = onStartSelect?.let { start -> { start(message) } },
                                )
                            }
                        }
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
                        .padding(top = topPadding + (TelegramMotion.FloatingDateTop - 7.dp).coerceAtLeast(0.dp))
                        .graphicsLayer { alpha = dateAlpha }
                        .testTag(FLOATING_DATE_TAG),
                )
            }
        }
        // While a recording runs the round button would sit between the recording's buttons: it goes.
        ScrollDownButton(
            visible = showDown && !recording,
            count = arrivedBelow,
            onClick = {
                arrivedBelow = 0
                scope.launch { listState.animateScrollToItem(0) }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = bottomPadding + 12.dp),
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

/**
 * One bubble (notes 2.1 to 2.3): its colors and shape from the series, the time and ticks at the
 * bottom end, and above the text the quote of the text it answers, if any (DEC-035). As in Telegram,
 * a tap offers "Reply" ([onReply], null when nothing can be sent) and "Delete" ([onDelete], DEC-037),
 * and a long press starts selecting with this message ([onSelect]); while [selecting], a round tick
 * stands at the start of the line, the contact's bubbles move aside for it, and a tap anywhere on the
 * line ticks the message or takes the tick away. The accessibility menu offers the same. Each bubble
 * keeps its own picture in [pictures], for the dust when it is deleted.
 */
@Composable
private fun MessageBubble(
    message: V2StoredMessage,
    position: SeriesPosition,
    time: String,
    maxWidth: Dp,
    enterProgress: () -> Float = { 1f },
    quote: QuoteInfo? = null,
    contactName: String = "",
    onQuoteClick: () -> Unit = {},
    onReply: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    highlighted: Boolean = false,
    player: VoicePlayer? = null,
    onPlayVoice: (fraction: Float) -> Unit = {},
    pictures: BubblePictures? = null,
    selecting: Boolean = false,
    selected: Boolean = false,
    onSelect: (() -> Unit)? = null,
    onStartSelect: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.messenger
    val outgoing = message.outgoing
    val shape = remember(outgoing, position) { BubbleShape(outgoing, position.tail, position.nearTop, position.nearBottom) }
    val strip = MessengerDimens.BubbleTailStrip.dp
    val highlight by animateFloatAsState(if (highlighted) 1f else 0f, tween(TelegramMotion.LIST_MOVE_MILLIS), label = "highlight")
    val selectMode by animateFloatAsState(if (selecting) 1f else 0f, tween(TelegramMotion.LIST_MOVE_MILLIS, easing = TelegramMotion.Decelerate), label = "select mode")
    val tick by animateFloatAsState(if (selected) 1f else 0f, tween(TelegramMotion.DIALOG_FADE_MILLIS), label = "select tick")
    var menu by remember { mutableStateOf(false) }
    val replyLabel = stringResource(R.string.reply_action)
    val deleteLabel = stringResource(R.string.message_delete)
    val selectLabel = stringResource(R.string.message_select)
    val picture = rememberGraphicsLayer()
    DisposableEffect(message.messageId, pictures) {
        pictures?.register(message.messageId, picture)
        onDispose { pictures?.forget(message.messageId, picture) }
    }
    val hasMenu = onReply != null || onDelete != null
    // The bubble's gesture outlives recompositions: it calls the newest of these.
    val select by rememberUpdatedState(onSelect)
    val startSelect by rememberUpdatedState(onStartSelect)
    // A long press (on the bubble, its bars, or the empty part of its line) only adds the message:
    // two of them answering the same press must not tick it and take the tick away again.
    val startSelecting: (() -> Unit)? = if (onStartSelect != null) ({ startSelect?.invoke() }) else null
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.link.copy(alpha = HIGHLIGHT_ALPHA * highlight))
            .background(colors.link.copy(alpha = SELECTED_ALPHA * tick * selectMode))
            // A long press on the empty part of the line selects the message too, as in Telegram.
            .pointerInput(startSelecting != null, selecting) {
                if (startSelecting != null && !selecting) detectTapGestures(onLongPress = { startSelect?.invoke() })
            },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                // The bubble sits 8 dp in from the edges; the contact's make room for the tick, ours stay at the end.
                .padding(horizontal = LineInset)
                .padding(start = if (outgoing) 0.dp else SelectShift * selectMode)
                .padding(bottom = if (position.tail) 6.dp else 2.dp),
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
                    .onGloballyPositioned { pictures?.place(message.messageId, it.positionInRoot(), it.size) }
                    .drawWithContent {
                        picture.record { this@drawWithContent.drawContent() }
                        drawLayer(picture)
                    }
                    .testTag(MESSAGE_TAG_PREFIX + message.messageId)
                    .semantics(mergeDescendants = true) {
                        customActions = buildList {
                            if (onReply != null) add(CustomAccessibilityAction(replyLabel) { onReply(); true })
                            if (onDelete != null) add(CustomAccessibilityAction(deleteLabel) { onDelete(); true })
                            if (onSelect != null) add(CustomAccessibilityAction(selectLabel) { onSelect(); true })
                        }
                    }
                    .pointerInput(hasMenu, startSelecting != null) {
                        // Telegram: a tap opens the menu, a long press starts selecting.
                        detectTapGestures(
                            onTap = { if (hasMenu) menu = true },
                            onLongPress = { if (startSelecting != null) startSelecting() else if (hasMenu) menu = true },
                        )
                    }
                    .widthIn(max = maxWidth)
                    .bubbleBackground(outgoing, shape)
                    .padding(
                        start = 11.dp + if (outgoing) 0.dp else strip,
                        end = 11.dp + if (outgoing) strip else 0.dp,
                        top = 6.dp,
                        bottom = 5.dp,
                    ),
            ) {
                Column {
                    if (quote != null) {
                        BubbleQuote(quote, contactName, outgoing, onQuoteClick, Modifier.testTag(QUOTE_TAG_PREFIX + message.messageId))
                    }
                    val voice = message.voice
                    if (voice != null && player != null) {
                        VoiceBubbleContent(
                            message = message,
                            voice = voice,
                            player = player,
                            maxWidth = maxWidth,
                            onPlay = onPlayVoice,
                            onLongPress = startSelecting ?: if (hasMenu) ({ menu = true }) else null,
                        ) {
                            MessageMeta(time, outgoing, message.status)
                        }
                    } else {
                        BubbleContent(message.text, MessengerType.MessageText, colors.messageText) {
                            MessageMeta(time, outgoing, message.status)
                        }
                    }
                }
                if (hasMenu) {
                    val items = listOfNotNull(
                        onReply?.let { Triple(R.drawable.tg_ic_ab_reply, replyLabel, it) to MESSAGE_MENU_REPLY_TAG },
                        onDelete?.let { Triple(R.drawable.tg_msg_delete, deleteLabel, it) to MESSAGE_MENU_DELETE_TAG },
                    )
                    TelegramPopupMenu(expanded = menu, onDismissRequest = { menu = false }, itemCount = items.size) {
                        items.forEachIndexed { index, (item, tag) ->
                            val (icon, text, action) = item
                            Item(
                                index = index,
                                icon = icon,
                                text = text,
                                onClick = {
                                    menu = false
                                    action()
                                },
                                modifier = Modifier.testTag(tag),
                            )
                        }
                    }
                }
            }
        }
        // The tick at the start of the line, and the whole line as the place to tap while selecting.
        if (selectMode > 0f) {
            SelectTick(tick, selectMode, Modifier.align(Alignment.CenterStart).padding(start = SelectTickInset))
        }
        if (selecting && onSelect != null) {
            val label = selectLabel
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) { detectTapGestures(onTap = { select?.invoke() }) }
                    .semantics {
                        this.selected = selected
                        role = Role.Checkbox
                        onClick(label) {
                            select?.invoke()
                            true
                        }
                    }
                    .testTag(MESSAGE_SELECT_TAG_PREFIX + message.messageId),
            )
        }
    }
}

/**
 * Telegram's tick of a selected message (`CheckBoxBase` in the chat): a 22 dp ring, filled in green
 * with a white check when selected; it grows in with the selection.
 */
@Composable
private fun SelectTick(checked: Float, appear: Float, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .size(SelectTickSize)
            .graphicsLayer {
                alpha = appear
                scaleX = 0.5f + 0.5f * appear
                scaleY = 0.5f + 0.5f * appear
            },
    ) {
        val stroke = 1.67.dp.toPx()
        val radius = size.minDimension / 2f
        drawCircle(Color.Black.copy(alpha = 0.12f), radius = radius)
        drawCircle(Color.White, radius = radius - stroke / 2f, style = Stroke(stroke))
        if (checked > 0f) {
            drawCircle(SelectTickColor, radius = radius * checked)
            val unit = size.minDimension / 22f
            val path = Path().apply {
                moveTo(center.x - 5f * unit, center.y)
                lineTo(center.x - 1.5f * unit, center.y + 3.5f * unit)
                lineTo(center.x + 5.5f * unit, center.y - 3.5f * unit)
            }
            drawPath(path, Color.White.copy(alpha = checked), style = Stroke(2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** A selected line is tinted like Telegram's `chat_selectedBackground`. */
private const val SELECTED_ALPHA = 0.16f
private val SelectTickSize = 22.dp
private val SelectTickInset = 14.dp

/** A bubble's distance from the screen's edges within its full-width line. */
private val LineInset = 8.dp

/** How far the contact's bubbles move aside for the tick. */
private val SelectShift = 38.dp
private val SelectTickColor = Color(0xFF4CB65A)

/** How strongly a message the person jumped to is lit up at first (Telegram's highlight). */
private const val HIGHLIGHT_ALPHA = 0.18f

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
 * The input bar as Telegram 12's glass island (notes 2.10, `ChatInputViewsContainer`): a pill with
 * a 22 dp radius floating over the chat, the reply being written above the field inside it, the
 * field (18 sp, growing to a few lines), and at its end a blue circle: send when there is something
 * to send, else the microphone (DEC-036). While a voice message is recorded, the recording panel
 * takes the field's place. A blocked contact (changed key) can be written to by nobody: the field is
 * off and there is no microphone ([recorder] null).
 */
@Composable
private fun GlassInputBar(
    glass: GlassSource,
    draft: String,
    onDraftChange: (String) -> Unit,
    enabled: Boolean,
    reply: ReplyTarget?,
    onCancelReply: () -> Unit,
    onSend: () -> Unit,
    focusRequester: FocusRequester,
    recorder: VoiceRecordController?,
    player: VoicePlayer,
) {
    val colors = MaterialTheme.messenger
    val canSend = enabled && draft.isNotBlank()
    val recording = recorder != null && recorder.phase != RecordPhase.IDLE
    Column(Modifier.fillMaxWidth().glass(glass, InputRadius)) {
        ReplyPanel(target = reply, onCancel = onCancelReply, background = Color.Transparent)
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 0.dp), verticalAlignment = Alignment.Bottom) {
            if (recording) {
                VoiceRecordPanel(checkNotNull(recorder), player, Modifier.weight(1f))
            } else {
                // After a recording the field comes back beside the bin, as in Telegram.
                FieldAfterRecording(recorder) { fieldModifier ->
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        enabled = enabled,
                        textStyle = MessengerType.InputText.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(colors.fab),
                        maxLines = INPUT_MAX_LINES,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = fieldModifier.focusRequester(focusRequester).testTag(MESSAGE_INPUT_TAG),
                        decorationBox = { field ->
                            Box(Modifier.heightIn(min = 44.dp).padding(horizontal = 10.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                                if (draft.isEmpty()) Text(stringResource(R.string.v2_message_hint), style = MessengerType.InputText, color = colors.hint)
                                field()
                            }
                        },
                    )
                }
            }
            // Send and microphone share the end of the bar and swap like Telegram's (scale and fade).
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = canSend && !recording,
                    enter = fadeIn(tween(TelegramMotion.SEND_BUTTON_MILLIS, easing = TelegramMotion.AccelerateDecelerate)) +
                        scaleIn(tween(TelegramMotion.SEND_BUTTON_MILLIS, easing = TelegramMotion.AccelerateDecelerate), initialScale = 0.1f),
                    exit = fadeOut(tween(TelegramMotion.SEND_BUTTON_MILLIS, easing = TelegramMotion.AccelerateDecelerate)) +
                        scaleOut(tween(TelegramMotion.SEND_BUTTON_MILLIS, easing = TelegramMotion.AccelerateDecelerate), targetScale = 0.1f),
                ) {
                    SendButton(onSend)
                }
                if (recorder != null) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !canSend || recording,
                        enter = fadeIn(tween(TelegramMotion.SEND_BUTTON_MILLIS, easing = TelegramMotion.AccelerateDecelerate)) +
                            scaleIn(tween(TelegramMotion.SEND_BUTTON_MILLIS, easing = TelegramMotion.AccelerateDecelerate), initialScale = 0.1f),
                        exit = fadeOut(tween(TelegramMotion.SEND_BUTTON_MILLIS, easing = TelegramMotion.AccelerateDecelerate)) +
                            scaleOut(tween(TelegramMotion.SEND_BUTTON_MILLIS, easing = TelegramMotion.AccelerateDecelerate), targetScale = 0.1f),
                    ) {
                        VoiceRecordButton(recorder)
                    }
                }
            }
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
