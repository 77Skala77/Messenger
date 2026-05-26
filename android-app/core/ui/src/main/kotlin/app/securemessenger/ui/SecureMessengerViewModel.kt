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
import app.securemessenger.domain.Contact
import app.securemessenger.domain.DisplayMessage
import app.securemessenger.domain.EncryptionMode
import app.securemessenger.domain.MessageStatus
import app.securemessenger.domain.VerificationStatus
import app.securemessenger.storage.AndroidKeystoreIdentityProvider
import app.securemessenger.storage.AndroidKeystoreVaultCipher
import app.securemessenger.storage.SecureLocalRepository
import app.securemessenger.storage.SecureMessengerDatabase
import app.securemessenger.storage.SharedPreferencesNonceStateStore
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class UiNotice {
    INVALID_INVITATION,
    CONTACT_ADDED,
}

data class SecureMessengerUiState(
    val profileCreated: Boolean = false,
    val invitationCode: String = "",
    val contacts: List<Contact> = emptyList(),
    val selectedContactId: String? = null,
    val messages: Map<String, List<DisplayMessage>> = emptyMap(),
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
    private val sessions = mutableMapOf<String, EstablishedSession>()
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
            }
            dao.observeContacts().collectLatest { entities ->
                val contacts = entities.map(localRepository::readContact)
                mutableState.value = mutableState.value.copy(contacts = contacts)
            }
        }
    }

    fun createProfile(displayName: String) {
        if (displayName.isBlank()) return
        viewModelScope.launch {
            val identity = identityProvider.loadOrCreate()
            localRepository.storeProfile(displayName.trim(), identity.publicIdentity.deviceId)
            mutableState.value = mutableState.value.copy(
                profileCreated = true,
                invitationCode = sessionManager.createInvitation(identity.publicIdentity, localQueueId()),
            )
        }
    }

    fun importInvitation(encoded: String, localContactName: String) {
        if (encoded.isBlank() || localContactName.isBlank()) {
            mutableState.value = mutableState.value.copy(notice = UiNotice.INVALID_INVITATION)
            return
        }
        viewModelScope.launch {
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
                val session = sessionManager.acceptInvitation(identityProvider.loadOrCreate(), encoded.trim())
                sessions[contactId] = session
                localRepository.storeContact(contact)
                localRepository.storeSession(contactId, session)
                mutableState.value = mutableState.value.copy(notice = UiNotice.CONTACT_ADDED)
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(notice = UiNotice.INVALID_INVITATION)
            }
        }
    }

    fun selectContact(contactId: String) {
        mutableState.value = mutableState.value.copy(selectedContactId = contactId, notice = null)
        if (sessions[contactId] == null) {
            val contact = mutableState.value.contacts.firstOrNull { it.contactId == contactId } ?: return
            sessions[contactId] = sessionManager.createSession(identityProvider.loadOrCreate(), contact.identity)
        }
    }

    fun sendMessage(plaintext: String) {
        val contactId = mutableState.value.selectedContactId ?: return
        val session = sessions[contactId] ?: return
        if (plaintext.isBlank()) return
        viewModelScope.launch {
            val timestampBucket = System.currentTimeMillis() / TIMESTAMP_BUCKET_MILLIS
            val plaintextBytes = plaintext.toByteArray()
            val encrypted = try {
                sessionManager.encryptMessage(session, plaintextBytes, timestampBucket)
            } finally {
                plaintextBytes.fill(0)
            }
            val id = UUID.randomUUID().toString()
            localRepository.storeEncryptedMessage(id, contactId, encrypted, outgoing = true, MessageStatus.PENDING.name)
            val visibleMessage = DisplayMessage(
                id = id,
                contactId = contactId,
                plaintextInMemoryOnly = plaintext,
                outgoing = true,
                status = MessageStatus.PENDING,
                createdAtEpochMillis = System.currentTimeMillis(),
            )
            val existing = mutableState.value.messages[contactId].orEmpty()
            mutableState.value = mutableState.value.copy(
                messages = mutableState.value.messages + (contactId to (existing + visibleMessage)),
            )
        }
    }

    fun verifySelectedContact() {
        val contactId = mutableState.value.selectedContactId ?: return
        val contact = mutableState.value.contacts.firstOrNull { it.contactId == contactId } ?: return
        viewModelScope.launch {
            localRepository.storeContact(contact.copy(verificationStatus = VerificationStatus.VERIFIED))
        }
    }

    fun clearNotice() {
        mutableState.value = mutableState.value.copy(notice = null)
    }

    fun deleteLocalData() {
        viewModelScope.launch {
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
            mutableState.value = SecureMessengerUiState()
        }
    }

    override fun onCleared() {
        sessions.values.forEach { it.rootKey.fill(0) }
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

    private companion object {
        const val TIMESTAMP_BUCKET_MILLIS = 60_000L
    }
}
