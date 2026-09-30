package app.securemessenger

import android.app.Application
import app.securemessenger.ui.LanguageController
import app.securemessenger.ui.LegacyReset

class SecureMessengerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LanguageController.applySavedOrRussianDefault(this)
        // DEC-021: once per install, remove the legacy prototype's keys and preferences before any
        // screen or worker runs. The database side is MIGRATION_7_8.
        LegacyReset.runOnce(this)
    }
}
