package app.securemessenger.ui

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Owner decision in the Phase 5 hand check (DEC-026): the invitation can be shared without the
 * clipboard; copying stays, but never without the warning that keyboards may keep it.
 */
@RunWith(AndroidJUnit4::class)
class InvitationSharingInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<LanguageSwitchHarnessActivity>()

    @Test
    fun theInvitationCanBeSharedAndCopyingCarriesAWarning() {
        val activity = composeRule.activity
        composeRule.runOnUiThread {
            activity.setContent { MaterialTheme { V2Invite(code = "smp-invite-v3:test", onCreate = {}, onBack = {}) } }
        }
        // The screen is a lazy list: on a small screen or with a large font the buttons below the QR
        // code are not composed until the list scrolls to them, so scroll the list, not the node.
        val list = composeRule.onNode(hasScrollAction())
        list.performScrollToNode(hasTestTag("v2_share_invitation"))
        composeRule.onNodeWithTag("v2_share_invitation").assertExists()
        list.performScrollToNode(hasTestTag("v2_copy_invitation"))
        composeRule.onNodeWithTag("v2_copy_invitation").assertExists()
        val warning = activity.getString(R.string.copy_invitation_warning)
        list.performScrollToNode(hasText(warning))
        composeRule.onNodeWithText(warning).assertExists()
    }
}
