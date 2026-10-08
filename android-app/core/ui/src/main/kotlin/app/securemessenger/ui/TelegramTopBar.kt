package app.securemessenger.ui

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal const val TOP_BAR_BACK_TAG = "top_bar_back"

/** The back button is this wide (`ActionBar.java`, 54 dp) and the bar this high (56 dp, 48 dp in landscape). */
private val BackButtonWidth = 54.dp
private val ActionWidth = 48.dp
private val TitleStartAfterBack = 18.dp

/**
 * Telegram's top bar (`ActionBar`, DEC-033): 56 dp high, a 54 dp back button with the arrow of
 * `BackDrawable`, the title in 20 sp medium starting 72 dp from the edge (18 dp without a back
 * button), and 48 dp wide actions at the end. A hairline under it sets it off from the list.
 */
@Composable
fun TelegramTopBar(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    title: String? = null,
    titleContent: (@Composable RowScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val height = if (landscape) 48.dp else 56.dp
    val scheme = MaterialTheme.colorScheme
    // The app draws under the status bar (edge to edge): the bar's color goes under it, its content below it.
    Column(modifier.fillMaxWidth().background(MaterialTheme.messenger.topBar).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                val description = stringResource(R.string.back)
                Box(
                    Modifier
                        .width(BackButtonWidth)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = false, radius = 24.dp),
                            role = Role.Button,
                            onClick = onBack,
                        )
                        .semantics { contentDescription = description }
                        .testTag(TOP_BAR_BACK_TAG),
                    contentAlignment = Alignment.Center,
                ) { BackArrowIcon(scheme.onSurfaceVariant) }
            }
            if (titleContent != null) {
                titleContent()
            } else if (title != null) {
                Text(
                    title,
                    style = if (landscape) MessengerType.TopBarTitleLandscape else MessengerType.TopBarTitle,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = TitleStartAfterBack),
                )
            }
            if (titleContent == null && title == null) Box(Modifier.weight(1f))
            actions()
        }
        HorizontalDivider(thickness = androidx.compose.ui.unit.Dp.Hairline, color = scheme.outlineVariant)
    }
}

/** An action at the end of the top bar: 48 dp wide, with a round ripple around its icon. */
@Composable
fun TopBarAction(onClick: () -> Unit, description: String, modifier: Modifier = Modifier, icon: @Composable () -> Unit) {
    Box(
        modifier
            .width(ActionWidth)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp),
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { icon() }
}

/**
 * The back arrow of `BackDrawable.java` at rest: a 2 dp round stroke, shaft from -6.75 dp to 7 dp,
 * the head's wings from the tip at -7.25 dp to 7 dp above and below, the whole thing 0.66 dp left
 * of the middle of a 24 dp square.
 */
@Composable
fun BackArrowIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val stroke = 2.dp.toPx()
        translate(center.x - 0.66.dp.toPx(), center.y) {
            drawLine(color, Offset(-6.75.dp.toPx(), 0f), Offset(8.dp.toPx() - stroke / 2f, 0f), stroke, StrokeCap.Round)
            val endY = 7.dp.toPx() - stroke / 4f
            val startX = -7.25.dp.toPx()
            val startYDiff = -0.25.dp.toPx()
            drawLine(color, Offset(startX, -startYDiff), Offset(0f, -endY), stroke, StrokeCap.Round)
            drawLine(color, Offset(startX, startYDiff), Offset(0f, endY), stroke, StrokeCap.Round)
        }
    }
}

/** Three dots, one above the other: the menu of the top bar (`ic_ab_other`, drawn). */
@Composable
fun MoreIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val radius = 2.dp.toPx()
        val gap = 6.dp.toPx()
        for (i in -1..1) drawCircle(color, radius, Offset(center.x, center.y + i * gap))
    }
}
