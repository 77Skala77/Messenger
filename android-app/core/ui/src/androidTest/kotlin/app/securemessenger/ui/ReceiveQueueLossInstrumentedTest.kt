package app.securemessenger.ui

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import app.securemessenger.signalstore.V2Contact
import app.securemessenger.signalstore.V2ContactState
import app.securemessenger.storage.AndroidKeystoreVaultCipher
import app.securemessenger.storage.EncryptedConversationV2Repository
import app.securemessenger.storage.LocalDataState
import app.securemessenger.storage.LocalProfileRepository
import app.securemessenger.storage.SecureMessengerDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Second review R6 (DEC-032) on a device: the inbox secret disappears while the database still has
 * a contact. Nothing creates a new inbox the contact would never learn about; the pipeline and the
 * worker refuse to run; the app explains it and only a confirmed deletion starts over. A phone that
 * nobody can reach yet simply gets its first inbox.
 */
@RunWith(AndroidJUnit4::class)
class ReceiveQueueLossInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<LanguageSwitchHarnessActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val vault = AndroidKeystoreVaultCipher()

    @Before
    fun startEmpty() = wipeEverything()

    @After
    fun cleanUp() = wipeEverything()

    @Test
    fun aLostInboxSecretStopsThePipelineAndTheWorkerAndIsNeverReplaced() = withDatabase { database ->
        loseTheInboxOfAPairedInstall(database)

        assertThrows(ReceiveQueueLostException::class.java) { V2Pipeline.createIfProfileExists(context, database) }
        assertNull("nothing created a new inbox", V2QueueCredentialStore(context, vault).stored())
        assertTrue(V2Pipeline.receiveQueueLost(context, database))

        val worker = TestListenableWorkerBuilder<V2SyncWorker>(context).build()
        // Not a retry: retrying cannot bring the secret back.
        assertEquals(ListenableWorker.Result.failure(), runBlocking { worker.doWork() })
        assertNull(V2QueueCredentialStore(context, vault).stored())
        assertEquals(1, database.dao().getV2Contacts().size)
    }

    @Test
    fun aPhoneNobodyCanReachYetGetsItsFirstInbox() = withDatabase { database ->
        LocalProfileRepository(database.dao(), vault).createProfile("Alice")

        assertFalse(V2Pipeline.receiveQueueLost(context, database))
        assertNotNull(V2Pipeline.createIfProfileExists(context, database))
        assertNotNull(V2QueueCredentialStore(context, vault).stored())
    }

    @Test
    fun theAppExplainsTheLostInboxAndOnlyAConfirmedDeletionStartsOver() {
        withDatabase(::loseTheInboxOfAPairedInstall)
        val activity = composeRule.activity
        composeRule.runOnUiThread { activity.setContent { SecureMessengerApp() } }
        val title = activity.getString(R.string.receive_queue_lost_title)
        composeRule.waitUntil(TIMEOUT_MILLIS) { displayed(title) }

        // Checking again changes nothing and creates no inbox.
        composeRule.onNodeWithTag(CHECK_AGAIN_TAG).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(title).assertExists()
        assertNull(V2QueueCredentialStore(context, vault).stored())

        composeRule.onNodeWithTag(DELETE_AND_RESTART_TAG).performScrollTo().performClick()
        composeRule.onNodeWithTag(CONFIRM_DELETE_TAG).performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) { displayed(activity.getString(R.string.onboarding_title)) }
        withDatabase { database ->
            assertEquals(LocalDataState.EMPTY, LocalProfileRepository(database.dao(), vault).localDataState())
            assertTrue(database.dao().getV2Contacts().isEmpty())
        }
    }

    /** A profile, its inbox and a contact who knows it; then the inbox secret disappears. */
    private fun loseTheInboxOfAPairedInstall(database: SecureMessengerDatabase) {
        LocalProfileRepository(database.dao(), vault).createProfile("Alice")
        assertNotNull(V2Pipeline.createIfProfileExists(context, database))
        assertNotNull(V2QueueCredentialStore(context, vault).stored())
        EncryptedConversationV2Repository(database.dao(), vault).saveContact(
            V2Contact("contact-1", "Bob", ByteArray(33) { 5 }, "q3-bob", "00".repeat(16), "11".repeat(16), V2ContactState.ACTIVE, 1L),
        )
        V2QueueCredentialStore(context, vault).clear()
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
