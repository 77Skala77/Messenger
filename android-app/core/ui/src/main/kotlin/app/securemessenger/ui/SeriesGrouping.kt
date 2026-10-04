package app.securemessenger.ui

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/** The two facts about a message that decide how it joins its neighbours. */
class SeriesInput(val outgoing: Boolean, val timeMillis: Long)

/**
 * How one message sits in a series of bubbles: [nearTop] and [nearBottom] mean it touches the
 * previous or the next bubble (tight corners); [tail] is true for the last of a series;
 * [dateChipBefore] is true where a day starts and the date pill goes above it.
 */
class SeriesPosition(
    val nearTop: Boolean,
    val nearBottom: Boolean,
    val tail: Boolean,
    val dateChipBefore: Boolean,
)

/** Neighbours of one series are at most this far apart (`docs/PHASE6_PORTING_NOTES.md`, 2.2). */
const val SERIES_MAX_GAP_MILLIS = 5 * 60 * 1000L

/**
 * Groups messages, oldest first, into series: neighbours go the same direction, are at most five
 * minutes apart, and are on the same calendar day in [zone]. Anything else starts a new series.
 */
fun seriesPositions(messages: List<SeriesInput>, zone: ZoneId): List<SeriesPosition> {
    val days = messages.map { Instant.ofEpochMilli(it.timeMillis).atZone(zone).toLocalDate() }
    fun joined(first: Int, second: Int): Boolean =
        messages[first].outgoing == messages[second].outgoing &&
            abs(messages[second].timeMillis - messages[first].timeMillis) <= SERIES_MAX_GAP_MILLIS &&
            days[first] == days[second]
    return messages.indices.map { index ->
        val joinedWithPrevious = index > 0 && joined(index - 1, index)
        val joinedWithNext = index < messages.lastIndex && joined(index, index + 1)
        SeriesPosition(
            nearTop = joinedWithPrevious,
            nearBottom = joinedWithNext,
            tail = !joinedWithNext,
            dateChipBefore = index == 0 || days[index - 1] != days[index],
        )
    }
}
