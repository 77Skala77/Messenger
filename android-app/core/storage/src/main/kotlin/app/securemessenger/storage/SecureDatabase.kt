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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSession(session: EncryptedSessionEntity)

    @Query("SELECT * FROM encrypted_sessions WHERE contactId = :contactId LIMIT 1")
    suspend fun getSession(contactId: String): EncryptedSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMessage(message: EncryptedMessageEntity)

    @Query("SELECT * FROM encrypted_messages WHERE contactId = :contactId ORDER BY createdAtEpochMillis")
    fun observeMessages(contactId: String): Flow<List<EncryptedMessageEntity>>

    @Query("DELETE FROM local_profile")
    suspend fun deleteProfile()

    @Query("DELETE FROM contacts")
    suspend fun deleteContacts()

    @Query("DELETE FROM encrypted_sessions")
    suspend fun deleteSessions()

    @Query("DELETE FROM encrypted_messages")
    suspend fun deleteMessages()
}

@Database(
    entities = [
        EncryptedProfileEntity::class,
        ContactEntity::class,
        EncryptedSessionEntity::class,
        EncryptedMessageEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class SecureMessengerDatabase : RoomDatabase() {
    abstract fun dao(): SecureMessengerDao

    companion object {
        fun create(context: Context): SecureMessengerDatabase =
            Room.databaseBuilder(context, SecureMessengerDatabase::class.java, "secure_messenger.db")
                .build()
    }
}
