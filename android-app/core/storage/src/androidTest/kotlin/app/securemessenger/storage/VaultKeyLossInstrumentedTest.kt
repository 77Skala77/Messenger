package app.securemessenger.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.securemessenger.signalstore.SignalLocalIdentityRecord
import app.securemessenger.signalstore.SignalProtocolAddress
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DEC-023 on the real Android Keystore: a missing, replaced or damaged vault key, and damaged rows,
 * fail closed. No key is ever created behind the user's back, nothing unreadable is reported as
 * "not stored", and nothing falls back to plaintext.
 */
@RunWith(AndroidJUnit4::class)
class VaultKeyLossInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, SecureMessengerDatabase::class.java).build()
    private val vault = AndroidKeystoreVaultCipher(TEST_ALIAS)
    private val profiles = LocalProfileRepository(database.dao(), vault)
    private val signalStore = EncryptedSignalStoreRepository(database.dao(), vault)

    @Before
    fun startWithoutKey() = vault.deleteKey()

    @After
    fun cleanUp() {
        database.close()
        vault.deleteKey()
    }

    @Test
    fun encryptAndDecryptNeverCreateAKey() {
        assertFalse(vault.hasKey())
        assertThrows(VaultKeyUnavailableException::class.java) { vault.encrypt(PLAINTEXT, AAD) }
        assertFalse(vault.hasKey())

        vault.createKeyIfAbsent()
        val blob = vault.encrypt(PLAINTEXT, AAD)
        vault.deleteKey()

        assertThrows(VaultKeyUnavailableException::class.java) { vault.decrypt(blob, AAD) }
        assertFalse("a lost key must never be replaced silently", vault.hasKey())
    }

    @Test
    fun createKeyIfAbsentKeepsTheExistingKey() {
        vault.createKeyIfAbsent()
        val blob = vault.encrypt(PLAINTEXT, AAD)

        vault.createKeyIfAbsent()

        assertArrayEquals(PLAINTEXT, vault.decrypt(blob, AAD))
    }

    @Test
    fun localDataStateFollowsTheKeyAndTheWipeIsTheWayOut() {
        assertEquals(LocalDataState.EMPTY, profiles.localDataState())
        profiles.createProfile("Alice")
        assertTrue(vault.hasKey())
        assertEquals(LocalDataState.READABLE, profiles.localDataState())

        // The key is lost: unreadable, and neither the check nor a new profile creates a key.
        vault.deleteKey()
        assertEquals(LocalDataState.UNREADABLE, profiles.localDataState())
        assertThrows(VaultKeyUnavailableException::class.java) { profiles.readProfileName() }
        assertThrows(IllegalStateException::class.java) { profiles.createProfile("Someone else") }
        assertFalse(vault.hasKey())

        // A different key under the same alias does not make the old data readable.
        vault.createKeyIfAbsent()
        assertEquals(LocalDataState.UNREADABLE, profiles.localDataState())
        assertThrows(VaultRecordRejectedException::class.java) { profiles.readProfileName() }

        // The way out needs no key; afterwards a new profile works.
        vault.deleteKey()
        profiles.wipeLocalData()
        assertEquals(LocalDataState.EMPTY, profiles.localDataState())
        profiles.createProfile("Alice again")
        assertEquals(LocalDataState.READABLE, profiles.localDataState())
        assertEquals("Alice again", profiles.readProfileName())
    }

    @Test
    fun aDamagedProfileRowMakesTheDataUnreadable() {
        profiles.createProfile("Alice")
        val stored = database.dao().getProfile()!!

        database.dao().putProfile(stored.copy(encryptedDisplayName = stored.encryptedDisplayName.withLastBitFlipped()))
        assertEquals(LocalDataState.UNREADABLE, profiles.localDataState())

        database.dao().putProfile(stored.copy(encryptedDisplayName = stored.encryptedDisplayName.copyOf(10)))
        assertEquals(LocalDataState.UNREADABLE, profiles.localDataState())

        database.dao().putProfile(stored)
        assertEquals(LocalDataState.READABLE, profiles.localDataState())
    }

    @Test
    fun damagedOrUndecryptableRecordsFailClosedAndAreNeverReportedAsMissing() {
        profiles.createProfile("Alice")
        val address = SignalProtocolAddress("contact", 1)
        signalStore.saveIdentityRecord(address, "identity".toByteArray())
        signalStore.saveSessionRecord(address, "session".toByteArray())
        signalStore.createLocalIdentity(SignalLocalIdentityRecord(7, "local-identity".toByteArray()))

        // A damaged identity must fail, never look like "no identity stored": that would let a new
        // identity be trusted on first use.
        val identity = database.dao().getSignalIdentityRecord("contact", 1)!!
        database.dao().putSignalIdentityRecord(identity.copy(encryptedRecord = identity.encryptedRecord.withLastBitFlipped()))
        assertThrows(VaultRecordRejectedException::class.java) { signalStore.loadIdentityRecord(address) }
        database.dao().putSignalIdentityRecord(identity)
        assertArrayEquals("identity".toByteArray(), signalStore.loadIdentityRecord(address))

        // With the key gone every stored record fails the same way, and the local identity is not
        // replaced by a new one.
        val storedLocalIdentity = database.dao().getSignalLocalIdentity(1)!!.encryptedRecord
        vault.deleteKey()
        assertThrows(VaultKeyUnavailableException::class.java) { signalStore.loadIdentityRecord(address) }
        assertThrows(VaultKeyUnavailableException::class.java) { signalStore.loadSessionRecord(address) }
        assertThrows(VaultKeyUnavailableException::class.java) { signalStore.loadLocalIdentity() }
        assertThrows(VaultKeyUnavailableException::class.java) {
            signalStore.createLocalIdentity(SignalLocalIdentityRecord(8, "replacement".toByteArray()))
        }
        assertArrayEquals(storedLocalIdentity, database.dao().getSignalLocalIdentity(1)!!.encryptedRecord)
        // Records that were never stored are still reported as absent, without touching the key.
        assertNull(signalStore.loadSessionRecord(SignalProtocolAddress("nobody", 1)))
        assertFalse(vault.hasKey())
    }

    private fun ByteArray.withLastBitFlipped(): ByteArray =
        copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }

    private companion object {
        const val TEST_ALIAS = "secure_messenger_key_loss_test_vault"
        val PLAINTEXT = "stored-value".toByteArray()
        val AAD = "row-id".toByteArray()
    }
}
