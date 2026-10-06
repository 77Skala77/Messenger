package app.securemessenger.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

/**
 * The icons taken from Telegram for Android (DEC-033, files listed in `THIRD_PARTY_NOTICES.md`): small
 * white-on-transparent bitmaps, drawn here in any color. Decorative unless [description] is given.
 * [size] [Dp.Unspecified] keeps the icon's own size, as Telegram draws its small list icons.
 */
@Composable
fun TelegramIcon(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    size: Dp = 24.dp,
    description: String? = null,
) {
    Image(
        painter = painterResource(icon),
        contentDescription = description,
        modifier = if (size.isSpecified) modifier.size(size) else modifier,
        colorFilter = ColorFilter.tint(tint),
    )
}
