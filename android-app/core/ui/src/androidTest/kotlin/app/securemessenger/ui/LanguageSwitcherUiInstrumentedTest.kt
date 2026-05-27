package app.securemessenger.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LanguageSwitcherUiInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<LanguageSwitchHarnessActivity>()

    @Before
    fun startInRussian() {
        composeRule.runOnUiThread {
            LanguageController.select(composeRule.activity, AppLanguage.RUSSIAN)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText("Настройки").assertIsDisplayed()
            }.isSuccess
        }
    }

    @After
    fun restoreRussian() {
        composeRule.runOnUiThread {
            LanguageController.select(composeRule.activity, AppLanguage.RUSSIAN)
        }
    }

    @Test
    fun pressingEnglishRowTextImmediatelyDisplaysEnglishResources() {
        composeRule.onNodeWithText("English").performClick()

        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Language").assertIsDisplayed()
    }

    @Test
    fun pressingRadioCircleAreaAlsoSelectsEnglish() {
        composeRule.onNodeWithTag(LanguageSwitchHarnessActivity.ENGLISH_OPTION).performTouchInput {
            click(Offset(20f, center.y))
        }

        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun pressingRussianRowTextReturnsVisibleResourcesToRussian() {
        composeRule.onNodeWithText("English").performClick()
        composeRule.onNodeWithText("Russian").performClick()

        composeRule.onNodeWithText("Настройки").assertIsDisplayed()
        composeRule.onNodeWithText("Язык").assertIsDisplayed()
    }

    @Test
    fun selectionPersistsAfterActivityRestart() {
        composeRule.onNodeWithText("English").performClick()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Language").assertIsDisplayed()
    }

    @Test
    fun emptySpaceInsideOptionRowTriggersSelection() {
        composeRule.onNodeWithTag(LanguageSwitchHarnessActivity.ENGLISH_OPTION).performTouchInput {
            click(Offset(300f, center.y))
        }

        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }
}
