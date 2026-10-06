package app.securemessenger.ui

import app.securemessenger.signalstore.V2StoredMessage
import java.time.ZoneId

/** One line of the conversation, in the order a person reads it: a date pill, or a message in its series position. */
internal sealed interface ConversationEntry {
    val key: String

    /** The pill where a day starts; [timeMillis] is the time of the first message of that day. */
    class Day(val timeMillis: Long, firstMessageId: String) : ConversationEntry {
        override val key: String = "day-$firstMessageId"
    }

    class Bubble(val message: V2StoredMessage, val position: SeriesPosition) : ConversationEntry {
        override val key: String get() = message.messageId
    }
}

/**
 * The conversation as entries, oldest first: a date pill before the first message of every day, and
 * each message with its place in a series of bubbles (`seriesPositions`, notes 2.2).
 */
internal fun conversationEntries(messages: List<V2StoredMessage>, zone: ZoneId): List<ConversationEntry> {
    val positions = seriesPositions(messages.map { SeriesInput(it.outgoing, it.createdAtEpochMillis) }, zone)
    return buildList {
        messages.forEachIndexed { index, message ->
            val position = positions[index]
            if (position.dateChipBefore) add(ConversationEntry.Day(message.createdAtEpochMillis, message.messageId))
            add(ConversationEntry.Bubble(message, position))
        }
    }
}

/** The digits of a safety number in groups of five, three groups to a line: 60 digits make four lines. */
internal fun safetyNumberLines(number: String): List<String> =
    number.filter(Char::isDigit).chunked(5).chunked(3).map { it.joinToString(" ") }

/** Where a bubble's text and its time block go, in pixels, inside the bubble's content box. */
internal class BubblePlan(val width: Int, val height: Int, val metaX: Int, val metaY: Int, val metaOnOwnLine: Boolean)

/**
 * Notes 2.3: if the last line of the text and the time block (with ticks) fit side by side within
 * [maxWidth], the time shares that line and the bubble is as wide as the wider of the text and that
 * line; otherwise the time goes on its own line under the text. The time block sits at the bottom
 * end either way. All numbers are pixels; [gap] separates the last line from the time.
 */
internal fun planBubble(
    textWidth: Int,
    textHeight: Int,
    lastLineWidth: Int,
    metaWidth: Int,
    metaHeight: Int,
    maxWidth: Int,
    gap: Int,
): BubblePlan {
    val shared = lastLineWidth + gap + metaWidth
    return if (shared <= maxWidth) {
        val width = maxOf(textWidth, shared)
        val height = maxOf(textHeight, metaHeight)
        BubblePlan(width, height, metaX = width - metaWidth, metaY = height - metaHeight, metaOnOwnLine = false)
    } else {
        val width = maxOf(textWidth, metaWidth)
        BubblePlan(width, textHeight + metaHeight, metaX = width - metaWidth, metaY = textHeight, metaOnOwnLine = true)
    }
}
