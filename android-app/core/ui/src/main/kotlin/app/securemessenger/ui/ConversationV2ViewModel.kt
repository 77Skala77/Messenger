package app.securemessenger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.securemessenger.domain.RelayEncryptedBlob
import app.securemessenger.domain.TransportClient
import app.securemessenger.signalprotocol.ConversationV2Exception
import app.securemessenger.signalprotocol.ConversationV2Service
import app.securemessenger.signalprotocol.ConversationV2Wiring
import app.securemessenger.signalprotocol.InvitationRejectedException
import app.securemessenger.signalprotocol.InvitationRejection
import app.securemessenger.signalprotocol.MalformedV2Exception
import app.securemessenger.signalstore.V2Contact
import app.securemessenger.signalstore.V2ContactRequest
import app.securemessenger.signalstore.V2StoredMessage
import app.securemessenger.storage.AndroidKeystoreVaultCipher
import app.securemessenger.storage.EncryptedConversationV2Repository
import app.securemessenger.storage.EncryptedSignalStoreRepository
import app.securemessenger.storage.RoomProtocolTransactionRunner
import app.securemessenger.storage.SecureLocalRepository
import app.securemessenger.storage.SecureMessengerDatabase
import app.securemessenger.transport.RelayHttpTransportClient
import app.securemessenger.transport.RelayServerSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
)

/**
 * UI state holder for the v2 pipeline (DEC-019). Runs only while the hidden debug QA switch is on:
 * in a release build [allowed] is false and this class never builds the pipeline, never creates a
 * libsignal identity, and never touches the relay. Messages are fetched only while the v2 screen is
 * open; background sync for v2 is not implemented.
 */
class ConversationV2ViewModel(application: Application) : AndroidViewModel(application) {
    private val allowed = application.resources.getBoolean(R.bool.enable_debug_qa_tools)
    private val mutableState = MutableStateFlow(ConversationV2UiState())
    val state: StateFlow<ConversationV2UiState> = mutableState.asStateFlow()

    private var database: SecureMessengerDatabase? = null
    private var service: ConversationV2Service? = null
    private var pollJob: Job? = null

    /**
     * Builds the pipeline and starts fetching. Called each time the v2 screen enters composition, so
     * a local wipe or a switch-off never leaves a stale service behind; creating it also creates
     * this device's libsignal identity if none exists. No-op in a release build.
     */
    fun start() {
        if (!allowed) return
        pollJob?.cancel()
        service = null
        mutableState.value = mutableState.value.copy(ready = false)
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val application = getApplication<Application>()
                val db = database ?: SecureMessengerDatabase.create(application).also { database = it }
                val dao = db.dao()
                val vault = AndroidKeystoreVaultCipher()
                val signalRecords = EncryptedSignalStoreRepository(dao, vault)
                val displayName = SecureLocalRepository(dao, vault).readProfileName().orEmpty()
                val settings = RelayServerSettings(
                    application,
                    application.resources.getBoolean(R.bool.allow_insecure_relay_http),
                )
                service = ConversationV2Wiring.create(
                    localIdentity = signalRecords,
                    identities = signalRecords,
                    sessions = signalRecords,
                    preKeys = signalRecords,
                    signedPreKeys = signalRecords,
                    kyberPreKeys = signalRecords,
                    conversations = EncryptedConversationV2Repository(dao, vault),
                    transactions = RoomProtocolTransactionRunner(db),
                    transport = CurrentRelayTransport(settings),
                    localReceiveQueueId = V2QueueIdStore(application).getOrCreateQueueId(),
                    localDisplayName = { displayName },
                )
                refresh()
                mutableState.value = mutableState.value.copy(ready = true)
                while (true) {
                    try {
                        service?.synchronize()
                        service?.expireInvitations()
                        refresh()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // The relay may be unreachable; try again on the next tick.
                    }
                    delay(POLL_INTERVAL_MILLIS)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(notice = V2Notice.FAILED)
            }
        }
    }

    fun createInvitation() = run(successNotice = V2Notice.INVITATION_READY) { it.createInvitation() }

    fun addContactFromCode(code: String, localName: String) {
        if (code.isBlank() || localName.isBlank()) {
            mutableState.value = mutableState.value.copy(notice = V2Notice.INVALID_CODE)
            return
        }
        run(successNotice = V2Notice.REQUEST_SENT) { it.acceptInvitation(code, localName.trim()) }
    }

    fun acceptRequest(requestId: String, localName: String) {
        if (localName.isBlank()) return
        run { it.acceptRequest(requestId, localName.trim()) }
    }

    fun declineRequest(requestId: String) = run { it.declineRequest(requestId) }

    fun selectContact(contactId: String?) {
        mutableState.value = mutableState.value.copy(selectedContactId = contactId, messages = emptyList(), safetyNumber = null)
        viewModelScope.launch(Dispatchers.IO) { refresh() }
    }

    fun sendText(text: String) {
        val contactId = mutableState.value.selectedContactId ?: return
        if (text.isBlank()) return
        run { it.sendText(contactId, text) }
    }

    fun synchronizeNow() = run { it.synchronize() }

    fun clearNotice() {
        mutableState.value = mutableState.value.copy(notice = null)
    }

    /** Stops fetching: the v2 screen left composition, the switch was turned off, or data was deleted. */
    fun stop() {
        pollJob?.cancel()
        pollJob = null
        service = null
    }

    override fun onCleared() {
        pollJob?.cancel()
        database?.close()
        super.onCleared()
    }

    private fun run(successNotice: V2Notice? = null, block: suspend (ConversationV2Service) -> Unit) {
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
            } catch (_: Exception) {
                V2Notice.FAILED
            }
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
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(notice = V2Notice.FAILED)
        }
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 3_000L
    }
}

/** Follows relay URL changes made in Settings without rebuilding the pipeline. */
private class CurrentRelayTransport(private val settings: RelayServerSettings) : TransportClient {
    private var url = ""
    private var client: RelayHttpTransportClient? = null

    @Synchronized
    private fun client(): RelayHttpTransportClient {
        val current = settings.currentUrl()
        return client.takeIf { current == url } ?: RelayHttpTransportClient(current).also {
            client = it
            url = current
        }
    }

    override suspend fun sendMessage(blob: RelayEncryptedBlob) = client().sendMessage(blob)

    override suspend fun fetchMessages(queueId: String): List<RelayEncryptedBlob> = client().fetchMessages(queueId)

    override suspend fun deleteMessage(messageId: String) = client().deleteMessage(messageId)

    override fun observeIncomingMessages(queueId: String): Flow<RelayEncryptedBlob> = client().observeIncomingMessages(queueId)
}
