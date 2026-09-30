package app.securemessenger.storage

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The local profile: a vault-encrypted display name. `deviceId` belonged to the removed legacy
 * P-256 identity (DEC-021); it is kept only so the table shape does not change, and is unused.
 */
@Entity(tableName = "local_profile", primaryKeys = ["profileId"])
data class EncryptedProfileEntity(
    val profileId: String,
    val encryptedDisplayName: ByteArray,
    val deviceId: String,
)

@Entity(tableName = "signal_identity_records", primaryKeys = ["recipientId", "deviceId"])
data class EncryptedSignalIdentityRecordEntity(
    val recipientId: String,
    val deviceId: Int,
    val encryptedRecord: ByteArray,
)

@Entity(tableName = "signal_session_records", primaryKeys = ["recipientId", "deviceId"])
data class EncryptedSignalSessionRecordEntity(
    val recipientId: String,
    val deviceId: Int,
    val encryptedRecord: ByteArray,
)

@Entity(tableName = "signal_prekey_records", primaryKeys = ["preKeyId"])
data class EncryptedSignalPreKeyRecordEntity(
    val preKeyId: Int,
    val encryptedRecord: ByteArray,
)

@Entity(tableName = "signal_signed_prekey_records", primaryKeys = ["signedPreKeyId"])
data class EncryptedSignalSignedPreKeyRecordEntity(
    val signedPreKeyId: Int,
    val encryptedRecord: ByteArray,
)

@Entity(tableName = "signal_kyber_prekey_records", primaryKeys = ["kyberPreKeyId"])
data class EncryptedSignalKyberPreKeyRecordEntity(
    val kyberPreKeyId: Int,
    val encryptedRecord: ByteArray,
    // Also bound into the record's vault AAD, so flipping this column makes decryption fail.
    val isLastResort: Boolean = false,
)

/** Singleton row holding the local libsignal identity key pair and registration ID. */
@Entity(tableName = "signal_local_identity", primaryKeys = ["singletonId"])
data class EncryptedSignalLocalIdentityEntity(
    val singletonId: Int,
    val encryptedRecord: ByteArray,
)

/** Seen `(kyberPreKeyId, signedPreKeyId, baseKey)` tuples for last-resort Kyber pre-keys. */
@Entity(
    tableName = "signal_kyber_base_key_uses",
    primaryKeys = ["kyberPreKeyId", "signedPreKeyId", "baseKeyDigestHex"],
)
data class SignalKyberBaseKeyUseEntity(
    val kyberPreKeyId: Int,
    val signedPreKeyId: Int,
    val baseKeyDigestHex: String,
)

// --- v2 conversations (DEC-019). Separate tables: legacy rows are never reinterpreted as v2. ---

@Entity(
    tableName = "v2_invitations",
    primaryKeys = ["invitationId"],
    indices = [Index(value = ["localRoutingTagHex"], unique = true)],
)
data class V2InvitationEntity(
    val invitationId: String,
    val localRoutingTagHex: String,
    val signedPreKeyId: Int,
    val oneTimePreKeyId: Int,
    val kyberPreKeyId: Int,
    val mailboxQueueId: String,
    /** Vault-encrypted QR text: it contains the bundle seal key. */
    val encryptedCodeText: ByteArray,
    val sealedBundle: ByteArray,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val usedByRequestId: String?,
)

@Entity(
    tableName = "v2_contact_requests",
    primaryKeys = ["requestId"],
    indices = [Index(value = ["localRoutingTagHex"], unique = true)],
)
data class V2ContactRequestEntity(
    val requestId: String,
    val invitationId: String,
    val identityKey: ByteArray,
    val peerQueueId: String,
    val peerRoutingTagHex: String,
    val localRoutingTagHex: String,
    val encryptedClaimedName: ByteArray,
    val receivedAtEpochMillis: Long,
)

@Entity(
    tableName = "v2_contacts",
    primaryKeys = ["contactId"],
    indices = [Index(value = ["localRoutingTagHex"], unique = true)],
)
data class V2ContactEntity(
    val contactId: String,
    val encryptedDisplayName: ByteArray,
    val identityKey: ByteArray,
    val peerQueueId: String,
    val peerRoutingTagHex: String,
    val localRoutingTagHex: String,
    val state: String,
    val createdAtEpochMillis: Long,
)

/** Message bodies are vault-encrypted (DEC-019 T1); queued ciphertext is dropped once sent. */
@Entity(
    tableName = "v2_messages",
    primaryKeys = ["messageId"],
    indices = [Index(value = ["contactId", "outgoing", "innerMessageIdHex"], unique = true)],
)
data class V2MessageEntity(
    val messageId: String,
    val contactId: String,
    val outgoing: Boolean,
    val kind: String,
    val innerMessageIdHex: String,
    val encryptedBody: ByteArray?,
    val status: String,
    val createdAtEpochMillis: Long,
    val peerQueueId: String?,
    val envelope: ByteArray?,
    val deliveryAttemptCount: Int,
    val nextRetryAtEpochMillis: Long?,
)

/** Digests of accepted v2 envelopes: a byte-identical re-delivery is deleted without decryption. */
@Entity(tableName = "v2_accepted_envelopes", primaryKeys = ["digestHex"])
data class V2AcceptedEnvelopeEntity(
    val digestHex: String,
    val acceptedAtEpochMillis: Long,
)

@Dao
interface SecureMessengerDao {
    // Every method is deliberately blocking (not suspend): libsignal calls its stores synchronously,
    // and the profile check and the local wipe run under the protocol lock, which must not be held
    // across a suspension point. Room refuses them on the main thread.

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putProfile(profile: EncryptedProfileEntity)

    @Query("SELECT * FROM local_profile LIMIT 1")
    fun getProfile(): EncryptedProfileEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertSignalLocalIdentityIfAbsent(record: EncryptedSignalLocalIdentityEntity): Long

    @Query("SELECT * FROM signal_local_identity WHERE singletonId = :singletonId LIMIT 1")
    fun getSignalLocalIdentity(singletonId: Int): EncryptedSignalLocalIdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putSignalIdentityRecord(record: EncryptedSignalIdentityRecordEntity)

    @Query("SELECT * FROM signal_identity_records WHERE recipientId = :recipientId AND deviceId = :deviceId LIMIT 1")
    fun getSignalIdentityRecord(recipientId: String, deviceId: Int): EncryptedSignalIdentityRecordEntity?

    @Query("DELETE FROM signal_identity_records WHERE recipientId = :recipientId AND deviceId = :deviceId")
    fun deleteSignalIdentityRecord(recipientId: String, deviceId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putSignalSessionRecord(record: EncryptedSignalSessionRecordEntity)

    @Query("SELECT * FROM signal_session_records WHERE recipientId = :recipientId AND deviceId = :deviceId LIMIT 1")
    fun getSignalSessionRecord(recipientId: String, deviceId: Int): EncryptedSignalSessionRecordEntity?

    @Query("DELETE FROM signal_session_records WHERE recipientId = :recipientId AND deviceId = :deviceId")
    fun deleteSignalSessionRecord(recipientId: String, deviceId: Int)

    @Query("SELECT deviceId FROM signal_session_records WHERE recipientId = :recipientId")
    fun getSignalSessionDeviceIds(recipientId: String): List<Int>

    @Query("DELETE FROM signal_session_records WHERE recipientId = :recipientId")
    fun deleteSignalSessionRecordsFor(recipientId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putSignalPreKeyRecord(record: EncryptedSignalPreKeyRecordEntity)

    @Query("SELECT * FROM signal_prekey_records WHERE preKeyId = :preKeyId LIMIT 1")
    fun getSignalPreKeyRecord(preKeyId: Int): EncryptedSignalPreKeyRecordEntity?

    @Query("DELETE FROM signal_prekey_records WHERE preKeyId = :preKeyId")
    fun deleteSignalPreKeyRecord(preKeyId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putSignalSignedPreKeyRecord(record: EncryptedSignalSignedPreKeyRecordEntity)

    @Query("SELECT * FROM signal_signed_prekey_records WHERE signedPreKeyId = :signedPreKeyId LIMIT 1")
    fun getSignalSignedPreKeyRecord(signedPreKeyId: Int): EncryptedSignalSignedPreKeyRecordEntity?

    @Query("SELECT * FROM signal_signed_prekey_records")
    fun getAllSignalSignedPreKeyRecords(): List<EncryptedSignalSignedPreKeyRecordEntity>

    @Query("DELETE FROM signal_signed_prekey_records WHERE signedPreKeyId = :signedPreKeyId")
    fun deleteSignalSignedPreKeyRecord(signedPreKeyId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun putSignalKyberPreKeyRecord(record: EncryptedSignalKyberPreKeyRecordEntity)

    @Query("SELECT * FROM signal_kyber_prekey_records WHERE kyberPreKeyId = :kyberPreKeyId LIMIT 1")
    fun getSignalKyberPreKeyRecord(kyberPreKeyId: Int): EncryptedSignalKyberPreKeyRecordEntity?

    @Query("SELECT * FROM signal_kyber_prekey_records")
    fun getAllSignalKyberPreKeyRecords(): List<EncryptedSignalKyberPreKeyRecordEntity>

    @Query("DELETE FROM signal_kyber_prekey_records WHERE kyberPreKeyId = :kyberPreKeyId")
    fun deleteSignalKyberPreKeyRecord(kyberPreKeyId: Int)

    /** Single atomic statement: returns 1 only for the first consumer of a one-time key. */
    @Query("DELETE FROM signal_kyber_prekey_records WHERE kyberPreKeyId = :kyberPreKeyId AND isLastResort = 0")
    fun deleteOneTimeSignalKyberPreKey(kyberPreKeyId: Int): Int

    /** Single atomic statement: returns -1 when the tuple was already recorded. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertSignalKyberBaseKeyUse(use: SignalKyberBaseKeyUseEntity): Long

    @Query("DELETE FROM signal_kyber_base_key_uses WHERE kyberPreKeyId = :kyberPreKeyId")
    fun deleteSignalKyberBaseKeyUsesForKyberKey(kyberPreKeyId: Int)

    @Query("DELETE FROM signal_kyber_base_key_uses WHERE signedPreKeyId = :signedPreKeyId")
    fun deleteSignalKyberBaseKeyUsesForSignedKey(signedPreKeyId: Int)

    @Query("DELETE FROM local_profile")
    fun deleteProfile()

    @Query("DELETE FROM signal_identity_records")
    fun deleteSignalIdentityRecords()

    @Query("DELETE FROM signal_session_records")
    fun deleteSignalSessionRecords()

    @Query("DELETE FROM signal_prekey_records")
    fun deleteSignalPreKeyRecords()

    @Query("DELETE FROM signal_signed_prekey_records")
    fun deleteSignalSignedPreKeyRecords()

    @Query("DELETE FROM signal_kyber_prekey_records")
    fun deleteSignalKyberPreKeyRecords()

    @Query("DELETE FROM signal_kyber_base_key_uses")
    fun deleteSignalKyberBaseKeyUses()

    @Query("DELETE FROM signal_local_identity")
    fun deleteSignalLocalIdentity()

    // v2 methods are blocking: they run inside ProtocolTransactionRunner transactions together with
    // the libsignal store writes of the same operation. Room refuses them on the main thread.

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertV2Invitation(invitation: V2InvitationEntity)

    @Query("SELECT * FROM v2_invitations WHERE invitationId = :invitationId LIMIT 1")
    fun getV2Invitation(invitationId: String): V2InvitationEntity?

    @Query("SELECT * FROM v2_invitations WHERE localRoutingTagHex = :tag AND usedByRequestId IS NULL LIMIT 1")
    fun getUnusedV2InvitationByTag(tag: String): V2InvitationEntity?

    @Query(
        """
        SELECT * FROM v2_invitations
        WHERE usedByRequestId IS NULL AND expiresAtEpochMillis > :nowEpochMillis
        ORDER BY createdAtEpochMillis DESC
        LIMIT 1
        """,
    )
    fun getLatestUsableV2Invitation(nowEpochMillis: Long): V2InvitationEntity?

    /** Single atomic statement: returns 1 only for the first request that uses the invitation. */
    @Query(
        """
        UPDATE v2_invitations SET usedByRequestId = :requestId
        WHERE invitationId = :invitationId AND usedByRequestId IS NULL
        """,
    )
    fun markV2InvitationUsed(invitationId: String, requestId: String): Int

    @Query("SELECT * FROM v2_invitations WHERE usedByRequestId IS NULL AND expiresAtEpochMillis <= :nowEpochMillis")
    fun getExpiredUnusedV2Invitations(nowEpochMillis: Long): List<V2InvitationEntity>

    @Query("DELETE FROM v2_invitations WHERE invitationId = :invitationId")
    fun deleteV2Invitation(invitationId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertV2ContactRequest(request: V2ContactRequestEntity)

    @Query("SELECT * FROM v2_contact_requests WHERE requestId = :requestId LIMIT 1")
    fun getV2ContactRequest(requestId: String): V2ContactRequestEntity?

    @Query("SELECT * FROM v2_contact_requests WHERE localRoutingTagHex = :tag LIMIT 1")
    fun getV2ContactRequestByTag(tag: String): V2ContactRequestEntity?

    @Query("SELECT * FROM v2_contact_requests ORDER BY receivedAtEpochMillis")
    fun getV2ContactRequests(): List<V2ContactRequestEntity>

    @Query("DELETE FROM v2_contact_requests WHERE requestId = :requestId")
    fun deleteV2ContactRequest(requestId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertV2Contact(contact: V2ContactEntity)

    @Query("SELECT * FROM v2_contacts WHERE contactId = :contactId LIMIT 1")
    fun getV2Contact(contactId: String): V2ContactEntity?

    @Query("SELECT * FROM v2_contacts WHERE localRoutingTagHex = :tag LIMIT 1")
    fun getV2ContactByTag(tag: String): V2ContactEntity?

    @Query("SELECT * FROM v2_contacts WHERE identityKey = :identityKey LIMIT 1")
    fun getV2ContactByIdentity(identityKey: ByteArray): V2ContactEntity?

    @Query("UPDATE v2_contacts SET state = :state WHERE contactId = :contactId")
    fun updateV2ContactState(contactId: String, state: String): Int

    /** Returns -1 when the message ID, or this contact's inner message ID, already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertV2Message(message: V2MessageEntity): Long

    @Query("SELECT * FROM v2_messages WHERE contactId = :contactId AND outgoing = 0 ORDER BY createdAtEpochMillis")
    fun getV2IncomingMessages(contactId: String): List<V2MessageEntity>

    @Query("SELECT * FROM v2_messages WHERE contactId = :contactId AND kind = 'TEXT' ORDER BY createdAtEpochMillis, messageId")
    fun getV2Texts(contactId: String): List<V2MessageEntity>

    @Query("SELECT * FROM v2_contacts ORDER BY createdAtEpochMillis, contactId")
    fun getV2Contacts(): List<V2ContactEntity>

    @Query("SELECT * FROM v2_messages WHERE messageId = :messageId AND outgoing = 1 LIMIT 1")
    fun getV2OutgoingMessage(messageId: String): V2MessageEntity?

    @Query(
        """
        SELECT * FROM v2_messages
        WHERE outgoing = 1
          AND status IN ('PENDING', 'FAILED')
          AND envelope IS NOT NULL
          AND (nextRetryAtEpochMillis IS NULL OR nextRetryAtEpochMillis <= :nowEpochMillis)
        ORDER BY createdAtEpochMillis
        """,
    )
    fun getDueV2OutgoingMessages(nowEpochMillis: Long): List<V2MessageEntity>

    @Query(
        """
        UPDATE v2_messages
        SET status = 'SENT', deliveryAttemptCount = :attemptCount, nextRetryAtEpochMillis = NULL, envelope = NULL
        WHERE messageId = :messageId AND outgoing = 1 AND status IN ('PENDING', 'FAILED')
        """,
    )
    fun markV2MessageSent(messageId: String, attemptCount: Int)

    @Query(
        """
        UPDATE v2_messages
        SET status = 'FAILED', deliveryAttemptCount = :attemptCount, nextRetryAtEpochMillis = :nextAttemptAtEpochMillis
        WHERE messageId = :messageId AND outgoing = 1 AND status IN ('PENDING', 'FAILED')
        """,
    )
    fun markV2MessageFailed(messageId: String, attemptCount: Int, nextAttemptAtEpochMillis: Long)

    /** A receipt only reaches TEXT messages sent to the contact whose session authenticated it. */
    @Query(
        """
        UPDATE v2_messages
        SET status = 'DELIVERED', nextRetryAtEpochMillis = NULL, envelope = NULL
        WHERE contactId = :contactId AND outgoing = 1 AND kind = 'TEXT' AND innerMessageIdHex = :innerMessageIdHex
        """,
    )
    fun markV2MessageDelivered(contactId: String, innerMessageIdHex: String): Int

    @Query("DELETE FROM v2_messages WHERE contactId = :contactId")
    fun deleteV2MessagesFor(contactId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertV2AcceptedEnvelope(envelope: V2AcceptedEnvelopeEntity): Long

    @Query("SELECT COUNT(*) FROM v2_accepted_envelopes WHERE digestHex = :digestHex")
    fun countV2AcceptedEnvelope(digestHex: String): Int

    @Query("DELETE FROM v2_invitations")
    fun deleteV2Invitations()

    @Query("DELETE FROM v2_contact_requests")
    fun deleteV2ContactRequests()

    @Query("DELETE FROM v2_contacts")
    fun deleteV2Contacts()

    @Query("DELETE FROM v2_messages")
    fun deleteV2Messages()

    @Query("DELETE FROM v2_accepted_envelopes")
    fun deleteV2AcceptedEnvelopes()
}

@Database(
    entities = [
        EncryptedProfileEntity::class,
        EncryptedSignalIdentityRecordEntity::class,
        EncryptedSignalSessionRecordEntity::class,
        EncryptedSignalPreKeyRecordEntity::class,
        EncryptedSignalSignedPreKeyRecordEntity::class,
        EncryptedSignalKyberPreKeyRecordEntity::class,
        EncryptedSignalLocalIdentityEntity::class,
        SignalKyberBaseKeyUseEntity::class,
        V2InvitationEntity::class,
        V2ContactRequestEntity::class,
        V2ContactEntity::class,
        V2MessageEntity::class,
        V2AcceptedEnvelopeEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class SecureMessengerDatabase : RoomDatabase() {
    abstract fun dao(): SecureMessengerDao

    companion object {
        fun create(context: Context): SecureMessengerDatabase =
            Room.databaseBuilder(context, SecureMessengerDatabase::class.java, "secure_messenger.db")
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                    MIGRATION_7_8, MIGRATION_8_9,
                )
                // Worker and foreground UI may hold separate Room instances in one process.
                .enableMultiInstanceInvalidation()
                .build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `contact_devices` (
                        `contactId` TEXT NOT NULL,
                        `deviceId` TEXT NOT NULL,
                        `encodedPublicKey` BLOB NOT NULL,
                        `fingerprint` TEXT NOT NULL,
                        `approvalStatus` TEXT NOT NULL,
                        `firstSeenEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`contactId`, `deviceId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `accepted_replay` (
                        `conversationId` TEXT NOT NULL,
                        `senderDeviceId` TEXT NOT NULL,
                        `messageNumber` INTEGER NOT NULL,
                        `acceptedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`conversationId`, `senderDeviceId`, `messageNumber`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `contact_devices`
                        (`contactId`, `deviceId`, `encodedPublicKey`, `fingerprint`, `approvalStatus`, `firstSeenEpochMillis`)
                    SELECT `contactId`, `deviceId`, `encodedPublicKey`, `fingerprint`,
                        CASE WHEN `verificationStatus` = 'VERIFIED' THEN 'VERIFIED' ELSE 'PRIMARY_UNVERIFIED' END,
                        0
                    FROM `contacts`
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `contact_devices` ADD COLUMN `queueId` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    UPDATE `contact_devices`
                    SET `queueId` = (
                        SELECT `queueId` FROM `contacts`
                        WHERE `contacts`.`contactId` = `contact_devices`.`contactId`
                    )
                    WHERE EXISTS (
                        SELECT 1 FROM `contacts`
                        WHERE `contacts`.`contactId` = `contact_devices`.`contactId`
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `encrypted_messages` ADD COLUMN `deliveryAttemptCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `encrypted_messages` ADD COLUMN `nextRetryAtEpochMillis` INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `signal_identity_records` (
                        `recipientId` TEXT NOT NULL,
                        `deviceId` INTEGER NOT NULL,
                        `encryptedRecord` BLOB NOT NULL,
                        PRIMARY KEY(`recipientId`, `deviceId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `signal_session_records` (
                        `recipientId` TEXT NOT NULL,
                        `deviceId` INTEGER NOT NULL,
                        `encryptedRecord` BLOB NOT NULL,
                        PRIMARY KEY(`recipientId`, `deviceId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `signal_prekey_records` (
                        `preKeyId` INTEGER NOT NULL,
                        `encryptedRecord` BLOB NOT NULL,
                        PRIMARY KEY(`preKeyId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `signal_signed_prekey_records` (
                        `signedPreKeyId` INTEGER NOT NULL,
                        `encryptedRecord` BLOB NOT NULL,
                        PRIMARY KEY(`signedPreKeyId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `signal_kyber_prekey_records` (
                        `kyberPreKeyId` INTEGER NOT NULL,
                        `encryptedRecord` BLOB NOT NULL,
                        PRIMARY KEY(`kyberPreKeyId`)
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * Adds the libsignal local identity, the Kyber last-resort flag, and Kyber base-key reuse
         * tracking. Existing Kyber rows are deleted: their vault AAD format changed to bind the
         * last-resort flag, and the store was a disabled spike, so no runtime conversation ever
         * depended on them. Deleting fails closed; keeping them would only produce rows that can
         * no longer be decrypted.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM `signal_kyber_prekey_records`")
                db.execSQL(
                    "ALTER TABLE `signal_kyber_prekey_records` ADD COLUMN `isLastResort` INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `signal_local_identity` (
                        `singletonId` INTEGER NOT NULL,
                        `encryptedRecord` BLOB NOT NULL,
                        PRIMARY KEY(`singletonId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `signal_kyber_base_key_uses` (
                        `kyberPreKeyId` INTEGER NOT NULL,
                        `signedPreKeyId` INTEGER NOT NULL,
                        `baseKeyDigestHex` TEXT NOT NULL,
                        PRIMARY KEY(`kyberPreKeyId`, `signedPreKeyId`, `baseKeyDigestHex`)
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * Adds the v2 conversation tables (DEC-019). Purely additive: no existing row is changed or
         * reinterpreted; legacy conversations keep working until the explicit reset of step 1f.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `v2_invitations` (
                        `invitationId` TEXT NOT NULL,
                        `localRoutingTagHex` TEXT NOT NULL,
                        `signedPreKeyId` INTEGER NOT NULL,
                        `oneTimePreKeyId` INTEGER NOT NULL,
                        `kyberPreKeyId` INTEGER NOT NULL,
                        `mailboxQueueId` TEXT NOT NULL,
                        `encryptedCodeText` BLOB NOT NULL,
                        `sealedBundle` BLOB NOT NULL,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        `expiresAtEpochMillis` INTEGER NOT NULL,
                        `usedByRequestId` TEXT,
                        PRIMARY KEY(`invitationId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_v2_invitations_localRoutingTagHex` " +
                        "ON `v2_invitations` (`localRoutingTagHex`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `v2_contact_requests` (
                        `requestId` TEXT NOT NULL,
                        `invitationId` TEXT NOT NULL,
                        `identityKey` BLOB NOT NULL,
                        `peerQueueId` TEXT NOT NULL,
                        `peerRoutingTagHex` TEXT NOT NULL,
                        `localRoutingTagHex` TEXT NOT NULL,
                        `encryptedClaimedName` BLOB NOT NULL,
                        `receivedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`requestId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_v2_contact_requests_localRoutingTagHex` " +
                        "ON `v2_contact_requests` (`localRoutingTagHex`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `v2_contacts` (
                        `contactId` TEXT NOT NULL,
                        `encryptedDisplayName` BLOB NOT NULL,
                        `identityKey` BLOB NOT NULL,
                        `peerQueueId` TEXT NOT NULL,
                        `peerRoutingTagHex` TEXT NOT NULL,
                        `localRoutingTagHex` TEXT NOT NULL,
                        `state` TEXT NOT NULL,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`contactId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_v2_contacts_localRoutingTagHex` " +
                        "ON `v2_contacts` (`localRoutingTagHex`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `v2_messages` (
                        `messageId` TEXT NOT NULL,
                        `contactId` TEXT NOT NULL,
                        `outgoing` INTEGER NOT NULL,
                        `kind` TEXT NOT NULL,
                        `innerMessageIdHex` TEXT NOT NULL,
                        `encryptedBody` BLOB,
                        `status` TEXT NOT NULL,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        `peerQueueId` TEXT,
                        `envelope` BLOB,
                        `deliveryAttemptCount` INTEGER NOT NULL,
                        `nextRetryAtEpochMillis` INTEGER,
                        PRIMARY KEY(`messageId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_v2_messages_contactId_outgoing_innerMessageIdHex` " +
                        "ON `v2_messages` (`contactId`, `outgoing`, `innerMessageIdHex`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `v2_accepted_envelopes` (
                        `digestHex` TEXT NOT NULL,
                        `acceptedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`digestHex`)
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * The explicit legacy reset (DEC-021): the prototype protocol's contacts, devices, session
         * root keys, stored ciphertext and replay markers are dropped on purpose. Nothing is
         * migrated or reinterpreted: a legacy session can never be read or accepted as a v2 session.
         * Tables are dropped rather than emptied so no legacy row survives in the schema.
         * `local_profile`, the libsignal record tables and the `v2_*` tables are untouched.
         * Keystore keys and preferences of the legacy path are removed by `LegacyReset`.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                LEGACY_TABLES.forEach { table -> db.execSQL("DROP TABLE IF EXISTS `$table`") }
            }
        }

        /** Tables of the removed prototype protocol, dropped by [MIGRATION_7_8]. */
        val LEGACY_TABLES = listOf("contacts", "contact_devices", "encrypted_sessions", "encrypted_messages", "accepted_replay")

        /**
         * DEC-024, explicit reset of the v2 test conversations: contacts know each other's pre-v3
         * relay queue IDs, which have no read secret, so every conversation, its libsignal session,
         * identity and pre-keys, and every invitation are deleted once. The profile and the local
         * libsignal identity stay. No schema change; the app shows a one-time notice.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                RELAY_V3_RESET_TABLES.forEach { table -> db.execSQL("DELETE FROM `$table`") }
            }
        }

        /** Rows deleted by [MIGRATION_8_9]; local_profile and signal_local_identity are kept. */
        val RELAY_V3_RESET_TABLES = listOf(
            "v2_accepted_envelopes", "v2_messages", "v2_contact_requests", "v2_contacts", "v2_invitations",
            "signal_session_records", "signal_identity_records", "signal_prekey_records",
            "signal_signed_prekey_records", "signal_kyber_prekey_records", "signal_kyber_base_key_uses",
        )
    }
}
