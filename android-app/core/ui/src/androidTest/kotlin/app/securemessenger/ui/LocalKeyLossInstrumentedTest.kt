package app.securemessenger.ui

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import app.securemessenger.storage.AndroidKeystoreVaultCipher
import app.securemessenger.storage.LocalDataState
import app.securemessenger.storage.LocalProfileRepository
import app.securemessenger.storage.SecureMessengerDatabase
import app.securemessenger.storage.VaultKeyUnavailableException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DEC-023 end to end on a device: the vault key of an existing install disappears. The pipeline and
 * the background worker refuse to run and change or create nothing; the app shows why and offers
 * only a confirmed deletion, after which the user starts over.
 */
@RunWith(AndroidJUnit4::class)
class LocalKeyLossInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<LanguageSwitchHarnessActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val vault = AndroidKeystoreVaultCipher()

    @Before
    fun startEmpty() = wipeEverything()

    @After
    fun cleanUp() = wipeEverything()

    @Test
    fun aLostKeyStopsThePipelineAndTheWorkerWithoutChangingOrCreatingAnything() {
        val (profileBefore, identityBefore) = loseTheKeyOfAnExistingInstall()

        withDatabase { database ->
            assertEquals(LocalDataState.UNREADABLE, LocalProfileRepository(database.dao(), vault).localDataState())
            assertThrows(VaultKeyUnavailableException::class.java) { V2Pipeline.createIfProfileExists(context, database) }
            val worker = TestListenableWorkerBuilder<V2SyncWorker>(context).build()
            // Not a retry: retrying cannot bring the key back.
            assertEquals(ListenableWorker.Result.failure(), runBlocking { worker.doWork() })

            assertFalse("nothing may create a replacement key", vault.hasKey())
            assertArrayEquals(profileBefore, database.dao().getProfile()!!.encryptedDisplayName)
            assertArrayEquals(identityBefore, database.dao().getSignalLocalIdentity(1)!!.encryptedRecord)
        }
    }

    @Test
    fun theAppExplainsTheLossAndOnlyAConfirmedDeletionStartsOver() {
        loseTheKeyOfAnExistingInstall()
        val activity = composeRule.activity
        composeRule.runOnUiThread { activity.setContent { SecureMessengerApp() } }
        val unreadableTitle = activity.getString(R.string.local_data_unreadable_title)
        composeRule.waitUntil(TIMEOUT_MILLIS) { displayed(unreadableTitle) }

        // Checking again changes nothing and never creates a key.
        composeRule.onNodeWithTag(CHECK_AGAIN_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(unreadableTitle).assertIsDisplayed()
        assertFalse(vault.hasKey())

        composeRule.onNodeWithTag(DELETE_AND_RESTART_TAG).performClick()
        composeRule.onNodeWithTag(CONFIRM_DELETE_TAG).performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) { displayed(activity.getString(R.string.onboarding_title)) }

        withDatabase { database ->
            assertEquals(LocalDataState.EMPTY, LocalProfileRepository(database.dao(), vault).localDataState())
            assertNull(database.dao().getSignalLocalIdentity(1))
        }
        assertFalse(vault.hasKey())
    }

    /** A profile and a libsignal identity exist; then the vault key disappears. Returns both rows. */
    private fun loseTheKeyOfAnExistingInstall(): Pair<ByteArray, ByteArray> = withDatabase { database ->
        LocalProfileRepository(database.dao(), vault).createProfile("Alice")
        assertNotNull(V2Pipeline.createIfProfileExists(context, database))
        val rows = database.dao().getProfile()!!.encryptedDisplayName to database.dao().getSignalLocalIdentity(1)!!.encryptedRecord
        vault.deleteKey()
        rows
    }

    private fun wipeEverything() = withDatabase { database -> V2Pipeline.wipeLocalData(context, database) }

    private fun <T> withDatabase(block: (SecureMessengerDatabase) -> T): T {
        val database = SecureMessengerDatabase.create(context)
        return try {
            block(database)
        } finally {
            database.close()
        }
    }

    private fun displayed(text: String): Boolean =
        runCatching { composeRule.onNodeWithText(text).assertIsDisplayed() }.isSuccess

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
