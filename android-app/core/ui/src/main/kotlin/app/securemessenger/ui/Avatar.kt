package app.securemessenger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

/** Share of the avatar's diameter taken by the letters: 18 on 42 in Telegram's header. */
private const val INITIALS_SIZE_SHARE = 18f / 42f

/**
 * A round avatar with the contact's initials on one of seven gradients. The name is always shown
 * next to it, so the avatar says nothing to a screen reader; its white letters are decoration.
 */
@Composable
fun Avatar(name: String, colorIndex: Int, modifier: Modifier = Modifier, size: Dp = MessengerDimens.AvatarList) {
    val gradient = AvatarGradients[Math.floorMod(colorIndex, AvatarGradients.size)]
    // The letters follow the avatar's size, not the font size setting: they must stay inside the circle.
    val fontSize = with(LocalDensity.current) { (size * INITIALS_SIZE_SHARE).toSp() }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(gradient.top), Color(gradient.bottom))))
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = avatarInitials(name),
            color = Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

/** The avatar of a contact: its color comes from the local contact ID ([avatarColorIndex]). */
@Composable
fun Avatar(name: String, localId: String, modifier: Modifier = Modifier, size: Dp = MessengerDimens.AvatarList) {
    Avatar(name = name, colorIndex = avatarColorIndex(localId), modifier = modifier, size = size)
}
