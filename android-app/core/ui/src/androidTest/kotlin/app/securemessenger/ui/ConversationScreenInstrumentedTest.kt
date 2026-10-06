package app.securemessenger.ui

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
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
    private val replies = mutableListOf<String?>()
    private val mutes = mutableListOf<Long?>()
    private var deletions = 0
    private var backs = 0
    private var safetyOpened = 0

    private fun contact(state: V2ContactState = V2ContactState.ACTIVE, verifiedAt: Long? = null, mutedUntil: Long? = null) =
        V2Contact("contact", "Alice Brown", ByteArray(33), "queue", "00", "01", state, now - DAY, verifiedAt, mutedUntilEpochMillis = mutedUntil)

    private fun message(id: String, text: String, outgoing: Boolean, status: V2MessageStatus, at: Long) =
        V2StoredMessage(id, "contact", outgoing, "00", text, status, at)

    private fun show(
        state: V2ContactState = V2ContactState.ACTIVE,
        messages: List<V2StoredMessage>,
        verifiedAt: Long? = null,
        mutedUntil: Long? = null,
    ) {
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MessengerTheme(AppThemeMode.LIGHT) {
                    V2Conversation(
                        contact = contact(state, verifiedAt, mutedUntil),
                        messages = messages,
                        onSend = { text, replyTo ->
                            sent += text
                            replies += replyTo
                        },
                        onBack = { backs++ },
                        onOpenSafety = { safetyOpened++ },
                        actions = ConversationActions(mute = { mutes += it }, delete = { deletions++ }),
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun exists(tag: String) = composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    /** A question, an answer quoting it, and a text that quotes something this phone does not have. */
    private fun replyConversation() = listOf(
        V2StoredMessage("q", "contact", false, INNER_Q, "Shall we have lunch at noon today?", V2MessageStatus.RECEIVED, now - 120_000L),
        V2StoredMessage("a", "contact", true, INNER_A, "Yes, see you", V2MessageStatus.DELIVERED, now - 60_000L, replyToInnerIdHex = INNER_Q),
        V2StoredMessage("gone", "contact", false, INNER_G, "About that", V2MessageStatus.RECEIVED, now - 30_000L, replyToInnerIdHex = INNER_MISSING),
    )

    /** DEC-035: a reply shows a quote of the text it answers, or says that this phone does not have it. */
    @Test
    fun aReplyShowsAQuoteOfTheTextItAnswers() {
        show(messages = replyConversation())

        composeRule.onNode(hasText("Shall we have lunch at noon today?") and hasAnyAncestor(hasTestTag(QUOTE_TAG_PREFIX + "a")), useUnmergedTree = true).assertExists()
        composeRule.onNode(
            hasText(activity.getString(R.string.reply_unavailable)) and hasAnyAncestor(hasTestTag(QUOTE_TAG_PREFIX + "gone")),
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNodeWithTag(QUOTE_TAG_PREFIX + "q").assertDoesNotExist()
    }

    /** DEC-035: a long press offers "Reply"; the answer is sent with the ID of the text it answers. */
    @Test
    fun aLongPressRepliesAndTheAnswerCarriesTheQuotedId() {
        show(messages = replyConversation())

        composeRule.onNodeWithTag(MESSAGE_TAG_PREFIX + "q").performTouchInput { longClick() }
        composeRule.waitUntil(5_000L) { exists(MESSAGE_MENU_REPLY_TAG) }
        composeRule.onNodeWithTag(MESSAGE_MENU_REPLY_TAG).performClick()
        composeRule.waitUntil(5_000L) { exists(REPLY_PANEL_TAG) }
        composeRule.onNode(hasText("Shall we have lunch at noon today?") and hasAnyAncestor(hasTestTag(REPLY_PANEL_TAG)), useUnmergedTree = true).assertExists()

        composeRule.onNodeWithTag(MESSAGE_INPUT_TAG).performTextInput("Sure")
        composeRule.waitUntil(5_000L) { exists(MESSAGE_SEND_TAG) }
        composeRule.onNodeWithTag(MESSAGE_SEND_TAG).performClick()
        assertEquals(listOf("Sure"), sent)
        assertEquals(listOf<String?>(INNER_Q), replies)
        composeRule.waitUntil(5_000L) { !exists(REPLY_PANEL_TAG) }
    }

    /** DEC-035: Telegram's swipe to the left answers too; the cross cancels, and the next text answers nothing. */
    @Test
    fun aSwipeToTheLeftRepliesAndTheCrossCancels() {
        show(messages = replyConversation())

        composeRule.onNodeWithTag(MESSAGE_TAG_PREFIX + "q").performTouchInput {
            // 0.4 cm to start, then 50 dp to answer: 150 dp is plenty, and stays on screen.
            swipeLeft(startX = right - 5f, endX = right - 5f - 150.dp.toPx(), durationMillis = 500)
        }
        composeRule.waitUntil(5_000L) { exists(REPLY_PANEL_TAG) }
        composeRule.onNodeWithTag(REPLY_PANEL_CANCEL_TAG).performClick()
        composeRule.waitUntil(5_000L) { !exists(REPLY_PANEL_TAG) }

        composeRule.onNodeWithTag(MESSAGE_INPUT_TAG).performTextInput("Plain")
        composeRule.waitUntil(5_000L) { exists(MESSAGE_SEND_TAG) }
        composeRule.onNodeWithTag(MESSAGE_SEND_TAG).performClick()
        assertEquals(listOf<String?>(null), replies)
    }

    /** DEC-035: the chat's menu mutes for a chosen time, and deleting the chat asks first. */
    @Test
    fun theChatMenuMutesForAChosenTimeAndDeletingAsksFirst() {
        show(messages = conversation())

        composeRule.onNodeWithTag(CHAT_MENU_TAG).performClick()
        composeRule.waitUntil(5_000L) { exists(CHAT_MENU_MUTE_TAG) }
        composeRule.onNodeWithTag(CHAT_MENU_MUTE_TAG).performClick()
        composeRule.waitUntil(5_000L) { exists(MUTE_FOR_TAG_PREFIX + "forever") }
        composeRule.onNodeWithTag(MUTE_FOR_TAG_PREFIX + "forever").performClick()
        assertEquals(listOf<Long?>(Long.MAX_VALUE), mutes)

        composeRule.waitUntil(5_000L) { !exists(MUTE_FOR_TAG_PREFIX + "forever") }
        composeRule.onNodeWithTag(CHAT_MENU_TAG).performClick()
        composeRule.waitUntil(5_000L) { exists(CHAT_MENU_DELETE_TAG) }
        composeRule.onNodeWithTag(CHAT_MENU_DELETE_TAG).performClick()
        composeRule.waitUntil(5_000L) { exists(CONFIRM_CHAT_ACTION_TAG) }
        assertEquals(0, deletions)
        composeRule.onNodeWithTag(CONFIRM_CHAT_ACTION_TAG).performClick()
        assertEquals(1, deletions)
    }

    /** DEC-035: a muted chat says so in its header, and its menu offers to unmute it. */
    @Test
    fun aMutedChatSaysSoAndItsMenuUnmutes() {
        show(messages = conversation(), mutedUntil = Long.MAX_VALUE)

        composeRule.onNode(hasContentDescription(activity.getString(R.string.chat_muted_description)), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(CHAT_MENU_TAG).performClick()
        composeRule.waitUntil(5_000L) { exists(CHAT_MENU_MUTE_TAG) }
        composeRule.onNodeWithTag(CHAT_MENU_MUTE_TAG).performClick()
        assertEquals(listOf<Long?>(null), mutes)
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
        const val INNER_Q = "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf"
        const val INNER_A = "b0b1b2b3b4b5b6b7b8b9babbbcbdbebf"
        const val INNER_G = "c0c1c2c3c4c5c6c7c8c9cacbcccdcecf"
        const val INNER_MISSING = "d0d1d2d3d4d5d6d7d8d9dadbdcdddedf"
    }
}
