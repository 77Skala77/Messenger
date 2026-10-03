package app.securemessenger.ui

import app.securemessenger.signalstore.V2Contact
import app.securemessenger.signalstore.V2ContactState
import app.securemessenger.signalstore.V2MessageStatus
import app.securemessenger.signalstore.V2StoredMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Audit SESS-01: a refresh never shows one contact's messages or safety number under another. */
class ConversationV2StateTest {
    private val alice = contact("alice")
    private val bob = contact("bob")

    @Test
    fun `a refresh read for the selected contact is shown`() {
        val state = ConversationV2UiState(selectedContactId = "alice")

        val refreshed = state.withSnapshot(snapshotFor("alice"))

        assertEquals("alice", refreshed.selectedContactId)
        assertEquals(listOf("text of alice"), refreshed.messages.map { it.text })
        assertEquals("number of alice", refreshed.safetyNumber)
        assertEquals(listOf("alice", "bob"), refreshed.contacts.map { it.contactId })
    }

    @Test
    fun `a refresh read before the selection changed keeps the new contact apart`() {
        // The user opened Bob while a refresh was still reading Alice's chat.
        val state = ConversationV2UiState(selectedContactId = "bob")

        val refreshed = state.withSnapshot(snapshotFor("alice"))

        assertEquals("bob", refreshed.selectedContactId)
        assertTrue(refreshed.messages.isEmpty())
        assertNull(refreshed.safetyNumber)
        // Everything that does not belong to one contact is still updated.
        assertEquals(listOf("alice", "bob"), refreshed.contacts.map { it.contactId })
        assertEquals("invitation", refreshed.invitationCode)
    }

    @Test
    fun `a stale refresh does not replace what a newer one showed for the new contact`() {
        val state = ConversationV2UiState(selectedContactId = "bob").withSnapshot(snapshotFor("bob"))

        val refreshed = state.withSnapshot(snapshotFor("alice"))

        assertEquals(listOf("text of bob"), refreshed.messages.map { it.text })
        assertEquals("number of bob", refreshed.safetyNumber)
    }

    @Test
    fun `leaving a chat while a refresh reads it shows nothing of it`() {
        val refreshed = ConversationV2UiState(selectedContactId = null).withSnapshot(snapshotFor("alice"))

        assertTrue(refreshed.messages.isEmpty())
        assertNull(refreshed.safetyNumber)
    }

    private fun snapshotFor(contactId: String) = V2Snapshot(
        contacts = listOf(alice, bob),
        requests = emptyList(),
        invitationCode = "invitation",
        selectedContactId = contactId,
        messages = listOf(
            V2StoredMessage("m-$contactId", contactId, false, "00", "text of $contactId", V2MessageStatus.RECEIVED, 1L),
        ),
        safetyNumber = "number of $contactId",
    )

    private fun contact(contactId: String) =
        V2Contact(contactId, contactId, ByteArray(33), "queue", "00", contactId, V2ContactState.ACTIVE, 1L)
}
