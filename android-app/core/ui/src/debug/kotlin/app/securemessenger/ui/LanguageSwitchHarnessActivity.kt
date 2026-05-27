package app.securemessenger.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource

/**
 * Debug-only test host for exercising locale recreation without entering app data.
 */
class LanguageSwitchHarnessActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val context = this@LanguageSwitchHarnessActivity
                var language by remember { mutableStateOf(LanguageController.selected(context)) }
                Column(Modifier.fillMaxSize().selectableGroup()) {
                    Text(stringResource(R.string.settings))
                    Text(stringResource(R.string.language))
                    SelectableOptionRow(
                        label = stringResource(R.string.russian),
                        selected = language == AppLanguage.RUSSIAN,
                        modifier = Modifier.testTag(RUSSIAN_OPTION),
                    ) {
                        language = AppLanguage.RUSSIAN
                        LanguageController.select(context, language)
                    }
                    SelectableOptionRow(
                        label = stringResource(R.string.english),
                        selected = language == AppLanguage.ENGLISH,
                        modifier = Modifier.testTag(ENGLISH_OPTION),
                    ) {
                        language = AppLanguage.ENGLISH
                        LanguageController.select(context, language)
                    }
                }
            }
        }
    }

    companion object {
        const val RUSSIAN_OPTION = "test_language_option_ru"
        const val ENGLISH_OPTION = "test_language_option_en"
    }
}
