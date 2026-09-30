package app.securemessenger.ui

import android.content.Context
import androidx.room.Room
import app.securemessenger.domain.RelayEncryptedBlob
import app.securemessenger.domain.TransportClient
import app.securemessenger.signalprotocol.ConversationV2Service
import app.securemessenger.signalprotocol.ConversationV2Wiring
import app.securemessenger.signalprotocol.EncryptedSignalProtocolStore
import app.securemessenger.signalprotocol.SignalSessions
import app.securemessenger.storage.EncryptedConversationV2Repository
import app.securemessenger.storage.EncryptedSignalStoreRepository
import app.securemessenger.storage.JcaLocalVaultCipher
import app.securemessenger.storage.RoomProtocolTransactionRunner
import app.securemessenger.storage.SecureMessengerDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** An in-memory relay that also keeps every blob it ever received, as an eavesdropping relay would. */
class MemoryRelay : TransportClient {
    private val queued = linkedMapOf<String, RelayEncryptedBlob>()
    val history = mutableListOf<RelayEncryptedBlob>()

    override suspend fun sendMessage(blob: RelayEncryptedBlob) = synchronized(this) {
        queued[blob.messageId] = blob
        history += blob
        Unit
    }

    override suspend fun fetchMessages(queueId: String): List<RelayEncryptedBlob> =
        synchronized(this) { queued.values.filter { it.queueId == queueId } }

    override suspend fun deleteMessage(messageId: String) {
        synchronized(this) { queued.remove(messageId) }
    }

    override fun observeIncomingMessages(queueId: String): Flow<RelayEncryptedBlob> = emptyFlow()
}

/**
 * One device of the v2 pipeline over a real, file-backed Room database and the real encrypted
 * stores. The vault key is a fixed test key (not Android Keystore) so that a "stolen image" can be
 * opened: the step 1f proofs assume the strongest attacker, who also has the vault key.
 */
class RoomV2TestDevice(
    private val context: Context,
    val name: String,
    private val relay: TransportClient,
    private val databaseName: String = "v2-proof-${name.lowercase()}.db",
) {
    private val vaultKey = ByteArray(32) { (it * 7 + name.length).toByte() }
    val queueId = "v2-proof-${name.lowercase()}-queue"
    private var database = open(databaseName)
    lateinit var service: ConversationV2Service
        private set

    init {
        wire()
    }

    /** Closes and reopens the database and rebuilds the pipeline, as an app restart does. */
    fun restart() {
        database.close()
        database = open(databaseName)
        wire()
    }

    /**
     * A complete copy of this device's database file taken now, opened as a separate device
     * state: what a thief with a full disk image (and the vault key) would hold.
     */
    fun stolenSessions(copyName: String): SignalSessions {
        database.close()
        copyDatabase(databaseName, copyName)
        database = open(databaseName)
        wire()
        return sessionsOver(open(copyName))
    }

    fun delete() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    private fun wire() {
        val dao = database.dao()
        val vault = JcaLocalVaultCipher(vaultKey)
        val records = EncryptedSignalStoreRepository(dao, vault)
        service = ConversationV2Wiring.create(
            localIdentity = records,
            identities = records,
            sessions = records,
            preKeys = records,
            signedPreKeys = records,
            kyberPreKeys = records,
            conversations = EncryptedConversationV2Repository(dao, vault),
            transactions = RoomProtocolTransactionRunner(database),
            transport = relay,
            localReceiveQueueId = queueId,
            localDisplayName = { name },
        )
    }

    private fun sessionsOver(copy: SecureMessengerDatabase): SignalSessions {
        val records = EncryptedSignalStoreRepository(copy.dao(), JcaLocalVaultCipher(vaultKey))
        return SignalSessions(EncryptedSignalProtocolStore(records, records, records, records, records, records))
    }

    private fun open(name: String): SecureMessengerDatabase =
        Room.databaseBuilder(context, SecureMessengerDatabase::class.java, name).build()

    private fun copyDatabase(from: String, to: String) {
        context.deleteDatabase(to)
        listOf("", "-wal", "-shm").forEach { suffix ->
            val source = context.getDatabasePath(from + suffix)
            if (source.exists()) source.copyTo(context.getDatabasePath(to + suffix), overwrite = true)
        }
    }
}
