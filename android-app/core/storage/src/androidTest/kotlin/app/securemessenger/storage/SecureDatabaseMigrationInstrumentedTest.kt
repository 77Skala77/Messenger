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

    private companion object {
        const val TEST_DATABASE = "signal-store-migration-test"
    }
}
