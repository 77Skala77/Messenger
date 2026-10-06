package app.securemessenger.ui

import app.securemessenger.signalstore.V2Contact
import app.securemessenger.signalstore.V2ContactState

/**
 * What a notification may say about new messages (DEC-027, DEC-031): who wrote, by the name the
 * person gave the contact, and how many unseen texts there are. Never what was written: no notice
 * has a field that could carry message text.
 */
internal sealed interface MessageNotice {
    /** New texts from a contact; [unseenCount] counts all of its unseen texts. */
    data class Chat(val contactId: String, val localName: String, val unseenCount: Int) : MessageNotice

    /**
     * A new contact request, or new texts held for one. The name a request claims is unverified and
     * chosen by whoever sent it, so it is never shown in a notification.
     */
    data class Request(val requestId: String) : MessageNotice
}

/** Unseen counts per contact and request, and the pending requests, at one moment. */
internal class UnseenSnapshot(val unseen: Map<String, Int>, val requestIds: Set<String>)

/**
 * What to announce after a sync: each contact whose count of unseen texts went up, and each request
 * that is new or got more texts. A blocked contact (changed key) is never announced. Message texts
 * are not even an input.
 */
internal fun messageNotices(before: UnseenSnapshot, after: UnseenSnapshot, contacts: List<V2Contact>): List<MessageNotice> {
    fun grew(id: String) = (after.unseen[id] ?: 0) > (before.unseen[id] ?: 0)
    val chats = contacts
        .filter { it.state != V2ContactState.KEY_CHANGED && grew(it.contactId) }
        .map { MessageNotice.Chat(it.contactId, it.localDisplayName, after.unseen[it.contactId] ?: 0) }
    val requests = after.requestIds
        .filter { it !in before.requestIds || grew(it) }
        .sorted()
        .map { MessageNotice.Request(it) }
    return chats + requests
}

/** The words a notification uses, in the current language. */
internal class NoticeStrings(
    /** "New message" for one, "3 new messages" for more. */
    val newMessages: (count: Int) -> String,
    val requestTitle: String,
    val requestText: String,
    /** All a locked screen ever shows: no name, no count. */
    val lockScreenTitle: String,
)

internal class NoticeText(val title: String, val text: String)

/** The title and text of a notification: built from the notice alone, so they cannot contain message text. */
internal fun noticeText(notice: MessageNotice, strings: NoticeStrings): NoticeText = when (notice) {
    is MessageNotice.Chat -> NoticeText(notice.localName, strings.newMessages(notice.unseenCount))
    is MessageNotice.Request -> NoticeText(strings.requestTitle, strings.requestText)
}

/** Local IDs are random UUIDs: anything else that arrives in an intent is ignored. */
internal fun isPlausibleLocalId(value: String?): Boolean =
    value != null && value.length in 1..64 && value.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' }
