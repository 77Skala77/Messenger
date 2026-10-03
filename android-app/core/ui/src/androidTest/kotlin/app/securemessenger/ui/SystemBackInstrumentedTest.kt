package app.securemessenger.ui

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.securemessenger.storage.AndroidKeystoreVaultCipher
import app.securemessenger.storage.LocalProfileRepository
import app.securemessenger.storage.SecureMessengerDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TQ-109: Android's system Back (button or gesture) goes one screen back, exactly like the app's own
 * Back button. System Back reaches the app through the activity's back dispatcher, which these tests
 * call; where nothing is registered there, the system minimizes or leaves the app as usual.
 */
@RunWith(AndroidJUnit4::class)
class SystemBackInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<LanguageSwitchHarnessActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun startEmpty() = wipeEverything()

    @After
    fun cleanUp() = wipeEverything()

    @Test
    fun theChatScreensGoBackToTheListAndTheListLeavesToTheSystem() {
        composeRule.runOnUiThread { composeRule.activity.setContent { ConversationV2Root(onOpenSettings = {}, onLocalDataUnreadable = {}) } }
        composeRule.waitUntil(TIMEOUT_MILLIS) { exists(MY_INVITATION) }
        assertFalse("nothing to go back to on the list", hasBackHandler())

        // Invitation screen -> list.
        composeRule.onNodeWithTag(MY_INVITATION).performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) { exists(CREATE_INVITATION) }
        assertTrue(hasBackHandler())
        pressSystemBack()
        composeRule.waitUntil(TIMEOUT_MILLIS) { exists(MY_INVITATION) && !exists(CREATE_INVITATION) }
        assertFalse(hasBackHandler())

        // Add-contact screen -> list.
        composeRule.onNodeWithTag(ADD_CONTACT).performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) { exists(CODE_INPUT) }
        assertTrue(hasBackHandler())
        pressSystemBack()
        composeRule.waitUntil(TIMEOUT_MILLIS) { exists(MY_INVITATION) && !exists(CODE_INPUT) }
        assertFalse(hasBackHandler())
    }

    @Test
    fun settingsGoesBackToTheMainScreen() {
        withDatabase { database -> LocalProfileRepository(database.dao(), AndroidKeystoreVaultCipher()).createProfile("Alice") }
        composeRule.runOnUiThread { composeRule.activity.setContent { SecureMessengerApp() } }
        composeRule.waitUntil(TIMEOUT_MILLIS) { exists(MY_INVITATION) }
        assertFalse("nothing to go back to on the main screen", hasBackHandler())

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.settings)).performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) { exists(LANGUAGE_OPTION) }
        assertTrue(hasBackHandler())

        pressSystemBack()
        composeRule.waitUntil(TIMEOUT_MILLIS) { exists(MY_INVITATION) && !exists(LANGUAGE_OPTION) }
        assertFalse(hasBackHandler())
    }

    /** Onboarding is the root of the app: no handler, the system behaves as usual. */
    @Test
    fun onboardingHasNothingToGoBackTo() {
        composeRule.runOnUiThread { composeRule.activity.setContent { SecureMessengerApp() } }
        composeRule.waitUntil(TIMEOUT_MILLIS) { composeRule.onAllNodesWithTagCount(MY_INVITATION) == 0 && onboardingShown() }
        assertFalse(hasBackHandler())
    }

    private fun onboardingShown(): Boolean =
        runCatching {
            composeRule.onNodeWithText(composeRule.activity.getString(R.string.onboarding_title)).assertExists()
        }.isSuccess

    private fun exists(tag: String): Boolean = composeRule.onAllNodesWithTagCount(tag) > 0

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagCount(tag: String): Int =
        onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private fun hasBackHandler(): Boolean {
        var enabled = false
        composeRule.runOnUiThread { enabled = composeRule.activity.onBackPressedDispatcher.hasEnabledCallbacks() }
        return enabled
    }

    /** What the system Back button or gesture does to an app: asks the activity's dispatcher. */
    private fun pressSystemBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals("the app must stay open", false, composeRule.activity.isFinishing)
    }

    private fun wipeEverything() = withDatabase { database -> V2Pipeline.wipeLocalData(context, database) }

    private fun <T> withDatabase(block: (SecureMessengerDatabase) -> T): T {
        val database = SecureMessengerDatabase.create(context)
        return try {
            block(database)
        } finally {
            database.close()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val MY_INVITATION = "v2_my_invitation"
        const val CREATE_INVITATION = "v2_create_invitation"
        const val ADD_CONTACT = "v2_add_contact"
        const val CODE_INPUT = "v2_code_input"
        const val LANGUAGE_OPTION = "language_option_ru"
    }
}
