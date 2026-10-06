package app.securemessenger.ui

import app.securemessenger.signalstore.V2ChatSummary
import app.securemessenger.signalstore.V2Contact
import app.securemessenger.signalstore.V2ContactState
import app.securemessenger.signalstore.V2MessageStatus
import app.securemessenger.signalstore.V2StoredMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Step 6d: the order of the chat list, what the second line says, and the tick beside the time. */
class ChatListModelTest {
    @Test
    fun `the chat with the newest text comes first, in either direction`() {
        val old = chat("old", lastAt = 1_000L)
        val newIncoming = chat("new-in", lastAt = 5_000L)
        val newOutgoing = chat("new-out", lastAt = 9_000L, outgoing = true)

        assertEquals(listOf("new-out", "new-in", "old"), ids(chatListOrder(listOf(old, newIncoming, newOutgoing))))
    }

    @Test
    fun `a chat without any text counts from the day the contact was added`() {
        val empty = chat("empty", lastAt = null, addedAt = 6_000L)
        val older = chat("older", lastAt = 3_000L)
        val newer = chat("newer", lastAt = 8_000L)

        assertEquals(listOf("newer", "empty", "older"), ids(chatListOrder(listOf(older, empty, newer))))
    }

    @Test
    fun `equal times keep a fixed order so the list never jumps`() {
        val first = chat("a", lastAt = 4_000L)
        val second = chat("b", lastAt = 4_000L)

        assertEquals(listOf("a", "b"), ids(chatListOrder(listOf(second, first))))
        assertEquals(listOf("a", "b"), ids(chatListOrder(listOf(first, second))))
    }

    @Test
    fun `unseen texts do not move a chat, only the newest activity does`() {
        val unreadButOld = chat("unread", lastAt = 1_000L, unread = 40)
        val readButNew = chat("read", lastAt = 2_000L, unread = 0)

        assertEquals(listOf("read", "unread"), ids(chatListOrder(listOf(unreadButOld, readButNew))))
    }

    @Test
    fun `the preview is the newest text on one line`() {
        val preview = chatPreview(chat("a", lastAt = 1L, text = "  first line\n\nsecond\t line  "))

        assertEquals(ChatPreview.LastText("first line second line", outgoing = false), preview)
    }

    @Test
    fun `a very long text is cut before it is looked at`() {
        val preview = chatPreview(chat("a", lastAt = 1L, text = "x".repeat(5_000))) as ChatPreview.LastText

        assertEquals(200, preview.text.length)
    }

    @Test
    fun `a contact waiting for acceptance says so until there is a text`() {
        val waiting = chat("w", lastAt = null, state = V2ContactState.AWAITING_ACCEPTANCE)
        val waitingWithText = chat("w", lastAt = 1L, state = V2ContactState.AWAITING_ACCEPTANCE, outgoing = true)

        assertEquals(ChatPreview.ContactState(V2ContactState.AWAITING_ACCEPTANCE), chatPreview(waiting))
        assertEquals(ChatPreview.LastText("text of w", outgoing = true), chatPreview(waitingWithText))
    }

    @Test
    fun `an active contact without texts says there are none`() {
        assertEquals(ChatPreview.NoMessages, chatPreview(chat("a", lastAt = null)))
    }

    @Test
    fun `a blocked contact always says so, even with texts on file`() {
        val blocked = chat("k", lastAt = 1L, state = V2ContactState.KEY_CHANGED)

        assertEquals(ChatPreview.ContactState(V2ContactState.KEY_CHANGED), chatPreview(blocked))
    }

    @Test
    fun `only our own newest text gets a tick, and never a read one`() {
        fun tick(status: V2MessageStatus, outgoing: Boolean) = chatTick(chat("a", lastAt = 1L, outgoing = outgoing, status = status))

        assertEquals(TickKind.CLOCK, tick(V2MessageStatus.PENDING, outgoing = true))
        assertEquals(TickKind.CLOCK, tick(V2MessageStatus.FAILED, outgoing = true))
        assertEquals(TickKind.SENT, tick(V2MessageStatus.SENT, outgoing = true))
        assertEquals(TickKind.DELIVERED, tick(V2MessageStatus.DELIVERED, outgoing = true))
        assertNull(tick(V2MessageStatus.RECEIVED, outgoing = false))
        assertNull(chatTick(chat("a", lastAt = null)))
    }

    private fun ids(chats: List<V2ChatSummary>) = chats.map { it.contact.contactId }

    /** DEC-035: pinned chats stay on top, the one pinned last first, whatever their activity. */
    @Test
    fun `pinned chats come first, the latest pin on top, then the rest by activity`() {
        val busy = chat("busy", lastAt = 9_000L)
        val pinnedEarly = chat("pinned-early", lastAt = 1_000L, pinnedAt = 100L)
        val pinnedLate = chat("pinned-late", lastAt = 2_000L, pinnedAt = 200L)
        val quiet = chat("quiet", lastAt = 3_000L)

        assertEquals(
            listOf("pinned-late", "pinned-early", "busy", "quiet"),
            ids(chatListOrder(listOf(quiet, pinnedEarly, busy, pinnedLate))),
        )
    }

    @Test
    fun `search finds a chat by the contact name or the newest text, ignoring case and outer spaces`() {
        val alice = chat("Alice", lastAt = 1L, text = "see you at the Station")
        val bob = chat("Bob", lastAt = 2L, text = "lunch?")
        val chats = listOf(alice, bob)

        assertEquals(listOf("Alice"), ids(chatListSearch(chats, "  ali ")))
        assertEquals(listOf("Alice"), ids(chatListSearch(chats, "STATION")))
        assertEquals(listOf("Bob"), ids(chatListSearch(chats, "lunch")))
        assertEquals(emptyList<String>(), ids(chatListSearch(chats, "zzz")))
    }

    @Test
    fun `an empty search keeps every chat in its order`() {
        val chats = listOf(chat("a", lastAt = 1L), chat("b", lastAt = 2L))

        assertEquals(listOf("a", "b"), ids(chatListSearch(chats, "")))
        assertEquals(listOf("a", "b"), ids(chatListSearch(chats, "   ")))
    }

    private fun chat(
        id: String,
        lastAt: Long?,
        addedAt: Long = 0L,
        unread: Int = 0,
        text: String = "text of $id",
        outgoing: Boolean = false,
        status: V2MessageStatus = if (outgoing) V2MessageStatus.SENT else V2MessageStatus.RECEIVED,
        state: V2ContactState = V2ContactState.ACTIVE,
        pinnedAt: Long? = null,
    ): V2ChatSummary {
        val contact = V2Contact(id, id, ByteArray(33), "queue", "00", id, state, addedAt, pinnedAtEpochMillis = pinnedAt)
        val last = lastAt?.let { V2StoredMessage("m-$id", id, outgoing, "00", text, status, it) }
        return V2ChatSummary(contact, last, unread)
    }
}
