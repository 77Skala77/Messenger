package app.securemessenger.ui

import android.content.Context
import androidx.work.WorkManager
import java.security.KeyStore

/**
 * The explicit, one-time legacy reset (DEC-021), for everything outside the database: the legacy
 * P-256 identity key in Android Keystore, the legacy nonce counters, device ID, queue ID and
 * diagnostics preferences, and the legacy WorkManager jobs. The database side is `MIGRATION_7_8`.
 * Nothing is migrated: a legacy conversation can never be read or accepted as a v2 conversation.
 *
 * Runs once per install; afterwards [isNoticePending] tells the UI to show a one-time notice if
 * legacy data actually existed. The vault key is kept: v2 records and the profile use it.
 */
object LegacyReset {
    private const val STATE = "secure_messenger_legacy_reset"
    private const val DONE = "done_v1"
    private const val NOTICE_PENDING = "notice_pending"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    /** Keystore alias of the removed legacy P-256 device identity. */
    internal const val LEGACY_IDENTITY_ALIAS = "secure_messenger_device_ecdh_v1"

    /** Preference files of the removed legacy path; their presence means legacy data existed. */
    internal val LEGACY_PREFERENCES = listOf(
        "secure_messenger_identity",
        "secure_messenger_nonce_state",
        "secure_messenger_queue",
        "secure_messenger_safe_diagnostics",
    )

    /** Preference files of intermediate development builds, removed without a notice. */
    private val OBSOLETE_PREFERENCES = listOf("secure_messenger_v2_qa")
    private val LEGACY_WORK = listOf("relay-periodic-sync", "relay-retry-sync", "relay-manual-sync")

    @Synchronized
    fun runOnce(context: Context) {
        val application = context.applicationContext
        val state = application.getSharedPreferences(STATE, Context.MODE_PRIVATE)
        if (state.getBoolean(DONE, false)) return

        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val hadLegacyKey = keyStore.containsAlias(LEGACY_IDENTITY_ALIAS)
        val hadLegacyPreferences = LEGACY_PREFERENCES.any { name ->
            application.getSharedPreferences(name, Context.MODE_PRIVATE).all.isNotEmpty()
        }

        if (hadLegacyKey) keyStore.deleteEntry(LEGACY_IDENTITY_ALIAS)
        (LEGACY_PREFERENCES + OBSOLETE_PREFERENCES).forEach { name ->
            application.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            application.deleteSharedPreferences(name)
        }
        val workManager = WorkManager.getInstance(application)
        LEGACY_WORK.forEach(workManager::cancelUniqueWork)

        state.edit()
            .putBoolean(DONE, true)
            .putBoolean(NOTICE_PENDING, hadLegacyKey || hadLegacyPreferences)
            .commit()
    }

    fun isNoticePending(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(STATE, Context.MODE_PRIVATE).getBoolean(NOTICE_PENDING, false)

    fun dismissNotice(context: Context) {
        context.applicationContext.getSharedPreferences(STATE, Context.MODE_PRIVATE)
            .edit().putBoolean(NOTICE_PENDING, false).apply()
    }
}
