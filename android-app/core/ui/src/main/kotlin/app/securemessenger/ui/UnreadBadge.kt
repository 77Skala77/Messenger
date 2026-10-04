package app.securemessenger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Counts above this show as "999+": a longer number would not fit the badge. */
internal const val BADGE_MAX_COUNT = 999

/** The text in the badge; nothing for zero or less. */
fun formatBadgeCount(count: Int): String = when {
    count <= 0 -> ""
    count > BADGE_MAX_COUNT -> "$BADGE_MAX_COUNT+"
    else -> count.toString()
}

/**
 * The count of unread messages in a chat, a local number only (DEC-027; never a read receipt).
 * Round for one digit, longer for more; gray when the chat is muted. Draws nothing for zero.
 */
@Composable
fun UnreadBadge(count: Int, modifier: Modifier = Modifier, muted: Boolean = false) {
    if (count <= 0) return
    val colors = MaterialTheme.messenger
    val description = LocalContext.current.resources.getQuantityString(R.plurals.unread_messages_description, count, count)
    Box(
        modifier = modifier
            .height(MessengerDimens.BadgeHeight)
            .defaultMinSize(minWidth = MessengerDimens.BadgeHeight)
            .background(if (muted) colors.badgeMuted else colors.badge, RoundedCornerShape(MessengerDimens.BadgeRadius))
            .padding(horizontal = MessengerDimens.BadgeTextPadding)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = formatBadgeCount(count),
            style = MessengerType.BadgeText,
            color = colors.badgeText,
            maxLines = 1,
        )
    }
}
