package app.securemessenger.ui

import app.securemessenger.signalstore.V2Contact
import app.securemessenger.signalstore.V2ContactState
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DEC-031 (owner's rules from DEC-027): what a notification announces, the words it uses, and that
 * the notification code never even reads message texts or the name a request claims.
 */
class NotificationRulesTest {
    private val alice = contact("alice", "Alice")
    private val bob = contact("bob", "Bob")
    private val carol = contact("carol", "Carol")

    @Test
    fun `a contact whose unseen texts grew is announced with its whole unseen count`() {
        val before = UnseenSnapshot(mapOf("alice" to 1, "bob" to 2), emptySet())
        val after = UnseenSnapshot(mapOf("alice" to 3, "bob" to 2, "carol" to 1), emptySet())

        assertEquals(
            listOf(MessageNotice.Chat("alice", "Alice", 3), MessageNotice.Chat("carol", "Carol", 1)),
            messageNotices(before, after, listOf(alice, bob, carol)),
        )
    }

    @Test
    fun `nothing new, fewer unseen texts, or a blocked contact announce nothing`() {
        val blocked = contact("blocked", "Blocked", V2ContactState.KEY_CHANGED)
        val before = UnseenSnapshot(mapOf("alice" to 2, "bob" to 1), emptySet())
        val after = UnseenSnapshot(mapOf("alice" to 2, "bob" to 0, "blocked" to 5), emptySet())

        assertTrue(messageNotices(before, after, listOf(alice, bob, blocked)).isEmpty())
    }

    @Test
    fun `a new request or one with more texts is announced, and never by a name`() {
        val before = UnseenSnapshot(mapOf("r-old" to 1, "r-same" to 1), setOf("r-old", "r-same"))
        val after = UnseenSnapshot(mapOf("r-old" to 2, "r-same" to 1, "r-new" to 0), setOf("r-old", "r-same", "r-new"))

        assertEquals(
            listOf(MessageNotice.Request("r-new"), MessageNotice.Request("r-old")),
            messageNotices(before, after, emptyList()),
        )
    }

    @Test
    fun `a request accepted meanwhile is not announced again as a contact`() {
        // Accepting keeps the request's ID as the contact ID, and its unseen texts with it.
        val before = UnseenSnapshot(mapOf("dave" to 2), setOf("dave"))
        val after = UnseenSnapshot(mapOf("dave" to 2), emptySet())

        assertTrue(messageNotices(before, after, listOf(contact("dave", "Dave"))).isEmpty())
    }

    @Test
    fun `the words come from the notice alone`() {
        val strings = NoticeStrings(
            newMessages = { if (it <= 1) "New message" else "$it new messages" },
            requestTitle = "Contact request",
            requestText = "Compare the safety number",
            lockScreenTitle = "New message",
        )

        val one = noticeText(MessageNotice.Chat("alice", "Alice", 1), strings)
        val many = noticeText(MessageNotice.Chat("alice", "Alice", 4), strings)
        val request = noticeText(MessageNotice.Request("r"), strings)

        assertEquals("Alice" to "New message", one.title to one.text)
        assertEquals("4 new messages", many.text)
        assertEquals("Contact request" to "Compare the safety number", request.title to request.text)
    }

    @Test
    fun `only random local IDs are taken from an intent`() {
        assertTrue(isPlausibleLocalId(UUID.randomUUID().toString()))
        listOf(null, "", "../chat", "a b", "x".repeat(65), "чат", "id;drop").forEach { value ->
            assertFalse("$value", isPlausibleLocalId(value))
        }
    }

    @Test
    fun `the notification code never reads message texts or claimed names and stays private on the lock screen`() {
        val sources = listOf("V2Notifications.kt", "NotificationRules.kt", "NotificationUi.kt", "V2BackgroundSync.kt")
            .associateWith { File("src/main/kotlin/app/securemessenger/ui/$it").readText() }
        val forbidden = listOf(
            "MessagingStyle", "BigTextStyle", "InboxStyle", "setTicker", "RemoteInput", "lastText", "texts(",
            "textsMarkingRead", "heldTexts", "claimedDisplayName", "readBody", "chatSummaries", "android.util.Log", "println(",
        )
        sources.forEach { (name, source) ->
            forbidden.forEach { word -> assertFalse("$name uses $word", source.contains(word)) }
        }
        val poster = sources.getValue("V2Notifications.kt")
        // Every notification is private, with a public version for locked screens (the channel's own
        // lock-screen visibility is the person's to set, not the app's).
        assertEquals(1, Regex("VISIBILITY_PRIVATE").findAll(poster).count())
        assertFalse(poster.contains("lockscreenVisibility ="))
        assertTrue(poster.contains(".setPublicVersion(publicVersion)"))
        assertTrue(poster.contains(".setContentTitle(strings.lockScreenTitle)"))
    }

    private fun contact(id: String, name: String, state: V2ContactState = V2ContactState.ACTIVE) =
        V2Contact(id, name, ByteArray(33), "queue", "00", id, state, 1L)
}
