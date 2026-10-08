package app.securemessenger.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image

/**
 * Telegram 12's settings, written anew in Compose from its numbers (DEC-038): white (on dark: #1D2733)
 * sections with 16 dp corners, 12 dp from the screen's sides, on the gray ground
 * (`RecyclerListView.setSections`); rows of 50 dp, 60 with a second line (`SettingsActivity.SettingCell`);
 * a colored 28 dp tile with a white icon for each main entry; switches and radio buttons drawn the way
 * `Switch` and `RadioButton` draw them; blue headings (`HeaderCell`) and gray notes under a section
 * (`TextInfoPrivacyCell`).
 */
internal object SettingsLook {
    val SectionMargin = 12.dp
    val SectionRadius = 16.dp
    val SectionGap = 16.dp
    val TextStart = 21.dp
    val IconTile = 28.dp
    val IconTileRadius = 10.dp
    val IconStart = 18.dp
    val IconTextGap = 18.dp
    val RowHeight = 50.dp
    val RowHeightTwoLines = 60.dp
    val BarHeight = 56.dp

    val Title = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = 0.sp)
    val Subtitle = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.sp)
    val Header = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp, letterSpacing = 0.sp)
    val Info = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = 0.sp)
}

/** The colors of a main entry's tile, top to bottom (`IconBackgroundColors`). */
internal enum class SettingsTile(val top: Color, val bottom: Color) {
    BLUE(Color(0xFF1CA5ED), Color(0xFF1488E1)),
    BLUE_DEEP(Color(0xFF4F85F6), Color(0xFF3568E8)),
    ORANGE(Color(0xFFF09F1B), Color(0xFFE18A11)),
    ORANGE_DEEP(Color(0xFFF28B31), Color(0xFFE26314)),
    GREEN(Color(0xFF55CA47), Color(0xFF27B434)),
    RED(Color(0xFFF45255), Color(0xFFDF3955)),
    CYAN(Color(0xFF32C0CE), Color(0xFF1D9CC6)),
    PURPLE(Color(0xFFC46EF4), Color(0xFF9F55DF)),
    GRAY(Color(0xFF8699AA), Color(0xFF6E8397)),
}

/**
 * One page of the settings: the gray ground from edge to edge, a column that scrolls under the top
 * bar, and the bar itself, which is clear until something scrolls under it and then takes its color
 * (Telegram 12). With [titleAfter] the title, too, appears only once the column has scrolled that far
 * (the main page, whose own header names the person). Without [onBack] (a tab of the main screen) there
 * is no back arrow; [bottomSpace] keeps the end clear of the tab bar.
 */
@Composable
internal fun SettingsPage(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    titleAfter: Dp? = null,
    scroll: ScrollState = rememberScrollState(),
    bottomSpace: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.messenger
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val titleAfterPx = with(LocalDensity.current) { titleAfter?.toPx() }
    val barShown = scroll.value > 0
    val titleShown = titleAfterPx == null || scroll.value > titleAfterPx
    val barAlpha by animateFloatAsState(if (barShown) 1f else 0f, tween(BAR_FADE_MILLIS), label = "settings bar")
    val titleAlpha by animateFloatAsState(if (titleShown) 1f else 0f, tween(BAR_FADE_MILLIS), label = "settings title")
    Box(modifier.fillMaxSize().background(colors.sectionBackground)) {
        // The column ends above the keyboard, so a field being typed in stays in view; the ground stays.
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scroll)
                .padding(top = if (titleAfter == null) top + SettingsLook.BarHeight + 8.dp else top + 12.dp, bottom = bottom + 16.dp + bottomSpace),
            content = content,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawRect(colors.topBar.copy(alpha = colors.topBar.alpha * barAlpha)) }
                .statusBarsPadding()
                .height(SettingsLook.BarHeight),
        ) {
            val description = stringResource(R.string.back)
            // A page shown as a tab of the main screen has no way back.
            if (onBack != null) {
                Box(
                    Modifier
                        .width(54.dp)
                        .fillMaxHeight()
                        .clickable(role = Role.Button, onClick = onBack)
                        .semantics { contentDescription = description }
                        .testTag(TOP_BAR_BACK_TAG),
                    contentAlignment = Alignment.Center,
                ) { BackArrowIcon(MaterialTheme.colorScheme.onSurface) }
            }
            Text(
                title,
                style = MessengerType.TopBarTitle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = if (onBack != null) 72.dp else 20.dp, end = 16.dp)
                    .graphicsLayer { alpha = titleAlpha },
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(Dp.Hairline)
                    .graphicsLayer { alpha = barAlpha }
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

private const val BAR_FADE_MILLIS = 220

/** One rounded section of rows. */
@Composable
internal fun SettingsSection(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .padding(horizontal = SettingsLook.SectionMargin)
            .fillMaxWidth()
            .clip(RoundedCornerShape(SettingsLook.SectionRadius))
            .background(MaterialTheme.colorScheme.surface),
        content = content,
    )
}

/** The space between two sections. */
@Composable
internal fun SettingsGap() = Spacer(Modifier.height(SettingsLook.SectionGap))

/** A blue heading at the top of a section. */
@Composable
internal fun SettingsHeader(text: String) {
    Text(
        text,
        style = SettingsLook.Header,
        color = MaterialTheme.messenger.settingsHeader,
        modifier = Modifier.padding(start = SettingsLook.TextStart, end = SettingsLook.TextStart, top = 15.dp, bottom = 6.dp),
    )
}

/** A gray note under a section, on the ground. */
@Composable
internal fun SettingsInfo(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = SettingsLook.Info,
        color = MaterialTheme.messenger.hint,
        modifier = modifier.padding(
            start = SettingsLook.SectionMargin + SettingsLook.TextStart,
            end = SettingsLook.SectionMargin + SettingsLook.TextStart,
            top = 10.dp,
            bottom = 14.dp,
        ),
    )
}

/** The thin line between two rows of a section, from where the text starts. */
@Composable
internal fun SettingsDivider(start: Dp = SettingsLook.TextStart) {
    Box(
        Modifier
            .padding(start = start)
            .fillMaxWidth()
            .height(Dp.Hairline)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/**
 * A main entry (`SettingCell`): the colored tile with its white icon, the title, a gray second line,
 * and an optional blue value at the end. Opens a page.
 */
@Composable
internal fun SettingsLinkRow(
    @DrawableRes icon: Int,
    tile: SettingsTile,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
) {
    val colors = MaterialTheme.messenger
    val dark = MaterialTheme.colorScheme.background.luminanceBelowHalf()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (subtitle == null) SettingsLook.RowHeight else SettingsLook.RowHeightTwoLines)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = SettingsLook.IconStart, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconTile(icon, tile, border = dark)
        Column(Modifier.weight(1f).padding(start = SettingsLook.IconTextGap, top = 6.dp, bottom = 6.dp)) {
            Text(title, style = SettingsLook.Title, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, style = SettingsLook.Subtitle, color = colors.hint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (value != null) {
            Text(value, style = SettingsLook.Title, color = colors.settingsValue, maxLines = 1, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

/** The 28 dp tile: a top-to-bottom gradient, radius 10 dp, on dark a 1 dp light rim (`SettingCell.Background`). */
@Composable
private fun SettingsIconTile(@DrawableRes icon: Int, tile: SettingsTile, border: Boolean) {
    Box(
        Modifier
            .size(SettingsLook.IconTile)
            .drawBehind {
                val radius = CornerRadius(SettingsLook.IconTileRadius.toPx())
                drawRoundRect(Brush.verticalGradient(listOf(tile.top, tile.bottom)), cornerRadius = radius)
                if (border) {
                    val stroke = 1.dp.toPx()
                    drawRoundRect(
                        Brush.verticalGradient(0f to Color(0x4DFFFFFF), 0.5f to Color(0x00FFFFFF), 1f to Color(0x1AFFFFFF)),
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = radius,
                        style = Stroke(stroke),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
    }
}

/**
 * A setting with a switch (`TextCheckCell`): the title, an optional gray explanation under it, and
 * Telegram's switch at the end. The whole row switches.
 */
@Composable
internal fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = SettingsLook.RowHeight)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(start = SettingsLook.TextStart, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(top = 13.dp, bottom = if (hint == null) 13.dp else 11.dp, end = 12.dp)) {
            Text(title, style = SettingsLook.Title, color = MaterialTheme.colorScheme.onSurface)
            if (hint != null) {
                Spacer(Modifier.height(4.dp))
                Text(hint, style = SettingsLook.Subtitle, color = MaterialTheme.messenger.hint)
            }
        }
        TelegramSwitch(checked)
    }
}

/**
 * A choice among several (`RadioColorCell`): Telegram's radio button at the start, the text after
 * it. The whole row selects, as a radio button.
 */
@Composable
internal fun SettingsRadioRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = SettingsLook.RowHeight)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(start = 19.dp, end = SettingsLook.TextStart),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TelegramRadio(selected)
        Column(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp)) {
            Text(title, style = SettingsLook.Title, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Text(subtitle, style = SettingsLook.Subtitle, color = MaterialTheme.messenger.hint)
        }
    }
}

/** A row that does something at once (`TextSettingsCell`): blue text, or red for a destructive action. */
@Composable
internal fun SettingsActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    value: String? = null,
) {
    val colors = MaterialTheme.messenger
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = SettingsLook.RowHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = SettingsLook.TextStart, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = SettingsLook.Title,
            color = if (destructive) colors.destructive else colors.settingsValue,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(value, style = SettingsLook.Subtitle, color = colors.hint, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

/** Plain text inside a section (an explanation, a state). */
@Composable
internal fun SettingsText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text,
        style = SettingsLook.Subtitle.copy(fontSize = 14.sp, lineHeight = 19.sp),
        color = color,
        modifier = modifier.padding(horizontal = SettingsLook.TextStart, vertical = 10.dp),
    )
}

/**
 * Telegram's switch (`Switch.onDraw`): a 31 by 14 dp track with round ends, and a thumb of 10 dp radius
 * in the track's color with an 8 dp disc of the section's color in it; it slides 17 dp in 250 ms while
 * the color goes from gray to blue.
 */
@Composable
internal fun TelegramSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.messenger
    val card = MaterialTheme.colorScheme.surface
    val progress by animateFloatAsState(if (checked) 1f else 0f, tween(SWITCH_MILLIS), label = "switch")
    Canvas(modifier.size(width = 37.dp, height = 20.dp)) {
        val track = lerp(colors.switchTrack, colors.switchTrackChecked, progress)
        val width = 31.dp.toPx()
        val height = 14.dp.toPx()
        val left = (size.width - width) / 2f
        drawRoundRect(track, topLeft = Offset(left, (size.height - height) / 2f), size = Size(width, height), cornerRadius = CornerRadius(height / 2f))
        val thumb = Offset(left + 7.dp.toPx() + 17.dp.toPx() * progress, size.height / 2f)
        drawCircle(track, radius = 10.dp.toPx(), center = thumb)
        drawCircle(card, radius = 8.dp.toPx(), center = thumb)
    }
}

private const val SWITCH_MILLIS = 250

/**
 * Telegram's radio button (`RadioButton.onDraw`, 20 dp): a 2 dp ring; when chosen the ring fills
 * inward and then opens to a 5 dp dot, gray turning blue, in 200 ms.
 */
@Composable
internal fun TelegramRadio(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.messenger
    val progress by animateFloatAsState(if (selected) 1f else 0f, tween(RADIO_MILLIS), label = "radio")
    Canvas(modifier.size(22.dp).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val size20 = 20.dp.toPx()
        val circleProgress: Float
        val color: Color
        if (progress <= 0.5f) {
            circleProgress = progress / 0.5f
            color = colors.radioOff
        } else {
            circleProgress = 2f - progress / 0.5f
            color = lerp(colors.radioOff, colors.radioOn, 1f - circleProgress)
        }
        val radius = size20 / 2f - (1f + circleProgress) * density
        drawCircle(color, radius = radius, style = Stroke(2.dp.toPx()))
        val inner = radius - 1.dp.toPx()
        if (progress <= 0.5f) {
            drawCircle(color, radius = inner)
            drawCircle(Color.Black, radius = inner * (1f - circleProgress), blendMode = BlendMode.Clear)
        } else {
            drawCircle(color, radius = size20 / 4f + (inner - size20 / 4f) * circleProgress)
        }
    }
}

private const val RADIO_MILLIS = 200

internal fun Color.luminanceBelowHalf(): Boolean = (0.2126f * red + 0.7152f * green + 0.0722f * blue) < 0.5f
