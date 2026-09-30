package app.securemessenger.storage

/**
 * The local profile (a vault-encrypted display name) and the local wipe. Conversations live in the
 * v2 tables (`EncryptedConversationV2Repository`) and the libsignal record tables. Blocking by
 * design, like the other repositories; call it off the main thread.
 */
class LocalProfileRepository(
    private val dao: SecureMessengerDao,
    private val vault: LocalVaultCipher,
) {
    fun storeProfile(displayName: String) {
        val encryptedName = vault.encrypt(displayName.toByteArray(), PROFILE_AAD)
        // deviceId belonged to the removed legacy identity; it is kept empty (DEC-021).
        dao.putProfile(EncryptedProfileEntity(PROFILE_ID, encryptedName, deviceId = ""))
    }

    fun hasProfile(): Boolean = dao.getProfile() != null

    /** The local display name, or null before a profile exists. */
    fun readProfileName(): String? =
        dao.getProfile()?.let { vault.decrypt(it.encryptedDisplayName, PROFILE_AAD).toString(Charsets.UTF_8) }

    /**
     * Deletes every local row: profile, conversations, libsignal records. Run it inside one
     * transaction under the protocol lock (the app does this in `V2Pipeline.wipeLocalData`), so no
     * protocol step can commit between the deletes. Callers also delete the vault key and the queue
     * ID. This is not a secure erase (flash storage keeps old blocks).
     */
    fun wipeLocalData() {
        dao.deleteV2AcceptedEnvelopes()
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
    }
}
