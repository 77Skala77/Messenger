package app.securemessenger.storage

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureDatabaseMigrationInstrumentedTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SecureMessengerDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationFourToFiveCreatesDisabledSignalRecordTables() {
        migrationHelper.createDatabase(TEST_DATABASE, 4).close()

        val migrated = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            5,
            true,
            SecureMessengerDatabase.MIGRATION_4_5,
        )
        migrated.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name LIKE 'signal_%_records'",
        ).use { cursor ->
            assertEquals(5, cursor.count)
        }
        migrated.close()
    }

    @Test
    fun migrationFiveToSixAddsLibsignalTablesAndDropsOldKyberRows() {
        migrationHelper.createDatabase(TEST_DATABASE, 5).use { db ->
            db.execSQL(
                "INSERT INTO signal_kyber_prekey_records (kyberPreKeyId, encryptedRecord) VALUES (7, X'0102')",
            )
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            6,
            true,
            SecureMessengerDatabase.MIGRATION_5_6,
        )
        migrated.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' " +
                "AND name IN ('signal_local_identity', 'signal_kyber_base_key_uses')",
        ).use { cursor ->
            assertEquals(2, cursor.count)
        }
        // Old rows used the v1 AAD without the last-resort flag, so they must not survive.
        migrated.query("SELECT COUNT(*) FROM signal_kyber_prekey_records").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun migrationSixToSevenAddsV2TablesAndKeepsExistingRows() {
        migrationHelper.createDatabase(TEST_DATABASE, 6).use { db ->
            db.execSQL(
                "INSERT INTO contacts (contactId, encryptedLocalDisplayName, queueId, deviceId, encodedPublicKey, " +
                    "fingerprint, verificationStatus) VALUES ('legacy', X'01', 'legacy-queue', 'device', X'02', 'FP', 'UNVERIFIED')",
            )
            db.execSQL(
                "INSERT INTO signal_session_records (recipientId, deviceId, encryptedRecord) VALUES ('someone', 1, X'0304')",
            )
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            7,
            true,
            SecureMessengerDatabase.MIGRATION_6_7,
        )
        migrated.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name LIKE 'v2_%'",
        ).use { cursor ->
            assertEquals(5, cursor.count)
        }
        migrated.query("SELECT COUNT(*) FROM contacts WHERE contactId = 'legacy'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM signal_session_records").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        migrated.close()
    }

    /** DEC-021: the explicit legacy reset. Legacy tables are gone; everything else survives. */
    @Test
    fun migrationSevenToEightDropsLegacyTablesAndKeepsProfileSignalAndV2Rows() {
        migrationHelper.createDatabase(TEST_DATABASE, 7).use { db ->
            db.execSQL("INSERT INTO local_profile (profileId, encryptedDisplayName, deviceId) VALUES ('local', X'01', 'legacy-device')")
            db.execSQL(
                "INSERT INTO contacts (contactId, encryptedLocalDisplayName, queueId, deviceId, encodedPublicKey, " +
                    "fingerprint, verificationStatus) VALUES ('legacy', X'01', 'legacy-queue', 'device', X'02', 'FP', 'UNVERIFIED')",
            )
            db.execSQL("INSERT INTO encrypted_sessions (contactId, encryptedState, updatedAtEpochMillis) VALUES ('legacy', X'0A0B', 1)")
            db.execSQL(
                "INSERT INTO encrypted_messages (messageId, contactId, encryptedPayload, encodedAssociatedData, outgoing, " +
                    "status, createdAtEpochMillis, deliveryAttemptCount, nextRetryAtEpochMillis) " +
                    "VALUES ('m1', 'legacy', X'0C', X'0D', 1, 'PENDING', 1, 0, NULL)",
            )
            db.execSQL("INSERT INTO accepted_replay (conversationId, senderDeviceId, messageNumber, acceptedAtEpochMillis) VALUES ('c', 's', 1, 1)")
            db.execSQL("INSERT INTO signal_session_records (recipientId, deviceId, encryptedRecord) VALUES ('v2-contact', 1, X'0304')")
            db.execSQL(
                "INSERT INTO v2_contacts (contactId, encryptedDisplayName, identityKey, peerQueueId, peerRoutingTagHex, " +
                    "localRoutingTagHex, state, createdAtEpochMillis) VALUES ('v2-contact', X'01', X'05', 'peer-queue-1', 'aa', 'bb', 'ACTIVE', 1)",
            )
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            8,
            true,
            SecureMessengerDatabase.MIGRATION_7_8,
        )
        SecureMessengerDatabase.LEGACY_TABLES.forEach { table ->
            migrated.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$table'").use { cursor ->
                assertEquals("legacy table $table survived", 0, cursor.count)
            }
        }
        listOf("local_profile", "signal_session_records", "v2_contacts").forEach { table ->
            migrated.query("SELECT COUNT(*) FROM $table").use { cursor ->
                cursor.moveToFirst()
                assertEquals("$table lost rows", 1, cursor.getInt(0))
            }
        }
        migrated.close()
    }

    /**
     * Phase 2 exit gate: a v5 install full of prototype data migrates through every step to v8. The
     * prototype data is dropped on purpose (DEC-021); the profile, the vault's canary (DEC-023),
     * survives byte for byte, so the vault key still opens it.
     */
    @Test
    fun migrationFromFiveWithPrototypeDataReachesEightDropsItAndKeepsTheProfile() {
        val profileBlob = "0102030405060708"
        migrationHelper.createDatabase(TEST_DATABASE, 5).use { db ->
            db.execSQL("INSERT INTO local_profile (profileId, encryptedDisplayName, deviceId) VALUES ('local', X'$profileBlob', 'legacy-device')")
            db.execSQL(
                "INSERT INTO contacts (contactId, encryptedLocalDisplayName, queueId, deviceId, encodedPublicKey, " +
                    "fingerprint, verificationStatus) VALUES ('legacy', X'01', 'legacy-queue', 'device', X'02', 'FP', 'UNVERIFIED')",
            )
            db.execSQL(
                "INSERT INTO contact_devices (contactId, deviceId, queueId, encodedPublicKey, fingerprint, approvalStatus, " +
                    "firstSeenEpochMillis) VALUES ('legacy', 'device', 'legacy-queue', X'02', 'FP', 'PRIMARY_UNVERIFIED', 1)",
            )
            db.execSQL("INSERT INTO encrypted_sessions (contactId, encryptedState, updatedAtEpochMillis) VALUES ('legacy', X'0A0B', 1)")
            db.execSQL(
                "INSERT INTO encrypted_messages (messageId, contactId, encryptedPayload, encodedAssociatedData, outgoing, " +
                    "status, createdAtEpochMillis, deliveryAttemptCount, nextRetryAtEpochMillis) " +
                    "VALUES ('m1', 'legacy', X'0C', X'0D', 1, 'PENDING', 1, 0, NULL)",
            )
            db.execSQL("INSERT INTO accepted_replay (conversationId, senderDeviceId, messageNumber, acceptedAtEpochMillis) VALUES ('c', 's', 1, 1)")
            db.execSQL("INSERT INTO signal_kyber_prekey_records (kyberPreKeyId, encryptedRecord) VALUES (7, X'0102')")
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            8,
            true,
            SecureMessengerDatabase.MIGRATION_5_6,
            SecureMessengerDatabase.MIGRATION_6_7,
            SecureMessengerDatabase.MIGRATION_7_8,
        )
        SecureMessengerDatabase.LEGACY_TABLES.forEach { table ->
            migrated.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$table'").use { cursor ->
                assertEquals("legacy table $table survived", 0, cursor.count)
            }
        }
        migrated.query("SELECT hex(encryptedDisplayName) FROM local_profile WHERE profileId = 'local'").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(profileBlob.uppercase(), cursor.getString(0))
        }
        listOf("signal_kyber_prekey_records", "v2_contacts", "v2_messages", "signal_session_records").forEach { table ->
            migrated.query("SELECT COUNT(*) FROM $table").use { cursor ->
                cursor.moveToFirst()
                assertEquals("$table is not empty", 0, cursor.getInt(0))
            }
        }
        migrated.close()
    }

    /**
     * DEC-024: the v2 test conversations are reset once for the relay-queue change. The profile and
     * the local libsignal identity survive byte for byte; everything tied to old contacts is gone.
     */
    @Test
    fun migrationEightToNineResetsV2ConversationsAndKeepsProfileAndLocalIdentity() {
        migrationHelper.createDatabase(TEST_DATABASE, 8).use { db ->
            db.execSQL("INSERT INTO local_profile (profileId, encryptedDisplayName, deviceId) VALUES ('local', X'0A0B', '')")
            db.execSQL("INSERT INTO signal_local_identity (singletonId, encryptedRecord) VALUES (1, X'0C0D')")
            db.execSQL("INSERT INTO signal_session_records (recipientId, deviceId, encryptedRecord) VALUES ('c', 1, X'01')")
            db.execSQL("INSERT INTO signal_identity_records (recipientId, deviceId, encryptedRecord) VALUES ('c', 1, X'02')")
            db.execSQL("INSERT INTO signal_prekey_records (preKeyId, encryptedRecord) VALUES (5, X'03')")
            db.execSQL(
                "INSERT INTO v2_contacts (contactId, encryptedDisplayName, identityKey, peerQueueId, peerRoutingTagHex, " +
                    "localRoutingTagHex, state, createdAtEpochMillis) VALUES ('c', X'01', X'05', 'v2-old-queue-1', 'aa', 'bb', 'ACTIVE', 1)",
            )
        }

        val migrated = migrationHelper.runMigrationsAndValidate(TEST_DATABASE, 9, true, SecureMessengerDatabase.MIGRATION_8_9)
        SecureMessengerDatabase.RELAY_V3_RESET_TABLES.forEach { table ->
            migrated.query("SELECT COUNT(*) FROM $table").use { cursor ->
                cursor.moveToFirst()
                assertEquals("$table kept rows", 0, cursor.getInt(0))
            }
        }
        migrated.query("SELECT hex(encryptedDisplayName) FROM local_profile").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("0A0B", cursor.getString(0))
        }
        migrated.query("SELECT hex(encryptedRecord) FROM signal_local_identity").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("0C0D", cursor.getString(0))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DATABASE = "signal-store-migration-test"
    }
}
