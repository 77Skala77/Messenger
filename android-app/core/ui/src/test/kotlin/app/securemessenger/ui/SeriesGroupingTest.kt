package app.securemessenger.ui

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesGroupingTest {
    private val zone = ZoneId.of("UTC")
    private fun at(day: Int, hour: Int, minute: Int, second: Int = 0) =
        ZonedDateTime.of(2026, 10, day, hour, minute, second, 0, zone).toInstant().toEpochMilli()

    private fun group(vararg messages: SeriesInput) = seriesPositions(messages.toList(), zone)

    @Test
    fun `no messages give no positions`() {
        assertTrue(group().isEmpty())
    }

    @Test
    fun `a single message has the tail and the date chip`() {
        val only = group(SeriesInput(true, at(4, 10, 0))).single()
        assertFalse(only.nearTop)
        assertFalse(only.nearBottom)
        assertTrue(only.tail)
        assertTrue(only.dateChipBefore)
    }

    @Test
    fun `neighbours in one direction within five minutes form a series`() {
        val (first, middle, last) = group(
            SeriesInput(false, at(4, 10, 0)),
            SeriesInput(false, at(4, 10, 3)),
            SeriesInput(false, at(4, 10, 8)),
        )
        assertFalse(first.nearTop); assertTrue(first.nearBottom); assertFalse(first.tail)
        assertTrue(middle.nearTop); assertTrue(middle.nearBottom); assertFalse(middle.tail)
        assertTrue(last.nearTop); assertFalse(last.nearBottom); assertTrue(last.tail)
        assertTrue(first.dateChipBefore)
        assertFalse(middle.dateChipBefore)
        assertFalse(last.dateChipBefore)
    }

    @Test
    fun `exactly five minutes still joins and one millisecond more breaks`() {
        val start = at(4, 10, 0)
        val joined = group(SeriesInput(true, start), SeriesInput(true, start + SERIES_MAX_GAP_MILLIS))
        assertTrue(joined[0].nearBottom && joined[1].nearTop)
        val apart = group(SeriesInput(true, start), SeriesInput(true, start + SERIES_MAX_GAP_MILLIS + 1))
        assertFalse(apart[0].nearBottom || apart[1].nearTop)
        assertTrue(apart[0].tail && apart[1].tail)
    }

    @Test
    fun `a change of direction ends the series`() {
        val (incoming, outgoing) = group(SeriesInput(false, at(4, 10, 0)), SeriesInput(true, at(4, 10, 1)))
        assertTrue(incoming.tail)
        assertFalse(incoming.nearBottom)
        assertFalse(outgoing.nearTop)
    }

    @Test
    fun `midnight ends the series and puts the date chip before the new day`() {
        val (late, early) = group(SeriesInput(true, at(4, 23, 59)), SeriesInput(true, at(5, 0, 1)))
        assertTrue(late.tail)
        assertFalse(early.nearTop)
        assertTrue(early.dateChipBefore)
    }

    @Test
    fun `a slightly earlier time from another clock still joins`() {
        val (first, second) = group(SeriesInput(false, at(4, 10, 5)), SeriesInput(false, at(4, 10, 4)))
        assertTrue(first.nearBottom && second.nearTop)
    }

    @Test
    fun `the same instant falls on different days in different zones`() {
        val instant = at(4, 23, 30)
        val pair = listOf(SeriesInput(true, instant), SeriesInput(true, instant + 60_000L))
        // 23:30 and 23:31 UTC are one day, and so are 01:30 and 01:31 the next day in Berlin (UTC+2).
        assertEquals(1, seriesPositions(pair, ZoneId.of("UTC")).count { it.dateChipBefore })
        assertEquals(1, seriesPositions(pair, ZoneId.of("Europe/Berlin")).count { it.dateChipBefore })
        // A pair that straddles local midnight in Berlin only.
        val straddle = listOf(SeriesInput(true, at(4, 21, 59, 30)), SeriesInput(true, at(4, 22, 0, 30)))
        assertEquals(1, seriesPositions(straddle, ZoneId.of("UTC")).count { it.dateChipBefore })
        assertEquals(2, seriesPositions(straddle, ZoneId.of("Europe/Berlin")).count { it.dateChipBefore })
    }
}
