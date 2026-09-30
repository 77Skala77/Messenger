package app.securemessenger.ui

import android.content.Context
import androidx.room.Room
import app.securemessenger.domain.RelayEncryptedBlob
import app.securemessenger.domain.RelayQueueCredentials
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
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import app.securemessenger.signalstore.SignalProtocolAddress as StoreAddress

/**
 * An in-memory relay with the v3 API (DEC-024) that also keeps every blob it ever received, as an
 * eavesdropping relay would.
 */
class MemoryRelay : TransportClient {
    private val queued = linkedMapOf<String, RelayEncryptedBlob>()
    private var nextId = 0
    val history = mutableListOf<RelayEncryptedBlob>()

    override suspend fun sendMessage(queueId: String, opaqueCiphertext: ByteArray, expiresAtEpochMillis: Long): String =
        synchronized(this) {
            val blob = RelayEncryptedBlob("relay-${nextId++}", queueId, opaqueCiphertext, expiresAtEpochMillis)
            queued[blob.messageId] = blob
            history += blob
            blob.messageId
        }

    override suspend fun fetchMessages(queue: RelayQueueCredentials): List<RelayEncryptedBlob> =
        synchronized(this) { queued.values.filter { it.queueId == queue.queueId } }

    override suspend fun deleteMessage(queue: RelayQueueCredentials, messageId: String) {
        synchronized(this) { if (queued[messageId]?.queueId == queue.queueId) queued.remove(messageId) }
    }
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
    private val queue = RelayQueueCredentials.generate()
    val queueId = queue.queueId
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

    /**
     * Every private key and every ratchet state this device stores right now, decrypted with the
     * test vault key and labelled for assertion messages (the bytes are never printed): what must
     * never appear in the database files in clear.
     */
    fun storedSecrets(): List<Pair<String, ByteArray>> {
        val dao = database.dao()
        val records = EncryptedSignalStoreRepository(dao, JcaLocalVaultCipher(vaultKey))
        val secrets = mutableListOf<Pair<String, ByteArray>>()
        records.loadLocalIdentity()?.let {
            secrets += "identity private key" to IdentityKeyPair(it.serializedIdentityKeyPair).privateKey.serialize()
        }
        records.loadAllSignedPreKeyRecords().forEach {
            secrets += "signed pre-key private key" to SignedPreKeyRecord(it).keyPair.privateKey.serialize()
        }
        records.loadAllKyberPreKeyRecords().forEach {
            secrets += "Kyber secret key" to KyberPreKeyRecord(it.serializedRecord).keyPair.secretKey.serialize()
        }
        // Every unused invitation, expired or not, still holds its one-time pre-key.
        dao.getExpiredUnusedV2Invitations(Long.MAX_VALUE).map { it.oneTimePreKeyId }.forEach { id ->
            records.loadPreKeyRecord(id)?.let { secrets += "one-time pre-key private key" to PreKeyRecord(it).keyPair.privateKey.serialize() }
        }
        dao.getV2Contacts().forEach { contact ->
            records.loadSessionRecord(StoreAddress(contact.contactId, SignalSessions.DEVICE_ID))?.let { record ->
                thirtyTwoByteFields(record).forEach { secrets += "session key" to it }
            }
        }
        return secrets
    }

    /**
     * Every 32-byte length-delimited field of a protobuf message, at any depth. In libsignal's
     * session record these are the root, chain and message keys and the private ratchet keys;
     * public keys are 33 bytes and IVs 16, so public values stored elsewhere cannot match.
     * Fields are kept only from (sub)messages that parse cleanly, so raw byte strings such as a
     * Kyber ciphertext are never mistaken for messages.
     */
    private fun thirtyTwoByteFields(message: ByteArray): List<ByteArray> {
        val found = mutableListOf<ByteArray>()
        fun walk(bytes: ByteArray, depth: Int): Boolean {
            var index = 0
            fun varint(): Long {
                var result = 0L
                var shift = 0
                while (true) {
                    require(index < bytes.size && shift <= 63)
                    val byte = bytes[index++].toInt() and 0xFF
                    result = result or ((byte and 0x7F).toLong() shl shift)
                    if (byte and 0x80 == 0) return result
                    shift += 7
                }
            }
            val collected = mutableListOf<ByteArray>()
            val nested = mutableListOf<ByteArray>()
            try {
                while (index < bytes.size) {
                    val key = varint()
                    require(key > 0)
                    when ((key and 7).toInt()) {
                        0 -> varint()
                        1 -> index += 8
                        5 -> index += 4
                        2 -> {
                            val length = varint()
                            require(length in 0..(bytes.size - index).toLong())
                            val payload = bytes.copyOfRange(index, index + length.toInt())
                            index += length.toInt()
                            if (payload.size == 32) collected += payload else nested += payload
                        }
                        else -> throw IllegalArgumentException()
                    }
                    require(index <= bytes.size)
                }
            } catch (_: IllegalArgumentException) {
                return false
            }
            found += collected
            if (depth < MAX_PROTOBUF_DEPTH) nested.forEach { walk(it, depth + 1) }
            return true
        }
        check(walk(message, 0)) { "Not a protobuf message" }
        return found
    }

    /** The database file and its write-ahead log as they are on disk now. */
    fun fileBytes(): ByteArray =
        listOf("", "-wal").map { context.getDatabasePath(databaseName + it) }
            .filter { it.exists() }
            .fold(ByteArray(0)) { all, file -> all + file.readBytes() }

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
            localReceiveQueue = queue,
            localDisplayName = { name },
        )
    }

    private fun sessionsOver(copy: SecureMessengerDatabase): SignalSessions {
        val records = EncryptedSignalStoreRepository(copy.dao(), JcaLocalVaultCipher(vaultKey))
        return SignalSessions(EncryptedSignalProtocolStore(records, records, records, records, records, records))
    }

    private companion object {
        const val MAX_PROTOBUF_DEPTH = 6
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
