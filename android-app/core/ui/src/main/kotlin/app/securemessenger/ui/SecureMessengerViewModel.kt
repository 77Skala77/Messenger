package app.securemessenger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.securemessenger.crypto.CryptoFeatureFlags
import app.securemessenger.crypto.EstablishedSession
import app.securemessenger.crypto.InvitationCodec
import app.securemessenger.crypto.PersistentCounterNonceManager
import app.securemessenger.crypto.SessionMessageCodec
import app.securemessenger.crypto.SimplifiedSessionManager
import app.securemessenger.crypto.StandardAead
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
import app.securemessenger.transport.RelayHttpTransportClient
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
    private val transport = RelayHttpTransportClient(RELAY_BASE_URL)
    private val sessions = mutableMapOf<String, EstablishedSession>()
    private val sendingMutexes = mutableMapOf<String, Mutex>()
    private val mutationJobs = mutableSetOf<Job>()
    private var receiverJob: Job? = null
    private var messageJob: Job? = null
    private var devicesJob: Job? = null
    private var selectionJob: Job? = null
    private val mutableState = MutableStateFlow(SecureMessengerUiState())
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
            val (id, encrypted) = sendingMutexes.getOrPut(contactId) { Mutex() }.withLock {
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
                messageId to encryptedMessage
            }
            try {
                transport.sendMessage(
                    RelayEncryptedBlob(
                        messageId = id,
                        queueId = contact.queueId,
                        opaqueCiphertext = SessionMessageCodec.encode(encrypted),
                        expiresAtEpochMillis = System.currentTimeMillis() + RELAY_TTL_MILLIS,
                    ),
                )
                localRepository.updateMessageStatus(id, MessageStatus.SENT.name)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                localRepository.updateMessageStatus(id, MessageStatus.FAILED.name)
            }
        }
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
            mutationJobs.toList().forEach { it.cancelAndJoin() }
            mutationJobs.clear()
            localRepository.wipeLocalData()
            identityProvider.deleteIdentity()
            vault.deleteKey()
            getApplication<Application>().getSharedPreferences("secure_messenger_queue", Application.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
            getApplication<Application>().getSharedPreferences("secure_messenger_nonce_state", Application.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
            sessions.values.forEach { it.rootKey.fill(0) }
            sessions.clear()
            sendingMutexes.clear()
            mutableState.value = SecureMessengerUiState()
        }
    }

    override fun onCleared() {
        sessions.values.forEach { it.rootKey.fill(0) }
        receiverJob?.cancel()
        messageJob?.cancel()
        devicesJob?.cancel()
        selectionJob?.cancel()
        mutationJobs.forEach(Job::cancel)
        database.close()
        super.onCleared()
    }

    private fun localQueueId(): String {
        val preferences = getApplication<Application>()
            .getSharedPreferences("secure_messenger_queue", Application.MODE_PRIVATE)
        return preferences.getString("local_queue_id", null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString("local_queue_id", it).apply()
        }
    }

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
        val wireMessage = try {
            SessionMessageCodec.decode(blob.opaqueCiphertext)
        } catch (_: Exception) {
            return
        }
        for (contact in mutableState.value.contacts) {
            val session = sessions[contact.contactId] ?: try {
                localRepository.loadSession(contact.contactId)?.also { sessions[contact.contactId] = it }
            } catch (_: Exception) {
                null
            } ?: continue
            val authenticatedPlaintext = try {
                sessionManager.decryptStoredMessage(session, wireMessage, outgoing = false)
            } catch (_: Exception) {
                continue
            }
            authenticatedPlaintext.fill(0)
            localRepository.acceptAndStoreIncoming(blob.messageId, contact.contactId, session, wireMessage)
            // Successfully accepted messages and authenticated duplicates no longer need relay retention.
            try {
                transport.deleteMessage(blob.messageId)
            } catch (_: Exception) {
                // The durable replay record makes a later relay retry harmless.
            }
            return
        }
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

    private fun launchMutation(block: suspend () -> Unit) {
        mutationJobs += viewModelScope.launch { block() }
    }

    private companion object {
        const val TIMESTAMP_BUCKET_MILLIS = 60_000L
        const val RELAY_TTL_MILLIS = 24 * 60 * 60 * 1000L
        const val RELAY_BASE_URL = "http://127.0.0.1:8787"
    }
}
