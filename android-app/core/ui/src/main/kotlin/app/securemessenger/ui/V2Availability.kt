package app.securemessenger.ui

import android.content.Context
import app.securemessenger.domain.RelayQueueCredentials
import app.securemessenger.storage.AndroidKeystoreVaultCipher
import app.securemessenger.storage.LocalVaultCipher
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
 * The read secret of this device's relay receive queue (DEC-024). It is a bearer credential, so it
 * is stored vault-encrypted, never in clear; contacts only ever learn the derived queue ID. Created
 * with the first pipeline (under the protocol lock), removed by the local wipe. Blocking.
 */
class V2QueueCredentialStore(
    context: Context,
    private val vault: LocalVaultCipher = AndroidKeystoreVaultCipher(),
) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun getOrCreate(): RelayQueueCredentials {
        preferences.getString(READ_SECRET, null)?.let { stored ->
            val secret = vault.decrypt(Base64.getDecoder().decode(stored), AAD)
            return try {
                RelayQueueCredentials.fromReadSecret(secret)
            } finally {
                secret.fill(0)
            }
        }
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
