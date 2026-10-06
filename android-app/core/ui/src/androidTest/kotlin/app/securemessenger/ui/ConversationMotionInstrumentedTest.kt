package app.securemessenger.ui

import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.securemessenger.signalstore.V2Contact
import app.securemessenger.signalstore.V2ContactState
import app.securemessenger.signalstore.V2MessageStatus
import app.securemessenger.signalstore.V2StoredMessage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DEC-033 on a real phone: the conversation's Telegram motion. A round button appears when the
 * newest message is out of view, counts the texts that arrive meanwhile and takes the person back;
 * a date pill floats while scrolling and goes a moment after.
 */
@RunWith(AndroidJUnit4::class)
class ConversationMotionInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<LanguageSwitchHarnessActivity>()

    private val now = System.currentTimeMillis()
    private var messages by mutableStateOf<List<V2StoredMessage>>(emptyList())

    private fun history(count: Int) = (1..count).map { i ->
        V2StoredMessage(
            "m$i", "contact", i % 2 == 0, "00", "Message number $i", if (i % 2 == 0) V2MessageStatus.DELIVERED else V2MessageStatus.RECEIVED,
            now - (count - i) * 6 * HOUR,
        )
    }

    private fun show(initial: List<V2StoredMessage>) {
        messages = initial
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MessengerTheme(AppThemeMode.LIGHT) {
                    V2Conversation(
                        contact = V2Contact("contact", "Alice Brown", ByteArray(33), "queue", "00", "01", V2ContactState.ACTIVE, now - DAY),
                        messages = messages,
                        onSend = {},
                        onBack = {},
                        onOpenSafety = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun exists(tag: String) = composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun scrollUp() {
        composeRule.onNodeWithTag(CONVERSATION_LIST_TAG).performScrollToIndex(40)
    }

    @Test
    fun theScrollDownButtonAppearsWhenScrolledUpAndTakesThePersonBack() {
        show(history(60))
        assertTrue("the list opens at the newest message", !exists(SCROLL_DOWN_TAG))

        scrollUp()
        composeRule.waitUntil(5_000L) { exists(SCROLL_DOWN_TAG) }
        composeRule.onNodeWithTag(SCROLL_DOWN_TAG).performClick()

        composeRule.waitUntil(8_000L) { !exists(SCROLL_DOWN_TAG) && exists(MESSAGE_TAG_PREFIX + "m60") }
    }

    @Test
    fun aTextThatArrivesWhileScrolledUpIsCountedAboveTheButton() {
        show(history(60))
        scrollUp()
        composeRule.waitUntil(5_000L) { exists(SCROLL_DOWN_TAG) }
        assertTrue(!exists(SCROLL_DOWN_COUNT_TAG))

        // Someone writes while the person reads higher up.
        composeRule.runOnUiThread {
            messages = messages + V2StoredMessage("m61", "contact", false, "00", "Are you there?", V2MessageStatus.RECEIVED, System.currentTimeMillis())
        }
        composeRule.waitUntil(5_000L) { exists(SCROLL_DOWN_COUNT_TAG) }

        composeRule.onNodeWithTag(SCROLL_DOWN_TAG).performClick()
        composeRule.waitUntil(8_000L) { !exists(SCROLL_DOWN_TAG) && !exists(SCROLL_DOWN_COUNT_TAG) }
    }

    @Test
    fun theFloatingDateShowsWhileScrollingAndGoesAMomentAfter() {
        show(history(60))
        assertTrue(!exists(FLOATING_DATE_TAG))

        // A finger drag, as a person scrolls (a jump to an index is never "scrolling in progress").
        composeRule.onNodeWithTag(CONVERSATION_LIST_TAG).performTouchInput {
            swipeDown(startY = top + height * 0.2f, endY = top + height * 0.8f, durationMillis = 600)
        }
        composeRule.waitUntil(5_000L) { exists(FLOATING_DATE_TAG) }
        // About a second after scrolling stopped (and 150 ms of fading) it is gone.
        composeRule.waitUntil(8_000L) { !exists(FLOATING_DATE_TAG) }
    }

    private companion object {
        const val HOUR = 60L * 60 * 1000
        const val DAY = 24 * HOUR
    }
}
