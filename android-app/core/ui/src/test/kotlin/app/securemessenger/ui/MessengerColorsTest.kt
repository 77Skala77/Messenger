package app.securemessenger.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every text and icon color is checked against the color behind it, in both themes, before it
 * ships (`docs/PHASE6_THEME_NOTES.md`): text 4.5:1 (WCAG AA); ticks, badge digits and the date pill's
 * text where it sits on a saturated or semi-transparent color, 3:1 (non-text and bold-text level).
 * What sits on the outgoing bubble is checked on each color of its gradient, what sits on the
 * wallpaper on each color of the wallpaper's gradient (DEC-038): every pixel of a gradient mixes them.
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
        val outgoing = colors.bubbleOutgoingGradient + colors.bubbleOutgoing
        val wallpaper = colors.wallpaperColors + colors.wallpaperBase
        fun onOutgoing(name: String, foreground: Color, minimum: Double) =
            outgoing.mapIndexed { index, stop -> Check("$name (bubble color $index)", foreground, stop, minimum) }
        fun onWallpaper(name: String, foreground: Color, layer: Color, minimum: Double) =
            wallpaper.mapIndexed { index, ground -> Check("$name (wallpaper color $index)", foreground, layer.compositeOver(ground), minimum) }
        return listOf(
            Check("message text on incoming bubble", colors.messageText, colors.bubbleIncoming, text),
            Check("time in incoming bubble", colors.timeIncoming, colors.bubbleIncoming, text),
            Check("list tick", colors.listTick, scheme.background, graphic),
            Check("list name", colors.listName, scheme.background, text),
            Check("list message", colors.listMessage, scheme.background, text),
            Check("list date", colors.listDate, scheme.background, text),
            Check("hint", colors.hint, scheme.background, text),
            Check("link", colors.link, scheme.background, text),
            Check("badge digits", colors.badgeText, colors.badge, graphic),
            Check("muted badge digits", colors.badgeText, colors.badgeMuted, graphic),
            Check("button icon", colors.fabIcon, colors.fab, graphic),
            Check("on background", scheme.onBackground, scheme.background, text),
            Check("on surface", scheme.onSurface, scheme.surface, text),
            Check("on surface variant", scheme.onSurfaceVariant, scheme.surfaceVariant, text),
            Check("on surface variant over the page", scheme.onSurfaceVariant, scheme.background, text),
            Check("on primary", scheme.onPrimary, scheme.primary, text),
            Check("primary text on the page", scheme.primary, scheme.background, text),
            Check("on primary container", scheme.onPrimaryContainer, scheme.primaryContainer, text),
            Check("on secondary container", scheme.onSecondaryContainer, scheme.secondaryContainer, text),
            Check("input border", scheme.outline, scheme.background, graphic),
            Check("text on the top bar", scheme.onSurface, colors.topBar, text),
            // DEC-036: the play button stands out from its bubble and carries its sign; the played bars too.
            Check("play button on incoming bubble", colors.voiceButtonIncoming, colors.bubbleIncoming, graphic),
            Check("play sign, incoming", colors.voiceIconIncoming, colors.voiceButtonIncoming, graphic),
            Check("play sign, outgoing", colors.voiceIconOutgoing, colors.voiceButtonOutgoing, graphic),
            Check("played bars, incoming", colors.waveIncomingPlayed, colors.bubbleIncoming, graphic),
            Check("recording dot", colors.recordDot, scheme.surface, graphic),
            // DEC-038: the settings in Telegram 12's look, on their sections and on the ground between them.
            Check("settings heading", colors.settingsHeader, scheme.surface, text),
            Check("settings value and action", colors.settingsValue, scheme.surface, text),
            Check("settings red action", colors.destructive, scheme.surface, text),
            Check("settings second line", colors.hint, scheme.surface, text),
            Check("settings note on the ground", colors.hint, colors.sectionBackground, text),
            Check("checked switch", colors.switchTrackChecked, scheme.surface, graphic),
            Check("chosen radio button", colors.radioOn, scheme.surface, graphic),
        ) +
            onOutgoing("message text on outgoing bubble", colors.messageText, text) +
            onOutgoing("time in outgoing bubble", colors.timeOutgoing, text) +
            onOutgoing("tick in outgoing bubble", colors.tickOutgoing, graphic) +
            onOutgoing("clock in outgoing bubble", colors.clockOutgoing, graphic) +
            onOutgoing("play button on outgoing bubble", colors.voiceButtonOutgoing, graphic) +
            onOutgoing("played bars, outgoing", colors.waveOutgoingPlayed, graphic) +
            onWallpaper("date pill text", colors.chipText, colors.chip, text) +
            // The glass panes over the chat: checked over the wallpaper they show through.
            onWallpaper("glass icon", colors.glassIcon, colors.glassTint, graphic) +
            onWallpaper("text on glass", scheme.onSurface, colors.glassTint, text) +
            onWallpaper("hint on glass", colors.hint, colors.glassTint, text) +
            onWallpaper("recording dot on glass", colors.recordDot, colors.glassTint, graphic)
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
        // Classic Telegram: a light green outgoing bubble (owner decision, DEC-027), one color.
        val green = LightMessengerColors.bubbleOutgoing
        assertTrue(green.green > green.red && green.green > green.blue)
        assertTrue(LightMessengerColors.bubbleOutgoingGradient.all { it == green })
    }

    /** DEC-038: Telegram's Dark Blue with its default accent, and its default wallpapers. */
    @Test
    fun `the dark theme is telegram's dark blue and both use telegram's wallpaper`() {
        assertEquals(Color(0xFF151E27), DarkMessengerColors.sectionBackground)
        assertEquals(Color(0xFF1D2733), DarkColorScheme.surface)
        assertEquals(Color(0xFF1D2733), DarkColorScheme.background)
        // Blue at the start of the gradient, violet at its end.
        val stops = DarkMessengerColors.bubbleOutgoingGradient
        assertEquals(4, stops.size)
        assertTrue(stops.first().blue > stops.first().red)
        assertTrue(stops.last().red > stops.last().green)
        assertEquals(DarkMessengerColors.bubbleOutgoing, stops.first())
        assertEquals(
            listOf(Color(0xFFDBDDBB), Color(0xFF6BA587), Color(0xFFD5D88D), Color(0xFF88B884)),
            LightMessengerColors.wallpaperColors,
        )
        assertEquals(
            listOf(Color(0xFF0E141A), Color(0xFF172431), Color(0xFF0F171E), Color(0xFF141C2B)),
            DarkMessengerColors.wallpaperColors,
        )
        assertEquals(0.34f, LightMessengerColors.wallpaperPatternAlpha)
        assertEquals(0.38f, DarkMessengerColors.wallpaperPatternAlpha)
    }

    @Test
    fun `a theme change passes through mixed colors and ends exactly on the target`() {
        val middle = lerp(LightMessengerColors, DarkMessengerColors, 0.5f)
        assertEquals(4, middle.bubbleOutgoingGradient.size)
        assertEquals(4, middle.wallpaperColors.size)
        assertEquals(0.36f, middle.wallpaperPatternAlpha, 0.0001f)
        assertTrue(lerp(LightMessengerColors, DarkMessengerColors, 1f) === DarkMessengerColors)
        assertTrue(lerp(LightMessengerColors, DarkMessengerColors, 0f) === LightMessengerColors)
    }
}
