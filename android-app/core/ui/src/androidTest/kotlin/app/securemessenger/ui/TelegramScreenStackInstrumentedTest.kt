package app.securemessenger.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DEC-033 on a real phone: the screen stack shows only the top screen to the person (and to
 * accessibility), opens and closes screens, and the swipe back closes a screen by Telegram's rules:
 * far enough or fast enough to the right, never for a short slow drag or a vertical one.
 */
@RunWith(AndroidJUnit4::class)
class TelegramScreenStackInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<LanguageSwitchHarnessActivity>()

    private var stack by mutableStateOf(listOf("A"))
    private var swipes = 0
    private var seenWhile = mutableListOf<Boolean>()

    private fun show(initial: List<String> = listOf("A"), swipeBackEnabled: Boolean = true) {
        stack = initial
        swipes = 0
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                TelegramScreenStack(
                    stack = stack,
                    onSwipeBack = {
                        swipes++
                        stack = stack.dropLast(1)
                    },
                    swipeBackEnabled = swipeBackEnabled,
                    modifier = Modifier.testTag("host"),
                ) { screen ->
                    val visible = LocalScreenVisible.current
                    androidx.compose.runtime.SideEffect { if (screen == "B") seenWhile += visible }
                    Box(Modifier.fillMaxSize().testTag("screen_$screen")) { Text(screen) }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun exists(tag: String) = composeRule.onAllNodesWithTagCount(tag) > 0

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagCount(tag: String): Int =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size

    @Test
    fun onlyTheTopScreenIsShownAndPushAndPopSwapScreens() {
        show()
        assertTrue(exists("screen_A"))
        assertFalse(exists("screen_B"))

        composeRule.runOnUiThread { stack = listOf("A", "B") }
        composeRule.waitUntil(5_000L) { exists("screen_B") && !exists("screen_A") }
        composeRule.waitForIdle()
        // The screen beneath stays composed but is invisible to the person and to accessibility.
        assertFalse(exists("screen_A"))

        composeRule.runOnUiThread { stack = listOf("A") }
        composeRule.waitUntil(5_000L) { exists("screen_A") && !exists("screen_B") }
    }

    @Test
    fun aScreenKnowsWhetherItIsTheOneOnShow() {
        seenWhile.clear()
        show(listOf("A", "B"))
        // B is the top screen: visible.
        assertTrue(seenWhile.isNotEmpty() && seenWhile.all { it })
    }

    @Test
    fun aFarSwipeToTheRightClosesTheScreen() {
        show(listOf("A", "B"))
        composeRule.onNodeWithTag("host").performTouchInput {
            swipeRight(startX = left + 10f, endX = right - 10f, durationMillis = 300)
        }
        composeRule.waitUntil(5_000L) { swipes == 1 }
        composeRule.waitUntil(5_000L) { exists("screen_A") && !exists("screen_B") }
        assertEquals(listOf("A"), stack)
    }

    @Test
    fun aShortSlowDragPutsTheScreenBack() {
        show(listOf("A", "B"))
        composeRule.onNodeWithTag("host").performTouchInput {
            // Past the start distance, but not a third of the width, and slowly.
            swipeRight(startX = left + 10f, endX = left + 10f + width * 0.15f, durationMillis = 900)
        }
        composeRule.waitForIdle()
        Thread.sleep(700)
        composeRule.waitForIdle()
        assertEquals(0, swipes)
        assertEquals(listOf("A", "B"), stack)
        assertTrue(exists("screen_B"))
    }

    @Test
    fun aVerticalDragNeverClosesTheScreen() {
        show(listOf("A", "B"))
        composeRule.onNodeWithTag("host").performTouchInput { swipeDown(durationMillis = 300) }
        composeRule.waitForIdle()
        Thread.sleep(500)
        assertEquals(0, swipes)
        assertEquals(listOf("A", "B"), stack)
    }

    @Test
    fun theRootScreenHasNothingToSwipeBackTo() {
        show(listOf("A"))
        composeRule.onNodeWithTag("host").performTouchInput {
            swipeRight(startX = left + 10f, endX = right - 10f, durationMillis = 300)
        }
        composeRule.waitForIdle()
        assertEquals(0, swipes)
        assertTrue(exists("screen_A"))
    }

    @Test
    fun aSwipeCanBeSwitchedOff() {
        show(listOf("A", "B"), swipeBackEnabled = false)
        composeRule.onNodeWithTag("host").performTouchInput {
            swipeRight(startX = left + 10f, endX = right - 10f, durationMillis = 300)
        }
        composeRule.waitForIdle()
        Thread.sleep(500)
        assertEquals(0, swipes)
    }
}
