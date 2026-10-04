package app.securemessenger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.securemessenger.signalstore.V2MessageStatus
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

internal const val THEME_PREVIEW_TAG = "theme_preview"
internal const val PREVIEW_BADGE_TAG = "preview_badge"
internal const val PREVIEW_BUBBLE_TAG = "preview_bubble"

private class PreviewMessage(val outgoing: Boolean, val time: ZonedDateTime, val text: Int, val status: V2MessageStatus)

/**
 * A debug screen that shows every piece of the messenger look in the current theme, so the colors
 * and shapes can be judged on a phone before the real screens use them (step 6c). Reached from the
 * debug tools in settings; release builds have no way to open it.
 */
@Composable
internal fun ThemePreviewScreen(onBack: () -> Unit) {
    val colors = MaterialTheme.messenger
    Scaffold(topBar = { NavigationTopBar(stringResource(R.string.theme_preview_title), onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).testTag(THEME_PREVIEW_TAG),
        ) {
            PreviewSection(R.string.theme_preview_avatars) {
                val names = listOf(
                    R.string.preview_name_1, R.string.preview_name_2, R.string.preview_name_3, R.string.preview_name_4,
                    R.string.preview_name_5, R.string.preview_name_6, R.string.preview_name_7,
                ).map { stringResource(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    names.forEachIndexed { index, name -> Avatar(name = name, colorIndex = index, size = 40.dp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(name = names[0], colorIndex = 0, size = MessengerDimens.AvatarList)
                    Avatar(name = names[1], colorIndex = 1, size = MessengerDimens.AvatarHeader)
                    Text(names[6], style = MessengerType.ListName, color = colors.listName)
                }
            }
            PreviewSection(R.string.theme_preview_badges) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    UnreadBadge(1, Modifier.testTag("${PREVIEW_BADGE_TAG}_1"))
                    UnreadBadge(24, Modifier.testTag("${PREVIEW_BADGE_TAG}_24"))
                    UnreadBadge(1200, Modifier.testTag("${PREVIEW_BADGE_TAG}_1200"))
                    UnreadBadge(5, Modifier.testTag("${PREVIEW_BADGE_TAG}_muted"), muted = true)
                }
            }
            PreviewSection(R.string.theme_preview_ticks) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.background(colors.bubbleOutgoing).padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MessageTicks(TickKind.CLOCK, colors.clockOutgoing)
                        MessageTicks(TickKind.SENT, colors.tickOutgoing)
                        MessageTicks(TickKind.DELIVERED, colors.tickOutgoing)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        MessageTicks(TickKind.SENT, colors.listTick)
                        MessageTicks(TickKind.DELIVERED, colors.listTick)
                    }
                }
            }
            PreviewSection(R.string.theme_preview_conversation, padded = false) { PreviewConversation() }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PreviewSection(title: Int, padded: Boolean = true, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = if (padded) 16.dp else 0.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { content() }
    }
}

/** A short conversation over the wallpaper: two days, two directions, series and a pause. */
@Composable
private fun PreviewConversation() {
    val zone = remember { ZoneId.systemDefault() }
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val patterns = rememberDatePatterns()
    val messages = remember(zone) {
        fun at(day: Int, hour: Int, minute: Int) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone)
        listOf(
            PreviewMessage(false, at(2, 21, 40), R.string.preview_message_1, V2MessageStatus.RECEIVED),
            PreviewMessage(false, at(2, 21, 41), R.string.preview_message_2, V2MessageStatus.RECEIVED),
            PreviewMessage(true, at(2, 21, 43), R.string.preview_message_3, V2MessageStatus.DELIVERED),
            PreviewMessage(true, at(4, 9, 12), R.string.preview_message_4, V2MessageStatus.DELIVERED),
            PreviewMessage(true, at(4, 9, 13), R.string.preview_message_5, V2MessageStatus.SENT),
            PreviewMessage(true, at(4, 9, 30), R.string.preview_message_6, V2MessageStatus.PENDING),
        )
    }
    val positions = seriesPositions(messages.map { SeriesInput(it.outgoing, it.time.toInstant().toEpochMilli()) }, zone)
    val now = ZonedDateTime.of(2026, 10, 4, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
    ChatWallpaper(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            messages.forEachIndexed { index, message ->
                val position = positions[index]
                val millis = message.time.toInstant().toEpochMilli()
                if (position.dateChipBefore) DateChip(formatDateChip(millis, now, zone, locale, patterns))
                PreviewBubble(message, position, formatMessageTime(millis, zone, locale), Modifier.testTag("${PREVIEW_BUBBLE_TAG}_$index"))
                Spacer(Modifier.height(if (position.tail) 6.dp else 2.dp))
            }
        }
    }
}

/** The bubble with its time and ticks below the text; the real layout comes with the conversation screen. */
@Composable
private fun PreviewBubble(message: PreviewMessage, position: SeriesPosition, time: String, modifier: Modifier) {
    val colors = MaterialTheme.messenger
    val shape = BubbleShape(message.outgoing, position.tail, position.nearTop, position.nearBottom)
    val strip = MessengerDimens.BubbleTailStrip.dp
    Row(modifier.fillMaxWidth(), horizontalArrangement = if (message.outgoing) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .background(if (message.outgoing) colors.bubbleOutgoing else colors.bubbleIncoming, shape)
                .padding(
                    start = 11.dp + if (message.outgoing) 0.dp else strip,
                    end = 11.dp + if (message.outgoing) strip else 0.dp,
                    top = 6.dp,
                    bottom = 5.dp,
                ),
        ) {
            Text(stringResource(message.text), style = MessengerType.MessageText, color = colors.messageText)
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    time,
                    style = MessengerType.MessageTime,
                    color = if (message.outgoing) colors.timeOutgoing else colors.timeIncoming,
                )
                tickKindFor(message.status)?.let { kind ->
                    MessageTicks(kind, if (kind == TickKind.CLOCK) colors.clockOutgoing else colors.tickOutgoing)
                }
            }
        }
    }
}
