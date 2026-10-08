package app.securemessenger.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The colors of the messenger look that Material's roles do not cover (DEC-027, step 6c; DEC-038).
 *
 * Light is Telegram's classic palette, dark its default night theme "Dark Blue" (`darkblue.attheme`
 * with the default accent of `Theme.java`: blue-to-violet outgoing bubbles, the dark gradient
 * wallpaper), as plain numbers. Where a Telegram value fails WCAG AA for text (4.5:1) or 3:1 for ticks
 * and badge digits, it is darkened or lightened just enough; `MessengerColorsTest` pins every pair.
 */
class MessengerColors(
    val bubbleIncoming: Color,
    /** The outgoing bubble's main color; with [bubbleOutgoingGradient] its first stop. */
    val bubbleOutgoing: Color,
    /**
     * The four colors of the outgoing bubbles' gradient, fixed to the screen (Telegram's
     * `chat_outBubbleGradient`); four times the same color draws one color.
     */
    val bubbleOutgoingGradient: List<Color>,
    val messageText: Color,
    val timeIncoming: Color,
    val timeOutgoing: Color,
    /** Tick and clock in an outgoing bubble. */
    val tickOutgoing: Color,
    val clockOutgoing: Color,
    /** Tick of the last message in the chat list. */
    val listTick: Color,
    val listName: Color,
    val listMessage: Color,
    val listDate: Color,
    val hint: Color,
    val link: Color,
    val badge: Color,
    val badgeMuted: Color,
    val badgeText: Color,
    val fab: Color,
    val fabIcon: Color,
    /** The date pill; semi-transparent, so it is checked over every [wallpaperColors]. */
    val chip: Color,
    val chipText: Color,
    /** Telegram's default wallpaper (DEC-038): the gradient's four colors and the pattern over them. */
    val wallpaperColors: List<Color>,
    val wallpaperPatternColor: Color,
    val wallpaperPatternAlpha: Float,
    /** The wallpaper's average color, where one color has to stand for it. */
    val wallpaperBase: Color,
    /** Gaps between sections (behind cards and lists); the settings' ground. */
    val sectionBackground: Color,
    /** The plain top bar (`actionBarDefault`). */
    val topBar: Color,
    /** Settings in Telegram 12's look (DEC-038): the blue headings and values, switches, radio buttons, red actions. */
    val settingsHeader: Color,
    val settingsValue: Color,
    val switchTrack: Color,
    val switchTrackChecked: Color,
    val radioOff: Color,
    val radioOn: Color,
    val destructive: Color,
    /** A voice message's round play button and its sign (DEC-036; Telegram's loader and media icon). */
    val voiceButtonIncoming: Color,
    val voiceButtonOutgoing: Color,
    val voiceIconIncoming: Color,
    val voiceIconOutgoing: Color,
    /** The loudness bars of a voice message: not yet played, and played. */
    val waveIncoming: Color,
    val waveIncomingPlayed: Color,
    val waveOutgoing: Color,
    val waveOutgoingPlayed: Color,
    /** The blinking dot while recording. */
    val recordDot: Color,
    /** Telegram's glass panes over the chat (DEC-036 block): their tint, icons, light lines and shadow. */
    val glassTint: Color,
    val glassIcon: Color,
    val glassStrokeTop: Color,
    val glassStrokeBottom: Color,
    val glassShadow: Color,
)

private val ClassicOutgoing = Color(0xFFEFFFDE)

internal val LightMessengerColors = MessengerColors(
    bubbleIncoming = Color(0xFFFFFFFF),
    bubbleOutgoing = ClassicOutgoing,
    bubbleOutgoingGradient = List(4) { ClassicOutgoing },
    messageText = Color(0xFF000000),
    timeIncoming = Color(0xFF697480),
    timeOutgoing = Color(0xFF3C7F2E),
    tickOutgoing = Color(0xFF3A9230),
    clockOutgoing = Color(0xFF4A9A3B),
    listTick = Color(0xFF3A9A2B),
    listName = Color(0xFF1A1D21),
    listMessage = Color(0xFF707477),
    listDate = Color(0xFF6F7479),
    // A shade darker than before: on the glass over the green wallpaper the placeholder keeps 4.5:1.
    hint = Color(0xFF686D67),
    link = Color(0xFF2678B6),
    badge = Color(0xFF229AF0),
    badgeMuted = Color(0xFF858B91),
    badgeText = Color(0xFFFFFFFF),
    fab = Color(0xFF229AF0),
    fabIcon = Color(0xFFFFFFFF),
    chip = Color(0x8C000000),
    chipText = Color(0xFFFFFFFF),
    // `Theme.createDefaultWallpaper`: the green gradient with the pattern in black at 34 percent.
    wallpaperColors = listOf(Color(0xFFDBDDBB), Color(0xFF6BA587), Color(0xFFD5D88D), Color(0xFF88B884)),
    wallpaperPatternColor = Color(0xFF000000),
    wallpaperPatternAlpha = 0.34f,
    wallpaperBase = Color(0xFFA9C495),
    sectionBackground = Color(0xFFF1F1F3),
    topBar = Color(0xFFFFFFFF),
    // Telegram's #298ACF headings and #4092CD values are 3.7:1 and 3.4:1 on white; the link blue holds 4.7:1.
    settingsHeader = Color(0xFF2678B6),
    settingsValue = Color(0xFF2678B6),
    switchTrack = Color(0xFFA6ADB3),
    switchTrackChecked = Color(0xFF229AF0),
    radioOff = Color(0xFFB3B3B3),
    radioOn = Color(0xFF229AF0),
    destructive = Color(0xFFCC2929),
    voiceButtonIncoming = Color(0xFF229AF0),
    // Telegram's #78C272 stands out from the green bubble by 2:1 only; darkened to 3.3:1.
    voiceButtonOutgoing = Color(0xFF4A9A3B),
    voiceIconIncoming = Color(0xFFFFFFFF),
    voiceIconOutgoing = Color(0xFFEFFFDE),
    waveIncoming = Color(0xFFDEE5EB),
    waveIncomingPlayed = Color(0xFF229AF0),
    waveOutgoing = Color(0xFFBBE3AC),
    waveOutgoingPlayed = Color(0xFF4A9A3B),
    recordDot = Color(0xFFDA564D),
    // Telegram's glass (`BlurredBackgroundColorProviderThemed`): the white panel at 85 percent, a faint
    // shadow; icons in `glass_defaultIcon`. Its white lines along the top and bottom showed as a rim on
    // the owner's phones where Telegram's are hardly seen (2026-10-07): left out.
    glassTint = Color(0xD8FFFFFF),
    glassIcon = Color(0x991B2227),
    glassStrokeTop = Color(0x00FFFFFF),
    glassStrokeBottom = Color(0x00FFFFFF),
    glassShadow = Color(0x20000000),
)

/**
 * Dark Blue with its default accent (`Theme.java`: outgoing bubbles #258DE5, #4272DF, #8146D7,
 * #9F3EAA; wallpaper #0E141A, #172431, #0F171E, #141C2B with the pattern at 38 percent). The first two
 * bubble colors carry white text at 3.5:1 and 4.5:1 only; they are darkened to #1F76C0 and #406ED7.
 * Telegram's gray texts (#7D8B99, #737F8B, time #8091A0 at 85 percent) become #8895A2 and #838E98, its
 * badge and button blues (#64B5EF, #5FA3DE, #609DD2) #559BCC, #5A9AD2 and #5E9ACE.
 */
internal val DarkMessengerColors = MessengerColors(
    bubbleIncoming = Color(0xFF232E3B),
    bubbleOutgoing = Color(0xFF1F76C0),
    bubbleOutgoingGradient = listOf(Color(0xFF1F76C0), Color(0xFF406ED7), Color(0xFF8146D7), Color(0xFF9F3EAA)),
    messageText = Color(0xFFFAFAFA),
    timeIncoming = Color(0xFF8895A2),
    timeOutgoing = Color(0xFFFFFFFF),
    tickOutgoing = Color(0xFFFFFFFF),
    clockOutgoing = Color(0xFFFFFFFF),
    listTick = Color(0xFF64B5EF),
    listName = Color(0xFFE9EEF4),
    listMessage = Color(0xFF8895A2),
    listDate = Color(0xFF838E98),
    hint = Color(0xFF8895A2),
    link = Color(0xFF64B5EF),
    badge = Color(0xFF559BCC),
    badgeMuted = Color(0xFF3E5263),
    badgeText = Color(0xFFFFFFFF),
    fab = Color(0xFF5A9AD2),
    fabIcon = Color(0xFFFFFFFF),
    chip = Color(0x82354251),
    chipText = Color(0xFFFFFFFF),
    wallpaperColors = listOf(Color(0xFF0E141A), Color(0xFF172431), Color(0xFF0F171E), Color(0xFF141C2B)),
    wallpaperPatternColor = Color(0xFFFFFFFF),
    wallpaperPatternAlpha = 0.38f,
    wallpaperBase = Color(0xFF121B25),
    sectionBackground = Color(0xFF151E27),
    topBar = Color(0xFF242D39),
    settingsHeader = Color(0xFF79C4FC),
    settingsValue = Color(0xFF64B5EF),
    switchTrack = Color(0xFF5D7484),
    switchTrackChecked = Color(0xFF61A3D7),
    radioOff = Color(0x9E7D8B99),
    radioOn = Color(0xFF64B5EF),
    destructive = Color(0xFFEE686F),
    voiceButtonIncoming = Color(0xFF5E9ACE),
    // In the gradient bubble Telegram draws the button white and the sign in the bubble's blue.
    voiceButtonOutgoing = Color(0xFFFFFFFF),
    voiceIconIncoming = Color(0xFFFFFFFF),
    voiceIconOutgoing = Color(0xFF1F76C0),
    waveIncoming = Color(0xFF5D6F80),
    waveIncomingPlayed = Color(0xFF64B5EF),
    waveOutgoing = Color(0x4DFFFFFF),
    waveOutgoingPlayed = Color(0xFFFFFFFF),
    recordDot = Color(0xFFED5D54),
    // The chat panel color of Dark Blue (#212D3B) at 85 percent; no shadow; Telegram's light lines (16 and 8
    // percent white) left out like the light theme's.
    glassTint = Color(0xD9212D3B),
    glassIcon = Color(0xA0FFFFFF),
    glassStrokeTop = Color(0x00FFFFFF),
    glassStrokeBottom = Color(0x00FFFFFF),
    glassShadow = Color(0x00000000),
)

internal val LocalMessengerColors = staticCompositionLocalOf { LightMessengerColors }

/** [a] at 0, [b] at 1, and in between the colors of both, so a theme change fades instead of jumping. */
internal fun lerp(a: MessengerColors, b: MessengerColors, t: Float): MessengerColors {
    if (t <= 0f) return a
    if (t >= 1f) return b
    fun mix(x: Color, y: Color) = androidx.compose.ui.graphics.lerp(x, y, t)
    fun mix(x: List<Color>, y: List<Color>) = x.zip(y) { first, second -> mix(first, second) }
    return MessengerColors(
        bubbleIncoming = mix(a.bubbleIncoming, b.bubbleIncoming),
        bubbleOutgoing = mix(a.bubbleOutgoing, b.bubbleOutgoing),
        bubbleOutgoingGradient = mix(a.bubbleOutgoingGradient, b.bubbleOutgoingGradient),
        messageText = mix(a.messageText, b.messageText),
        timeIncoming = mix(a.timeIncoming, b.timeIncoming),
        timeOutgoing = mix(a.timeOutgoing, b.timeOutgoing),
        tickOutgoing = mix(a.tickOutgoing, b.tickOutgoing),
        clockOutgoing = mix(a.clockOutgoing, b.clockOutgoing),
        listTick = mix(a.listTick, b.listTick),
        listName = mix(a.listName, b.listName),
        listMessage = mix(a.listMessage, b.listMessage),
        listDate = mix(a.listDate, b.listDate),
        hint = mix(a.hint, b.hint),
        link = mix(a.link, b.link),
        badge = mix(a.badge, b.badge),
        badgeMuted = mix(a.badgeMuted, b.badgeMuted),
        badgeText = mix(a.badgeText, b.badgeText),
        fab = mix(a.fab, b.fab),
        fabIcon = mix(a.fabIcon, b.fabIcon),
        chip = mix(a.chip, b.chip),
        chipText = mix(a.chipText, b.chipText),
        wallpaperColors = mix(a.wallpaperColors, b.wallpaperColors),
        wallpaperPatternColor = mix(a.wallpaperPatternColor, b.wallpaperPatternColor),
        wallpaperPatternAlpha = a.wallpaperPatternAlpha + (b.wallpaperPatternAlpha - a.wallpaperPatternAlpha) * t,
        wallpaperBase = mix(a.wallpaperBase, b.wallpaperBase),
        sectionBackground = mix(a.sectionBackground, b.sectionBackground),
        topBar = mix(a.topBar, b.topBar),
        settingsHeader = mix(a.settingsHeader, b.settingsHeader),
        settingsValue = mix(a.settingsValue, b.settingsValue),
        switchTrack = mix(a.switchTrack, b.switchTrack),
        switchTrackChecked = mix(a.switchTrackChecked, b.switchTrackChecked),
        radioOff = mix(a.radioOff, b.radioOff),
        radioOn = mix(a.radioOn, b.radioOn),
        destructive = mix(a.destructive, b.destructive),
        voiceButtonIncoming = mix(a.voiceButtonIncoming, b.voiceButtonIncoming),
        voiceButtonOutgoing = mix(a.voiceButtonOutgoing, b.voiceButtonOutgoing),
        voiceIconIncoming = mix(a.voiceIconIncoming, b.voiceIconIncoming),
        voiceIconOutgoing = mix(a.voiceIconOutgoing, b.voiceIconOutgoing),
        waveIncoming = mix(a.waveIncoming, b.waveIncoming),
        waveIncomingPlayed = mix(a.waveIncomingPlayed, b.waveIncomingPlayed),
        waveOutgoing = mix(a.waveOutgoing, b.waveOutgoing),
        waveOutgoingPlayed = mix(a.waveOutgoingPlayed, b.waveOutgoingPlayed),
        recordDot = mix(a.recordDot, b.recordDot),
        glassTint = mix(a.glassTint, b.glassTint),
        glassIcon = mix(a.glassIcon, b.glassIcon),
        glassStrokeTop = mix(a.glassStrokeTop, b.glassStrokeTop),
        glassStrokeBottom = mix(a.glassStrokeBottom, b.glassStrokeBottom),
        glassShadow = mix(a.glassShadow, b.glassShadow),
    )
}

/** The same for Material's roles. */
internal fun lerp(a: ColorScheme, b: ColorScheme, t: Float): ColorScheme {
    if (t <= 0f) return a
    if (t >= 1f) return b
    fun mix(x: Color, y: Color) = androidx.compose.ui.graphics.lerp(x, y, t)
    return a.copy(
        primary = mix(a.primary, b.primary),
        onPrimary = mix(a.onPrimary, b.onPrimary),
        primaryContainer = mix(a.primaryContainer, b.primaryContainer),
        onPrimaryContainer = mix(a.onPrimaryContainer, b.onPrimaryContainer),
        inversePrimary = mix(a.inversePrimary, b.inversePrimary),
        secondary = mix(a.secondary, b.secondary),
        onSecondary = mix(a.onSecondary, b.onSecondary),
        secondaryContainer = mix(a.secondaryContainer, b.secondaryContainer),
        onSecondaryContainer = mix(a.onSecondaryContainer, b.onSecondaryContainer),
        tertiary = mix(a.tertiary, b.tertiary),
        onTertiary = mix(a.onTertiary, b.onTertiary),
        tertiaryContainer = mix(a.tertiaryContainer, b.tertiaryContainer),
        onTertiaryContainer = mix(a.onTertiaryContainer, b.onTertiaryContainer),
        background = mix(a.background, b.background),
        onBackground = mix(a.onBackground, b.onBackground),
        surface = mix(a.surface, b.surface),
        onSurface = mix(a.onSurface, b.onSurface),
        surfaceVariant = mix(a.surfaceVariant, b.surfaceVariant),
        onSurfaceVariant = mix(a.onSurfaceVariant, b.onSurfaceVariant),
        surfaceTint = mix(a.surfaceTint, b.surfaceTint),
        inverseSurface = mix(a.inverseSurface, b.inverseSurface),
        inverseOnSurface = mix(a.inverseOnSurface, b.inverseOnSurface),
        error = mix(a.error, b.error),
        onError = mix(a.onError, b.onError),
        errorContainer = mix(a.errorContainer, b.errorContainer),
        onErrorContainer = mix(a.onErrorContainer, b.onErrorContainer),
        outline = mix(a.outline, b.outline),
        outlineVariant = mix(a.outlineVariant, b.outlineVariant),
        scrim = mix(a.scrim, b.scrim),
        surfaceBright = mix(a.surfaceBright, b.surfaceBright),
        surfaceDim = mix(a.surfaceDim, b.surfaceDim),
        surfaceContainer = mix(a.surfaceContainer, b.surfaceContainer),
        surfaceContainerHigh = mix(a.surfaceContainerHigh, b.surfaceContainerHigh),
        surfaceContainerHighest = mix(a.surfaceContainerHighest, b.surfaceContainerHighest),
        surfaceContainerLow = mix(a.surfaceContainerLow, b.surfaceContainerLow),
        surfaceContainerLowest = mix(a.surfaceContainerLowest, b.surfaceContainerLowest),
    )
}

/** `MaterialTheme.messenger.bubbleOutgoing`, next to `MaterialTheme.colorScheme`. */
val MaterialTheme.messenger: MessengerColors
    @Composable
    @ReadOnlyComposable
    get() = LocalMessengerColors.current

/** Material roles for the light look; the surface containers are set so no purple tint shows. */
internal val LightColorScheme: ColorScheme = lightColorScheme(
    // Telegram's link blue: buttons and links stand out on white and carry white text (4.7:1). The
    // brighter #229AF0 stays on the badge and the send button, where it holds only digits and icons.
    primary = Color(0xFF2678B6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6ECFC),
    onPrimaryContainer = Color(0xFF0A2540),
    secondary = Color(0xFF4F6B80),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3EEF7),
    onSecondaryContainer = Color(0xFF1A1D21),
    tertiary = Color(0xFF3A9230),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1A1D21),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1D21),
    surfaceVariant = Color(0xFFF1F1F3),
    onSurfaceVariant = Color(0xFF666A6E),
    surfaceTint = Color(0xFF2678B6),
    outline = Color(0xFF79828A),
    // Dividers and the borders of outlined buttons (Material 3 draws both with this color).
    outlineVariant = Color(0xFFC8CCD0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7F9),
    surfaceContainer = Color(0xFFF1F1F3),
    surfaceContainerHigh = Color(0xFFEBEBED),
    surfaceContainerHighest = Color(0xFFE5E5E8),
)

/**
 * Material roles for the dark look (Telegram's Dark Blue): lists and cards #1D2733 on the #151E27
 * ground, dialogs #212D3B, menus #283848.
 */
internal val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF64B5EF),
    onPrimary = Color(0xFF0A2540),
    primaryContainer = Color(0xFF2B4D6E),
    onPrimaryContainer = Color(0xFFD6ECFC),
    secondary = Color(0xFFA9C4D8),
    onSecondary = Color(0xFF0F2433),
    secondaryContainer = Color(0xFF2C3C4E),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF7DD071),
    onTertiary = Color(0xFF0B2A08),
    background = Color(0xFF1D2733),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF1D2733),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF151E27),
    onSurfaceVariant = Color(0xFF8895A2),
    surfaceTint = Color(0xFF64B5EF),
    outline = Color(0xFF6E8091),
    // Telegram's divider is black at 58 percent, which also hides the borders of outlined buttons.
    outlineVariant = Color(0xFF34424F),
    surfaceContainerLowest = Color(0xFF151E27),
    surfaceContainerLow = Color(0xFF1A2430),
    surfaceContainer = Color(0xFF212D3B),
    surfaceContainerHigh = Color(0xFF283848),
    surfaceContainerHighest = Color(0xFF2E3F51),
)
