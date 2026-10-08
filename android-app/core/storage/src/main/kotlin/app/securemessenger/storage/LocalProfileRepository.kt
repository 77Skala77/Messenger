package app.securemessenger.storage

/** Whether the stored data can be read with the vault key (DEC-023). */
enum class LocalDataState {
    /** No profile and therefore no vault in use: a new key is created with the next profile. */
    EMPTY,

    /** The profile decrypts: the vault key is present and is the one the data was written with. */
    READABLE,

    /**
     * A profile exists but cannot be decrypted: the vault key is missing, invalidated, or not the
     * one the data was written with, or the profile row is corrupted. Nothing stored can be read
     * again. The app must not run the protocol and must not create a replacement key.
     */
    UNREADABLE,
}

/**
 * The local profile (a vault-encrypted display name) and the local wipe. Conversations live in the
 * v2 tables (`EncryptedConversationV2Repository`) and the libsignal record tables. Blocking by
 * design, like the other repositories; call it off the main thread.
 *
 * The profile row is the vault's canary (DEC-023): it is written first, together with the vault
 * key, and deleted last, and nothing else is stored while no profile exists.
 */
class LocalProfileRepository(
    private val dao: SecureMessengerDao,
    private val vault: LocalVaultCipher,
) {
    /**
     * Creates the profile of a new, empty vault; the vault key is created here and nowhere else.
     * Refuses to replace an existing profile, so a lost key can never be papered over by a new one.
     * One creation at a time in the process, and the row is inserted, never replaced: a second
     * call, even at the same moment, fails instead of writing over the first (audit STORE-04).
     */
    fun createProfile(displayName: String): Unit = synchronized(PROFILE_CREATION_LOCK) {
        check(dao.getProfile() == null) { "A local profile already exists" }
        vault.createKeyIfAbsent()
        val encryptedName = vault.encrypt(displayName.toByteArray(), PROFILE_AAD)
        // deviceId belonged to the removed legacy identity; it is kept empty (DEC-021).
        dao.insertProfile(EncryptedProfileEntity(PROFILE_ID, encryptedName, deviceId = ""))
    }

    fun hasProfile(): Boolean = dao.getProfile() != null

    /** The local display name, or null before a profile exists. Never creates a key. */
    fun readProfileName(): String? =
        dao.getProfile()?.let { vault.decrypt(it.encryptedDisplayName, PROFILE_AAD).toString(Charsets.UTF_8) }

    /**
     * Checks the vault key against the stored data by decrypting the profile. Other failures, such
     * as a Keystore that does not answer right now ([VaultTemporarilyUnavailableException]), are
     * thrown: they may be temporary, and the data must not be declared lost because of them.
     */
    fun localDataState(): LocalDataState {
        val profile = dao.getProfile() ?: return LocalDataState.EMPTY
        return try {
            vault.decrypt(profile.encryptedDisplayName, PROFILE_AAD).fill(0)
            LocalDataState.READABLE
        } catch (_: VaultKeyUnavailableException) {
            LocalDataState.UNREADABLE
        } catch (_: VaultRecordRejectedException) {
            // A key exists, but not the one this data was written with, or the row was damaged.
            LocalDataState.UNREADABLE
        }
    }

    /**
     * Deletes every local row: profile, conversations, libsignal records. Run it inside one
     * transaction under the protocol lock (the app does this in `V2Pipeline.wipeLocalData`), so no
     * protocol step can commit between the deletes. Works without the vault key: nothing is
     * decrypted. Callers also delete the vault key and the queue ID. This is not a secure erase
     * (flash storage keeps old blocks).
     */
    fun wipeLocalData() {
        dao.deleteV2AcceptedEnvelopes()
        dao.deleteAllV2MediaChunks()
        dao.deleteV2Messages()
        dao.deleteV2ContactRequests()
        dao.deleteV2Contacts()
        dao.deleteV2Invitations()
        dao.deleteSignalKyberBaseKeyUses()
        dao.deleteSignalLocalIdentity()
        dao.deleteSignalKyberPreKeyRecords()
        dao.deleteSignalSignedPreKeyRecords()
        dao.deleteSignalPreKeyRecords()
        dao.deleteSignalSessionRecords()
        dao.deleteSignalIdentityRecords()
        dao.deleteProfile()
    }

    private companion object {
        const val PROFILE_ID = "local"
        val PROFILE_AAD = "local-profile-v1".toByteArray()
        val PROFILE_CREATION_LOCK = Any()
    }
}
