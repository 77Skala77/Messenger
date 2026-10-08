package app.securemessenger.ui

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Collections
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DEC-038 on a phone: the settings in Telegram 12's layout. The main page shows the person and its
 * entries, each entry opens its page, the chat page chooses the theme and the swipe, the privacy page
 * switches screen security and asks before deleting everything.
 */
@RunWith(AndroidJUnit4::class)
class SettingsInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<LanguageSwitchHarnessActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val swipeBefore = ChatSwipeSetting.get(context)

    @After
    fun restore() {
        ChatSwipeSetting.set(context, swipeBefore)
    }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.runOnUiThread { composeRule.activity.setContent { MessengerTheme(AppThemeMode.DARK) { content() } } }
        composeRule.waitForIdle()
    }

    @Test
    fun theMainPageShowsThePersonAndEachEntryOpensItsPage() {
        val opened = Collections.synchronizedList(mutableListOf<SettingsPageId>())
        show {
            SettingsScreen(
                profileName = "Alice Brown",
                chatsAvailable = true,
                debugQaToolsEnabled = true,
                onOpen = { opened += it },
                onOpenThemePreview = {},
                onResetTestData = {},
                onBack = {},
            )
        }
        composeRule.onNode(hasText("Alice Brown")).assertExists()
        for (page in SettingsPageId.entries) {
            composeRule.onNodeWithTag(entryTag(page)).performScrollTo().performClick()
        }
        composeRule.waitForIdle()
        assertEquals(SettingsPageId.entries.toList(), opened.toList())
        // The debug tools stay in debug builds.
        composeRule.onNodeWithTag("debug_reset_test_data").performScrollTo().assertExists()
    }

    @Test
    fun withoutChatsThereAreNoNotificationAndConnectionEntries() {
        show {
            SettingsScreen(
                profileName = "Alice Brown",
                chatsAvailable = false,
                debugQaToolsEnabled = false,
                onOpen = {},
                onOpenThemePreview = {},
                onResetTestData = {},
                onBack = {},
            )
        }
        composeRule.onNodeWithTag(entryTag(SettingsPageId.NOTIFICATIONS)).assertDoesNotExist()
        composeRule.onNodeWithTag(entryTag(SettingsPageId.CONNECTION)).assertDoesNotExist()
        composeRule.onNodeWithTag("debug_reset_test_data").assertDoesNotExist()
        composeRule.onNodeWithTag(entryTag(SettingsPageId.PRIVACY)).assertExists()
    }

    @Test
    fun theChatPageChoosesTheThemeAndTheSwipe() {
        val chosen = Collections.synchronizedList(mutableListOf<AppThemeMode>())
        show {
            ChatSettingsPage(
                profileName = "Alice Brown",
                chatsAvailable = true,
                themeMode = AppThemeMode.SYSTEM,
                onThemeSelected = { chosen += it },
                onBack = {},
            )
        }
        composeRule.onNodeWithTag(THEME_OPTION_DARK).performClick()
        composeRule.onNodeWithTag(THEME_OPTION_LIGHT).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(AppThemeMode.DARK, AppThemeMode.LIGHT), chosen.toList())

        val other = ChatSwipeAction.entries.first { it != ChatSwipeSetting.get(context) }
        composeRule.onNodeWithTag(SWIPE_OPTION_TAG_PREFIX + other.name.lowercase()).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(other, ChatSwipeSetting.get(context))
    }

    @Test
    fun thePrivacyPageSwitchesScreenSecurityAndAsksBeforeDeleting() {
        val switched = Collections.synchronizedList(mutableListOf<Boolean>())
        var deleted = 0
        show {
            PrivacySettingsPage(
                screenSecurity = false,
                onScreenSecurityChanged = { switched += it },
                onDeleteLocalData = { deleted++ },
                onBack = {},
            )
        }
        composeRule.onNodeWithTag(SCREEN_SECURITY_TAG).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(true), switched.toList())

        composeRule.onNode(hasText(composeRule.activity.getString(R.string.delete_local_data))).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("nothing goes without the confirmation", 0, deleted)
        composeRule.onNode(hasText(composeRule.activity.getString(R.string.delete_confirm))).performClick()
        composeRule.waitForIdle()
        assertEquals(1, deleted)
    }

    @Test
    fun theLanguagePageMarksTheCurrentLanguage() {
        show { LanguageSettingsPage(onBack = {}) }
        composeRule.onNodeWithTag("language_option_ru").assertExists()
        composeRule.onNodeWithTag("language_option_en").assertExists()
        assertTrue(LanguageController.selected(context) in AppLanguage.entries)
    }
}
