package app.securemessenger.ui

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.securemessenger.signalstore.V2Contact
import app.securemessenger.signalstore.V2ContactState
import app.securemessenger.signalstore.V2MessageStatus
import app.securemessenger.signalstore.V2StoredMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 6e on a real phone: the conversation shows bubbles newest at the bottom with date pills,
 * ticks only on our own texts, the time on the last line of a short text, bubbles that never exceed
 * their maximum width, a header that leads to the safety number, and an input bar whose send button
 * appears with text. The screen is given its state directly: no relay, key or database is involved.
 */
@RunWith(AndroidJUnit4::class)
class ConversationScreenInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<LanguageSwitchHarnessActivity>()

    private val activity get() = composeRule.activity
    private val now = System.currentTimeMillis()
    private val sent = mutableListOf<String>()
    private var backs = 0
    private var safetyOpened = 0

    private fun contact(state: V2ContactState = V2ContactState.ACTIVE, verifiedAt: Long? = null) =
        V2Contact("contact", "Alice Brown", ByteArray(33), "queue", "00", "01", state, now - DAY, verifiedAt)

    private fun message(id: String, text: String, outgoing: Boolean, status: V2MessageStatus, at: Long) =
        V2StoredMessage(id, "contact", outgoing, "00", text, status, at)

    private fun show(state: V2ContactState = V2ContactState.ACTIVE, messages: List<V2StoredMessage>, verifiedAt: Long? = null) {
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MessengerTheme(AppThemeMode.LIGHT) {
                    V2Conversation(
                        contact = contact(state, verifiedAt),
                        messages = messages,
                        onSend = { sent += it },
                        onBack = { backs++ },
                        onOpenSafety = { safetyOpened++ },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun top(id: String): Float = composeRule.onNodeWithTag(MESSAGE_TAG_PREFIX + id).fetchSemanticsNode().boundsInRoot.top

    private fun conversation() = listOf(
        message("m1", "Hi! How are you?", outgoing = false, status = V2MessageStatus.RECEIVED, at = now - 2 * DAY),
        message("m2", "Fine, thanks", outgoing = true, status = V2MessageStatus.DELIVERED, at = now - 2 * DAY + 60_000L),
        message("m3", "See you at noon", outgoing = true, status = V2MessageStatus.PENDING, at = now - 60_000L),
    )

    @Test
    fun theNewestMessageIsAtTheBottomAndTicksBelongToOurTextsOnly() {
        show(messages = conversation())

        assertTrue(top("m1") < top("m2"))
        assertTrue(top("m2") < top("m3"))
        fun hasTick(id: String, description: String) = composeRule.onNode(
            hasContentDescription(description) and hasAnyAncestor(hasTestTag(MESSAGE_TAG_PREFIX + id)),
            useUnmergedTree = true,
        )
        hasTick("m2", activity.getString(R.string.status_delivered)).assertExists()
        hasTick("m3", activity.getString(R.string.status_pending)).assertExists()
        // An incoming text has no tick at all, and nothing says "read".
        hasTick("m1", activity.getString(R.string.status_delivered)).assertDoesNotExist()
        hasTick("m1", activity.getString(R.string.status_received)).assertDoesNotExist()
        composeRule.onNodeWithText("Hi! How are you?").assertExists()
    }

    @Test
    fun aShortTextKeepsItsTimeOnTheSameLine() {
        show(messages = listOf(message("short", "Ok", outgoing = false, status = V2MessageStatus.RECEIVED, at = now)))

        // One line of text plus the bubble's paddings: far below two lines even at a large font size.
        val height = composeRule.onNodeWithTag(MESSAGE_TAG_PREFIX + "short").fetchSemanticsNode().size.height
        val density = activity.resources.displayMetrics.density
        assertTrue("the time is on the text line (height $height px)", height < 60 * density)
    }

    @Test
    fun aLongTextWrapsWithinTheMaximumWidth() {
        val long = "word ".repeat(80)
        show(messages = listOf(message("long", long, outgoing = true, status = V2MessageStatus.SENT, at = now)))

        val density = activity.resources.displayMetrics.density
        val width = composeRule.onNodeWithTag(MESSAGE_TAG_PREFIX + "long").fetchSemanticsNode().size.width
        val maxDp = (activity.resources.configuration.screenWidthDp - 80).coerceIn(160, 480)
        assertTrue("bubble ${width / density} dp wide, at most $maxDp dp", width <= maxDp * density + 1f)
        // The time block of a wrapped text is on its own line when the last line is nearly full, so the
        // bubble is at least two text lines high.
        val height = composeRule.onNodeWithTag(MESSAGE_TAG_PREFIX + "long").fetchSemanticsNode().size.height
        assertTrue(height > 60 * density)
    }

    @Test
    fun theHeaderShowsTheContactAndLeadsToTheSafetyNumber() {
        show(messages = conversation())

        composeRule.onNodeWithText("Alice Brown").assertExists()
        composeRule.onNodeWithText(activity.getString(R.string.conversation_subtitle_unverified)).assertExists()
        composeRule.onNodeWithTag(CONVERSATION_HEADER_TAG).performClick()
        assertEquals(1, safetyOpened)
        composeRule.onNodeWithTag(TOP_BAR_BACK_TAG).performClick()
        assertEquals(1, backs)
    }

    /** DEC-030: the header says "checked" only for an active contact whose number the person confirmed. */
    @Test
    fun theHeaderSaysWhetherTheNumberWasChecked() {
        show(messages = conversation(), verifiedAt = now)
        composeRule.onNodeWithText(activity.getString(R.string.conversation_subtitle_verified)).assertExists()
        composeRule.onNodeWithText(activity.getString(R.string.conversation_subtitle_unverified)).assertDoesNotExist()

        // A blocked contact is never shown as checked, whatever is stored.
        show(state = V2ContactState.KEY_CHANGED, messages = conversation(), verifiedAt = now)
        composeRule.onNodeWithText(activity.getString(R.string.conversation_subtitle_verified)).assertDoesNotExist()
        composeRule.onNodeWithText(activity.getString(R.string.v2_state_key_changed)).assertExists()
    }

    @Test
    fun theSendButtonAppearsWithTextAndSendsAndClearsTheField() {
        show(messages = conversation())

        composeRule.onNodeWithTag(MESSAGE_SEND_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(MESSAGE_INPUT_TAG).performTextInput("   ")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(MESSAGE_SEND_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(MESSAGE_INPUT_TAG).performTextInput("Hello")
        composeRule.waitUntil(5_000L) { composeRule.onAllNodesWithTagCount(MESSAGE_SEND_TAG) > 0 }
        composeRule.onNodeWithTag(MESSAGE_SEND_TAG).performClick()

        assertEquals(listOf("   Hello"), sent)
        composeRule.waitUntil(5_000L) { composeRule.onAllNodesWithTagCount(MESSAGE_SEND_TAG) == 0 }
    }

    @Test
    fun aBlockedContactCannotBeWrittenTo() {
        show(state = V2ContactState.KEY_CHANGED, messages = conversation())

        composeRule.onNodeWithText(activity.getString(R.string.v2_state_key_changed)).assertExists()
        composeRule.onNodeWithTag(MESSAGE_INPUT_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(MESSAGE_SEND_TAG).assertDoesNotExist()
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagCount(tag: String): Int =
        onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
