package app.securemessenger.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.securemessenger.signalstore.V2ChatSummary
import app.securemessenger.signalstore.V2ContactState
import java.time.ZoneId
import java.util.Locale

internal const val CHAT_LIST_TAG = "chat_list"
internal const val CHAT_LIST_EMPTY_TAG = "chat_list_empty"
internal const val CHAT_LIST_FAB_TAG = "chat_list_fab"

/** The tag of a chat row; the suffix is the local contact ID, which never leaves the phone. */
internal const val CHAT_ROW_TAG_PREFIX = "chat_row_"
internal const val MENU_MY_INVITATION_TAG = "v2_my_invitation"
internal const val MENU_ADD_CONTACT_TAG = "v2_add_contact"

/** Notes 2.6: a two-line row is 70 dp high, the text starts 72 dp from the edge (11 + 52 + 9). */
internal val ChatRowMinHeight = 70.dp
private val ChatRowAvatarStart = 11.dp
private val ChatRowAvatarGap = 9.dp
private val ChatRowEnd = 15.dp
private val ChatRowTextStart = ChatRowAvatarStart + MessengerDimens.AvatarList + ChatRowAvatarGap

/**
 * One chat in the list (notes 2.6): avatar, the contact's name and the time of the newest text,
 * under them that text (or the state) and the count of unseen texts. A tick before the time says
 * what became of our own newest text; it is never "read". A muted chat has a speaker after its
 * name and a gray counter; a pinned one a pin where the counter would be (DEC-035). A long press
 * selects it ([onLongClick]); a selected chat has a check on its avatar and a shaded background.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatListRow(
    chat: V2ChatSummary,
    nowMillis: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
) {
    val colors = MaterialTheme.messenger
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val patterns = rememberDatePatterns()
    val time = chat.lastText?.let { formatChatListTime(it.createdAtEpochMillis, nowMillis, ZoneId.systemDefault(), locale, patterns) }
    val tick = chatTick(chat)
    val muted = chat.contact.isMuted(nowMillis)
    val shade by animateFloatAsState(if (selected) 1f else 0f, tween(TelegramMotion.DIALOG_FADE_MILLIS), label = "selected row")
    val selectionShade = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f * shade)
    Column(
        modifier
            .fillMaxWidth()
            .testTag(CHAT_ROW_TAG_PREFIX + chat.contact.contactId)
            .background(selectionShade)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = ChatRowMinHeight).padding(start = ChatRowAvatarStart),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Avatar(chat.contact.localDisplayName, chat.contact.contactId, size = MessengerDimens.AvatarList)
                SelectedCheck(selected, Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp))
            }
            Column(Modifier.weight(1f).padding(start = ChatRowAvatarGap, end = ChatRowEnd, top = 8.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = chat.contact.localDisplayName,
                        style = MessengerType.ListName,
                        color = colors.listName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (muted) {
                        Spacer(Modifier.width(4.dp))
                        RowStateIcon(R.drawable.tg_list_mute, stringResource(R.string.chat_muted_description))
                    }
                    Spacer(Modifier.weight(1f))
                    if (tick != null) {
                        MessageTicks(tick, if (tick == TickKind.CLOCK) colors.listDate else colors.listTick)
                        Spacer(Modifier.width(4.dp))
                    }
                    if (time != null) Text(time, style = MessengerType.ListTime, color = colors.listDate, maxLines = 1)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChatPreviewText(chat, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    if (chat.unreadCount == 0 && chat.contact.pinned) {
                        RowStateIcon(R.drawable.tg_list_pin, stringResource(R.string.chat_pinned_description))
                    }
                    UnreadCounter(chat.unreadCount, muted)
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = ChatRowTextStart),
            thickness = Dp.Hairline,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun ChatPreviewText(chat: V2ChatSummary, modifier: Modifier) {
    val colors = MaterialTheme.messenger
    val preview = chatPreview(chat)
    val you = stringResource(R.string.chat_preview_you)
    val voiceText = stringResource(R.string.voice_message)
    val onceText = stringResource(R.string.voice_once)
    val text = when (preview) {
        // Our own newest text starts with "You:" in the accent color, like Telegram's list.
        is ChatPreview.LastText -> buildAnnotatedString {
            if (preview.outgoing) {
                withStyle(SpanStyle(color = colors.link)) { append(you) }
                append(" ")
            }
            append(preview.text)
        }
        // A voice message is named in the accent color, like Telegram's media in the list (DEC-036).
        is ChatPreview.Voice -> buildAnnotatedString {
            if (preview.outgoing) {
                withStyle(SpanStyle(color = colors.link)) { append(you) }
                append(" ")
            }
            withStyle(SpanStyle(color = colors.link)) { append(if (preview.once) onceText else voiceText) }
        }
        is ChatPreview.ContactState -> AnnotatedString(stateLabel(preview.state))
        ChatPreview.NoMessages -> AnnotatedString(stringResource(R.string.chat_preview_no_messages))
    }
    val color = when {
        preview is ChatPreview.ContactState && preview.state == V2ContactState.KEY_CHANGED -> MaterialTheme.colorScheme.error
        preview is ChatPreview.NoMessages -> colors.hint
        else -> colors.listMessage
    }
    Text(text, style = MessengerType.ListMessage, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** The badge pops in, rolls its digits and shrinks away like Telegram's counter ([AnimatedUnreadBadge], DEC-033); gray when muted. */
@Composable
private fun UnreadCounter(count: Int, muted: Boolean) {
    AnimatedUnreadBadge(count, muted = muted)
}

/** The list when nothing is in it yet: says what to do, in the round button's words. */
@Composable
internal fun EmptyChatList(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.messenger
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp).testTag(CHAT_LIST_EMPTY_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.chat_list_empty_title),
            style = MessengerType.HeaderTitle,
            color = colors.listName,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            stringResource(R.string.chat_list_empty_hint),
            style = MessengerType.ListMessage,
            color = colors.hint,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The round 56 dp button at the bottom end (notes 2.11). It opens a small menu with the two ways
 * to start a chat: show my invitation, or add a contact by theirs. Like Telegram's, it slips away
 * (fading, shrinking to 70 percent, 80 dp down, 280 ms) while the list is scrolled down and comes
 * back when it is scrolled up.
 */
@Composable
internal fun NewChatFab(onInvite: () -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier, visible: Boolean = true) {
    val colors = MaterialTheme.messenger
    var open by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        if (visible) 1f else 0f,
        tween(TelegramMotion.SIDE_BUTTON_MILLIS, easing = TelegramMotion.Decelerate),
        label = "new chat button",
    )
    val slidePx = with(LocalDensity.current) { TelegramMotion.SideButtonSlide.toPx() }
    if (progress <= 0f) return
    Box(
        modifier.graphicsLayer {
            alpha = progress
            val scale = TelegramMotion.SIDE_BUTTON_MIN_SCALE + (1f - TelegramMotion.SIDE_BUTTON_MIN_SCALE) * progress
            scaleX = scale
            scaleY = scale
            translationY = slidePx * (1f - progress)
        },
    ) {
        FloatingActionButton(
            onClick = { open = true },
            shape = CircleShape,
            containerColor = colors.fab,
            contentColor = colors.fabIcon,
            modifier = Modifier.testTag(CHAT_LIST_FAB_TAG),
        ) {
            PlusIcon(colors.fabIcon, stringResource(R.string.chat_list_new_chat))
        }
        TelegramPopupMenu(expanded = open, onDismissRequest = { open = false }, itemCount = 2, fromBottom = true) {
            Item(
                index = 0,
                text = stringResource(R.string.v2_my_invitation),
                onClick = {
                    open = false
                    onInvite()
                },
                modifier = Modifier.testTag(MENU_MY_INVITATION_TAG),
                icon = R.drawable.tg_msg_qrcode,
            )
            Item(
                index = 1,
                text = stringResource(R.string.v2_add_contact),
                onClick = {
                    open = false
                    onAdd()
                },
                modifier = Modifier.testTag(MENU_ADD_CONTACT_TAG),
                icon = R.drawable.tg_msg_contacts,
            )
        }
    }
}

/** A plus sign, drawn: the icon set of Material would add thousands of icons for this one shape. */
@Composable
private fun PlusIcon(color: Color, description: String) {
    Canvas(Modifier.size(24.dp).semantics { contentDescription = description }) {
        val half = 8.dp.toPx()
        val width = 2.dp.toPx()
        drawLine(color, Offset(center.x - half, center.y), Offset(center.x + half, center.y), width, StrokeCap.Round)
        drawLine(color, Offset(center.x, center.y - half), Offset(center.x, center.y + half), width, StrokeCap.Round)
    }
}
