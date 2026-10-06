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
 * The colors of the messenger look that Material's roles do not cover (DEC-027, step 6c).
 *
 * Light is Telegram's classic palette, dark its Night one (`docs/PHASE6_THEME_NOTES.md`, 2a), as
 * plain numbers. Where a Telegram value fails WCAG AA for text (4.5:1) or 3:1 for ticks and badge
 * digits, it is darkened or lightened just enough; `MessengerColorsTest` pins every pair.
 */
class MessengerColors(
    val bubbleIncoming: Color,
    val bubbleOutgoing: Color,
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
    /** The date pill; semi-transparent, so it is checked over [wallpaperBase]. */
    val chip: Color,
    val chipText: Color,
    val wallpaperBase: Color,
    val wallpaperPattern: Color,
    /** Gaps between sections (behind cards and lists). */
    val sectionBackground: Color,
)

internal val LightMessengerColors = MessengerColors(
    bubbleIncoming = Color(0xFFFFFFFF),
    bubbleOutgoing = Color(0xFFEFFFDE),
    messageText = Color(0xFF000000),
    timeIncoming = Color(0xFF697480),
    timeOutgoing = Color(0xFF3C7F2E),
    tickOutgoing = Color(0xFF3A9230),
    clockOutgoing = Color(0xFF4A9A3B),
    listTick = Color(0xFF3A9A2B),
    listName = Color(0xFF1A1D21),
    listMessage = Color(0xFF707477),
    listDate = Color(0xFF6F7479),
    hint = Color(0xFF70756F),
    link = Color(0xFF2678B6),
    badge = Color(0xFF229AF0),
    badgeMuted = Color(0xFF858B91),
    badgeText = Color(0xFFFFFFFF),
    fab = Color(0xFF229AF0),
    fabIcon = Color(0xFFFFFFFF),
    chip = Color(0x8C000000),
    chipText = Color(0xFFFFFFFF),
    wallpaperBase = Color(0xFFDDE9CC),
    wallpaperPattern = Color(0xFFC3D3B0),
    sectionBackground = Color(0xFFF1F1F3),
)

internal val DarkMessengerColors = MessengerColors(
    bubbleIncoming = Color(0xFF1F2123),
    bubbleOutgoing = Color(0xFF366CAF),
    messageText = Color(0xFFFAFAFA),
    timeIncoming = Color(0xFF8A8C8E),
    timeOutgoing = Color(0xFFEAF4FC),
    tickOutgoing = Color(0xFFC9F0FF),
    clockOutgoing = Color(0xFF99C9EF),
    listTick = Color(0xFF46ADF3),
    listName = Color(0xFFF1F1F1),
    listMessage = Color(0xFF828282),
    listDate = Color(0xFF8A8A8A),
    hint = Color(0xFF8A8A8A),
    link = Color(0xFF64B5EF),
    badge = Color(0xFF2A8AD9),
    badgeMuted = Color(0xFF454545),
    badgeText = Color(0xFFFFFFFF),
    fab = Color(0xFF2A8AD9),
    fabIcon = Color(0xFFFFFFFF),
    chip = Color(0x96373737),
    chipText = Color(0xFFFFFFFF),
    wallpaperBase = Color(0xFF0F0F10),
    wallpaperPattern = Color(0xFF1B1D1F),
    sectionBackground = Color(0xFF000000),
)

internal val LocalMessengerColors = staticCompositionLocalOf { LightMessengerColors }

/** [a] at 0, [b] at 1, and in between the colors of both, so a theme change fades instead of jumping. */
internal fun lerp(a: MessengerColors, b: MessengerColors, t: Float): MessengerColors {
    if (t <= 0f) return a
    if (t >= 1f) return b
    fun mix(x: Color, y: Color) = androidx.compose.ui.graphics.lerp(x, y, t)
    return MessengerColors(
        bubbleIncoming = mix(a.bubbleIncoming, b.bubbleIncoming),
        bubbleOutgoing = mix(a.bubbleOutgoing, b.bubbleOutgoing),
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
        wallpaperBase = mix(a.wallpaperBase, b.wallpaperBase),
        wallpaperPattern = mix(a.wallpaperPattern, b.wallpaperPattern),
        sectionBackground = mix(a.sectionBackground, b.sectionBackground),
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

/** Material roles for the dark look (Telegram's Night values). */
internal val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF64B5EF),
    onPrimary = Color(0xFF0A2540),
    primaryContainer = Color(0xFF1B4F7A),
    onPrimaryContainer = Color(0xFFD6ECFC),
    secondary = Color(0xFFA9C4D8),
    onSecondary = Color(0xFF0F2433),
    secondaryContainer = Color(0xFF2B3A46),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF7DD071),
    onTertiary = Color(0xFF0B2A08),
    background = Color(0xFF181819),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF181819),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF232326),
    onSurfaceVariant = Color(0xFF8A8A8A),
    surfaceTint = Color(0xFF64B5EF),
    outline = Color(0xFF8A9096),
    // Telegram's Night divider is pure black, which also hid the borders of outlined buttons.
    outlineVariant = Color(0xFF3F4043),
    surfaceContainerLowest = Color(0xFF0F0F10),
    surfaceContainerLow = Color(0xFF1B1B1C),
    surfaceContainer = Color(0xFF202022),
    surfaceContainerHigh = Color(0xFF262628),
    surfaceContainerHighest = Color(0xFF2C2C2F),
)
