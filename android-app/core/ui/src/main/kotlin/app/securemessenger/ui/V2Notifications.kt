package app.securemessenger.ui

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Notifications about new messages (DEC-027, DEC-031). They say who wrote and how many unseen texts
 * there are, never what was written. Every notification is private with a public version that has
 * no name and no count, so a locked phone shows only "New message" unless the person chose to show
 * all notification content on the lock screen; the text exists in no version either way. Posted
 * only by the background sync while the app is not on screen, and only in debug builds, where v2
 * exists.
 */
internal object V2Notifications {
    const val CHANNEL_ID = "messages"
    private const val CHAT_NOTIFICATION_ID = 1
    private const val REQUEST_NOTIFICATION_ID = 2

    /** Whether Android lets this app post them now (the Android 13+ permission and the user's switch). */
    fun canPost(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || permissionGranted(context)
    }

    fun permissionGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * The channel; creating it again is harmless. Its lock-screen visibility is not ours to set:
     * Android leaves it to the person's own settings (a device test showed it stays "no override"),
     * so what a locked screen shows is decided by each notification's private visibility and its
     * public version, and by the person's lock-screen setting.
     */
    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_messages),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun post(context: Context, notices: List<MessageNotice>) {
        if (notices.isEmpty() || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val strings = strings(context)
        val manager = NotificationManagerCompat.from(context)
        notices.forEach { notice ->
            val text = noticeText(notice, strings)
            val (tag, id, chatId, count) = when (notice) {
                is MessageNotice.Chat -> Target(notice.contactId, CHAT_NOTIFICATION_ID, notice.contactId, notice.unseenCount)
                is MessageNotice.Request -> Target(notice.requestId, REQUEST_NOTIFICATION_ID, null, 0)
            }
            val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_message)
                .setContentTitle(strings.lockScreenTitle)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .build()
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_message)
                .setContentTitle(text.title)
                .setContentText(text.text)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setNumber(count)
                .setAutoCancel(true)
                .setContentIntent(openIntent(context, chatId))
                .build()
            try {
                manager.notify(tag, id, notification)
            } catch (_: SecurityException) {
                // The permission was withdrawn between the check and the post: nothing to show.
            }
        }
    }

    fun cancelChat(context: Context, contactId: String) =
        NotificationManagerCompat.from(context).cancel(contactId, CHAT_NOTIFICATION_ID)

    fun cancelRequest(context: Context, requestId: String) =
        NotificationManagerCompat.from(context).cancel(requestId, REQUEST_NOTIFICATION_ID)

    /** After "Delete local data": the notifications name contacts that no longer exist here. */
    fun cancelAll(context: Context) = NotificationManagerCompat.from(context).cancelAll()

    private data class Target(val tag: String, val id: Int, val chatId: String?, val count: Int)

    private fun strings(context: Context) = NoticeStrings(
        newMessages = { count ->
            if (count <= 1) {
                context.getString(R.string.notification_new_message)
            } else {
                context.resources.getQuantityString(R.plurals.notification_new_messages, count, count)
            }
        },
        requestTitle = context.getString(R.string.request_review_title),
        requestText = context.getString(R.string.notification_request_text),
        lockScreenTitle = context.getString(R.string.notification_new_message),
    )

    /**
     * Opens the app, and the chat when there is one. The intent carries only the local contact ID
     * (a random UUID that never leaves the phone). A test package has no launcher: no intent then.
     */
    private fun openIntent(context: Context, chatId: String?): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (chatId != null) launch.putExtra(V2OpenChat.EXTRA_CHAT_ID, chatId)
        return PendingIntent.getActivity(
            context,
            chatId?.hashCode() ?: 0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** Whether any screen of the app is visible: then the app shows new messages itself and posts nothing. */
internal object V2Foreground {
    @Volatile
    var visible: Boolean = false
}

/**
 * A chat to open, handed over by the activity when a notification was tapped. The chat screen opens
 * it once the contact is known, then clears it.
 */
object V2OpenChat {
    const val EXTRA_CHAT_ID = "app.securemessenger.ui.extra.CHAT_ID"

    private val requested = MutableStateFlow<String?>(null)
    internal val requests: StateFlow<String?> = requested.asStateFlow()

    /** Takes the chat to open out of [intent] (and removes it, so a recreated activity does not open it again). */
    fun take(intent: Intent?) {
        val chatId = intent?.getStringExtra(EXTRA_CHAT_ID) ?: return
        intent.removeExtra(EXTRA_CHAT_ID)
        if (isPlausibleLocalId(chatId)) requested.value = chatId
    }

    internal fun opened() {
        requested.value = null
    }
}

/** Whether the person said "Not now" to the notification prompt; asked again only from settings. */
internal object NotificationPrompt {
    private const val PREFERENCES = "secure_messenger_notifications"
    private const val DISMISSED = "prompt_dismissed"

    fun dismissed(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(DISMISSED, false)

    fun dismiss(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean(DISMISSED, true).apply()
    }
}
