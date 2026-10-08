package app.securemessenger.ui

import android.Manifest
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.securemessenger.signalprotocol.VoiceMedia
import app.securemessenger.signalstore.V2Contact
import app.securemessenger.signalstore.V2ContactState
import app.securemessenger.signalstore.V2MediaState
import app.securemessenger.signalstore.V2MessageStatus
import app.securemessenger.signalstore.V2StoredMessage
import app.securemessenger.signalstore.V2VoiceInfo
import java.util.Collections
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DEC-036 on a real phone: the voice bubble in each of its states, the microphone in the input bar,
 * and the real recorder and player — a recording is made from the phone's microphone into memory,
 * is a well-formed AAC stream, plays from memory, and is zeroed once played. The screen is given its
 * state directly: no relay, key or database is involved.
 */
@RunWith(AndroidJUnit4::class)
class VoiceMessagesInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<LanguageSwitchHarnessActivity>()

    private val activity get() = composeRule.activity
    private val now = System.currentTimeMillis()
    private val recordings: MutableList<VoiceRecording> = Collections.synchronizedList(mutableListOf())
    private val voiceReplies: MutableList<String?> = Collections.synchronizedList(mutableListOf())
    private val loads: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val onceLoads: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val deletions: MutableList<Pair<List<String>, Boolean>> = Collections.synchronizedList(mutableListOf())
    private val played: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** What loadVoice hands over; null (cannot be read) unless a test sets it. */
    @Volatile
    private var loadable: ByteArray? = null

    @Before
    fun allowTheMicrophone() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.RECORD_AUDIO)
    }

    @After
    fun wipeRecordings() {
        recordings.forEach { it.wipe() }
    }

    private fun contact(state: V2ContactState = V2ContactState.ACTIVE) =
        V2Contact("contact", "Alice Brown", ByteArray(33), "queue", "00", "01", state, now - 60_000L)

    private fun voice(id: String, outgoing: Boolean, state: V2MediaState, millis: Int = 7_000, once: Boolean = false, listened: Boolean = true) = V2StoredMessage(
        id, "contact", outgoing, id.padEnd(32, '0'), "",
        if (outgoing) V2MessageStatus.PENDING else V2MessageStatus.RECEIVED,
        now - 10_000L, voice = V2VoiceInfo(millis, ByteArray(40) { (it % 32).toByte() }, state, once, listened),
    )

    private fun show(messages: List<V2StoredMessage> = emptyList(), state: V2ContactState = V2ContactState.ACTIVE) {
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MessengerTheme(AppThemeMode.LIGHT) {
                    V2Conversation(
                        contact = contact(state),
                        messages = messages,
                        onSend = { _, _ -> },
                        onBack = {},
                        onOpenSafety = {},
                        onSendVoice = { recording, replyTo ->
                            recordings += recording
                            voiceReplies += replyTo
                        },
                        loadVoice = { messageId ->
                            loads += messageId
                            loadable?.copyOf()
                        },
                        playOnce = { messageId ->
                            onceLoads += messageId
                            null
                        },
                        onDeleteMessages = { ids, forBoth -> deletions += ids to forBoth },
                        onVoicePlayed = { played += it },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun exists(tag: String) = composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aVoiceBubbleShowsItsLengthAndWhereItsRecordingIs() {
        show(
            listOf(
                voice("aa", outgoing = false, V2MediaState.READY),
                voice("bb", outgoing = false, V2MediaState.DOWNLOADING, millis = 12_000),
                voice("cc", outgoing = false, V2MediaState.UNAVAILABLE),
                voice("dd", outgoing = true, V2MediaState.UPLOADING, millis = 65_000),
            ),
        )
        val play = activity.getString(R.string.voice_play)
        composeRule.onNode(hasTestTag(VOICE_PLAY_TAG_PREFIX + "aa") and hasContentDescription(play)).assertExists()
        composeRule.onNode(hasTestTag(VOICE_PLAY_TAG_PREFIX + "bb") and hasContentDescription(activity.getString(R.string.voice_downloading)), useUnmergedTree = true).assertExists()
        composeRule.onNode(hasTestTag(VOICE_PLAY_TAG_PREFIX + "cc") and hasContentDescription(activity.getString(R.string.voice_unavailable)), useUnmergedTree = true).assertExists()
        // Our own plays at once, while its pieces still go up.
        composeRule.onNode(hasTestTag(VOICE_PLAY_TAG_PREFIX + "dd") and hasContentDescription(play)).assertExists()
        composeRule.onNode(hasText("00:07"), useUnmergedTree = true).assertExists()
        composeRule.onNode(hasText("01:05"), useUnmergedTree = true).assertExists()
        composeRule.onNode(hasText(activity.getString(R.string.voice_unavailable)), useUnmergedTree = true).assertExists()

        // Playing asks for the recording; one that cannot be read says so.
        composeRule.onNodeWithTag(VOICE_PLAY_TAG_PREFIX + "aa").performClick()
        composeRule.waitUntil(5_000L) { exists(VOICE_HINT_TAG) }
        assertEquals(listOf("aa"), loads.toList())
        // One still on its way cannot be played.
        composeRule.onNodeWithTag(VOICE_PLAY_TAG_PREFIX + "bb", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("aa"), loads.toList())
    }

    @Test
    fun theMicrophoneTakesTheSendButtonsPlaceWhileTheFieldIsEmpty() {
        show()
        composeRule.onNodeWithTag(VOICE_RECORD_TAG).assertExists()
        composeRule.onNodeWithTag(MESSAGE_SEND_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(MESSAGE_INPUT_TAG).performTextInput("Hi")
        composeRule.waitUntil(5_000L) { exists(MESSAGE_SEND_TAG) && !exists(VOICE_RECORD_TAG) }

        // A blocked contact (changed key) gets no voice messages either.
        show(state = V2ContactState.KEY_CHANGED)
        composeRule.onNodeWithTag(VOICE_RECORD_TAG).assertDoesNotExist()
    }

    /** The whole way through the screen: record (locked, as TalkBack does it), then send. */
    @Test
    fun aLockedRecordingIsSentWithTheRoundButton() {
        show()
        composeRule.onNodeWithTag(VOICE_RECORD_TAG).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000L) { exists(VOICE_PANEL_TAG) && exists(VOICE_CANCEL_TAG) }
        Thread.sleep(1_600)
        composeRule.onNodeWithTag(VOICE_RECORD_TAG).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(10_000L) { recordings.isNotEmpty() }
        composeRule.waitUntil(5_000L) { !exists(VOICE_PANEL_TAG) }

        val recording = recordings.single()
        assertTrue("recorded ${recording.durationMillis} ms", recording.durationMillis in 1_000..6_000)
        assertTrue(recording.waveform.size in 1..VoiceMedia.WAVEFORM_MAX_SAMPLES)
        assertTrue(recording.waveform.all { it in 0..VoiceMedia.WAVEFORM_MAX_VALUE })
        assertWellFormedAdts(recording.bytes)
        assertEquals(listOf<String?>(null), voiceReplies.toList())
    }

    /** Telegram's gestures: hold to record, let go to send; slide left to cancel; slide up to lock. */
    @Test
    fun holdingRecordsSlidingLeftCancelsAndSlidingUpLocks() {
        show()
        val density = activity.resources.displayMetrics.density

        // Hold and let go: sent.
        composeRule.onNodeWithTag(VOICE_RECORD_TAG).performTouchInput { down(center) }
        composeRule.waitUntil(5_000L) { exists(VOICE_PANEL_TAG) }
        Thread.sleep(1_300)
        composeRule.onNodeWithTag(VOICE_RECORD_TAG).performTouchInput { up() }
        composeRule.waitUntil(10_000L) { recordings.size == 1 }

        // Hold, slide far to the left: thrown away.
        composeRule.onNodeWithTag(VOICE_RECORD_TAG).performTouchInput { down(center) }
        composeRule.waitUntil(5_000L) { exists(VOICE_PANEL_TAG) }
        Thread.sleep(800)
        composeRule.onNodeWithTag(VOICE_RECORD_TAG).performTouchInput {
            moveBy(androidx.compose.ui.geometry.Offset(-150f * density, 0f))
        }
        composeRule.waitUntil(5_000L) { !exists(VOICE_PANEL_TAG) }
        composeRule.onNodeWithTag(VOICE_RECORD_TAG).performTouchInput { up() }

        // Hold, slide up: locked, it goes on after letting go, and CANCEL throws it away.
        composeRule.onNodeWithTag(VOICE_RECORD_TAG).performTouchInput { down(center) }
        composeRule.waitUntil(5_000L) { exists(VOICE_PANEL_TAG) }
        composeRule.onNodeWithTag(VOICE_RECORD_TAG).performTouchInput {
            moveBy(androidx.compose.ui.geometry.Offset(0f, -70f * density))
        }
        composeRule.waitUntil(5_000L) { exists(VOICE_CANCEL_TAG) }
        composeRule.onNodeWithTag(VOICE_RECORD_TAG).performTouchInput { up() }
        Thread.sleep(500)
        assertTrue("still recording after letting go", exists(VOICE_PANEL_TAG))
        composeRule.onNodeWithTag(VOICE_CANCEL_TAG).performClick()
        composeRule.waitUntil(5_000L) { !exists(VOICE_PANEL_TAG) }
        Thread.sleep(500)
        assertEquals("only the first recording was sent", 1, recordings.size)

        // A tap only says how it works.
        composeRule.onNodeWithTag(VOICE_RECORD_TAG).performClick()
        composeRule.waitUntil(5_000L) { exists(VOICE_HINT_TAG) }
        assertEquals(1, recordings.size)
    }

    /**
     * Telegram's newest record screen: "1" makes it one-time, and its tooltip next to it says so each
     * time it is switched on, never when switched off; pause and go on, then send.
     */
    @Test
    fun aLockedRecordingCanBeMadeOneTimeAndPausedBeforeSending() {
        show()
        composeRule.onNodeWithTag(VOICE_RECORD_TAG).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000L) { exists(VOICE_ONCE_TOGGLE_TAG) && exists(VOICE_PAUSE_TAG) }
        composeRule.onNodeWithTag(VOICE_ONCE_TOGGLE_TAG).performClick()
        composeRule.waitUntil(5_000L) { exists(VOICE_TOOLTIP_TAG) }
        composeRule.onNode(hasText(activity.getString(R.string.voice_once_on)), useUnmergedTree = true).assertExists()
        Thread.sleep(1_000)

        // Paused: the microphone goes, the bin and the recording so far show; then it goes on.
        composeRule.onNodeWithTag(VOICE_PAUSE_TAG).performClick()
        composeRule.waitUntil(5_000L) { exists(VOICE_DELETE_RECORDING_TAG) && exists(VOICE_PREVIEW_PLAY_TAG) }
        // While paused (no sound is added): "1" off takes the tooltip away, on again shows it again
        // (the owner's report of 2026-10-07: it showed only the first time).
        composeRule.onNodeWithTag(VOICE_ONCE_TOGGLE_TAG).performClick()
        composeRule.waitUntil(5_000L) { !exists(VOICE_TOOLTIP_TAG) }
        composeRule.onNodeWithTag(VOICE_ONCE_TOGGLE_TAG).performClick()
        composeRule.waitUntil(5_000L) { exists(VOICE_TOOLTIP_TAG) }
        composeRule.onNode(hasText(activity.getString(R.string.voice_once_on)), useUnmergedTree = true).assertExists()
        Thread.sleep(800)
        composeRule.onNodeWithTag(VOICE_PAUSE_TAG).performClick()
        composeRule.waitUntil(5_000L) { exists(VOICE_CANCEL_TAG) }
        Thread.sleep(800)
        composeRule.onNodeWithTag(VOICE_RECORD_TAG).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(10_000L) { recordings.isNotEmpty() }

        val recording = recordings.single()
        assertTrue("one-time", recording.once)
        // About 1.8 s of sound: the paused time is not in it.
        assertTrue("recorded ${recording.durationMillis} ms", recording.durationMillis in 1_200..3_500)
        assertWellFormedAdts(recording.bytes)
    }

    /**
     * DEC-036: a one-time recording carries the "1"; the contact's plays only through the one-time
     * path; once played it says so and the "1" is gone.
     */
    @Test
    fun aOneTimeRecordingIsMarkedAndPlayedOnlyOnce() {
        show(listOf(voice("in1", outgoing = false, V2MediaState.READY, once = true), voice("out1", outgoing = true, V2MediaState.READY, once = true)))
        composeRule.onNodeWithTag(VOICE_ONCE_MARK_TAG_PREFIX + "in1", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(VOICE_ONCE_MARK_TAG_PREFIX + "out1", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(VOICE_PLAY_TAG_PREFIX + "in1").performClick()
        composeRule.waitUntil(5_000L) { onceLoads.isNotEmpty() }
        assertEquals(listOf("in1"), onceLoads.toList())
        assertTrue("never through the ordinary way", loads.isEmpty())
        // Ours plays the ordinary way.
        composeRule.onNodeWithTag(VOICE_PLAY_TAG_PREFIX + "out1").performClick()
        composeRule.waitUntil(5_000L) { loads.isNotEmpty() }
        assertEquals(listOf("out1"), loads.toList())

        show(listOf(voice("in1", outgoing = false, V2MediaState.PLAYED, once = true)))
        composeRule.onNodeWithTag(VOICE_ONCE_MARK_TAG_PREFIX + "in1", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNode(hasText(activity.getString(R.string.voice_played)), useUnmergedTree = true).assertExists()
    }

    /** DEC-037: a long press offers "Delete"; the dialog asks, and the tick deletes it for both. */
    @Test
    fun aMessageIsDeletedFromItsMenuForBothWhenTicked() {
        val text = V2StoredMessage("t1", "contact", false, "aa".repeat(16), "Delete me", V2MessageStatus.RECEIVED, now - 5_000L)
        show(listOf(text))
        composeRule.onNodeWithTag(MESSAGE_TAG_PREFIX + "t1").performTouchInput { click() }
        composeRule.waitUntil(5_000L) { exists(MESSAGE_MENU_DELETE_TAG) }
        composeRule.onNodeWithTag(MESSAGE_MENU_DELETE_TAG).performClick()
        composeRule.waitUntil(5_000L) { exists(DELETE_MESSAGE_CONFIRM_TAG) }
        composeRule.onNode(hasText(activity.getString(R.string.delete_message_for, "Alice Brown")), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(DELETE_MESSAGE_FOR_BOTH_TAG).performClick()
        composeRule.onNodeWithTag(DELETE_MESSAGE_CONFIRM_TAG).performClick()
        composeRule.waitUntil(5_000L) { deletions.isNotEmpty() }
        assertEquals(listOf(listOf("t1") to true), deletions.toList())

        // A blocked contact (changed key) cannot be asked: only this phone's copy can go.
        show(listOf(text), state = V2ContactState.KEY_CHANGED)
        composeRule.onNodeWithTag(MESSAGE_TAG_PREFIX + "t1").performTouchInput { click() }
        composeRule.waitUntil(5_000L) { exists(MESSAGE_MENU_DELETE_TAG) }
        composeRule.onNodeWithTag(MESSAGE_MENU_DELETE_TAG).performClick()
        composeRule.waitUntil(5_000L) { exists(DELETE_MESSAGE_CONFIRM_TAG) }
        composeRule.onNodeWithTag(DELETE_MESSAGE_FOR_BOTH_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(DELETE_MESSAGE_CONFIRM_TAG).performClick()
        composeRule.waitUntil(5_000L) { deletions.size == 2 }
        assertEquals(listOf("t1") to false, deletions.last())
    }

    /**
     * DEC-039: a voice message not played yet carries Telegram's dot on both phones; playing the
     * contact's tells the app once, playing our own tells nothing.
     */
    @Test
    fun theDotShowsUntilAVoiceMessageIsPlayed() {
        loadable = ByteArray(64)
        show(
            listOf(
                voice("in1", outgoing = false, V2MediaState.READY, listened = false),
                voice("out1", outgoing = true, V2MediaState.READY, listened = false),
                voice("in2", outgoing = false, V2MediaState.READY),
            ),
        )
        composeRule.onNodeWithTag(VOICE_UNHEARD_TAG_PREFIX + "in1", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(VOICE_UNHEARD_TAG_PREFIX + "out1", useUnmergedTree = true).assertExists()
        assertTrue(!exists(VOICE_UNHEARD_TAG_PREFIX + "in2"))

        composeRule.onNodeWithTag(VOICE_PLAY_TAG_PREFIX + "out1").performClick()
        composeRule.waitUntil(5_000L) { loads.size == 1 }
        composeRule.onNodeWithTag(VOICE_PLAY_TAG_PREFIX + "in1").performClick()
        composeRule.waitUntil(5_000L) { played.isNotEmpty() }
        composeRule.waitForIdle()
        assertEquals(listOf("in1"), played.toList())
    }

    /**
     * Telegram's selection (owner, 2026-10-07): a long press on a voice message selects it (its bars and
     * its bubble both answer the press, and it must stay selected), and a long press on the empty part
     * of a message's line selects that message too.
     */
    @Test
    fun aLongPressSelectsAVoiceMessageAndTheEmptyPartOfALine() {
        show(listOf(voice("aa", outgoing = false, V2MediaState.READY), voice("bb", outgoing = false, V2MediaState.READY)))
        composeRule.onNodeWithTag(MESSAGE_TAG_PREFIX + "aa").performTouchInput { longClick() }
        composeRule.waitUntil(5_000L) { exists(MESSAGES_SELECTION_COUNT_TAG) }
        composeRule.onNode(hasText(activity.getString(R.string.messages_selected, 1)), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(MESSAGES_SELECTION_CLOSE_TAG).performClick()
        composeRule.waitUntil(5_000L) { !exists(MESSAGES_SELECTION_COUNT_TAG) }

        // Far right on the line of the contact's message "bb": no bubble there.
        val bubble = composeRule.onNodeWithTag(MESSAGE_TAG_PREFIX + "bb").fetchSemanticsNode().boundsInRoot
        val list = composeRule.onNodeWithTag(CONVERSATION_LIST_TAG).fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag(CONVERSATION_LIST_TAG).performTouchInput {
            longClick(androidx.compose.ui.geometry.Offset(list.width - 30f, bubble.center.y - list.top))
        }
        composeRule.waitUntil(5_000L) { exists(MESSAGES_SELECTION_COUNT_TAG) }
        composeRule.onNode(hasText(activity.getString(R.string.messages_selected, 1)), useUnmergedTree = true).assertExists()
        assertTrue("nothing was loaded or played", loads.isEmpty())
    }

    /** Telegram's seek bar: a drag along the bars of a message not playing moves the place it starts from. */
    @Test
    fun aDragAlongTheBarsMovesThePlace() {
        show(listOf(voice("aa", outgoing = false, V2MediaState.READY, millis = 10_000)))
        composeRule.onNode(hasText("00:10"), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(VOICE_WAVEFORM_TAG_PREFIX + "aa", useUnmergedTree = true).performTouchInput {
            swipe(start = centerLeft.copy(x = 1f), end = center, durationMillis = 600)
        }
        composeRule.waitForIdle()
        // Half the bars from the start: about five seconds in.
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodes(hasText("00:04") or hasText("00:05"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue("a drag is not a tap", loads.isEmpty())
    }

    /**
     * Telegram's trim while paused: the left handle cuts off the start; going on asks once whether to
     * trim, and what is sent is shorter than what was recorded, a whole AAC stream all the same.
     */
    @Test
    fun aPausedRecordingIsTrimmedBeforeGoingOn() {
        activity.getSharedPreferences("secure_messenger_voice_hints", android.content.Context.MODE_PRIVATE).edit().remove("trim_hint").commit()
        show()
        composeRule.onNodeWithTag(VOICE_RECORD_TAG).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000L) { exists(VOICE_PAUSE_TAG) }
        Thread.sleep(3_000)
        composeRule.onNodeWithTag(VOICE_PAUSE_TAG).performClick()
        composeRule.waitUntil(5_000L) { exists(VOICE_TRIM_LEFT_TAG) && exists(VOICE_PREVIEW_PLAY_TAG) }
        Thread.sleep(800)
        val panelWidth = composeRule.onNodeWithTag(VOICE_PANEL_TAG).fetchSemanticsNode().size.width.toFloat()
        composeRule.onNodeWithTag(VOICE_TRIM_LEFT_TAG).performTouchInput {
            down(center)
            repeat(10) { moveBy(androidx.compose.ui.geometry.Offset(panelWidth * 0.05f, 0f)) }
            up()
        }
        composeRule.waitForIdle()

        // Going on asks first; "Trim" cuts and records on.
        composeRule.onNodeWithTag(VOICE_PAUSE_TAG).performClick()
        composeRule.waitUntil(5_000L) { exists(VOICE_TRIM_CONFIRM_TAG) }
        composeRule.onNodeWithTag(VOICE_TRIM_CONFIRM_TAG).performClick()
        composeRule.waitUntil(5_000L) { exists(VOICE_CANCEL_TAG) }
        Thread.sleep(1_000)
        composeRule.onNodeWithTag(VOICE_RECORD_TAG).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(10_000L) { recordings.isNotEmpty() }

        val recording = recordings.single()
        // About 3 s, half of it cut, then about 1 s more.
        assertTrue("recorded ${recording.durationMillis} ms", recording.durationMillis in 1_500..3_400)
        assertWellFormedAdts(recording.bytes)
    }

    /** The recorder and the player on their own: from memory to sound, and zeroed afterwards. */
    @Test
    fun aRecordingPlaysFromMemoryAndIsZeroedAfterwards() {
        show()
        val recorder = VoiceRecorder()
        assertTrue("the microphone opens", runBlocking { recorder.start() })
        Thread.sleep(1_500)
        val recording = runBlocking { recorder.stop() }
        assertNotNull(recording)
        checkNotNull(recording)
        assertWellFormedAdts(recording.bytes)

        val player = VoicePlayer(activity)
        val failed = java.util.concurrent.atomic.AtomicBoolean(false)
        composeRule.runOnUiThread { player.play("m", recording.bytes) { failed.set(true) } }
        composeRule.waitUntil(5_000L) { player.playing || failed.get() }
        assertTrue("the phone could not play its own recording", !failed.get() && player.playing)
        assertTrue("length ${player.durationMillis} ms", player.durationMillis in 1_000..4_000)
        composeRule.runOnUiThread { player.release() }
        assertTrue("the recording is zeroed once played", recording.bytes.all { it == 0.toByte() })
    }

    /** Every frame starts with an ADTS header naming AAC-LC and mono, and the frames fill the bytes exactly. */
    private fun assertWellFormedAdts(bytes: ByteArray) {
        assertTrue("${bytes.size} bytes", bytes.size > 1_000)
        var offset = 0
        var frames = 0
        while (offset < bytes.size) {
            assertTrue("a whole header at $offset", offset + ADTS_HEADER_BYTES <= bytes.size)
            assertEquals(0xFF, bytes[offset].toInt() and 0xFF)
            assertEquals(0xF1, bytes[offset + 1].toInt() and 0xFF)
            assertEquals("AAC-LC", 1, (bytes[offset + 2].toInt() and 0xC0) shr 6)
            assertEquals("mono", 1, ((bytes[offset + 2].toInt() and 0x01) shl 2) or ((bytes[offset + 3].toInt() and 0xC0) shr 6))
            val length = ((bytes[offset + 3].toInt() and 0x03) shl 11) or
                ((bytes[offset + 4].toInt() and 0xFF) shl 3) or
                ((bytes[offset + 5].toInt() and 0xE0) shr 5)
            assertTrue("frame length $length", length > ADTS_HEADER_BYTES)
            offset += length
            frames++
        }
        assertEquals("the frames end with the bytes", bytes.size, offset)
        assertTrue("$frames frames", frames > 10)
    }
}
