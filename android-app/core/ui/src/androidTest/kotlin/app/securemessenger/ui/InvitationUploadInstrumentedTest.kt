package app.securemessenger.ui

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Audit L2 on a real phone: the invitation screen says whether the invitation reached the delivery
 * server, offers "try again" when it did not (or is not known to have), and does not offer to send
 * a code that cannot work.
 */
@RunWith(AndroidJUnit4::class)
class InvitationUploadInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<LanguageSwitchHarnessActivity>()

    private val activity get() = composeRule.activity
    private var retries = 0

    private fun show(upload: InvitationUpload, busy: Boolean = false) {
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MessengerTheme(AppThemeMode.LIGHT) {
                    V2Invite(
                        code = "smp-invite-v3:test",
                        onCreate = {},
                        onBack = {},
                        upload = upload,
                        busy = busy,
                        onRetry = { retries++ },
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
    fun aFailedUploadSaysSoOffersTryAgainAndHoldsBackSharing() {
        show(InvitationUpload.FAILED)

        composeRule.onNodeWithText(activity.getString(R.string.invitation_status_failed)).assertExists()
        scrollTo("v2_retry_invitation")
        composeRule.onNodeWithText(activity.getString(R.string.invitation_retry)).assertExists()
        composeRule.onNodeWithTag("v2_retry_invitation").performClick()
        assertEquals(1, retries)
        scrollTo("v2_share_invitation")
        composeRule.onNodeWithTag("v2_share_invitation").assertIsNotEnabled()
        scrollTo("v2_copy_invitation")
        composeRule.onNodeWithTag("v2_copy_invitation").assertIsNotEnabled()
    }

    @Test
    fun anUploadedInvitationSaysReadyAndOffersNoRetry() {
        show(InvitationUpload.UPLOADED)

        composeRule.onNodeWithText(activity.getString(R.string.invitation_status_uploaded)).assertExists()
        composeRule.onNodeWithTag("v2_retry_invitation").assertDoesNotExist()
        scrollTo("v2_share_invitation")
        composeRule.onNodeWithTag("v2_share_invitation").assertIsEnabled()
    }

    @Test
    fun anUnknownUploadOffersToSendAgainAndKeepsSharingOpen() {
        show(InvitationUpload.UNKNOWN)

        composeRule.onNodeWithText(activity.getString(R.string.invitation_status_unknown)).assertExists()
        scrollTo("v2_retry_invitation")
        composeRule.onNodeWithText(activity.getString(R.string.invitation_resend)).assertExists()
        scrollTo("v2_share_invitation")
        composeRule.onNodeWithTag("v2_share_invitation").assertIsEnabled()
    }

    @Test
    fun whileSendingThereIsNoRetryAndNothingToShareYet() {
        show(InvitationUpload.FAILED, busy = true)

        composeRule.onNodeWithText(activity.getString(R.string.invitation_sending)).assertExists()
        composeRule.onNodeWithTag("v2_retry_invitation").assertDoesNotExist()
        scrollTo("v2_share_invitation")
        composeRule.onNodeWithTag("v2_share_invitation").assertIsNotEnabled()
    }
}
