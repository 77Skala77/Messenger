package app.securemessenger.ui

import app.securemessenger.signalstore.V2Contact
import app.securemessenger.signalstore.V2ContactState
import app.securemessenger.signalstore.V2MessageStatus
import app.securemessenger.signalstore.V2StoredMessage
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Step 6e: what the conversation lists, and where a bubble puts its time (notes 2.2 and 2.3). */
class ConversationModelTest {
    private fun at(day: Int, hour: Int, minute: Int) =
        ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun message(id: String, outgoing: Boolean, at: Long) =
        V2StoredMessage(id, "contact", outgoing, "00", "text $id", if (outgoing) V2MessageStatus.SENT else V2MessageStatus.RECEIVED, at)

    @Test
    fun `a date pill comes before the first message of every day and series are marked`() {
        val entries = conversationEntries(
            listOf(
                message("a", outgoing = false, at = at(2, 21, 40)),
                message("b", outgoing = false, at = at(2, 21, 42)),
                message("c", outgoing = true, at = at(4, 9, 0)),
            ),
            ZoneOffset.UTC,
        )

        assertEquals(listOf("Day", "Bubble", "Bubble", "Day", "Bubble"), entries.map { it::class.simpleName })
        val first = (entries[1] as ConversationEntry.Bubble).position
        val second = (entries[2] as ConversationEntry.Bubble).position
        assertTrue(first.nearBottom && !first.tail)
        assertTrue(second.nearTop && second.tail)
        assertEquals(at(4, 9, 0), (entries[3] as ConversationEntry.Day).timeMillis)
        assertEquals(entries.size, entries.map { it.key }.toSet().size)
    }

    @Test
    fun `a safety number is read in groups of five, three to a line`() {
        val number = (1..60).joinToString("") { (it % 10).toString() }

        val lines = safetyNumberLines(number)

        assertEquals(4, lines.size)
        assertEquals("12345 67890 12345", lines[0])
        assertEquals(60, lines.joinToString("").count { it.isDigit() })
        // Anything but digits (spaces a person typed, dashes) never changes the digits.
        assertEquals(lines, safetyNumberLines(number.chunked(5).joinToString("-")))
        assertTrue(safetyNumberLines("").isEmpty())
    }

    @Test
    fun `only a confirmed contact that is not blocked counts as checked`() {
        fun contact(state: V2ContactState, verifiedAt: Long?) =
            V2Contact("c", "C", ByteArray(33), "queue", "00", "01", state, 1L, verifiedAt)

        assertTrue(contact(V2ContactState.ACTIVE, 5L).safetyNumberChecked)
        assertTrue(contact(V2ContactState.AWAITING_ACCEPTANCE, 5L).safetyNumberChecked)
        assertFalse(contact(V2ContactState.ACTIVE, null).safetyNumberChecked)
        assertFalse(contact(V2ContactState.KEY_CHANGED, 5L).safetyNumberChecked)
    }

    @Test
    fun `an empty conversation has no entries`() {
        assertTrue(conversationEntries(emptyList(), ZoneOffset.UTC).isEmpty())
    }

    @Test
    fun `the time shares the last line when both fit and the bubble is as wide as that line`() {
        val plan = planBubble(textWidth = 400, textHeight = 60, lastLineWidth = 120, metaWidth = 80, metaHeight = 28, maxWidth = 500, gap = 16)

        assertFalse(plan.metaOnOwnLine)
        // The text is the wider part: the time sits at its bottom end.
        assertEquals(400, plan.width)
        assertEquals(60, plan.height)
        assertEquals(320, plan.metaX)
        assertEquals(32, plan.metaY)
    }

    @Test
    fun `a short text grows the bubble to hold the time on its line`() {
        val plan = planBubble(textWidth = 100, textHeight = 40, lastLineWidth = 100, metaWidth = 80, metaHeight = 28, maxWidth = 500, gap = 16)

        assertFalse(plan.metaOnOwnLine)
        assertEquals(196, plan.width)
        assertEquals(116, plan.metaX)
    }

    @Test
    fun `the time goes under the text when the last line leaves no room`() {
        val plan = planBubble(textWidth = 480, textHeight = 60, lastLineWidth = 450, metaWidth = 80, metaHeight = 28, maxWidth = 500, gap = 16)

        assertTrue(plan.metaOnOwnLine)
        assertEquals(480, plan.width)
        assertEquals(88, plan.height)
        assertEquals(400, plan.metaX)
        assertEquals(60, plan.metaY)
    }

    @Test
    fun `an exact fit still shares the line`() {
        val plan = planBubble(textWidth = 404, textHeight = 40, lastLineWidth = 404, metaWidth = 80, metaHeight = 28, maxWidth = 500, gap = 16)

        assertFalse(plan.metaOnOwnLine)
        assertEquals(500, plan.width)
    }

    @Test
    fun `a time block taller than the text never goes above the bubble`() {
        val plan = planBubble(textWidth = 50, textHeight = 20, lastLineWidth = 50, metaWidth = 80, metaHeight = 28, maxWidth = 500, gap = 16)

        assertEquals(28, plan.height)
        assertEquals(0, plan.metaY)
    }
}
