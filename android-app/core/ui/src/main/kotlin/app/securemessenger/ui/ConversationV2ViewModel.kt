package app.securemessenger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.securemessenger.signalprotocol.ConversationV2Exception
import app.securemessenger.signalprotocol.ConversationV2Service
import app.securemessenger.signalprotocol.InvitationRejectedException
import app.securemessenger.signalprotocol.InvitationRejection
import app.securemessenger.signalprotocol.MalformedV2Exception
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

class V2RequestView(val request: V2ContactRequest, val safetyNumber: String?, val heldTextCount: Int)

data class ConversationV2UiState(
    val ready: Boolean = false,
    val contacts: List<V2Contact> = emptyList(),
    val requests: List<V2RequestView> = emptyList(),
    val invitationCode: String? = null,
    val selectedContactId: String? = null,
    val messages: List<V2StoredMessage> = emptyList(),
    val safetyNumber: String? = null,
    val notice: V2Notice? = null,
    /** DEC-023: the vault key disappeared while the chat was open; the app must re-check its data. */
    val localDataUnreadable: Boolean = false,
)

/**
 * UI state holder for the v2 pipeline (DEC-021). In a release build v2 is unavailable, and this
 * class never builds the pipeline, never creates a libsignal identity, and never touches the relay.
 * While the chat screen is open it fetches every few seconds; otherwise best-effort background
 * sync (`V2SyncWorker`) takes over.
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
     * Builds the pipeline and starts fetching. Called each time the chat screen enters
     * composition, so a local wipe never leaves a stale service behind. Creating the pipeline also
     * creates this device's libsignal identity if none exists.
     */
    fun start() {
        if (!available) return
        pollJob?.cancel()
        service = null
        mutableState.value = mutableState.value.copy(ready = false, localDataUnreadable = false)
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val application = getApplication<Application>()
                val db = database ?: SecureMessengerDatabase.create(application).also { database = it }
                // No profile means the local data was just deleted; the app returns to onboarding.
                service = V2Pipeline.createIfProfileExists(application, db) ?: return@launch
                scheduler.ensurePeriodic()
                refresh()
                mutableState.value = mutableState.value.copy(ready = true)
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
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(notice = V2Notice.FAILED)
            }
        }
    }

    /** Stops foreground fetching: the chat screen left composition or local data was deleted. */
    fun stop() {
        pollJob?.cancel()
        pollJob = null
        service = null
    }

    fun createInvitation() = run(successNotice = V2Notice.INVITATION_READY) { it.createInvitation() }

    fun addContactFromCode(code: String, localName: String) {
        if (code.isBlank() || localName.isBlank()) {
            mutableState.value = mutableState.value.copy(notice = V2Notice.INVALID_CODE)
            return
        }
        run(successNotice = V2Notice.REQUEST_SENT, syncAfterwards = true) { it.acceptInvitation(code, localName.trim()) }
    }

    fun acceptRequest(requestId: String, localName: String) {
        if (localName.isBlank()) return
        run(syncAfterwards = true) { it.acceptRequest(requestId, localName.trim()) }
    }

    fun declineRequest(requestId: String) = run { it.declineRequest(requestId) }

    fun selectContact(contactId: String?) {
        mutableState.value = mutableState.value.copy(selectedContactId = contactId, messages = emptyList(), safetyNumber = null)
        viewModelScope.launch(Dispatchers.IO) { refresh() }
    }

    fun sendText(text: String) {
        val contactId = mutableState.value.selectedContactId ?: return
        if (text.isBlank()) return
        run(syncAfterwards = true) { it.sendText(contactId, text) }
    }

    fun synchronizeNow() = run { it.synchronize() }

    fun clearNotice() {
        mutableState.value = mutableState.value.copy(notice = null)
    }

    override fun onCleared() {
        pollJob?.cancel()
        database?.close()
        super.onCleared()
    }

    private fun run(
        successNotice: V2Notice? = null,
        syncAfterwards: Boolean = false,
        block: suspend (ConversationV2Service) -> Unit,
    ) {
        val current = service ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val notice = try {
                block(current)
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
            if (notice != null) mutableState.value = mutableState.value.copy(notice = notice)
        }
    }

    private suspend fun refresh() = withContext(Dispatchers.IO) {
        val current = service ?: return@withContext
        try {
            val selected = mutableState.value.selectedContactId
            mutableState.value = mutableState.value.copy(
                contacts = current.contacts(),
                requests = current.requests().map { request ->
                    V2RequestView(request, current.safetyNumber(request.requestId), current.heldTexts(request.requestId).size)
                },
                invitationCode = current.currentInvitationCode(),
                messages = selected?.let(current::texts).orEmpty(),
                safetyNumber = selected?.let(current::safetyNumber),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: VaultKeyUnavailableException) {
            localDataLost()
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(notice = V2Notice.FAILED)
        }
    }

    /** DEC-023: stop everything and hand over to the app, which shows why the data is unreadable. */
    private fun localDataLost() {
        mutableState.value = mutableState.value.copy(ready = false, localDataUnreadable = true)
        stop()
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 3_000L
    }
}
