package app.securemessenger.ui

import app.securemessenger.signalstore.V2MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The small pure rules behind the badge, the ticks, the bubble corners, the theme choice and the wallpaper. */
class MessengerPiecesTest {
    @Test
    fun `the badge text is empty for zero and caps at 999 plus`() {
        assertEquals("", formatBadgeCount(0))
        assertEquals("", formatBadgeCount(-3))
        assertEquals("1", formatBadgeCount(1))
        assertEquals("999", formatBadgeCount(999))
        assertEquals("999+", formatBadgeCount(1000))
        assertEquals("999+", formatBadgeCount(Int.MAX_VALUE))
    }

    @Test
    fun `ticks follow the message status and there is no read state`() {
        assertEquals(TickKind.CLOCK, tickKindFor(V2MessageStatus.PENDING))
        // A failed send is retried, so it still waits.
        assertEquals(TickKind.CLOCK, tickKindFor(V2MessageStatus.FAILED))
        assertEquals(TickKind.SENT, tickKindFor(V2MessageStatus.SENT))
        assertEquals(TickKind.DELIVERED, tickKindFor(V2MessageStatus.DELIVERED))
        assertNull(tickKindFor(V2MessageStatus.RECEIVED))
        assertEquals(3, TickKind.entries.size)
    }

    @Test
    fun `an outgoing bubble with a tail has the tail on the right and tight corners next to neighbours`() {
        val last = bubbleGeometry(outgoing = true, tail = true, nearTop = false, nearBottom = false)
        assertEquals(17f, last.topLeft); assertEquals(17f, last.topRight)
        assertEquals(0f, last.bottomRight); assertEquals(17f, last.bottomLeft)
        assertTrue(last.tail)

        val middle = bubbleGeometry(outgoing = true, tail = false, nearTop = true, nearBottom = true)
        assertEquals(6f, middle.topRight); assertEquals(6f, middle.bottomRight)
        assertEquals(17f, middle.topLeft); assertEquals(17f, middle.bottomLeft)
        assertTrue(!middle.tail)
    }

    @Test
    fun `an incoming bubble mirrors the outgoing one`() {
        val last = bubbleGeometry(outgoing = false, tail = true, nearTop = true, nearBottom = false)
        assertEquals(6f, last.topLeft); assertEquals(17f, last.topRight)
        assertEquals(17f, last.bottomRight); assertEquals(0f, last.bottomLeft)

        val first = bubbleGeometry(outgoing = false, tail = false, nearTop = false, nearBottom = true)
        assertEquals(17f, first.topLeft); assertEquals(6f, first.bottomLeft)
        assertEquals(17f, first.topRight); assertEquals(17f, first.bottomRight)
    }

    @Test
    fun `the tail replaces the corner even if the bubble is also marked as touching the next one`() {
        assertEquals(0f, bubbleGeometry(outgoing = true, tail = true, nearTop = false, nearBottom = true).bottomRight)
        assertEquals(0f, bubbleGeometry(outgoing = false, tail = true, nearTop = false, nearBottom = true).bottomLeft)
    }

    @Test
    fun `the theme choice defaults to the system and knows its three values`() {
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStored(null))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStored("purple"))
        AppThemeMode.entries.forEach { assertEquals(it, AppThemeMode.fromStored(it.storedValue)) }
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromStored("dark"))
        assertEquals(setOf("system", "light", "dark"), AppThemeMode.entries.map { it.storedValue }.toSet())
    }

    @Test
    fun `the wallpaper doodles are a fixed function of the cell`() {
        for (row in 0..30) {
            for (column in 0..30) {
                val doodle = doodleFor(column, row)
                assertTrue(doodle.kind in 0 until DOODLE_KINDS)
                assertTrue(doodle.degrees in 0..359)
                assertTrue(doodle.shiftX in -1f..1f && doodle.shiftY in -1f..1f)
                val again = doodleFor(column, row)
                assertEquals(doodle.kind, again.kind)
                assertEquals(doodle.degrees, again.degrees)
            }
        }
        val kinds = (0..30).flatMap { row -> (0..30).map { column -> doodleFor(column, row).kind } }.toSet()
        assertEquals((0 until DOODLE_KINDS).toSet(), kinds)
    }
}
