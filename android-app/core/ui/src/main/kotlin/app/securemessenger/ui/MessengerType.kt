package app.securemessenger.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Text sizes of the messenger look (`docs/PHASE6_PORTING_NOTES.md`, section 2). Telegram counts
 * them in dp; here they are sp, so the system font size setting still works.
 */
object MessengerType {
    val ListName = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium)
    val ListMessage = TextStyle(fontSize = 16.sp)
    val ListTime = TextStyle(fontSize = 13.sp)
    val MessageText = TextStyle(fontSize = 16.sp)
    val MessageTime = TextStyle(fontSize = 12.sp)
    val DateChip = TextStyle(fontSize = 14.sp)
    val HeaderTitle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium)
    val HeaderSubtitle = TextStyle(fontSize = 14.sp)
    val InputText = TextStyle(fontSize = 18.sp)
    val BadgeText = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
}

/** Sizes and radii in dp, from the same notes. */
object MessengerDimens {
    val AvatarList = 52.dp
    val AvatarHeader = 42.dp

    const val BubbleRadius = 17f
    const val BubbleNearRadius = 6f

    /** The strip on the sender's side of every bubble of a series, where the tail of the last one lives. */
    const val BubbleTailStrip = 8f

    /** How far the tail's tip is from the outer edge of the strip. */
    const val BubbleTailTipInset = 2.6f

    /** Where the tail's curve meets the side of the body, measured up from the bottom. */
    const val BubbleTailJoin = 9f

    val BadgeHeight = 20.67.dp
    val BadgeRadius = 11.5.dp
    val BadgeTextPadding = 6.33.dp
}
