package app.securemessenger.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.securemessenger.signalprotocol.ConversationV2Service
import app.securemessenger.storage.AndroidKeystoreVaultCipher
import app.securemessenger.storage.EncryptedSignalStoreRepository
import app.securemessenger.storage.LocalProfileRepository
import app.securemessenger.storage.SecureMessengerDatabase
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The local wipe and the creation of the pipeline hold the protocol lock: a protocol step of the UI
 * or the background worker can never commit between or after the wipe's deletes, and a wipe can
 * never leave a new libsignal identity behind.
 */
@RunWith(AndroidJUnit4::class)
class V2PipelineWipeInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val vault = AndroidKeystoreVaultCipher()
    private lateinit var database: SecureMessengerDatabase

    @Before
    fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(context, SecureMessengerDatabase::class.java).build()
    }

    @After
    fun cleanUp() {
        database.close()
        vault.deleteKey()
        V2QueueIdStore(context).clear()
    }

    @Test
    fun withoutAProfileNeitherAPipelineNorAnIdentityIsCreated() {
        assertNull(V2Pipeline.createIfProfileExists(context, database))
        assertNull(EncryptedSignalStoreRepository(database.dao(), vault).loadLocalIdentity())
    }

    @Test
    fun theWipeWaitsForARunningProtocolStepAndRemovesWhatTheStepWrote() {
        val profiles = LocalProfileRepository(database.dao(), vault)
        val signalRecords = EncryptedSignalStoreRepository(database.dao(), vault)
        profiles.createProfile("Wipe Test")
        assertNotNull(V2Pipeline.createIfProfileExists(context, database))
        assertNotNull(signalRecords.loadLocalIdentity())

        val stepHoldsLock = CountDownLatch(1)
        val finishStep = CountDownLatch(1)
        val step = thread {
            ConversationV2Service.exclusively {
                stepHoldsLock.countDown()
                finishStep.await(10, TimeUnit.SECONDS)
                // The last write of a running step, committed just before it releases the lock.
                signalRecords.savePreKeyRecord(LATE_PRE_KEY_ID, "written by the step".toByteArray())
            }
        }
        assertTrue(stepHoldsLock.await(10, TimeUnit.SECONDS))
        val wiped = CountDownLatch(1)
        val wipe = thread {
            V2Pipeline.wipeLocalData(context, database)
            wiped.countDown()
        }
        assertFalse("the wipe must wait for the running step", wiped.await(500, TimeUnit.MILLISECONDS))

        finishStep.countDown()
        assertTrue(wiped.await(10, TimeUnit.SECONDS))
        step.join()
        wipe.join()

        assertFalse(profiles.hasProfile())
        assertNull(database.dao().getSignalPreKeyRecord(LATE_PRE_KEY_ID))
        assertNull(database.dao().getSignalLocalIdentity(1))
        assertNull(V2Pipeline.createIfProfileExists(context, database))
        assertNull(database.dao().getSignalLocalIdentity(1))
        // DEC-023: the wipe deleted the vault key, and nothing but a new profile creates one.
        assertFalse(vault.hasKey())
    }

    private companion object {
        const val LATE_PRE_KEY_ID = 424242
    }
}
