package app.securemessenger.ui

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

/**
 * DEC-031 end to end on a real phone: two devices over real Room databases and real libsignal; the
 * background sync's notifications are read back from Android. No notification, and no public
 * version of one, ever carries message text or the name a request claims; a locked screen gets
 * only "New message"; nothing is posted while the app is on screen.
 */
@RunWith(AndroidJUnit4::class)
class NotificationPrivacyInstrumentedTest {
    @get:Rule
    val testName = TestName()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val relay = MemoryRelay()
    private lateinit var alice: RoomV2TestDevice
    private lateinit var bob: RoomV2TestDevice
    private val extraDevices = mutableListOf<RoomV2TestDevice>()

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        V2Foreground.visible = false
        V2Notifications.cancelAll(context)
        alice = RoomV2TestDevice(context, "Alice", relay, "notify-alice-${testName.methodName}.db")
        bob = RoomV2TestDevice(context, "Bob", relay, "notify-bob-${testName.methodName}.db")
    }

    @After
    fun cleanUp() {
        V2Notifications.cancelAll(context)
        V2Foreground.visible = false
        alice.delete()
        bob.delete()
        extraDevices.forEach(RoomV2TestDevice::delete)
    }

    @Test
    fun aNewTextIsAnnouncedByTheLocalNameAndNeverByItsText() = runBlocking {
        val (aliceOnBob, _) = pair(localNameOfBob = "Bobby")
        bob.service.sendText(aliceOnBob, SECRET_TEXT)

        V2BackgroundNotices.syncAndNotify(context, alice.service)

        val notification = awaitPosted().single()
        assertEquals("Bobby", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(context.getString(R.string.notification_new_message), notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertNoSecret(notification)

        // A locked screen: "New message", without the name.
        val locked = checkNotNull(notification.publicVersion)
        assertEquals(context.getString(R.string.notification_new_message), locked.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(everyText(locked).none { it.contains("Bobby") })
        // The channel's own lock-screen visibility is left to the person (Android ignores an app's
        // value: both phones answered "no override"); the app must at least never make it public.
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(V2Notifications.CHANNEL_ID)
        assertTrue(channel.lockscreenVisibility != Notification.VISIBILITY_PUBLIC)
    }

    @Test
    fun moreTextsUpdateTheCountAndNothingNewPostsNothing() = runBlocking {
        val (aliceOnBob, _) = pair(localNameOfBob = "Bob")
        bob.service.sendText(aliceOnBob, SECRET_TEXT)
        bob.service.sendText(aliceOnBob, SECRET_TEXT + " 2")
        V2BackgroundNotices.syncAndNotify(context, alice.service)
        assertEquals(
            context.resources.getQuantityString(R.plurals.notification_new_messages, 2, 2),
            awaitPosted().single().extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )

        V2Notifications.cancelAll(context)
        V2BackgroundNotices.syncAndNotify(context, alice.service)
        assertTrue("an empty sync announces nothing", settledOurs().isEmpty())
    }

    @Test
    fun aRequestIsAnnouncedWithoutTheNameItClaims() = runBlocking {
        val impostor = RoomV2TestDevice(context, CLAIMED_NAME, relay, "notify-impostor.db").also { extraDevices += it }
        impostor.service.acceptInvitation(alice.service.createInvitation(), "Alice")
        impostor.service.sendText(impostor.service.contacts().single().contactId, SECRET_TEXT)

        V2BackgroundNotices.syncAndNotify(context, alice.service)

        val notification = awaitPosted().single()
        assertEquals(context.getString(R.string.request_review_title), notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(everyText(notification).none { it.contains(CLAIMED_NAME) })
        assertNoSecret(notification)
    }

    @Test
    fun nothingIsPostedWhileTheAppIsOnScreen() = runBlocking {
        val (aliceOnBob, _) = pair(localNameOfBob = "Bob")
        bob.service.sendText(aliceOnBob, SECRET_TEXT)
        V2Foreground.visible = true

        V2BackgroundNotices.syncAndNotify(context, alice.service)

        assertTrue(settledOurs().isEmpty())
        // The text arrived all the same; the app shows it.
        assertEquals(1, alice.service.unreadCounts().values.sum())
    }

    /** Android lists a posted notification a moment later: wait for it, up to a few seconds. */
    private fun awaitPosted(): List<Notification> {
        val deadline = System.currentTimeMillis() + POST_WAIT_MILLIS
        while (ours().isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MILLIS)
        return ours()
    }

    /** For "nothing is posted": give a wrongly posted notification time to show up before looking. */
    private fun settledOurs(): List<Notification> {
        Thread.sleep(SETTLE_MILLIS)
        return ours()
    }

    private fun ours(): List<Notification> =
        context.getSystemService(NotificationManager::class.java).activeNotifications
            .map { it.notification }
            .filter { it.channelId == V2Notifications.CHANNEL_ID }

    private fun assertNoSecret(notification: Notification) {
        val texts = everyText(notification) + notification.publicVersion?.let(::everyText).orEmpty()
        assertTrue(texts.none { it.contains(SECRET_TEXT) })
    }

    /** Every piece of text a notification carries: ticker and every extra, whatever its key. */
    private fun everyText(notification: Notification): List<String> = buildList {
        notification.tickerText?.let { add(it.toString()) }
        addAll(bundleTexts(notification.extras))
    }

    @Suppress("DEPRECATION")
    private fun bundleTexts(bundle: Bundle): List<String> = bundle.keySet().flatMap { key ->
        when (val value = bundle.get(key)) {
            null -> emptyList()
            is Bundle -> bundleTexts(value)
            is Array<*> -> value.map { it.toString() }
            else -> listOf(value.toString())
        }
    }

    private suspend fun pair(localNameOfBob: String): Pair<String, String> {
        val aliceOnBob = bob.service.acceptInvitation(alice.service.createInvitation(), "Alice")
        alice.service.synchronize()
        val bobOnAlice = alice.service.requests().single().requestId
        alice.service.acceptRequest(bobOnAlice, localNameOfBob)
        bob.service.synchronize()
        return aliceOnBob to bobOnAlice
    }

    private companion object {
        const val SECRET_TEXT = "meet at the old bridge at nine"
        const val CLAIMED_NAME = "Bank support"
        const val POST_WAIT_MILLIS = 5_000L
        const val POLL_MILLIS = 100L
        const val SETTLE_MILLIS = 1_500L
    }
}
