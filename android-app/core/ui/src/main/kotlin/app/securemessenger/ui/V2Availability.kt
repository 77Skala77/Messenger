package app.securemessenger.ui

import android.content.Context
import app.securemessenger.domain.RelayQueueCredentials
import app.securemessenger.storage.AndroidKeystoreVaultCipher
import app.securemessenger.storage.LocalVaultCipher
import app.securemessenger.storage.VaultRecordRejectedException
import java.util.Base64

/**
 * The v2 protocol is the app's only chat path, and it is available in debug builds only until the
 * independent review of Phase 5 (DEC-021): `enable_v2_protocol` is `false` in the main resources and
 * `true` only in the debug resources. A release build never constructs the pipeline.
 */
object V2Availability {
    fun isAvailable(context: Context): Boolean = context.resources.getBoolean(R.bool.enable_v2_protocol)
}

/**
 * The secret of this phone's relay inbox is gone while contacts, requests or invitations still point
 * to that inbox (second review R6): whatever they send can no longer be read, and a new inbox would
 * stay unknown to them. Nothing runs; the person is told and may start over (DEC-032).
 */
class ReceiveQueueLostException : IllegalStateException("The receive queue secret is missing or damaged")

/**
 * The read secret of this device's relay receive queue (DEC-024). It is a bearer credential, so it
 * is stored vault-encrypted, never in clear; contacts only ever learn the derived queue ID. Created
 * with the first pipeline (under the protocol lock), removed by the local wipe, and never replaced
 * once anyone may know the queue (R6, DEC-032). Blocking.
 */
class V2QueueCredentialStore(
    context: Context,
    private val vault: LocalVaultCipher = AndroidKeystoreVaultCipher(),
) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    /**
     * The stored queue, or null if none is stored. A stored secret that is not valid base64, does
     * not authenticate, or has the wrong length is a damaged record: [VaultRecordRejectedException]
     * (DEC-023), never a generic failure (Phase 5 F10).
     */
    fun stored(): RelayQueueCredentials? {
        val stored = preferences.getString(READ_SECRET, null) ?: return null
        val blob = try {
            Base64.getDecoder().decode(stored)
        } catch (failure: IllegalArgumentException) {
            throw VaultRecordRejectedException(failure)
        }
        val secret = vault.decrypt(blob, AAD)
        return try {
            RelayQueueCredentials.fromReadSecret(secret)
        } catch (failure: IllegalArgumentException) {
            throw VaultRecordRejectedException(failure)
        } finally {
            secret.fill(0)
        }
    }

    /**
     * The queue to use: the stored one, or a new one only while nobody can know the old one
     * ([knownToSomeone] false: no contact, request or invitation). Otherwise a missing or damaged
     * secret is [ReceiveQueueLostException]: a silent new queue would leave every contact writing
     * into the old one (second review R6).
     */
    fun getOrCreate(knownToSomeone: Boolean): RelayQueueCredentials {
        val existing = try {
            stored()
        } catch (_: VaultRecordRejectedException) {
            if (knownToSomeone) throw ReceiveQueueLostException() else null
        }
        if (existing != null) return existing
        if (knownToSomeone) throw ReceiveQueueLostException()
        val created = RelayQueueCredentials.generate()
        val secret = created.readSecret()
        try {
            val encrypted = Base64.getEncoder().encodeToString(vault.encrypt(secret, AAD))
            check(preferences.edit().putString(READ_SECRET, encrypted).commit()) { "Queue secret not stored" }
        } finally {
            secret.fill(0)
        }
        return created
    }

    fun clear() {
        preferences.edit().clear().commit()
    }

    internal companion object {
        const val PREFERENCES = "secure_messenger_v2_queue"
        const val READ_SECRET = "local_queue_read_secret_v3"

        /** Pre-DEC-024 plain queue ID; removed once by [LegacyReset]. */
        const val OLD_QUEUE_ID = "local_queue_id"
        val AAD = "relay-queue-read-secret-v1".toByteArray()
    }
}
