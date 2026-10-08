package app.securemessenger.ui

import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.securemessenger.signalstore.V2Contact
import app.securemessenger.signalstore.V2ContactState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Telegram 12's main screen (owner, 2026-10-07): the tab bar switches the page, Back on another tab
 * returns to the chats, the bar goes while chats are searched or selected, and the Chats tab counts
 * unread chats. The Contacts tab lists the contacts by name and opens a chat with a tap.
 */
@RunWith(AndroidJUnit4::class)
class HomeTabsInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<LanguageSwitchHarnessActivity>()

    private fun exists(tag: String) = composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private var hide by mutableStateOf(false)

    private fun show(unread: Int = 0) {
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MessengerTheme(AppThemeMode.DARK) {
                    var tab by androidx.compose.runtime.remember { mutableStateOf(HomeTab.CHATS) }
                    HomeTabsScaffold(tab = tab, onTab = { tab = it }, unreadChats = unread, profileName = "Alice", chromeHidden = hide) { shown ->
                        Text("page ${shown.name}", modifier = Modifier.testTag("page_${shown.name}"))
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun theTabsSwitchThePageAndBackReturnsToTheChats() {
        show(unread = 3)
        composeRule.onNodeWithTag(HOME_TAB_TAG_PREFIX + "chats").assertIsSelected()
        composeRule.onNode(hasText("3"), useUnmergedTree = true).assertExists()
        assertTrue(exists("page_CHATS"))

        composeRule.onNodeWithTag(HOME_TAB_TAG_PREFIX + "settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(HOME_TAB_TAG_PREFIX + "settings").assertIsSelected()
        assertTrue(exists("page_SETTINGS"))
        assertTrue("the old page is gone", !exists("page_CHATS"))

        composeRule.onNodeWithTag(HOME_TAB_TAG_PREFIX + "profile").performClick()
        composeRule.waitForIdle()
        assertTrue(exists("page_PROFILE"))

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertTrue(exists("page_CHATS"))
        composeRule.onNodeWithTag(HOME_TAB_TAG_PREFIX + "chats").assertIsSelected()
    }

    @Test
    fun theBarGoesWhileChatsAreSearchedOrSelected() {
        show()
        assertTrue(exists(HOME_TAB_BAR_TAG))
        composeRule.runOnIdle { hide = true }
        composeRule.waitUntil(3_000L) { !exists(HOME_TAB_BAR_TAG) }
        composeRule.runOnIdle { hide = false }
        composeRule.waitUntil(3_000L) { exists(HOME_TAB_BAR_TAG) }
    }

    @Test
    fun theContactsTabListsContactsByNameAndOpensAChat() {
        val opened = mutableListOf<String>()
        val now = System.currentTimeMillis()
        fun contact(id: String, name: String, state: V2ContactState = V2ContactState.ACTIVE) =
            V2Contact(id, name, ByteArray(33), "queue", "0$id", "1$id", state, now)
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MessengerTheme(AppThemeMode.LIGHT) {
                    ContactsTab(
                        contacts = listOf(contact("b", "Zoe"), contact("a", "anna"), contact("c", "Mark", V2ContactState.KEY_CHANGED)),
                        onOpen = { opened += it },
                        onInvite = {},
                        onAdd = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val order = composeRule.onAllNodes(hasTestTag(CONTACT_ROW_TAG_PREFIX + "a") or hasTestTag(CONTACT_ROW_TAG_PREFIX + "b") or hasTestTag(CONTACT_ROW_TAG_PREFIX + "c"))
            .fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.top }
            .map { node -> node.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag].removePrefix(CONTACT_ROW_TAG_PREFIX) }
        assertEquals(listOf("a", "c", "b"), order)
        composeRule.onNode(hasText(composeRule.activity.getString(R.string.contacts_status_blocked)), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(CONTACT_ROW_TAG_PREFIX + "b").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("b"), opened)
    }
}
