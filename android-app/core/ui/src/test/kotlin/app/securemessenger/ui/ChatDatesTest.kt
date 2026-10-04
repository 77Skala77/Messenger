package app.securemessenger.ui

import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The date rules in both languages, with a fixed clock (`docs/PHASE6_PORTING_NOTES.md`, 2.7). */
class ChatDatesTest {
    private val zone = ZoneId.of("UTC")
    private val russian = Locale.forLanguageTag("ru")
    private val english = Locale.forLanguageTag("en-US")
    private val british = Locale.forLanguageTag("en-GB")
    private val ruPatterns = DatePatterns("dd.MM.yy", "d MMM", "d MMMM", "d MMMM yyyy")
    private val enPatterns = DatePatterns("MM/dd/yy", "MMM d", "MMMM d", "MMMM d, yyyy")

    private fun millis(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0) =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    // Tuesday, 20 October 2026.
    private val now = millis(2026, 10, 20, 12, 0)

    private fun listRu(time: Long, at: Long = now) = formatChatListTime(time, at, zone, russian, ruPatterns)
    private fun listEn(time: Long, at: Long = now) = formatChatListTime(time, at, zone, english, enPatterns)

    @Test
    fun `today shows the time, in 24 hours or 12 hours as the language does`() {
        val morning = millis(2026, 10, 20, 9, 5)
        assertEquals("09:05", listRu(morning))
        assertEquals("09:05", formatChatListTime(morning, now, zone, british, enPatterns))
        assertTrue(listEn(morning), Regex("9:05[\\s  ]?AM").matches(listEn(morning)))
        assertTrue(Regex("2:30[\\s  ]?PM").matches(formatMessageTime(millis(2026, 10, 20, 14, 30), zone, english)))
    }

    @Test
    fun `yesterday shows the time only when it is under eight hours ago`() {
        val earlyNow = millis(2026, 10, 20, 3, 0)
        assertEquals("22:00", listRu(millis(2026, 10, 19, 22, 0), earlyNow))
        // Yesterday at 08:00 is 28 hours before noon today: older than eight hours, so the weekday.
        assertEquals("пн", listRu(millis(2026, 10, 19, 8, 0)))
        assertEquals("Mon", listEn(millis(2026, 10, 19, 8, 0)))
    }

    @Test
    fun `the last six days show the weekday`() {
        assertEquals("ср", listRu(millis(2026, 10, 14)))
        assertEquals("Wed", listEn(millis(2026, 10, 14)))
        assertEquals("вс", listRu(millis(2026, 10, 18)))
    }

    @Test
    fun `seven days and more show day and month`() {
        assertEquals("13 окт.", listRu(millis(2026, 10, 13)))
        assertEquals("Oct 13", listEn(millis(2026, 10, 13)))
        assertEquals("5 окт.", listRu(millis(2026, 10, 5)))
    }

    @Test
    fun `a year ago is the boundary for the short date with the year`() {
        val yearAgo = now - 365L * 24 * 60 * 60 * 1000
        assertEquals("20 окт.", listRu(yearAgo))
        assertEquals("20.10.25", listRu(yearAgo - 1))
        assertEquals("15.03.25", listRu(millis(2025, 3, 15, 10, 0)))
        assertEquals("03/15/25", listEn(millis(2025, 3, 15, 10, 0)))
    }

    @Test
    fun `a time slightly in the future from another clock still shows the time`() {
        assertEquals("14:00", listRu(millis(2026, 10, 20, 14, 0)))
    }

    @Test
    fun `the date chip has no year within a year and the year after it`() {
        assertEquals("5 октября", formatDateChip(millis(2026, 10, 5), now, zone, russian, ruPatterns))
        assertEquals("October 5", formatDateChip(millis(2026, 10, 5), now, zone, english, enPatterns))
        assertEquals("15 марта 2025", formatDateChip(millis(2025, 3, 15), now, zone, russian, ruPatterns))
        assertEquals("March 15, 2025", formatDateChip(millis(2025, 3, 15), now, zone, english, enPatterns))
    }

    @Test
    fun `the zone decides the day`() {
        val lateEvening = millis(2026, 10, 19, 22, 30)
        assertEquals("19 октября", formatDateChip(lateEvening, now, zone, russian, ruPatterns))
        assertEquals("20 октября", formatDateChip(lateEvening, now, ZoneId.of("Europe/Berlin"), russian, ruPatterns))
    }
}
