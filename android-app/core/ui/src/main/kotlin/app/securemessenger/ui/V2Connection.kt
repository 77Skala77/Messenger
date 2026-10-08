package app.securemessenger.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.securemessenger.storage.SecureMessengerDatabase
import app.securemessenger.storage.VaultKeyUnavailableException
import app.securemessenger.storage.VaultRecordRejectedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal const val CONNECTION_SWITCH_TAG = "settings_background_connection"
internal const val BATTERY_EXEMPTION_TAG = "settings_battery_exemption"

/**
 * The background connection (owner decision, DEC-034): while the app is closed, a foreground service
 * fetches every [INTERVAL_MILLIS], so new messages are announced within half a minute instead of
 * whenever Android runs the periodic work. On by default where chats exist (debug builds); a switch
 * in the settings turns it off. Android shows a permanent notification for it, and it costs battery.
 * It runs the same sync and the same notification rules as the periodic work (DEC-031).
 */
internal object BackgroundConnection {
    private const val PREFERENCES = "secure_messenger_connection"
    private const val ENABLED = "background_connection"
    const val INTERVAL_MILLIS = 30_000L

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(ENABLED, true)

    fun set(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean(ENABLED, enabled).apply()
        if (enabled) startIfWanted(context) else stop(context)
    }

    /**
     * Starts the service where chats exist and the person wants it. Android lets an app start it
     * only while the app is in front, after the phone started, or after an update; elsewhere the
     * attempt is refused and the periodic work stays the fallback.
     */
    fun startIfWanted(context: Context) {
        if (!V2Availability.isAvailable(context) || !enabled(context)) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, V2ConnectionService::class.java))
        } catch (_: IllegalStateException) {
            // Android 12+ refused a start from the background (ForegroundServiceStartNotAllowedException).
        } catch (_: SecurityException) {
            // A build without the service in its manifest (release) never gets here; nothing to start.
        }
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, V2ConnectionService::class.java))
    }

    /** Whether Android lets the app run without putting it to sleep (the person's "allow" in the system dialog). */
    fun ignoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** Opens Android's own question; the person answers it there. */
    fun askForBatteryExemption(context: Context) {
        val ask = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName))
        try {
            context.startActivity(ask)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}

/**
 * The foreground service of [BackgroundConnection]. Each round builds the pipeline afresh (so a wipe
 * of the local data never leaves a stale one behind) and runs [V2BackgroundNotices.syncAndNotify],
 * which announces only while no screen of the app is visible; while one is, the screen fetches by
 * itself and this round is skipped. A lost key, unreadable data or a lost inbox secret stops it for
 * good, like the periodic work; the app explains why when it is opened. It logs nothing.
 *
 * A partial wake lock keeps the processor awake between rounds, or the phone would sleep through
 * them; it is renewed each round with a timeout, so it lapses by itself if the loop ever stops.
 */
class V2ConnectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android expects the notification right after the start, whatever happens next.
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (!V2Availability.isAvailable(this) || !BackgroundConnection.enabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (loop?.isActive != true) loop = scope.launch { connect() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    private suspend fun connect() {
        val database = SecureMessengerDatabase.create(applicationContext)
        val lock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { setReferenceCounted(false) }
        wakeLock = lock
        try {
            while (currentCoroutineContext().isActive) {
                lock.acquire(BackgroundConnection.INTERVAL_MILLIS * 2)
                if (!V2Foreground.visible && !syncOnce(database)) {
                    stopSelf()
                    return
                }
                delay(BackgroundConnection.INTERVAL_MILLIS)
            }
        } finally {
            database.close()
        }
    }

    /** One round; false when the service must stop for good. */
    private suspend fun syncOnce(database: SecureMessengerDatabase): Boolean =
        try {
            // No profile: the local data was deleted. The app starts the service again later.
            val service = V2Pipeline.createIfProfileExists(applicationContext, database)
            if (service != null) V2BackgroundNotices.syncAndNotify(applicationContext, service)
            service != null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: VaultKeyUnavailableException) {
            false
        } catch (_: VaultRecordRejectedException) {
            false
        } catch (_: ReceiveQueueLostException) {
            false
        } catch (_: Exception) {
            // The relay may be out of reach, or one envelope failed: the next round tries again.
            true
        }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.connection_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.connection_channel_description)
                setShowBadge(false)
            },
        )
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            PendingIntent.getActivity(this, OPEN_REQUEST, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle(getString(R.string.connection_notification_title))
            .setContentText(getString(R.string.connection_notification_text))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "connection"
        const val NOTIFICATION_ID = 30
        const val OPEN_REQUEST = 30
        const val WAKE_LOCK_TAG = "SecureMessenger:connection"
    }
}

/** Starts the background connection again after the phone started, or after the app was updated. */
class V2ConnectionStarter : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            BackgroundConnection.startIfWanted(context)
        }
    }
}

/**
 * The switch for the background connection and the way to Android's battery question, as settings
 * sections (DEC-038): the switch with its explanation, then whether Android lets the app run in the
 * background, with the button to ask, and the note about phone makers.
 */
@Composable
internal fun BackgroundConnectionSettings() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(BackgroundConnection.enabled(context)) }
    var exempt by remember { mutableStateOf(BackgroundConnection.ignoringBatteryOptimizations(context)) }
    // The person answers Android's question outside the app.
    LifecycleResumeEffect(Unit) {
        exempt = BackgroundConnection.ignoringBatteryOptimizations(context)
        onPauseOrDispose {}
    }
    SettingsSection {
        SettingsSwitchRow(
            title = stringResource(R.string.connection_setting_title),
            hint = stringResource(R.string.connection_setting_hint),
            checked = enabled,
            onCheckedChange = {
                enabled = it
                BackgroundConnection.set(context, it)
            },
            modifier = Modifier.testTag(CONNECTION_SWITCH_TAG),
        )
    }
    if (enabled) {
        SettingsGap()
        SettingsSection {
            SettingsText(stringResource(if (exempt) R.string.battery_allowed else R.string.battery_not_allowed))
            if (!exempt) {
                SettingsDivider()
                SettingsActionRow(
                    stringResource(R.string.battery_allow),
                    onClick = { BackgroundConnection.askForBatteryExemption(context) },
                    modifier = Modifier.testTag(BATTERY_EXEMPTION_TAG),
                )
            }
        }
        SettingsInfo(stringResource(R.string.battery_maker_hint))
    }
}
