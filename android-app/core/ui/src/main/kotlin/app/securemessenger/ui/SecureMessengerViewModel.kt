package app.securemessenger.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.securemessenger.storage.AndroidKeystoreVaultCipher
import app.securemessenger.storage.LocalDataState
import app.securemessenger.storage.LocalProfileRepository
import app.securemessenger.storage.SecureMessengerDatabase
import app.securemessenger.transport.RelayHealthStatus
import app.securemessenger.transport.RelayHttpTransportClient
import app.securemessenger.transport.RelayServerSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Why the app cannot use the stored data (DEC-023). While set, nothing else is shown or run. */
enum class LocalDataProblem {
    /** The vault key is missing or does not match the data: it can never be read again. */
    UNREADABLE,

    /** Android Keystore or the database did not answer. Nothing is declared lost; try again. */
    TEMPORARY,
}

data class SecureMessengerUiState(
    val profileKnown: Boolean = false,
    val profileCreated: Boolean = false,
    val localDataProblem: LocalDataProblem? = null,
    val relayServerUrl: String = RelayServerSettings.DEFAULT_EMULATOR_URL,
    val relayHealthStatus: RelayHealthStatus? = null,
    val allowInsecureRelayHttp: Boolean = false,
    val debugQaToolsEnabled: Boolean = false,
    val v2Available: Boolean = false,
    val isPhysicalDevice: Boolean = true,
    val legacyResetNotice: Boolean = false,
)

/**
 * App-level state: profile, relay settings, local wipe, and the check that the stored data can be
 * read (DEC-023). Conversations are handled by [ConversationV2ViewModel]; the legacy prototype
 * path was removed (DEC-021).
 */
class SecureMessengerViewModel(application: Application) : AndroidViewModel(application) {
    private val database = SecureMessengerDatabase.create(application)
    private val profiles = LocalProfileRepository(database.dao(), AndroidKeystoreVaultCipher())
    private val allowInsecureRelayHttp = application.resources.getBoolean(R.bool.allow_insecure_relay_http)
    private val relayServerSettings = RelayServerSettings(application, allowInsecureRelayHttp)
    private val scheduler = V2SyncScheduler(application)
    private val mutableState = MutableStateFlow(
        SecureMessengerUiState(
            relayServerUrl = relayServerSettings.currentUrl(),
            allowInsecureRelayHttp = allowInsecureRelayHttp,
            debugQaToolsEnabled = application.resources.getBoolean(R.bool.enable_debug_qa_tools),
            v2Available = V2Availability.isAvailable(application),
            isPhysicalDevice = isProbablyPhysicalDevice(),
            legacyResetNotice = LegacyReset.isNoticePending(application),
        ),
    )
    val state: StateFlow<SecureMessengerUiState> = mutableState.asStateFlow()

    init {
        checkLocalData()
    }

    /**
     * Checks that the stored data can be read before anything uses it (DEC-023). With a problem,
     * no chat or background sync runs and no key is created; the app explains what happened.
     */
    fun checkLocalData() {
        viewModelScope.launch {
            // Opening the database also runs MIGRATION_7_8, the database side of the legacy reset.
            val checked = withContext(Dispatchers.IO) {
                try {
                    profiles.localDataState()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
            val problem = when (checked) {
                null -> LocalDataProblem.TEMPORARY
                LocalDataState.UNREADABLE -> LocalDataProblem.UNREADABLE
                LocalDataState.EMPTY, LocalDataState.READABLE -> null
            }
            mutableState.value = mutableState.value.copy(
                profileKnown = true,
                profileCreated = checked != LocalDataState.EMPTY,
                localDataProblem = problem,
            )
            if (checked == LocalDataState.READABLE) scheduler.ensurePeriodic() else scheduler.cancel()
        }
    }

    fun createProfile(displayName: String) {
        if (displayName.isBlank()) return
        viewModelScope.launch {
            val created = withContext(Dispatchers.IO) {
                try {
                    profiles.createProfile(displayName.trim())
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
            }
            if (created) {
                mutableState.value = mutableState.value.copy(profileCreated = true)
                scheduler.ensurePeriodic()
            } else {
                checkLocalData()
            }
        }
    }

    fun saveRelayServerUrl(url: String) {
        viewModelScope.launch {
            val saved = relayServerSettings.saveUrl(url)
            mutableState.value = if (saved == null) {
                mutableState.value.copy(relayHealthStatus = RelayHealthStatus.CONNECTION_ERROR)
            } else {
                mutableState.value.copy(relayServerUrl = saved, relayHealthStatus = null)
            }
        }
    }

    fun checkRelayServer(url: String) {
        viewModelScope.launch {
            val normalized = RelayServerSettings.normalize(url, allowInsecureRelayHttp)
            val status = if (normalized == null) {
                RelayHealthStatus.CONNECTION_ERROR
            } else {
                RelayHttpTransportClient(normalized).checkHealth()
            }
            mutableState.value = mutableState.value.copy(relayHealthStatus = status)
        }
    }

    fun synchronizeInBackground() = scheduler.requestSoon()

    fun dismissLegacyResetNotice() {
        LegacyReset.dismissNotice(getApplication())
        mutableState.value = mutableState.value.copy(legacyResetNotice = false)
    }

    /**
     * Deletes the profile, every conversation and libsignal record, the vault key and the v2 queue
     * ID, under the protocol lock ([V2Pipeline.wipeLocalData]). Works without the vault key, so it
     * is also the way out of [LocalDataProblem.UNREADABLE]. The relay URL is kept. Not a secure
     * erase: flash storage can keep old blocks, but they cannot be decrypted without the key.
     */
    fun deleteLocalData() {
        viewModelScope.launch {
            scheduler.cancel()
            val wiped = withContext(Dispatchers.IO) {
                try {
                    V2Pipeline.wipeLocalData(getApplication(), database)
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
            }
            if (wiped) {
                mutableState.value = mutableState.value.copy(
                    profileCreated = false,
                    localDataProblem = null,
                    relayHealthStatus = null,
                )
            } else {
                checkLocalData()
            }
        }
    }

    fun resetTestDataForQa() {
        if (!mutableState.value.debugQaToolsEnabled) return
        deleteLocalData()
    }

    override fun onCleared() {
        database.close()
        super.onCleared()
    }

    private fun isProbablyPhysicalDevice(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        return !(fingerprint.contains("generic") || fingerprint.contains("emulator") || model.contains("sdk_gphone"))
    }
}
