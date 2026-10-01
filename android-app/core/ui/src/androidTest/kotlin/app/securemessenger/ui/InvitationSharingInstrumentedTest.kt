package app.securemessenger.ui

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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
        composeRule.onNodeWithTag("v2_share_invitation").performScrollTo().assertExists()
        composeRule.onNodeWithTag("v2_copy_invitation").performScrollTo().assertExists()
        composeRule.onNodeWithText(activity.getString(R.string.copy_invitation_warning)).performScrollTo().assertExists()
    }
}
