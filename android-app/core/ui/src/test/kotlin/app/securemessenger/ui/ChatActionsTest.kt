package app.securemessenger.ui

import app.securemessenger.signalprotocol.ConversationV2Service
import app.securemessenger.signalstore.V2MessageStatus
import app.securemessenger.signalstore.V2StoredMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DEC-035: the rules behind the chat list's swipe and selection, the chat's mute menu, and quotes. */
class ChatActionsTest {
    @Test
    fun `a swipe counts past 45 percent of the row, or with a fast fling to the left`() {
        assertFalse(swipeCounts(offsetPx = -449f, widthPx = 1_000f, velocityX = 0f))
        assertTrue(swipeCounts(offsetPx = -450f, widthPx = 1_000f, velocityX = 0f))
        assertTrue(swipeCounts(offsetPx = -10f, widthPx = 1_000f, velocityX = -3_500f))
        assertFalse(swipeCounts(offsetPx = -10f, widthPx = 1_000f, velocityX = -3_499f))
        assertFalse("a fling to the right is no swipe to the left", swipeCounts(offsetPx = -10f, widthPx = 1_000f, velocityX = 5_000f))
        assertFalse("a row not laid out yet", swipeCounts(offsetPx = -500f, widthPx = 0f, velocityX = 0f))
    }

    @Test
    fun `the reply swipe starts like Telegram's, to the left only, and stays between its place and 80 dp`() {
        assertTrue(shouldStartReplySwipe(dx = -30f, dy = 5f, minDistancePx = 30f))
        assertFalse(shouldStartReplySwipe(dx = -29f, dy = 0f, minDistancePx = 30f))
        assertFalse("too steep: the list scrolls", shouldStartReplySwipe(dx = -30f, dy = 10f, minDistancePx = 30f))
        assertFalse("to the right is the swipe back", shouldStartReplySwipe(dx = 30f, dy = 0f, minDistancePx = 30f))
        assertEquals(0f, replySwipeOffset(dx = 25f, maxPx = 240f))
        assertEquals(-100f, replySwipeOffset(dx = -100f, maxPx = 240f))
        assertEquals(-240f, replySwipeOffset(dx = -500f, maxPx = 240f))
    }

    @Test
    fun `pinning or muting a selection follows Telegram's rule`() {
        assertTrue("some unpinned: pin them all", ChatSelection.pinAll(listOf(true, false)))
        assertFalse("all pinned: unpin them all", ChatSelection.pinAll(listOf(true, true)))
        assertTrue(ChatSelection.muteAll(listOf(false)))
        assertFalse(ChatSelection.muteAll(listOf(true, true)))
    }

    @Test
    fun `a mute lasts as long as chosen, and forever is for good`() {
        val now = 1_800_000_000_000L
        val hour = 60 * 60_000L
        assertEquals(now + hour, MuteFor.HOUR.until(now))
        assertEquals(now + 8 * hour, MuteFor.EIGHT_HOURS.until(now))
        assertEquals(now + 48 * hour, MuteFor.TWO_DAYS.until(now))
        assertEquals(ConversationV2Service.MUTED_FOREVER, MuteFor.FOREVER.until(now))
    }

    @Test
    fun `a quote is looked up in this phone's own copy and shows one line`() {
        val question = V2StoredMessage("q", "c", false, "aa", "Lunch\n\n at   noon?", V2MessageStatus.RECEIVED, 1L)
        val mine = V2StoredMessage("m", "c", true, "bb", "Mine", V2MessageStatus.SENT, 2L)
        val byId = listOf(question, mine).associateBy { it.innerMessageIdHex }

        assertEquals(QuoteInfo(authorIsMe = false, text = "Lunch at noon?"), quoteOf("aa", byId))
        assertEquals(QuoteInfo(authorIsMe = true, text = "Mine"), quoteOf("bb", byId))
        assertEquals("a text this phone lacks", QuoteInfo(authorIsMe = false, text = null), quoteOf("cc", byId))
        assertNull("a plain text quotes nothing", quoteOf(null, byId))
        assertEquals(200, quoteLine("x".repeat(5_000)).length)

        // DEC-036: a quoted voice message has no text and is named as one.
        val voice = V2StoredMessage(
            "v", "c", false, "dd", "", V2MessageStatus.RECEIVED, 3L,
            voice = app.securemessenger.signalstore.V2VoiceInfo(5_000, ByteArray(0), app.securemessenger.signalstore.V2MediaState.DOWNLOADING),
        )
        assertEquals(QuoteInfo(authorIsMe = false, text = "", voice = true), quoteOf("dd", byId + ("dd" to voice)))
    }
}
