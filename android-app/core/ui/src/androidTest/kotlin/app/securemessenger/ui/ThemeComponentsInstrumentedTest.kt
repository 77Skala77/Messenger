package app.securemessenger.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 6c on a real phone: the bubble outline has the sizes of the notes, the pieces have their
 * measured sizes, the theme choice picks the palette, and the preview screen shows everything in
 * both themes.
 */
@RunWith(AndroidJUnit4::class)
class ThemeComponentsInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<LanguageSwitchHarnessActivity>()

    @After
    fun forgetTheChoice() {
        ThemeController.select(composeRule.activity, AppThemeMode.SYSTEM)
    }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.runOnUiThread { composeRule.activity.setContent { content() } }
        composeRule.waitForIdle()
    }

    private fun bounds(outgoing: Boolean, tail: Boolean, nearTop: Boolean = false, nearBottom: Boolean = false): androidx.compose.ui.geometry.Rect {
        val outline = BubbleShape(outgoing, tail, nearTop, nearBottom)
            .createOutline(Size(600f, 120f), LayoutDirection.Ltr, Density(2f))
        return (outline as Outline.Generic).path.getBounds()
    }

    @Test
    fun theBubbleKeepsItsTailStripAndTheTailReachesToTheTipInset() {
        // At density 2: the strip is 16 px and the tail's tip ends 5.2 px from the outer edge.
        val outgoingTail = bounds(outgoing = true, tail = true)
        assertEquals(0f, outgoingTail.left, 0.5f)
        assertEquals(600f - 5.2f, outgoingTail.right, 0.5f)
        assertEquals(120f, outgoingTail.bottom, 0.5f)

        val outgoingBody = bounds(outgoing = true, tail = false, nearBottom = true)
        assertEquals(600f - 16f, outgoingBody.right, 0.5f)

        val incomingTail = bounds(outgoing = false, tail = true)
        assertEquals(5.2f, incomingTail.left, 0.5f)
        assertEquals(600f, incomingTail.right, 0.5f)

        val incomingBody = bounds(outgoing = false, tail = false, nearTop = true)
        assertEquals(16f, incomingBody.left, 0.5f)
    }

    @Test
    fun theBadgeAndTheAvatarHaveTheirMeasuredSizes() {
        show {
            MessengerTheme(AppThemeMode.LIGHT) {
                Box {
                    Box(Modifier.testTag("empty")) { UnreadBadge(0) }
                    Box(Modifier.testTag("single")) { UnreadBadge(7) }
                    Box(Modifier.testTag("avatar")) { Avatar("Alice Brown", "contact-1", size = 52.dp) }
                }
            }
        }
        composeRule.onNodeWithTag("empty").assertWidthIsEqualTo(0.dp)
        composeRule.onNodeWithTag("single").assertHeightIsEqualTo(MessengerDimens.BadgeHeight)
        composeRule.onNodeWithTag("single").assertWidthIsEqualTo(MessengerDimens.BadgeHeight)
        composeRule.onNodeWithTag("avatar").assertWidthIsEqualTo(52.dp)
        composeRule.onNodeWithTag("avatar").assertHeightIsEqualTo(52.dp)
    }

    @Test
    fun theChoiceOfThemePicksThePalette() {
        var light: Color? = null
        var dark: Color? = null
        show { MessengerTheme(AppThemeMode.LIGHT) { light = MaterialTheme.messenger.bubbleOutgoing } }
        show { MessengerTheme(AppThemeMode.DARK) { dark = MaterialTheme.messenger.bubbleOutgoing } }

        assertEquals(LightMessengerColors.bubbleOutgoing, light)
        assertEquals(DarkMessengerColors.bubbleOutgoing, dark)
    }

    @Test
    fun theChoiceIsRememberedAndAnUnknownValueMeansTheSystem() {
        ThemeController.select(composeRule.activity, AppThemeMode.DARK)
        assertEquals(AppThemeMode.DARK, ThemeController.selected(composeRule.activity))
        ThemeController.select(composeRule.activity, AppThemeMode.LIGHT)
        assertEquals(AppThemeMode.LIGHT, ThemeController.selected(composeRule.activity))
        composeRule.activity.getSharedPreferences("secure_messenger_theme", 0).edit().putString("selected_mode", "nonsense").commit()
        assertEquals(AppThemeMode.SYSTEM, ThemeController.selected(composeRule.activity))
    }

    @Test
    fun thePreviewShowsEveryPieceInBothThemes() {
        val activity = composeRule.activity
        for (mode in listOf(AppThemeMode.LIGHT, AppThemeMode.DARK)) {
            show { MessengerTheme(mode) { ThemePreviewScreen(onBack = {}) } }

            composeRule.onNodeWithTag(THEME_PREVIEW_TAG).assertExists()
            composeRule.onNodeWithTag("${PREVIEW_BADGE_TAG}_24")
                .assertContentDescriptionEquals(activity.resources.getQuantityString(R.plurals.unread_messages_description, 24, 24))
            composeRule.onNodeWithTag("${PREVIEW_BADGE_TAG}_1200").assertExists()
            composeRule.onNodeWithTag("${PREVIEW_BADGE_TAG}_muted").assertExists()
            for (index in 0..5) composeRule.onNodeWithTag("${PREVIEW_BUBBLE_TAG}_$index").assertExists()
        }
    }

    @Test
    fun theWallpaperDrawsInsideAnyContentAndTheChipIsCentered() {
        show {
            MessengerTheme(AppThemeMode.DARK) {
                ChatWallpaper(Modifier.testTag("wallpaper")) { Box(Modifier.testTag("content")) { DateChip("4 Oct") } }
            }
        }
        composeRule.onNodeWithTag("wallpaper").assertExists()
        composeRule.onNodeWithTag("content").assertExists()
        assertTrue(composeRule.onNodeWithTag("wallpaper").fetchSemanticsNode().size.height > 0)
    }
}
