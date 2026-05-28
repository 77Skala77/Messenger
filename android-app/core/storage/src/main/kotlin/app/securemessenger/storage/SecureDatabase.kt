package app.securemessenger.storage

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.securemessenger.delivery.DeliveryReceiptAcceptance
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "local_profile", primaryKeys = ["profileId"])
data class EncryptedProfileEntity(
    val profileId: String,
    val encryptedDisplayName: ByteArray,
    val deviceId: String,
)

@Entity(tableName = "contacts", primaryKeys = ["contactId"])
data class ContactEntity(
    val contactId: String,
    val encryptedLocalDisplayName: ByteArray,
    val queueId: String,
    val deviceId: String,
    val encodedPublicKey: ByteArray,
    val fingerprint: String,
    val verificationStatus: String,
)

@Entity(tableName = "encrypted_sessions", primaryKeys = ["contactId"])
data class EncryptedSessionEntity(
    val contactId: String,
    val encryptedState: ByteArray,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "encrypted_messages", primaryKeys = ["messageId"])
data class EncryptedMessageEntity(
    val messageId: String,
    val contactId: String,
    val encryptedPayload: ByteArray,
    val encodedAssociatedData: ByteArray,
    val outgoing: Boolean,
    val status: String,
    val createdAtEpochMillis: Long,
    val deliveryAttemptCount: Int = 0,
    val nextRetryAtEpochMillis: Long? = null,
)

@Entity(tableName = "contact_devices", primaryKeys = ["contactId", "deviceId"])
data class ContactDeviceEntity(
    val contactId: String,
    val deviceId: String,
    val queueId: String,
    val encodedPublicKey: ByteArray,
    val fingerprint: String,
    val approvalStatus: String,
    val firstSeenEpochMillis: Long,
)

@Entity(tableName = "accepted_replay", primaryKeys = ["conversationId", "senderDeviceId", "messageNumber"])
data class AcceptedReplayEntity(
    val conversationId: String,
    val senderDeviceId: String,
    val messageNumber: Long,
    val acceptedAtEpochMillis: Long,
)

data class DeliveryQueueMetrics(
    val pendingOutgoingCount: Int,
    val failedOutgoingCount: Int,
    val nextRetryAtEpochMillis: Long?,
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
)

@Dao
interface SecureMessengerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putProfile(profile: EncryptedProfileEntity)

    @Query("SELECT * FROM local_profile LIMIT 1")
    suspend fun getProfile(): EncryptedProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putContact(contact: ContactEntity)

    @Query("SELECT * FROM contacts ORDER BY contactId")
    fun observeContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts ORDER BY contactId")
    suspend fun getContactsSnapshot(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE contactId = :contactId LIMIT 1")
    suspend fun getContact(contactId: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putContactDevice(device: ContactDeviceEntity)

    @Query("SELECT * FROM contact_devices WHERE contactId = :contactId ORDER BY firstSeenEpochMillis")
    fun observeContactDevices(contactId: String): Flow<List<ContactDeviceEntity>>

    @Query("SELECT * FROM contact_devices WHERE contactId = :contactId AND deviceId = :deviceId LIMIT 1")
    suspend fun getContactDevice(contactId: String, deviceId: String): ContactDeviceEntity?

    @Query("SELECT * FROM contact_devices WHERE contactId = :contactId ORDER BY firstSeenEpochMillis")
    suspend fun getContactDevices(contactId: String): List<ContactDeviceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSession(session: EncryptedSessionEntity)

    @Query("SELECT * FROM encrypted_sessions WHERE contactId = :contactId LIMIT 1")
    suspend fun getSession(contactId: String): EncryptedSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMessage(message: EncryptedMessageEntity)

    @Query("SELECT * FROM encrypted_messages WHERE contactId = :contactId ORDER BY createdAtEpochMillis")
    fun observeMessages(contactId: String): Flow<List<EncryptedMessageEntity>>

    @Query("SELECT * FROM encrypted_messages WHERE messageId = :messageId AND outgoing = 1 LIMIT 1")
    suspend fun getOutgoingMessage(messageId: String): EncryptedMessageEntity?

    @Query(
        """
        SELECT * FROM encrypted_messages
        WHERE outgoing = 1
          AND status IN ('PENDING', 'FAILED')
          AND (nextRetryAtEpochMillis IS NULL OR nextRetryAtEpochMillis <= :nowEpochMillis)
        ORDER BY createdAtEpochMillis
        """,
    )
    suspend fun getDueOutgoingMessages(nowEpochMillis: Long): List<EncryptedMessageEntity>

    @Query(
        """
        SELECT * FROM encrypted_messages
        WHERE outgoing = 1
          AND status IN ('PENDING', 'FAILED')
        ORDER BY createdAtEpochMillis
        """,
    )
    suspend fun getRetryableOutgoingMessages(): List<EncryptedMessageEntity>

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN outgoing = 1 AND status = 'PENDING' THEN 1 ELSE 0 END), 0)
                AS pendingOutgoingCount,
            COALESCE(SUM(CASE WHEN outgoing = 1 AND status = 'FAILED' THEN 1 ELSE 0 END), 0)
                AS failedOutgoingCount,
            MIN(CASE WHEN outgoing = 1 AND status = 'FAILED' THEN nextRetryAtEpochMillis ELSE NULL END)
                AS nextRetryAtEpochMillis
        FROM encrypted_messages
        """,
    )
    fun observeDeliveryQueueMetrics(): Flow<DeliveryQueueMetrics>

    @Query(
        """
        UPDATE encrypted_messages
        SET status = :status,
            deliveryAttemptCount = :attemptCount,
            nextRetryAtEpochMillis = :nextRetryAtEpochMillis
        WHERE messageId = :messageId
        """,
    )
    suspend fun updateDeliveryState(
        messageId: String,
        status: String,
        attemptCount: Int,
        nextRetryAtEpochMillis: Long?,
    )

    @Query(
        """
        UPDATE encrypted_messages
        SET status = :status,
            nextRetryAtEpochMillis = NULL
        WHERE messageId = :messageId
          AND outgoing = 1
        """,
    )
    suspend fun updateOutgoingStatus(messageId: String, status: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun acceptIncomingNumber(replay: AcceptedReplayEntity): Long

    @Transaction
    suspend fun acceptAndStoreIncoming(replay: AcceptedReplayEntity, message: EncryptedMessageEntity): Boolean {
        if (acceptIncomingNumber(replay) == -1L) return false
        putMessage(message)
        return true
    }

    @Transaction
    suspend fun acceptDeliveryReceiptAndMarkDelivered(
        replay: AcceptedReplayEntity,
        originalMessageId: String,
        deliveredStatus: String,
    ): DeliveryReceiptAcceptance {
        if (acceptIncomingNumber(replay) == -1L) return DeliveryReceiptAcceptance.DUPLICATE_SUPPRESSED
        val updated = updateOutgoingStatus(originalMessageId, deliveredStatus)
        return if (updated > 0) {
            DeliveryReceiptAcceptance.STATUS_UPDATED
        } else {
            DeliveryReceiptAcceptance.UNKNOWN_MESSAGE
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSignalIdentityRecord(record: EncryptedSignalIdentityRecordEntity)

    @Query("SELECT * FROM signal_identity_records WHERE recipientId = :recipientId AND deviceId = :deviceId LIMIT 1")
    suspend fun getSignalIdentityRecord(recipientId: String, deviceId: Int): EncryptedSignalIdentityRecordEntity?

    @Query("DELETE FROM signal_identity_records WHERE recipientId = :recipientId AND deviceId = :deviceId")
    suspend fun deleteSignalIdentityRecord(recipientId: String, deviceId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSignalSessionRecord(record: EncryptedSignalSessionRecordEntity)

    @Query("SELECT * FROM signal_session_records WHERE recipientId = :recipientId AND deviceId = :deviceId LIMIT 1")
    suspend fun getSignalSessionRecord(recipientId: String, deviceId: Int): EncryptedSignalSessionRecordEntity?

    @Query("DELETE FROM signal_session_records WHERE recipientId = :recipientId AND deviceId = :deviceId")
    suspend fun deleteSignalSessionRecord(recipientId: String, deviceId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSignalPreKeyRecord(record: EncryptedSignalPreKeyRecordEntity)

    @Query("SELECT * FROM signal_prekey_records WHERE preKeyId = :preKeyId LIMIT 1")
    suspend fun getSignalPreKeyRecord(preKeyId: Int): EncryptedSignalPreKeyRecordEntity?

    @Query("DELETE FROM signal_prekey_records WHERE preKeyId = :preKeyId")
    suspend fun deleteSignalPreKeyRecord(preKeyId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSignalSignedPreKeyRecord(record: EncryptedSignalSignedPreKeyRecordEntity)

    @Query("SELECT * FROM signal_signed_prekey_records WHERE signedPreKeyId = :signedPreKeyId LIMIT 1")
    suspend fun getSignalSignedPreKeyRecord(signedPreKeyId: Int): EncryptedSignalSignedPreKeyRecordEntity?

    @Query("DELETE FROM signal_signed_prekey_records WHERE signedPreKeyId = :signedPreKeyId")
    suspend fun deleteSignalSignedPreKeyRecord(signedPreKeyId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSignalKyberPreKeyRecord(record: EncryptedSignalKyberPreKeyRecordEntity)

    @Query("SELECT * FROM signal_kyber_prekey_records WHERE kyberPreKeyId = :kyberPreKeyId LIMIT 1")
    suspend fun getSignalKyberPreKeyRecord(kyberPreKeyId: Int): EncryptedSignalKyberPreKeyRecordEntity?

    @Query("DELETE FROM signal_kyber_prekey_records WHERE kyberPreKeyId = :kyberPreKeyId")
    suspend fun deleteSignalKyberPreKeyRecord(kyberPreKeyId: Int)

    @Query("DELETE FROM local_profile")
    suspend fun deleteProfile()

    @Query("DELETE FROM contacts")
    suspend fun deleteContacts()

    @Query("DELETE FROM contact_devices")
    suspend fun deleteContactDevices()

    @Query("DELETE FROM encrypted_sessions")
    suspend fun deleteSessions()

    @Query("DELETE FROM encrypted_messages")
    suspend fun deleteMessages()

    @Query("DELETE FROM accepted_replay")
    suspend fun deleteAcceptedReplay()

    @Query("DELETE FROM signal_identity_records")
    suspend fun deleteSignalIdentityRecords()

    @Query("DELETE FROM signal_session_records")
    suspend fun deleteSignalSessionRecords()

    @Query("DELETE FROM signal_prekey_records")
    suspend fun deleteSignalPreKeyRecords()

    @Query("DELETE FROM signal_signed_prekey_records")
    suspend fun deleteSignalSignedPreKeyRecords()

    @Query("DELETE FROM signal_kyber_prekey_records")
    suspend fun deleteSignalKyberPreKeyRecords()
}

@Database(
    entities = [
        EncryptedProfileEntity::class,
        ContactEntity::class,
        EncryptedSessionEntity::class,
        EncryptedMessageEntity::class,
        ContactDeviceEntity::class,
        AcceptedReplayEntity::class,
        EncryptedSignalIdentityRecordEntity::class,
        EncryptedSignalSessionRecordEntity::class,
        EncryptedSignalPreKeyRecordEntity::class,
        EncryptedSignalSignedPreKeyRecordEntity::class,
        EncryptedSignalKyberPreKeyRecordEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class SecureMessengerDatabase : RoomDatabase() {
    abstract fun dao(): SecureMessengerDao

    companion object {
        fun create(context: Context): SecureMessengerDatabase =
            Room.databaseBuilder(context, SecureMessengerDatabase::class.java, "secure_messenger.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
    }
}
