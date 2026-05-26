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

    private companion object {
        const val TEST_DATABASE = "signal-store-migration-test"
    }
}
