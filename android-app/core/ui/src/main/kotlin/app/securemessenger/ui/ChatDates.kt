package app.securemessenger.ui

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * The date patterns of the current language (`date_pattern_*` resources), in `DateTimeFormatter`
 * syntax. Weekdays and times come from the locale itself.
 */
class DatePatterns(
    val listOld: String,
    val listMonth: String,
    val chipRecent: String,
    val chipOld: String,
)

private const val DAY_MILLIS = 24L * 60 * 60 * 1000
private const val YEAR_MILLIS = 365 * DAY_MILLIS

/** A message from yesterday still shows its time if it is younger than this (`LocaleController.java` rules). */
private const val YESTERDAY_AS_TIME_MILLIS = 8L * 60 * 60 * 1000

/** The time of day, with a 12-hour clock where the locale uses one. */
fun formatMessageTime(timeMillis: Long, zone: ZoneId, locale: Locale): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(at(timeMillis, zone))

/**
 * The time next to a chat in the chat list: older than a year `dd.MM.yy`; today, or yesterday if
 * under eight hours ago, the time; within the last six days the weekday; else day and month.
 */
fun formatChatListTime(timeMillis: Long, nowMillis: Long, zone: ZoneId, locale: Locale, patterns: DatePatterns): String {
    val time = at(timeMillis, zone)
    val age = nowMillis - timeMillis
    val daysAgo = ChronoUnit.DAYS.between(time.toLocalDate(), at(nowMillis, zone).toLocalDate())
    return when {
        age > YEAR_MILLIS -> format(time, patterns.listOld, locale)
        daysAgo <= 0 -> formatMessageTime(timeMillis, zone, locale)
        daysAgo == 1L && age < YESTERDAY_AS_TIME_MILLIS -> formatMessageTime(timeMillis, zone, locale)
        daysAgo < 7 -> format(time, "EEE", locale)
        else -> format(time, patterns.listMonth, locale)
    }
}

/** The date on the pill between days of a conversation: day and month, with the year after a year. */
fun formatDateChip(timeMillis: Long, nowMillis: Long, zone: ZoneId, locale: Locale, patterns: DatePatterns): String {
    val pattern = if (nowMillis - timeMillis > YEAR_MILLIS) patterns.chipOld else patterns.chipRecent
    return format(at(timeMillis, zone), pattern, locale)
}

private fun at(timeMillis: Long, zone: ZoneId): ZonedDateTime = Instant.ofEpochMilli(timeMillis).atZone(zone)

private fun format(time: ZonedDateTime, pattern: String, locale: Locale): String =
    DateTimeFormatter.ofPattern(pattern, locale).format(time)
