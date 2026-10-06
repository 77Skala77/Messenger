package app.securemessenger.ui

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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

    private fun show(state: ConversationV2UiState) {
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
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun chatsComeNewestFirstAndShowTheirParts() {
        show(ConversationV2UiState(chats = listOf(alice(unread = 3), bob(), carol())))

        val bobTop = rowTop("bob")
        val aliceTop = rowTop("alice")
        val carolTop = rowTop("carol")
        assertTrue("Bob wrote last", bobTop < aliceTop)
        assertTrue("Carol has no text and the oldest start", aliceTop < carolTop)

        composeRule.onNodeWithText("Hello Alice").assertExists()
        composeRule.onNodeWithText("See you tomorrow").assertExists()
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

    private fun scrollTo(tag: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
    }

    private fun rowTop(contactId: String): Float {
        scrollTo(CHAT_ROW_TAG_PREFIX + contactId)
        return composeRule.onNodeWithTag(CHAT_ROW_TAG_PREFIX + contactId).fetchSemanticsNode().boundsInRoot.top
    }

    private fun contact(id: String, name: String, state: V2ContactState = V2ContactState.ACTIVE, addedAt: Long = now - DAY) =
        V2Contact(id, name, ByteArray(33), "queue-$id", "00", id, state, addedAt)

    private fun text(contactId: String, text: String, outgoing: Boolean, status: V2MessageStatus, at: Long) =
        V2StoredMessage("m-$contactId", contactId, outgoing, "00", text, status, at)

    private fun alice(unread: Int, state: V2ContactState = V2ContactState.ACTIVE) = V2ChatSummary(
        contact("alice", "Alice Brown", state),
        text("alice", "Hello Alice", outgoing = false, status = V2MessageStatus.RECEIVED, at = now - 60_000L),
        unread,
    )

    private fun bob() = V2ChatSummary(
        contact("bob", "Boris Ivanov"),
        text("bob", "See you tomorrow", outgoing = true, status = V2MessageStatus.DELIVERED, at = now - 10_000L),
        0,
    )

    private fun carol() = V2ChatSummary(contact("carol", "Carol", addedAt = now - 2 * DAY), null, 0)

    private fun requestFrom(name: String) = V2RequestView(
        V2ContactRequest("request-1", "invitation-1", ByteArray(33), "queue-r", "00", "01", name, now - 5_000L),
        safetyNumber = null,
        heldTextCount = 0,
    )

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
