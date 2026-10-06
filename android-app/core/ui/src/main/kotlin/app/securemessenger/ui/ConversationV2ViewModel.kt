package app.securemessenger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.securemessenger.signalprotocol.ConversationV2Exception
import app.securemessenger.signalprotocol.ConversationV2Service
import app.securemessenger.signalprotocol.InvitationRejectedException
import app.securemessenger.signalprotocol.InvitationRejection
import app.securemessenger.signalprotocol.MalformedV2Exception
import app.securemessenger.signalstore.V2ChatSummary
import app.securemessenger.signalstore.V2Contact
import app.securemessenger.signalstore.V2ContactRequest
import app.securemessenger.signalstore.V2StoredMessage
import app.securemessenger.storage.SecureMessengerDatabase
import app.securemessenger.storage.VaultKeyUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class V2Notice {
    INVALID_CODE,
    UNSUPPORTED_VERSION,
    NOT_FOUND,
    EXPIRED,
    OWN_INVITATION,
    ALREADY_PAIRED,
    REQUEST_SENT,
    RELAY_ERROR,
    FAILED,
    INVITATION_READY,
    BLOCKED,
}

/**
 * Whether the invitation on screen reached the relay (audit L2). [UNKNOWN] after a restart, when
 * only the stored invitation is known; "send again" is always allowed then.
 */
enum class InvitationUpload { UNKNOWN, UPLOADED, FAILED }

class V2RequestView(val request: V2ContactRequest, val safetyNumber: String?, val heldTextCount: Int)

data class ConversationV2UiState(
    val ready: Boolean = false,
    val contacts: List<V2Contact> = emptyList(),
    /** One row per contact for the chat list: newest text and count of unseen texts (DEC-029). */
    val chats: List<V2ChatSummary> = emptyList(),
    val requests: List<V2RequestView> = emptyList(),
    val invitationCode: String? = null,
    val invitationUpload: InvitationUpload = InvitationUpload.UNKNOWN,
    /** True while an invitation is being created or uploaded again. */
    val invitationBusy: Boolean = false,
    val selectedContactId: String? = null,
    val messages: List<V2StoredMessage> = emptyList(),
    val safetyNumber: String? = null,
    val notice: V2Notice? = null,
    /** True while a fetch the person asked for (pull down on the chat list) is running. */
    val refreshing: Boolean = false,
    /**
     * The contact this phone just added by scanning or pasting an invitation: the screen opens the
     * comparison of its safety number once (owner decision, DEC-030), then clears it.
     */
    val newContactId: String? = null,
    /** DEC-023: the vault key disappeared while the chat was open; the app must re-check its data. */
    val localDataUnreadable: Boolean = false,
)

/** What one refresh read. [messages] and [safetyNumber] belong to [selectedContactId]. */
internal class V2Snapshot(
    val contacts: List<V2Contact>,
    val requests: List<V2RequestView>,
    val invitationCode: String?,
    val selectedContactId: String?,
    val messages: List<V2StoredMessage>,
    val safetyNumber: String?,
    val chats: List<V2ChatSummary> = emptyList(),
)

/**
 * Applies a refresh. If another contact was selected while it read the store, its messages and
 * safety number are dropped rather than shown under that other contact (audit SESS-01); the next
 * refresh reads them for the new selection.
 */
internal fun ConversationV2UiState.withSnapshot(snapshot: V2Snapshot): ConversationV2UiState {
    val sameContact = selectedContactId == snapshot.selectedContactId
    return copy(
        contacts = snapshot.contacts,
        chats = snapshot.chats,
        requests = snapshot.requests,
        invitationCode = snapshot.invitationCode,
        // With no invitation left (used or expired) nothing is known about an upload any more.
        invitationUpload = if (snapshot.invitationCode == null) InvitationUpload.UNKNOWN else invitationUpload,
        messages = if (sameContact) snapshot.messages else messages,
        safetyNumber = if (sameContact) snapshot.safetyNumber else safetyNumber,
    )
}

/**
 * UI state holder for the v2 pipeline (DEC-021). In a release build v2 is unavailable, and this
 * class never builds the pipeline, never creates a libsignal identity, and never touches the relay.
 * While the chat screen is on screen (started) it fetches every few seconds; otherwise best-effort
 * background sync (`V2SyncWorker`) takes over, and only it announces new messages (DEC-031), so a
 * text fetched here was always seen in the app. Several coroutines change the state, so every change is an
 * atomic [update]: none of them may write back a stale copy over a newer one (audit SESS-01).
 */
class ConversationV2ViewModel(application: Application) : AndroidViewModel(application) {
    private val available = V2Availability.isAvailable(application)
    private val scheduler = V2SyncScheduler(application)
    private val mutableState = MutableStateFlow(ConversationV2UiState())
    val state: StateFlow<ConversationV2UiState> = mutableState.asStateFlow()

    private var database: SecureMessengerDatabase? = null
    private var service: ConversationV2Service? = null
    private var pollJob: Job? = null

    /**
     * True only while the conversation screen is shown with the app in front: then, and only then,
     * its incoming texts count as seen (DEC-029). The UI sets it from the lifecycle.
     */
    @Volatile
    private var conversationVisible = false

    /**
     * Builds the pipeline and starts fetching. Called each time the chat screen is started (shown,
     * or back from the background), so a local wipe never leaves a stale service behind; [stop]
     * runs when it is stopped. Creating the pipeline also creates this device's libsignal identity
     * if none exists.
     */
    fun start() {
        if (!available) return
        pollJob?.cancel()
        service = null
        mutableState.update { it.copy(ready = false, localDataUnreadable = false) }
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val application = getApplication<Application>()
                val db = database ?: SecureMessengerDatabase.create(application).also { database = it }
                // No profile means the local data was just deleted; the app returns to onboarding.
                service = V2Pipeline.createIfProfileExists(application, db) ?: return@launch
                scheduler.ensurePeriodic()
                refresh()
                mutableState.update { it.copy(ready = true) }
                while (true) {
                    try {
                        // Expiry first, so a failing sync cannot keep expired invitations alive.
                        service?.expireInvitations()
                        service?.synchronize()
                        refresh()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (lost: VaultKeyUnavailableException) {
                        throw lost
                    } catch (_: Exception) {
                        // The relay may be unreachable, or one envelope failed: show what did arrive
                        // and try again on the next tick.
                        refresh()
                    }
                    delay(POLL_INTERVAL_MILLIS)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: VaultKeyUnavailableException) {
                localDataLost()
            } catch (_: ReceiveQueueLostException) {
                // Second review R6 (DEC-032): the app re-checks and explains, like a lost key.
                localDataLost()
            } catch (_: Exception) {
                mutableState.update { it.copy(notice = V2Notice.FAILED) }
            }
        }
    }

    /** Stops foreground fetching: the chat screen was stopped (app in the background) or left, or local data was deleted. */
    fun stop() {
        pollJob?.cancel()
        pollJob = null
        service = null
    }

    fun createInvitation() = run(successNotice = V2Notice.INVITATION_READY, uploadsInvitation = true) { it.createInvitation() }

    /** The person's "try again" for the invitation on screen (audit L2). */
    fun retryInvitationUpload() =
        run(successNotice = V2Notice.INVITATION_READY, uploadsInvitation = true) { it.retryCurrentInvitationUpload() }

    fun addContactFromCode(code: String, localName: String) {
        if (code.isBlank() || localName.isBlank()) {
            mutableState.update { it.copy(notice = V2Notice.INVALID_CODE) }
            return
        }
        run(successNotice = V2Notice.REQUEST_SENT, syncAfterwards = true) { service ->
            val contactId = service.acceptInvitation(code, localName.trim())
            mutableState.update { it.copy(newContactId = contactId) }
        }
    }

    /** The screen has opened the comparison for the contact just added. */
    fun newContactShown() {
        mutableState.update { it.copy(newContactId = null) }
    }

    /**
     * [comparedSafetyNumber] is the number shown with the request when the person confirmed that it
     * matches, or null when they accept without comparing (DEC-030). The service marks the contact
     * verified only if that is still the request's number.
     */
    fun acceptRequest(requestId: String, localName: String, comparedSafetyNumber: String? = null) {
        if (localName.isBlank()) return
        V2Notifications.cancelRequest(getApplication(), requestId)
        run(syncAfterwards = true) { it.acceptRequest(requestId, localName.trim(), comparedSafetyNumber) }
    }

    /** The person confirmed that [shownSafetyNumber], the number on screen, matches the contact's. */
    fun markVerified(contactId: String, shownSafetyNumber: String) = run { it.markVerified(contactId, shownSafetyNumber) }

    fun clearVerified(contactId: String) = run { it.clearVerified(contactId) }

    fun declineRequest(requestId: String) {
        V2Notifications.cancelRequest(getApplication(), requestId)
        run { it.declineRequest(requestId) }
    }

    fun selectContact(contactId: String?) {
        mutableState.update { it.copy(selectedContactId = contactId, messages = emptyList(), safetyNumber = null) }
        viewModelScope.launch(Dispatchers.IO) { refresh() }
    }

    /** The conversation screen came into view with the app in front, or left it (DEC-029). */
    fun setConversationVisible(visible: Boolean) {
        if (conversationVisible == visible) return
        conversationVisible = visible
        if (!visible) return
        // Its notification is answered by what is on screen now (DEC-031).
        mutableState.value.selectedContactId?.let { V2Notifications.cancelChat(getApplication(), it) }
        // What is on screen now counts as seen at once, not only at the next poll.
        viewModelScope.launch(Dispatchers.IO) { refresh() }
    }

    /** [replyToInnerIdHex] names the text this one answers (DEC-035), or null. */
    fun sendText(text: String, replyToInnerIdHex: String? = null) {
        val contactId = mutableState.value.selectedContactId ?: return
        if (text.isBlank()) return
        run(syncAfterwards = true) { it.sendText(contactId, text, replyToInnerIdHex) }
    }

    // --- Chat management from the list and the chat's menu (DEC-035). All local; nothing is sent. ---

    fun setPinned(contactIds: Collection<String>, pinned: Boolean) =
        run { service -> contactIds.forEach { service.setPinned(it, pinned) } }

    /** [mutedUntilEpochMillis] null unmutes; [ConversationV2Service.MUTED_FOREVER] mutes for good. */
    fun setMuted(contactIds: Collection<String>, mutedUntilEpochMillis: Long?) =
        run { service -> contactIds.forEach { service.setMuted(it, mutedUntilEpochMillis) } }

    fun markChatsRead(contactIds: Collection<String>) {
        contactIds.forEach { V2Notifications.cancelChat(getApplication(), it) }
        run { service -> contactIds.forEach { service.markChatRead(it) } }
    }

    fun clearHistory(contactId: String) = run { it.clearHistory(contactId) }

    /** Deletes the contacts with their keys and texts (owner decision, DEC-035), after the person confirmed. */
    fun deleteChats(contactIds: Collection<String>) {
        contactIds.forEach { V2Notifications.cancelChat(getApplication(), it) }
        run { service -> contactIds.forEach { service.deleteContact(it) } }
    }

    fun synchronizeNow() = run(showRefreshing = true) { it.synchronize() }

    fun clearNotice() {
        mutableState.update { it.copy(notice = null) }
    }

    override fun onCleared() {
        pollJob?.cancel()
        database?.close()
        super.onCleared()
    }

    private fun run(
        successNotice: V2Notice? = null,
        syncAfterwards: Boolean = false,
        showRefreshing: Boolean = false,
        uploadsInvitation: Boolean = false,
        block: suspend (ConversationV2Service) -> Unit,
    ) {
        val current = service ?: return
        if (showRefreshing) mutableState.update { it.copy(refreshing = true) }
        if (uploadsInvitation) mutableState.update { it.copy(invitationBusy = true) }
        val job = viewModelScope.launch(Dispatchers.IO) {
            val notice = try {
                block(current)
                if (uploadsInvitation) mutableState.update { it.copy(invitationUpload = InvitationUpload.UPLOADED) }
                successNotice
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: InvitationRejectedException) {
                when (failure.reason) {
                    InvitationRejection.UNSUPPORTED_VERSION -> V2Notice.UNSUPPORTED_VERSION
                    InvitationRejection.NOT_FOUND -> V2Notice.NOT_FOUND
                    InvitationRejection.EXPIRED -> V2Notice.EXPIRED
                    InvitationRejection.MALFORMED,
                    InvitationRejection.SEAL_MISMATCH,
                    InvitationRejection.INVALID_KEYS,
                    -> V2Notice.INVALID_CODE
                }
            } catch (failure: ConversationV2Exception) {
                when (failure.message) {
                    "Own invitation" -> V2Notice.OWN_INVITATION
                    "Already paired" -> V2Notice.ALREADY_PAIRED
                    "Contact is blocked" -> V2Notice.BLOCKED
                    else -> V2Notice.FAILED
                }
            } catch (_: MalformedV2Exception) {
                V2Notice.INVALID_CODE
            } catch (_: java.io.IOException) {
                if (uploadsInvitation) mutableState.update { it.copy(invitationUpload = InvitationUpload.FAILED) }
                V2Notice.RELAY_ERROR
            } catch (_: VaultKeyUnavailableException) {
                localDataLost()
                return@launch
            } catch (_: Exception) {
                V2Notice.FAILED
            }
            // Anything still queued (relay down, app about to close) is retried in the background.
            if (syncAfterwards) scheduler.requestSoon()
            refresh()
            if (notice != null) mutableState.update { it.copy(notice = notice) }
        }
        // Whatever the outcome (done, failed, cancelled), the pull-down spinner and the busy flag must stop.
        if (showRefreshing) job.invokeOnCompletion { mutableState.update { it.copy(refreshing = false) } }
        if (uploadsInvitation) job.invokeOnCompletion { mutableState.update { it.copy(invitationBusy = false) } }
    }

    private suspend fun refresh() = withContext(Dispatchers.IO) {
        val current = service ?: return@withContext
        try {
            val selected = mutableState.value.selectedContactId
            // Shown means seen: only a conversation on screen marks its texts (DEC-029). This runs
            // before the chat list is read, so the list never shows a count for what is on screen.
            val messages = when {
                selected == null -> emptyList()
                conversationVisible -> current.textsMarkingRead(selected)
                else -> current.texts(selected)
            }
            val snapshot = V2Snapshot(
                contacts = current.contacts(),
                chats = current.chatSummaries(),
                requests = current.requests().map { request ->
                    V2RequestView(request, current.safetyNumber(request.requestId), current.heldTexts(request.requestId).size)
                },
                invitationCode = current.currentInvitationCode(),
                selectedContactId = selected,
                messages = messages,
                safetyNumber = selected?.let(current::safetyNumber),
            )
            mutableState.update { it.withSnapshot(snapshot) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: VaultKeyUnavailableException) {
            localDataLost()
        } catch (_: Exception) {
            mutableState.update { it.copy(notice = V2Notice.FAILED) }
        }
    }

    /** DEC-023: stop everything and hand over to the app, which shows why the data is unreadable. */
    private fun localDataLost() {
        mutableState.update { it.copy(ready = false, localDataUnreadable = true) }
        stop()
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 3_000L
    }
}
