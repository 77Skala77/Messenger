package app.securemessenger.ui

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanguageControllerInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun clearPreferences() {
        context.getSharedPreferences("secure_messenger_language", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun firstLaunchDefaultsToRussian() {
        LanguageController.applySavedOrRussianDefault(context)

        assertEquals(AppLanguage.RUSSIAN, LanguageController.selected(context))
    }

    @Test
    fun englishSelectionPersistsAcrossControllerReads() {
        LanguageController.select(context, AppLanguage.ENGLISH)

        assertEquals(AppLanguage.ENGLISH, LanguageController.selected(context))
    }
}
