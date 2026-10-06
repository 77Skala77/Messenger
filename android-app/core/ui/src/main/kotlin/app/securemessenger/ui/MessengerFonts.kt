package app.securemessenger.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * The app's typeface (owner's choice, DEC-033): Source Sans 3, Adobe, SIL Open Font License 1.1,
 * bundled as one variable font (`res/font/source_sans_3.ttf`, licence in `licenses/`). It has
 * Cyrillic and every weight; the four used here are cut from it. Text looks the same on every phone
 * instead of following the phone maker's font.
 */
@OptIn(ExperimentalTextApi::class)
internal val SourceSans3: FontFamily = FontFamily(
    Font(R.font.source_sans_3, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.source_sans_3, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.source_sans_3, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.source_sans_3, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/** Material's type scale with its sizes, in the app's typeface. */
internal fun messengerTypography(): Typography {
    val base = Typography()

    fun TextStyle.sans() = copy(fontFamily = SourceSans3)
    return Typography(
        displayLarge = base.displayLarge.sans(),
        displayMedium = base.displayMedium.sans(),
        displaySmall = base.displaySmall.sans(),
        headlineLarge = base.headlineLarge.sans(),
        headlineMedium = base.headlineMedium.sans(),
        headlineSmall = base.headlineSmall.sans(),
        titleLarge = base.titleLarge.sans(),
        titleMedium = base.titleMedium.sans(),
        titleSmall = base.titleSmall.sans(),
        bodyLarge = base.bodyLarge.sans(),
        bodyMedium = base.bodyMedium.sans(),
        bodySmall = base.bodySmall.sans(),
        labelLarge = base.labelLarge.sans(),
        labelMedium = base.labelMedium.sans(),
        labelSmall = base.labelSmall.sans(),
    )
}
