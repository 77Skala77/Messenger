package app.securemessenger.ui

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.securemessenger.signalstore.V2Contact
import app.securemessenger.signalstore.V2ContactRequest
import app.securemessenger.signalstore.V2ContactState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Audit H1 and DEC-030 on a real phone: the comparison step before accepting a request offers three
 * explicit answers and passes on exactly the number it showed; the contact page offers "the numbers
 * match" only for an unchecked contact that is not blocked, and shows and removes the mark. The mark
 * itself is set by the service, which re-checks the number (`ConversationV2ServiceTest`).
 */
@RunWith(AndroidJUnit4::class)
class SafetyNumberCheckInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<LanguageSwitchHarnessActivity>()

    private val activity get() = composeRule.activity
    private val accepted = mutableListOf<Triple<String, String, String?>>()
    private val declined = mutableListOf<String>()
    private val marked = mutableListOf<Pair<String, String>>()
    private val cleared = mutableListOf<String>()

    private fun showRequest(safetyNumber: String? = NUMBER) {
        val request = V2ContactRequest("request-1", "invitation-1", ByteArray(33), "queue", "00", "01", "Bob", 1L)
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MessengerTheme(AppThemeMode.LIGHT) {
                    V2RequestReview(
                        view = V2RequestView(request, safetyNumber, heldTextCount = 0),
                        onAccept = { id, name, compared -> accepted += Triple(id, name, compared) },
                        onDecline = { declined += it },
                        onBack = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun showContact(state: V2ContactState = V2ContactState.ACTIVE, verifiedAt: Long? = null) {
        val contact = V2Contact("contact-1", "Alice Brown", ByteArray(33), "queue", "00", "01", state, 1L, verifiedAt)
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MessengerTheme(AppThemeMode.LIGHT) {
                    V2SafetyNumber(
                        number = NUMBER,
                        onBack = {},
                        contact = contact,
                        onMarkVerified = { id, shown -> marked += id to shown },
                        onClearVerified = { cleared += it },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun scrollTo(tag: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun acceptingNeedsANameAndTheMatchingAnswerPassesTheNumberShown() {
        showRequest()

        scrollTo(ACCEPT_MATCHING_TAG)
        composeRule.onNodeWithTag(ACCEPT_MATCHING_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(ACCEPT_UNCHECKED_TAG).assertIsNotEnabled()

        scrollTo(REQUEST_NAME_TAG)
        composeRule.onNodeWithTag(REQUEST_NAME_TAG).performTextInput("Bob")
        scrollTo(ACCEPT_MATCHING_TAG)
        composeRule.onNodeWithTag(ACCEPT_MATCHING_TAG).performClick()

        assertEquals(listOf(Triple("request-1", "Bob", NUMBER)), accepted)
        assertTrue(declined.isEmpty())
    }

    @Test
    fun acceptingWithoutComparingPassesNoNumber() {
        showRequest()

        scrollTo(REQUEST_NAME_TAG)
        composeRule.onNodeWithTag(REQUEST_NAME_TAG).performTextInput("Bob")
        scrollTo(ACCEPT_UNCHECKED_TAG)
        composeRule.onNodeWithTag(ACCEPT_UNCHECKED_TAG).performClick()

        assertEquals(1, accepted.size)
        assertNull(accepted.single().third)
    }

    @Test
    fun differentNumbersDeclineWithoutAName() {
        showRequest()

        scrollTo(NUMBERS_DIFFER_TAG)
        composeRule.onNodeWithTag(NUMBERS_DIFFER_TAG).performClick()

        assertEquals(listOf("request-1"), declined)
        assertTrue(accepted.isEmpty())
    }

    @Test
    fun withoutANumberOnlyAcceptingWithoutComparingIsPossible() {
        showRequest(safetyNumber = null)

        scrollTo(REQUEST_NAME_TAG)
        composeRule.onNodeWithTag(REQUEST_NAME_TAG).performTextInput("Bob")
        scrollTo(ACCEPT_MATCHING_TAG)
        composeRule.onNodeWithTag(ACCEPT_MATCHING_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(ACCEPT_UNCHECKED_TAG).assertIsEnabled()
    }

    @Test
    fun anUncheckedContactOffersTheMatchingAnswerWithTheNumberShown() {
        showContact()

        composeRule.onNodeWithTag(VERIFIED_STATUS_TAG).assertTextContains(activity.getString(R.string.conversation_subtitle_unverified))
        composeRule.onNodeWithTag(CLEAR_VERIFIED_TAG).assertDoesNotExist()
        scrollTo(MARK_VERIFIED_TAG)
        composeRule.onNodeWithTag(MARK_VERIFIED_TAG).performClick()

        assertEquals(listOf("contact-1" to NUMBER), marked)
    }

    @Test
    fun aCheckedContactShowsTheMarkAndCanLoseIt() {
        showContact(verifiedAt = System.currentTimeMillis())

        composeRule.onNodeWithTag(MARK_VERIFIED_TAG).assertDoesNotExist()
        scrollTo(CLEAR_VERIFIED_TAG)
        composeRule.onNodeWithTag(CLEAR_VERIFIED_TAG).performClick()

        assertEquals(listOf("contact-1"), cleared)
        assertTrue(marked.isEmpty())
    }

    /** I2: a scanner still waiting is told that someone else may have used the invitation first. */
    @Test
    fun aContactWaitingForAcceptanceGetsTheHint() {
        showContact(state = V2ContactState.AWAITING_ACCEPTANCE)
        composeRule.onNodeWithTag(AWAITING_HINT_TAG).assertExists()

        showContact(state = V2ContactState.ACTIVE)
        composeRule.onNodeWithTag(AWAITING_HINT_TAG).assertDoesNotExist()
    }

    @Test
    fun aBlockedContactOffersNeitherAnswerAndIsNeverShownAsChecked() {
        showContact(state = V2ContactState.KEY_CHANGED, verifiedAt = 1L)

        composeRule.onNodeWithTag(MARK_VERIFIED_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(CLEAR_VERIFIED_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(VERIFIED_STATUS_TAG).assertDoesNotExist()
    }

    private companion object {
        const val NUMBER = "123451234512345123451234512345123451234512345123451234512345"
    }
}
