package app.securemessenger.ui

import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.securemessenger.signalstore.V2ChatSummary
import app.securemessenger.signalstore.V2Contact
import app.securemessenger.signalstore.V2ContactRequest
import app.securemessenger.signalstore.V2ContactState
import app.securemessenger.signalstore.V2MessageStatus
import app.securemessenger.signalstore.V2StoredMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 6d on a real phone: the chat list shows the chats newest first with their parts, requests
 * stay on top, the round button leads to the two ways of starting a chat, and an empty list says
 * what to do. The screen is given its state directly, so no relay, key or database is involved.
 */
@RunWith(AndroidJUnit4::class)
class ChatListInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<LanguageSwitchHarnessActivity>()

    private val activity get() = composeRule.activity
    private val now = System.currentTimeMillis()
    private var opened: String? = null
    private var reviewed: String? = null
    private var invitations = 0
    private var additions = 0

    private fun show(state: ConversationV2UiState, actions: ChatListActions = ChatListActions()) {
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MessengerTheme(AppThemeMode.LIGHT) {
                    V2Home(
                        state = state,
                        onInvite = { invitations++ },
                        onAdd = { additions++ },
                        onOpen = { opened = it },
                        onReview = { reviewed = it },
                        onDecline = {},
                        onRefresh = {},
                        onSettings = {},
                        actions = actions,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** DEC-035: a pinned chat stays on top and shows a pin; a muted one shows a speaker. */
    @Test
    fun pinnedChatsStayOnTopAndPinnedAndMutedChatsSayIt() {
        show(ConversationV2UiState(chats = listOf(alice(unread = 2, muted = true), bob(), carol(pinned = true))))

        assertTrue("Carol is pinned, so above Bob, who wrote last", rowTop("carol") < rowTop("bob"))
        fun icon(description: Int, contactId: String) = composeRule.onNode(
            hasContentDescription(activity.getString(description)) and hasAnyAncestor(hasTestTag(CHAT_ROW_TAG_PREFIX + contactId)),
            useUnmergedTree = true,
        )
        icon(R.string.chat_pinned_description, "carol").assertExists()
        icon(R.string.chat_muted_description, "alice").assertExists()
        icon(R.string.chat_muted_description, "bob").assertDoesNotExist()
        icon(R.string.chat_pinned_description, "bob").assertDoesNotExist()
    }

    /** DEC-035: a long press selects; taps then add to the selection instead of opening; the bar acts on all. */
    @Test
    fun aLongPressSelectsChatsAndTheBarActsOnThemAndDeletingAsksFirst() {
        val pins = mutableListOf<Pair<Set<String>, Boolean>>()
        val deletions = mutableListOf<Set<String>>()
        show(
            ConversationV2UiState(chats = listOf(alice(unread = 0), bob(), carol())),
            ChatListActions(pin = { ids, pin -> pins += ids.toSet() to pin }, delete = { deletions += it.toSet() }),
        )

        composeRule.onNodeWithTag(CHAT_ROW_TAG_PREFIX + "alice").performTouchInput { longClick() }
        composeRule.waitUntil(5_000L) { exists(SELECTION_COUNT_TAG) }
        composeRule.onNodeWithTag(CHAT_ROW_TAG_PREFIX + "bob").performClick()
        assertEquals("a tap while selecting does not open the chat", null, opened)
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasText("2") and hasTestTag(SELECTION_COUNT_TAG)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(SELECTION_PIN_TAG).performClick()
        assertEquals(listOf(setOf("alice", "bob") to true), pins)
        composeRule.waitUntil(5_000L) { !exists(SELECTION_COUNT_TAG) }

        composeRule.onNodeWithTag(CHAT_ROW_TAG_PREFIX + "carol").performTouchInput { longClick() }
        composeRule.waitUntil(5_000L) { exists(SELECTION_DELETE_TAG) }
        composeRule.onNodeWithTag(SELECTION_DELETE_TAG).performClick()
        composeRule.waitUntil(5_000L) { exists(CONFIRM_CHAT_ACTION_TAG) }
        assertTrue("nothing is deleted before the person confirms", deletions.isEmpty())
        composeRule.onNodeWithTag(CONFIRM_CHAT_ACTION_TAG).performClick()
        assertEquals(listOf(setOf("carol")), deletions)
    }

    /** DEC-035: a swipe to the left past 45 percent of the row runs the chosen action; a short one does nothing. */
    @Test
    fun aLongSwipeToTheLeftPinsAndAShortOneDoesNothing() {
        ChatSwipeSetting.set(activity, ChatSwipeAction.PIN)
        val pins = mutableListOf<Pair<Set<String>, Boolean>>()
        show(ConversationV2UiState(chats = listOf(alice(unread = 0))), ChatListActions(pin = { ids, pin -> pins += ids.toSet() to pin }))

        composeRule.onNodeWithTag(CHAT_ROW_TAG_PREFIX + "alice").performTouchInput {
            swipeLeft(startX = right - 10f, endX = right - 10f - width * 0.2f, durationMillis = 600)
        }
        composeRule.waitForIdle()
        assertTrue(pins.isEmpty())

        composeRule.onNodeWithTag(CHAT_ROW_TAG_PREFIX + "alice").performTouchInput {
            swipeLeft(startX = right - 10f, endX = left + 10f, durationMillis = 600)
        }
        composeRule.waitForIdle()
        assertEquals(listOf(setOf("alice") to true), pins)
    }

    @Test
    fun chatsComeNewestFirstAndShowTheirParts() {
        show(ConversationV2UiState(chats = listOf(alice(unread = 3), bob(), carol())))

        val bobTop = rowTop("bob")
        val aliceTop = rowTop("alice")
        val carolTop = rowTop("carol")
        assertTrue("Bob wrote last", bobTop < aliceTop)
        assertTrue("Carol has no text and the oldest start", aliceTop < carolTop)

        composeRule.onNodeWithText("Hello Alice", substring = true).assertExists()
        // Our own newest text starts with "You:" in the list.
        composeRule.onNodeWithText("See you tomorrow", substring = true).assertExists()
        composeRule.onNodeWithText(activity.getString(R.string.chat_preview_you) + " See you tomorrow").assertExists()
        composeRule.onNodeWithText(activity.getString(R.string.chat_preview_no_messages)).assertExists()
        // The badge says how many texts are unseen; the tick only what became of our own newest text.
        composeRule.onNode(
            hasContentDescription(activity.resources.getQuantityString(R.plurals.unread_messages_description, 3, 3)) and
                hasAnyAncestor(hasTestTag(CHAT_ROW_TAG_PREFIX + "alice")),
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNode(
            hasContentDescription(activity.getString(R.string.status_delivered)) and
                hasAnyAncestor(hasTestTag(CHAT_ROW_TAG_PREFIX + "bob")),
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNode(
            hasContentDescription(activity.getString(R.string.status_delivered)) and
                hasAnyAncestor(hasTestTag(CHAT_ROW_TAG_PREFIX + "alice")),
            useUnmergedTree = true,
        ).assertDoesNotExist()
    }

    @Test
    fun aRowIsAtLeastSeventyDpHighAndOpensItsChat() {
        show(ConversationV2UiState(chats = listOf(alice(unread = 0))))

        composeRule.onNodeWithTag(CHAT_ROW_TAG_PREFIX + "alice").assertHeightIsAtLeast(70.dp)
        composeRule.onNodeWithTag(CHAT_ROW_TAG_PREFIX + "alice").performClick()
        assertEquals("alice", opened)
    }

    @Test
    fun aBlockedContactSaysSoInItsRow() {
        show(ConversationV2UiState(chats = listOf(alice(unread = 0, state = V2ContactState.KEY_CHANGED))))

        composeRule.onNodeWithText(activity.getString(R.string.v2_state_key_changed)).assertExists()
        composeRule.onNodeWithText("Hello Alice").assertDoesNotExist()
    }

    @Test
    fun anEmptyListSaysWhatToDoAndRequestsStayOnTop() {
        show(ConversationV2UiState())
        composeRule.onNodeWithTag(CHAT_LIST_EMPTY_TAG).assertExists()
        composeRule.onNodeWithText(activity.getString(R.string.chat_list_empty_title)).assertExists()

        show(ConversationV2UiState(chats = listOf(alice(unread = 0)), requests = listOf(requestFrom("Dave"))))
        composeRule.onNodeWithTag(CHAT_LIST_EMPTY_TAG).assertDoesNotExist()
        composeRule.onNodeWithText(activity.getString(R.string.v2_requests_title)).assertExists()
        // Both are on the list at the start (before any scrolling), the request in its own card first.
        // The card leads to the comparison step (DEC-030); it accepts nothing by itself.
        // Scroll to the button first: on a small screen with a large font the card may be partly off screen.
        scrollTo(REVIEW_REQUEST_TAG)
        composeRule.onNodeWithTag(REVIEW_REQUEST_TAG).performClick()
        assertEquals("request-1", reviewed)
        scrollTo(CHAT_ROW_TAG_PREFIX + "alice")
        composeRule.onNodeWithTag(CHAT_ROW_TAG_PREFIX + "alice").assertExists()
    }

    @Test
    fun theRoundButtonOffersMyInvitationAndAddContact() {
        show(ConversationV2UiState(chats = listOf(alice(unread = 0))))

        composeRule.onNodeWithTag(MENU_ADD_CONTACT_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(CHAT_LIST_FAB_TAG).performClick()
        composeRule.onNodeWithTag(MENU_ADD_CONTACT_TAG).performClick()
        assertEquals(1, additions)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(MENU_ADD_CONTACT_TAG).assertDoesNotExist()

        composeRule.onNodeWithTag(CHAT_LIST_FAB_TAG).performClick()
        composeRule.onNodeWithTag(MENU_MY_INVITATION_TAG).performClick()
        assertEquals(1, invitations)
        assertEquals(1, additions)
    }

    @Test
    fun searchNarrowsTheListToWhatMatchesAndClosesBackToTheFullList() {
        show(ConversationV2UiState(chats = listOf(alice(unread = 0), bob(), carol())))

        composeRule.onNodeWithTag(SEARCH_BUTTON_TAG).performClick()
        composeRule.waitUntil(5_000L) { exists(SEARCH_FIELD_TAG) }
        composeRule.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput("boris")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(CHAT_ROW_TAG_PREFIX + "bob").assertExists()
        composeRule.onNodeWithTag(CHAT_ROW_TAG_PREFIX + "alice").assertDoesNotExist()

        // A text that matches nothing says so; the cross clears it.
        composeRule.onNodeWithTag(SEARCH_FIELD_TAG).performTextReplacement("zzzz")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SEARCH_EMPTY_TAG).assertExists()
        composeRule.onNodeWithTag(SEARCH_CLEAR_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(CHAT_ROW_TAG_PREFIX + "alice").assertExists()

        // The arrow closes the search and brings the title bar back.
        composeRule.onNodeWithTag(TOP_BAR_BACK_TAG).performClick()
        composeRule.waitUntil(5_000L) { exists(SEARCH_BUTTON_TAG) && !exists(SEARCH_FIELD_TAG) }
    }

    /**
     * The owner's report (2026-10-06): a second swipe repeated the first action (muted again, pinned
     * again) until the screen was rebuilt. Each swipe must act on the chat as it is now, so repeated
     * swipes switch back and forth, while the list stays on screen the whole time.
     */
    @Test
    fun repeatedSwipesSwitchPinAndMuteBackAndForth() {
        val pins = mutableListOf<Boolean>()
        val mutes = mutableListOf<Boolean>()
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                var pinned by remember { mutableStateOf(false) }
                var muted by remember { mutableStateOf(false) }
                MessengerTheme(AppThemeMode.LIGHT) {
                    V2Home(
                        state = ConversationV2UiState(chats = listOf(alice(unread = 0, muted = muted, pinned = pinned))),
                        onInvite = {},
                        onAdd = {},
                        onOpen = {},
                        onReview = {},
                        onDecline = {},
                        onRefresh = {},
                        onSettings = {},
                        actions = ChatListActions(
                            pin = { _, pin ->
                                pins += pin
                                pinned = pin
                            },
                            mute = { _, until ->
                                mutes += until != null
                                muted = until != null
                            },
                        ),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        fun swipe() {
            composeRule.onNodeWithTag(CHAT_ROW_TAG_PREFIX + "alice").performTouchInput {
                swipeLeft(startX = right - 10f, endX = left + 10f, durationMillis = 600)
            }
            composeRule.waitForIdle()
        }

        ChatSwipeSetting.set(activity, ChatSwipeAction.PIN)
        repeat(3) { swipe() }
        assertEquals(listOf(true, false, true), pins)

        ChatSwipeSetting.set(activity, ChatSwipeAction.MUTE)
        repeat(3) { swipe() }
        assertEquals(listOf(true, false, true), mutes)
        ChatSwipeSetting.set(activity, ChatSwipeAction.PIN)
    }

    private fun exists(tag: String) = composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun scrollTo(tag: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
    }

    private fun rowTop(contactId: String): Float {
        scrollTo(CHAT_ROW_TAG_PREFIX + contactId)
        return composeRule.onNodeWithTag(CHAT_ROW_TAG_PREFIX + contactId).fetchSemanticsNode().boundsInRoot.top
    }

    private fun contact(
        id: String,
        name: String,
        state: V2ContactState = V2ContactState.ACTIVE,
        addedAt: Long = now - DAY,
        pinned: Boolean = false,
        muted: Boolean = false,
    ) = V2Contact(
        id, name, ByteArray(33), "queue-$id", "00", id, state, addedAt,
        pinnedAtEpochMillis = if (pinned) now else null,
        mutedUntilEpochMillis = if (muted) Long.MAX_VALUE else null,
    )

    private fun text(contactId: String, text: String, outgoing: Boolean, status: V2MessageStatus, at: Long) =
        V2StoredMessage("m-$contactId", contactId, outgoing, "00", text, status, at)

    private fun alice(unread: Int, state: V2ContactState = V2ContactState.ACTIVE, muted: Boolean = false, pinned: Boolean = false) = V2ChatSummary(
        contact("alice", "Alice Brown", state, muted = muted, pinned = pinned),
        text("alice", "Hello Alice", outgoing = false, status = V2MessageStatus.RECEIVED, at = now - 60_000L),
        unread,
    )

    private fun bob() = V2ChatSummary(
        contact("bob", "Boris Ivanov"),
        text("bob", "See you tomorrow", outgoing = true, status = V2MessageStatus.DELIVERED, at = now - 10_000L),
        0,
    )

    private fun carol(pinned: Boolean = false) = V2ChatSummary(contact("carol", "Carol", addedAt = now - 2 * DAY, pinned = pinned), null, 0)

    private fun requestFrom(name: String) = V2RequestView(
        V2ContactRequest("request-1", "invitation-1", ByteArray(33), "queue-r", "00", "01", name, now - 5_000L),
        safetyNumber = null,
        heldTextCount = 0,
    )

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
