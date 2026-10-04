package app.securemessenger.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every text and icon color is checked against the color behind it, in both themes, before it
 * ships (`docs/PHASE6_THEME_NOTES.md`): text 4.5:1 (WCAG AA); ticks, badge digits and the date pill's
 * text where it sits on a saturated or semi-transparent color, 3:1 (non-text and bold-text level).
 */
class MessengerColorsTest {
    private class Check(val name: String, val foreground: Color, val background: Color, val minimum: Double)

    private fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color) =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(foreground: Color, background: Color): Double {
        val front = luminance(foreground.compositeOver(background))
        val back = luminance(background)
        return (maxOf(front, back) + 0.05) / (minOf(front, back) + 0.05)
    }

    private fun checks(colors: MessengerColors, scheme: ColorScheme): List<Check> {
        val text = 4.5
        val graphic = 3.0
        val chipOverWallpaper = colors.chip.compositeOver(colors.wallpaperBase)
        return listOf(
            Check("message text on incoming bubble", colors.messageText, colors.bubbleIncoming, text),
            Check("message text on outgoing bubble", colors.messageText, colors.bubbleOutgoing, text),
            Check("time in incoming bubble", colors.timeIncoming, colors.bubbleIncoming, text),
            Check("time in outgoing bubble", colors.timeOutgoing, colors.bubbleOutgoing, text),
            Check("tick in outgoing bubble", colors.tickOutgoing, colors.bubbleOutgoing, graphic),
            Check("clock in outgoing bubble", colors.clockOutgoing, colors.bubbleOutgoing, graphic),
            Check("list tick", colors.listTick, scheme.background, graphic),
            Check("list name", colors.listName, scheme.background, text),
            Check("list message", colors.listMessage, scheme.background, text),
            Check("list date", colors.listDate, scheme.background, text),
            Check("hint", colors.hint, scheme.background, text),
            Check("link", colors.link, scheme.background, text),
            Check("badge digits", colors.badgeText, colors.badge, graphic),
            Check("muted badge digits", colors.badgeText, colors.badgeMuted, graphic),
            Check("button icon", colors.fabIcon, colors.fab, graphic),
            Check("date pill text", colors.chipText, chipOverWallpaper, text),
            Check("on background", scheme.onBackground, scheme.background, text),
            Check("on surface", scheme.onSurface, scheme.surface, text),
            Check("on surface variant", scheme.onSurfaceVariant, scheme.surfaceVariant, text),
            Check("on surface variant over the page", scheme.onSurfaceVariant, scheme.background, text),
            Check("on primary", scheme.onPrimary, scheme.primary, text),
            Check("primary text on the page", scheme.primary, scheme.background, text),
            Check("on primary container", scheme.onPrimaryContainer, scheme.primaryContainer, text),
            Check("on secondary container", scheme.onSecondaryContainer, scheme.secondaryContainer, text),
            Check("input border", scheme.outline, scheme.background, graphic),
        )
    }

    private fun assertAll(theme: String, checks: List<Check>) {
        val failures = checks.filter { contrast(it.foreground, it.background) < it.minimum }
            .joinToString { "${it.name} %.2f < %.1f".format(contrast(it.foreground, it.background), it.minimum) }
        assertTrue("$theme theme: $failures", failures.isEmpty())
    }

    @Test
    fun `the light theme keeps its contrast`() {
        assertAll("light", checks(LightMessengerColors, LightColorScheme))
    }

    @Test
    fun `the dark theme keeps its contrast`() {
        assertAll("dark", checks(DarkMessengerColors, DarkColorScheme))
    }

    @Test
    fun `the two themes really differ and the outgoing bubble is green in the light one`() {
        assertTrue(LightMessengerColors.bubbleOutgoing != DarkMessengerColors.bubbleOutgoing)
        assertTrue(LightColorScheme.background != DarkColorScheme.background)
        // Classic Telegram: a light green outgoing bubble (owner decision, DEC-027).
        val green = LightMessengerColors.bubbleOutgoing
        assertTrue(green.green > green.red && green.green > green.blue)
    }
}
