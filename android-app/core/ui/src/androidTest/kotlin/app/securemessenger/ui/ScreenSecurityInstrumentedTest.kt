package app.securemessenger.ui

import android.content.Context
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DEC-032 on a device: screen security is off by default; switched on, the app's window carries
 * FLAG_SECURE (no screenshots or screen recording, hidden in recent apps); switched off, it does not.
 * The invitation screen is not protected on its own (owner, 2026-10-06), and it shows when the code
 * on it was used up.
 */
@RunWith(AndroidJUnit4::class)
class ScreenSecurityInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<LanguageSwitchHarnessActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun forgetTheChoice() = ScreenSecurity.set(context, false)

    @Test
    fun screenSecurityIsOffByDefaultAndRemembered() {
        context.getSharedPreferences("secure_messenger_privacy", Context.MODE_PRIVATE).edit().clear().commit()
        assertFalse(ScreenSecurity.enabled(context))
        ScreenSecurity.set(context, true)
        assertTrue(ScreenSecurity.enabled(context))
    }

    @Test
    fun theWindowIsSecureExactlyWhileTheSwitchIsOn() {
        var enabled by mutableStateOf(true)
        composeRule.runOnUiThread { composeRule.activity.setContent { SecureWindowEffect(enabled) } }
        composeRule.waitForIdle()
        assertTrue(secure())

        composeRule.runOnUiThread { enabled = false }
        composeRule.waitForIdle()
        assertFalse(secure())
    }

    @Test
    fun theInvitationScreenIsNotProtectedAndSaysWhenItsCodeWasUsedUp() {
        var code by mutableStateOf<String?>(null)
        composeRule.runOnUiThread {
            composeRule.activity.setContent { MessengerTheme(AppThemeMode.LIGHT) { V2Invite(code = code, onCreate = {}, onBack = {}) } }
        }
        composeRule.waitForIdle()
        assertFalse(secure())
        composeRule.onNodeWithTag(INVITATION_SPENT_TAG).assertDoesNotExist()

        composeRule.runOnUiThread { code = "smp-invite-v3:first" }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(INVITATION_SPENT_TAG).assertDoesNotExist()

        // Used (or expired) while on screen: the code goes, the note comes.
        composeRule.runOnUiThread { code = null }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(INVITATION_SPENT_TAG).assertExists()

        // A new invitation clears it.
        composeRule.runOnUiThread { code = "smp-invite-v3:second" }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(INVITATION_SPENT_TAG).assertDoesNotExist()
        assertFalse(secure())
    }

    private fun secure(): Boolean {
        var flags = 0
        composeRule.runOnUiThread { flags = composeRule.activity.window.attributes.flags }
        return flags and WindowManager.LayoutParams.FLAG_SECURE != 0
    }
}
