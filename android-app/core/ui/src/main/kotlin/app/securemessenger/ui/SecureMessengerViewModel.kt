package app.securemessenger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.securemessenger.crypto.CryptoFeatureFlags
import app.securemessenger.crypto.EstablishedSession
import app.securemessenger.crypto.InvitationCodec
import app.securemessenger.crypto.PersistentCounterNonceManager
import app.securemessenger.crypto.SimplifiedSessionManager
import app.securemessenger.crypto.StandardAead
import app.securemessenger.delivery.IncomingDeliveryProcessor
import app.securemessenger.delivery.OutgoingAttemptResult
import app.securemessenger.delivery.OutgoingDeliveryProcessor
import app.securemessenger.delivery.PrimarySessionOnlyFanOutPlanner
import app.securemessenger.delivery.ReceiveBinding
import app.securemessenger.domain.Contact
import app.securemessenger.domain.ContactDevice
import app.securemessenger.domain.DisplayMessage
import app.securemessenger.domain.EncryptionMode
import app.securemessenger.domain.MessageStatus
import app.securemessenger.domain.RelayEncryptedBlob
import app.securemessenger.domain.VerificationStatus
import app.securemessenger.storage.AndroidKeystoreIdentityProvider
import app.securemessenger.storage.AndroidKeystoreVaultCipher
import app.securemessenger.storage.SecureLocalRepository
import app.securemessenger.storage.SecureMessengerDatabase
import app.securemessenger.storage.SharedPreferencesNonceStateStore
import app.securemessenger.transport.RelayHealthStatus
import app.securemessenger.transport.RelayHttpTransportClient
import app.securemessenger.transport.RelayServerSettings
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class UiNotice {
    INVALID_INVITATION,
    CONTACT_ADDED,
    DEVICE_STAGED,
    SESSION_UNAVAILABLE,
    DELIVERY_BLOCKED,
    SYNC_REQUESTED,
}

data class SecureMessengerUiState(
    val profileCreated: Boolean = false,
    val invitationCode: String = "",
    val contacts: List<Contact> = emptyList(),
    val selectedContactId: String? = null,
    val messages: Map<String, List<DisplayMessage>> = emptyMap(),
    val devices: Map<String, List<ContactDevice>> = emptyMap(),
    val encryptionMode: EncryptionMode = EncryptionMode.STANDARD_AEAD,
    val paranoiaAvailable: Boolean = CryptoFeatureFlags.PARANOIA_C4_AEAD_AVAILABLE,
    val relayServerUrl: String = RelayServerSettings.DEFAULT_EMULATOR_URL,
    val relayHealthStatus: RelayHealthStatus? = null,
    val notice: UiNotice? = null,
)

class SecureMessengerViewModel(application: Application) : AndroidViewModel(application) {
    private val database = SecureMessengerDatabase.create(application)
    private val dao = database.dao()
    private val vault = AndroidKeystoreVaultCipher()
    private val localRepository = SecureLocalRepository(dao, vault)
    private val identityProvider = AndroidKeystoreIdentityProvider(application)
    private val standardAead = StandardAead(
        PersistentCounterNonceManager(SharedPreferencesNonceStateStore(application)),
    )
    private val sessionManager = SimplifiedSessionManager(standardAead)
    private val relayServerSettings = RelayServerSettings(application)
    private val queueIdStore = LocalQueueIdStore(application)
    private val syncScheduler: DeliverySyncScheduler = WorkManagerDeliverySyncScheduler(application)
    private var transport = RelayHttpTransportClient(relayServerSettings.currentUrl())
    private val fanOutPlanner = PrimarySessionOnlyFanOutPlanner()
    private var outgoingDelivery = OutgoingDeliveryProcessor(localRepository, transport, fanOutPlanner)
    private var incomingDelivery = IncomingDeliveryProcessor(localRepository, transport, sessionManager)
    private val sessions = mutableMapOf<String, EstablishedSession>()
    private val sendingMutexes = mutableMapOf<String, Mutex>()
    private val mutationJobs = mutableSetOf<Job>()
    private var receiverJob: Job? = null
    private var messageJob: Job? = null
    private var devicesJob: Job? = null
    private var selectionJob: Job? = null
    private var retryJob: Job? = null
    private val mutableState = MutableStateFlow(
        SecureMessengerUiState(relayServerUrl = relayServerSettings.currentUrl()),
    )
    val state: StateFlow<SecureMessengerUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            if (dao.getProfile() != null) {
                val identity = identityProvider.loadOrCreate()
                mutableState.value = mutableState.value.copy(
                    profileCreated = true,
                    invitationCode = sessionManager.createInvitation(identity.publicIdentity, localQueueId()),
                )
                startIncomingPolling()
                startForegroundRetryLoop()
                syncScheduler.ensurePeriodicSync()
            }
            dao.observeContacts().collectLatest { entities ->
                val contacts = entities.map(localRepository::readContact)
                mutableState.value = mutableState.value.copy(contacts = contacts)
            }
        }
    }

    fun createProfile(displayName: String) {
        if (displayName.isBlank()) return
        launchMutation {
            val identity = identityProvider.loadOrCreate()
            localRepository.storeProfile(displayName.trim(), identity.publicIdentity.deviceId)
            mutableState.value = mutableState.value.copy(
                profileCreated = true,
                invitationCode = sessionManager.createInvitation(identity.publicIdentity, localQueueId()),
            )
            startIncomingPolling()
            startForegroundRetryLoop()
            syncScheduler.ensurePeriodicSync()
        }
    }

    fun importInvitation(encoded: String, localContactName: String) {
        if (encoded.isBlank() || localContactName.isBlank()) {
            mutableState.value = mutableState.value.copy(notice = UiNotice.INVALID_INVITATION)
            return
        }
        launchMutation {
            try {
                val invitation = InvitationCodec.decode(encoded.trim())
                val existing = mutableState.value.contacts.firstOrNull {
                    it.identity.deviceId == invitation.identity.deviceId
                }
                val status = when {
                    existing == null -> VerificationStatus.UNVERIFIED
                    existing.identity.fingerprint != invitation.identity.fingerprint -> VerificationStatus.KEY_CHANGED
                    else -> existing.verificationStatus
                }
                val contactId = existing?.contactId ?: UUID.randomUUID().toString()
                val contact = Contact(
                    contactId = contactId,
                    localDisplayName = localContactName.trim(),
                    queueId = invitation.queueId,
                    identity = invitation.identity,
                    verificationStatus = status,
                )
                localRepository.storeContact(contact)
                if (existing == null) {
                    val session = sessionManager.acceptInvitation(identityProvider.loadOrCreate(), encoded.trim())
                    sessions[contactId] = session
                    localRepository.storeSession(contactId, session)
                } else if (status == VerificationStatus.KEY_CHANGED) {
                    sessions.remove(contactId)?.rootKey?.fill(0)
                }
                mutableState.value = mutableState.value.copy(notice = UiNotice.CONTACT_ADDED)
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(notice = UiNotice.INVALID_INVITATION)
            }
        }
    }

    fun selectContact(contactId: String) {
        mutableState.value = mutableState.value.copy(selectedContactId = contactId, notice = null)
        val contact = mutableState.value.contacts.firstOrNull { it.contactId == contactId } ?: return
        selectionJob?.cancel()
        selectionJob = viewModelScope.launch {
            val session = sessions[contactId] ?: try {
                localRepository.loadSession(contactId)
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(notice = UiNotice.SESSION_UNAVAILABLE)
                null
            }
            if (session == null) return@launch
            sessions[contactId] = session
            observeMessages(contact, session)
            observeDevices(contactId)
        }
    }

    fun sendMessage(plaintext: String) {
        val contactId = mutableState.value.selectedContactId ?: return
        val session = sessions[contactId] ?: return
        val contact = mutableState.value.contacts.firstOrNull { it.contactId == contactId } ?: return
        if (plaintext.isBlank() || contact.verificationStatus == VerificationStatus.KEY_CHANGED) return
        launchMutation {
            if (fanOutPlanner.targetsForSingleDeviceCiphertext(contact, localRepository.deliveryDevices(contactId)).size != 1) {
                mutableState.value = mutableState.value.copy(notice = UiNotice.DELIVERY_BLOCKED)
                return@launchMutation
            }
            val id = sendingMutexes.getOrPut(contactId) { Mutex() }.withLock {
                val timestampBucket = System.currentTimeMillis() / TIMESTAMP_BUCKET_MILLIS
                val plaintextBytes = plaintext.toByteArray()
                val encryptedMessage = try {
                    sessionManager.encryptMessage(session, plaintextBytes, timestampBucket)
                } finally {
                    plaintextBytes.fill(0)
                }
                val messageId = UUID.randomUUID().toString()
                // Persist the incremented counter before any delivery attempt to prevent nonce reuse after restart.
                localRepository.storeSession(contactId, session)
                localRepository.storeEncryptedMessage(
                    messageId,
                    contactId,
                    encryptedMessage,
                    outgoing = true,
                    MessageStatus.PENDING.name,
                )
                messageId
            }
            if (outgoingDelivery.attemptMessage(id) == OutgoingAttemptResult.RETRY_SCHEDULED) {
                syncScheduler.requestRetry()
            }
        }
    }

    fun saveRelayServerUrl(url: String) {
        viewModelScope.launch {
            val savedUrl = relayServerSettings.saveUrl(url)
            if (savedUrl == null) {
                mutableState.value = mutableState.value.copy(relayHealthStatus = RelayHealthStatus.CONNECTION_ERROR)
                return@launch
            }
            receiverJob?.cancelAndJoin()
            receiverJob = null
            transport = RelayHttpTransportClient(savedUrl)
            outgoingDelivery = OutgoingDeliveryProcessor(localRepository, transport, fanOutPlanner)
            incomingDelivery = IncomingDeliveryProcessor(localRepository, transport, sessionManager)
            mutableState.value = mutableState.value.copy(relayServerUrl = savedUrl, relayHealthStatus = null)
            if (mutableState.value.profileCreated) startIncomingPolling()
        }
    }

    fun checkRelayServer(url: String) {
        viewModelScope.launch {
            val normalized = RelayServerSettings.normalize(url)
            val status = if (normalized == null) {
                RelayHealthStatus.CONNECTION_ERROR
            } else {
                RelayHttpTransportClient(normalized).checkHealth()
            }
            mutableState.value = mutableState.value.copy(relayHealthStatus = status)
        }
    }

    fun synchronizeNow() {
        syncScheduler.synchronizeNow()
        mutableState.value = mutableState.value.copy(notice = UiNotice.SYNC_REQUESTED)
    }

    fun verifySelectedContact() {
        val contactId = mutableState.value.selectedContactId ?: return
        val contact = mutableState.value.contacts.firstOrNull { it.contactId == contactId } ?: return
        launchMutation {
            if (contact.verificationStatus == VerificationStatus.KEY_CHANGED) {
                val newSession = sessionManager.createSession(identityProvider.loadOrCreate(), contact.identity)
                sessions.remove(contactId)?.rootKey?.fill(0)
                sessions[contactId] = newSession
                localRepository.storeSession(contactId, newSession)
                observeMessages(contact, newSession)
            }
            localRepository.storeContact(contact.copy(verificationStatus = VerificationStatus.VERIFIED))
            localRepository.approveDevice(contactId, contact.identity)
        }
    }

    fun stageDeviceInvitation(encoded: String) {
        val contactId = mutableState.value.selectedContactId ?: return
        if (encoded.isBlank()) return
        launchMutation {
            try {
                val invitation = InvitationCodec.decode(encoded.trim())
                localRepository.stageNewDevice(contactId, invitation)
                mutableState.value = mutableState.value.copy(notice = UiNotice.DEVICE_STAGED)
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(notice = UiNotice.INVALID_INVITATION)
            }
        }
    }

    fun approveDevice(device: ContactDevice) {
        launchMutation {
            localRepository.approveDevice(device.contactId, device.identity)
        }
    }

    fun revokeDevice(device: ContactDevice) {
        launchMutation {
            localRepository.revokeDevice(device.contactId, device.identity)
        }
    }

    fun clearNotice() {
        mutableState.value = mutableState.value.copy(notice = null)
    }

    fun deleteLocalData() {
        viewModelScope.launch {
            receiverJob?.cancelAndJoin()
            receiverJob = null
            messageJob?.cancelAndJoin()
            messageJob = null
            devicesJob?.cancelAndJoin()
            devicesJob = null
            selectionJob?.cancelAndJoin()
            selectionJob = null
            retryJob?.cancelAndJoin()
            retryJob = null
            mutationJobs.toList().forEach { it.cancelAndJoin() }
            mutationJobs.clear()
            localRepository.wipeLocalData()
            syncScheduler.cancel()
            identityProvider.deleteIdentity()
            vault.deleteKey()
            queueIdStore.clear()
            getApplication<Application>().getSharedPreferences("secure_messenger_nonce_state", Application.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
            sessions.values.forEach { it.rootKey.fill(0) }
            sessions.clear()
            sendingMutexes.clear()
            mutableState.value = SecureMessengerUiState(relayServerUrl = relayServerSettings.currentUrl())
        }
    }

    override fun onCleared() {
        sessions.values.forEach { it.rootKey.fill(0) }
        receiverJob?.cancel()
        messageJob?.cancel()
        devicesJob?.cancel()
        selectionJob?.cancel()
        retryJob?.cancel()
        mutationJobs.forEach(Job::cancel)
        database.close()
        super.onCleared()
    }

    private fun localQueueId(): String = queueIdStore.getOrCreateQueueId()

    private fun startIncomingPolling() {
        if (receiverJob != null) return
        receiverJob = viewModelScope.launch {
            transport.observeIncomingMessages(localQueueId()).collect { blob ->
                try {
                    processIncoming(blob)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Retain relay ciphertext for retry after a local persistence failure.
                }
            }
        }
    }

    private suspend fun processIncoming(blob: RelayEncryptedBlob) {
        val bindings = mutableState.value.contacts.mapNotNull { contact ->
            val session = sessions[contact.contactId] ?: try {
                localRepository.loadSession(contact.contactId)?.also { sessions[contact.contactId] = it }
            } catch (_: Exception) {
                null
            }
            session?.let { ReceiveBinding(contact.contactId, it) }
        }
        incomingDelivery.processFetched(blob, bindings)
    }

    private fun observeMessages(contact: Contact, session: EstablishedSession) {
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            dao.observeMessages(contact.contactId).collectLatest { entities ->
                val visible = entities.mapNotNull { entity ->
                    try {
                        val encrypted = localRepository.readEncryptedMessage(entity)
                        val plaintext = sessionManager.decryptStoredMessage(session, encrypted, entity.outgoing)
                        val displayText = plaintext.toString(Charsets.UTF_8)
                        plaintext.fill(0)
                        DisplayMessage(
                            id = entity.messageId,
                            contactId = entity.contactId,
                            plaintextInMemoryOnly = displayText,
                            outgoing = entity.outgoing,
                            status = MessageStatus.valueOf(entity.status),
                            createdAtEpochMillis = entity.createdAtEpochMillis,
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
                mutableState.value = mutableState.value.copy(
                    messages = mutableState.value.messages + (contact.contactId to visible),
                )
            }
        }
    }

    private fun observeDevices(contactId: String) {
        devicesJob?.cancel()
        devicesJob = viewModelScope.launch {
            localRepository.observeDevices(contactId).collectLatest { devices ->
                mutableState.value = mutableState.value.copy(
                    devices = mutableState.value.devices + (contactId to devices),
                )
            }
        }
    }

    private fun startForegroundRetryLoop() {
        if (retryJob != null) return
        retryJob = viewModelScope.launch {
            while (true) {
                try {
                    if (outgoingDelivery.retryDueMessages().any { it == OutgoingAttemptResult.RETRY_SCHEDULED }) {
                        syncScheduler.requestRetry()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Keep pending ciphertext queued for a later foreground attempt.
                }
                delay(RETRY_POLL_INTERVAL_MILLIS)
            }
        }
    }

    private fun launchMutation(block: suspend () -> Unit) {
        mutationJobs += viewModelScope.launch { block() }
    }

    private companion object {
        const val TIMESTAMP_BUCKET_MILLIS = 60_000L
        const val RETRY_POLL_INTERVAL_MILLIS = 15_000L
    }
}
