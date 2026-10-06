package app.securemessenger.ui

import app.securemessenger.signalstore.V2ChatSummary
import app.securemessenger.signalstore.V2ContactState

/** How many characters of a text the chat list ever looks at: the row shows one line anyway. */
private const val PREVIEW_MAX_CHARS = 200

/**
 * When a chat last had something to show: its newest text in either direction, or, before the first
 * text, the day the contact was added. An incoming text's time is the contact's own only within a
 * minute before it arrived here (DEC-034), never later than its arrival, so the contact can move its
 * chat at most a minute down the list, never up.
 */
internal fun V2ChatSummary.lastActivityMillis(): Long = lastText?.createdAtEpochMillis ?: contact.createdAtEpochMillis

/**
 * The chat list's order (notes 2.6): pinned chats on top, the one pinned last first, like Telegram's
 * (DEC-035); then the chat with the newest activity first; equal times by contact ID, so it never jumps.
 */
internal fun chatListOrder(chats: List<V2ChatSummary>): List<V2ChatSummary> =
    chats.sortedWith(
        compareByDescending<V2ChatSummary> { it.contact.pinnedAtEpochMillis ?: Long.MIN_VALUE }
            .thenByDescending { it.lastActivityMillis() }
            .thenBy { it.contact.contactId },
    )

/**
 * The chats whose contact name or newest text contains [query] (case-insensitive, surrounding spaces
 * ignored); an empty query keeps all. It works on what the list already holds, on this phone only.
 */
internal fun chatListSearch(chats: List<V2ChatSummary>, query: String): List<V2ChatSummary> {
    val needle = query.trim()
    if (needle.isEmpty()) return chats
    return chats.filter {
        it.contact.localDisplayName.contains(needle, ignoreCase = true) ||
            it.lastText?.text?.contains(needle, ignoreCase = true) == true
    }
}

/** What the second line of a chat row says. */
internal sealed interface ChatPreview {
    /** The newest text; [outgoing] when it is ours. */
    data class LastText(val text: String, val outgoing: Boolean) : ChatPreview

    /** No text to show, and the contact's state matters: waiting for acceptance, or blocked. */
    data class ContactState(val state: V2ContactState) : ChatPreview

    data object NoMessages : ChatPreview
}

/**
 * A blocked contact (changed key) always says so, even with texts on file: nothing is sent or
 * received for it, and the person must not miss that. Otherwise the newest text; with none, the
 * state while waiting for acceptance; with none of that, "no messages yet".
 */
internal fun chatPreview(chat: V2ChatSummary): ChatPreview {
    val last = chat.lastText
    return when {
        chat.contact.state == V2ContactState.KEY_CHANGED -> ChatPreview.ContactState(V2ContactState.KEY_CHANGED)
        last != null -> ChatPreview.LastText(oneLine(last.text), last.outgoing)
        chat.contact.state == V2ContactState.AWAITING_ACCEPTANCE -> ChatPreview.ContactState(V2ContactState.AWAITING_ACCEPTANCE)
        else -> ChatPreview.NoMessages
    }
}

/** The tick beside the time: only for a chat whose newest text is ours (never "read", the protocol has no such state). */
internal fun chatTick(chat: V2ChatSummary): TickKind? =
    chat.lastText?.takeIf { it.outgoing }?.let { tickKindFor(it.status) }

/** Line breaks and runs of blanks become single spaces, so a multi-line text still reads as one line. */
internal fun oneLine(text: String): String = text.take(PREVIEW_MAX_CHARS).replace(WHITESPACE_RUN, " ").trim()

private val WHITESPACE_RUN = Regex("\\s+")
