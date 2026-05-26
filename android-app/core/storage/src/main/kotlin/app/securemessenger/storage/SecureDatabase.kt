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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun acceptIncomingNumber(replay: AcceptedReplayEntity): Long

    @Transaction
    suspend fun acceptAndStoreIncoming(replay: AcceptedReplayEntity, message: EncryptedMessageEntity): Boolean {
        if (acceptIncomingNumber(replay) == -1L) return false
        putMessage(message)
        return true
    }

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
}

@Database(
    entities = [
        EncryptedProfileEntity::class,
        ContactEntity::class,
        EncryptedSessionEntity::class,
        EncryptedMessageEntity::class,
        ContactDeviceEntity::class,
        AcceptedReplayEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class SecureMessengerDatabase : RoomDatabase() {
    abstract fun dao(): SecureMessengerDao

    companion object {
        fun create(context: Context): SecureMessengerDatabase =
            Room.databaseBuilder(context, SecureMessengerDatabase::class.java, "secure_messenger.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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
    }
}
