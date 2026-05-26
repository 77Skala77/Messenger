package app.securemessenger.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

enum class AppLanguage(val languageTag: String) {
    RUSSIAN("ru"),
    ENGLISH("en"),
}

object LanguageController {
    private const val PREFERENCES = "secure_messenger_language"
    private const val SELECTED_LANGUAGE = "selected_language"

    fun applySavedOrRussianDefault(context: Context) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val tag = preferences.getString(SELECTED_LANGUAGE, null) ?: AppLanguage.RUSSIAN.languageTag.also {
            preferences.edit().putString(SELECTED_LANGUAGE, it).apply()
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    fun select(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(SELECTED_LANGUAGE, language.languageTag)
            .apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.languageTag))
    }

    fun selected(context: Context): AppLanguage {
        val tag = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(SELECTED_LANGUAGE, AppLanguage.RUSSIAN.languageTag)
        return if (tag == AppLanguage.ENGLISH.languageTag) AppLanguage.ENGLISH else AppLanguage.RUSSIAN
    }
}
