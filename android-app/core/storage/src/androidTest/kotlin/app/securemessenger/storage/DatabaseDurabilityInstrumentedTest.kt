package app.securemessenger.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 5, second review (R5, I6): an envelope's relay copy is deleted once its transaction
 * committed, so a commit must survive a power loss (`synchronous` FULL), and deleted rows (old
 * ratchet states, spent pre-keys, even though vault-encrypted) must not linger in the file's free
 * pages (`secure_delete`). This pins the settings the database really runs with on the phone.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseDurabilityInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun commitsSurviveAPowerLossAndDeletedRowsAreOverwritten() {
        val database = SecureMessengerDatabase.create(context)
        try {
            val db = database.openHelper.writableDatabase
            fun pragma(name: String): String = db.query("PRAGMA $name").use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0)
            }
            val settings = "journal_mode=${pragma("journal_mode")} synchronous=${pragma("synchronous")} secure_delete=${pragma("secure_delete")}"
            assertEquals(settings, "2", pragma("synchronous"))
            assertEquals(settings, "1", pragma("secure_delete"))
        } finally {
            database.close()
        }
    }
}
