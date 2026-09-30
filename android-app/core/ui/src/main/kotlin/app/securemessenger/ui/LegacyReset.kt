package app.securemessenger.ui

import android.content.Context
import androidx.work.WorkManager
import java.security.KeyStore

/**
 * Explicit one-time resets outside the database, run from `Application.onCreate` before any screen
 * or worker. Nothing is ever migrated: an old conversation can never be read or accepted as a new
 * one.
 *
 * 1. Legacy prototype (DEC-021): the legacy P-256 identity key in Android Keystore, the legacy nonce
 *    counters, device ID, queue ID and diagnostics preferences, and the legacy WorkManager jobs. The
 *    database side is `MIGRATION_7_8`.
 * 2. Relay queues v3 (DEC-024): the old plain v2 queue ID. The database side is `MIGRATION_8_9`,
 *    which deletes the v2 test conversations.
 *
 * Each stage runs once per install and remembers whether old data existed, so the UI can show a
 * one-time notice. The vault key is kept: the profile and new records use it.
 */
object LegacyReset {
    private const val STATE = "secure_messenger_legacy_reset"
    private const val DONE = "done_v1"
    private const val NOTICE_PENDING = "notice_pending"
    private const val DONE_RELAY_V3 = "done_relay_v3"
    private const val RELAY_NOTICE_PENDING = "relay_notice_pending"
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
        if (!state.getBoolean(DONE, false)) resetLegacyPrototype(application)
        if (!state.getBoolean(DONE_RELAY_V3, false)) resetRelayQueueV2(application)
    }

    private fun resetLegacyPrototype(application: Context) {
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

        application.getSharedPreferences(STATE, Context.MODE_PRIVATE).edit()
            .putBoolean(DONE, true)
            .putBoolean(NOTICE_PENDING, hadLegacyKey || hadLegacyPreferences)
            .commit()
    }

    /** A plain pre-v3 queue ID means v2 conversations existed; `MIGRATION_8_9` deletes them. */
    private fun resetRelayQueueV2(application: Context) {
        val queue = application.getSharedPreferences(V2QueueCredentialStore.PREFERENCES, Context.MODE_PRIVATE)
        val hadOldQueue = queue.contains(V2QueueCredentialStore.OLD_QUEUE_ID)
        queue.edit().remove(V2QueueCredentialStore.OLD_QUEUE_ID).commit()
        val state = application.getSharedPreferences(STATE, Context.MODE_PRIVATE)
        // One notice is enough when the legacy reset already explained a reset.
        val notice = hadOldQueue && !state.getBoolean(NOTICE_PENDING, false)
        state.edit().putBoolean(DONE_RELAY_V3, true).putBoolean(RELAY_NOTICE_PENDING, notice).commit()
    }

    fun isNoticePending(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(STATE, Context.MODE_PRIVATE).getBoolean(NOTICE_PENDING, false)

    fun dismissNotice(context: Context) {
        context.applicationContext.getSharedPreferences(STATE, Context.MODE_PRIVATE)
            .edit().putBoolean(NOTICE_PENDING, false).apply()
    }

    fun isRelayNoticePending(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(STATE, Context.MODE_PRIVATE).getBoolean(RELAY_NOTICE_PENDING, false)

    fun dismissRelayNotice(context: Context) {
        context.applicationContext.getSharedPreferences(STATE, Context.MODE_PRIVATE)
            .edit().putBoolean(RELAY_NOTICE_PENDING, false).apply()
    }
}
