package app.securemessenger

import android.app.Application
import app.securemessenger.ui.LanguageController

class SecureMessengerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LanguageController.applySavedOrRussianDefault(this)
    }
}
