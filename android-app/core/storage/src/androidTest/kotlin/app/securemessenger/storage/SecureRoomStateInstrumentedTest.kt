package app.securemessenger.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.securemessenger.signalstore.SignalLocalIdentityRecord
import app.securemessenger.signalstore.SignalProtocolAddress
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureRoomStateInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, SecureMessengerDatabase::class.java).build()
    private val profiles = LocalProfileRepository(database.dao(), JcaLocalVaultCipher(ByteArray(32) { 4 }))
    private val signalStore = EncryptedSignalStoreRepository(database.dao(), JcaLocalVaultCipher(ByteArray(32) { 7 }))

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun profileNameIsEncryptedAtRest() = runBlocking {
        assertFalse(profiles.hasProfile())
        assertNull(profiles.readProfileName())

        profiles.storeProfile("Private Display Name")

        assertTrue(profiles.hasProfile())
        assertEquals("Private Display Name", profiles.readProfileName())
        val stored = database.dao().getProfile()!!
        assertFalse(String(stored.encryptedDisplayName, Charsets.ISO_8859_1).contains("Private Display Name"))
        assertEquals("", stored.deviceId)
    }

    @Test
    fun signalRecordsAreEncryptedAtRestAndCanBeDeleted() = runBlocking {
        val address = SignalProtocolAddress("future-recipient", 2)
        val identityRecord = "identity-record-private-material".toByteArray()
        val sessionRecord = "session-record-secret-state".toByteArray()
        val preKeyRecord = "prekey-private-material".toByteArray()
        val signedPreKeyRecord = "signed-prekey-private-material".toByteArray()
        val kyberPreKeyRecord = "kyber-prekey-private-material".toByteArray()

        signalStore.saveIdentityRecord(address, identityRecord)
        signalStore.saveSessionRecord(address, sessionRecord)
        signalStore.savePreKeyRecord(11, preKeyRecord)
        signalStore.saveSignedPreKeyRecord(12, signedPreKeyRecord)
        signalStore.saveKyberPreKeyRecord(13, kyberPreKeyRecord, lastResort = false)

        val encryptedRecords = listOf(
            database.dao().getSignalIdentityRecord(address.recipientId, address.deviceId)!!.encryptedRecord,
            database.dao().getSignalSessionRecord(address.recipientId, address.deviceId)!!.encryptedRecord,
            database.dao().getSignalPreKeyRecord(11)!!.encryptedRecord,
            database.dao().getSignalSignedPreKeyRecord(12)!!.encryptedRecord,
            database.dao().getSignalKyberPreKeyRecord(13)!!.encryptedRecord,
        )
        listOf(identityRecord, sessionRecord, preKeyRecord, signedPreKeyRecord, kyberPreKeyRecord)
            .zip(encryptedRecords)
            .forEach { (record, stored) ->
                assertFalse(stored.toString(Charsets.UTF_8).contains(record.toString(Charsets.UTF_8)))
            }

        assertArrayEquals(identityRecord, signalStore.loadIdentityRecord(address))
        assertArrayEquals(sessionRecord, signalStore.loadSessionRecord(address))
        assertArrayEquals(preKeyRecord, signalStore.loadPreKeyRecord(11))
        assertArrayEquals(signedPreKeyRecord, signalStore.loadSignedPreKeyRecord(12))
        assertArrayEquals(kyberPreKeyRecord, signalStore.loadKyberPreKeyRecord(13)!!.serializedRecord)

        signalStore.deleteIdentityRecord(address)
        signalStore.deleteSessionRecord(address)
        signalStore.deletePreKeyRecord(11)
        signalStore.deleteSignedPreKeyRecord(12)
        signalStore.deleteKyberPreKeyRecord(13)

        assertNull(signalStore.loadIdentityRecord(address))
        assertNull(signalStore.loadSessionRecord(address))
        assertNull(signalStore.loadPreKeyRecord(11))
        assertNull(signalStore.loadSignedPreKeyRecord(12))
        assertNull(signalStore.loadKyberPreKeyRecord(13))
    }

    @Test
    fun oneTimeKyberPreKeyIsConsumedExactlyOnce() {
        signalStore.saveKyberPreKeyRecord(21, "one-time-kyber".toByteArray(), lastResort = false)

        assertTrue(signalStore.consumeKyberPreKey(21, 5, digest(1)))
        assertNull(signalStore.loadKyberPreKeyRecord(21))
        assertFalse(signalStore.consumeKyberPreKey(21, 5, digest(2)))
    }

    @Test
    fun lastResortKyberPreKeyRejectsRepeatedBaseKeyButStaysAvailable() {
        signalStore.saveKyberPreKeyRecord(22, "last-resort-kyber".toByteArray(), lastResort = true)

        assertTrue(signalStore.consumeKyberPreKey(22, 5, digest(1)))
        assertFalse(signalStore.consumeKyberPreKey(22, 5, digest(1)))
        assertTrue(signalStore.consumeKyberPreKey(22, 5, digest(2)))
        assertTrue(signalStore.consumeKyberPreKey(22, 6, digest(1)))
        assertTrue(signalStore.loadKyberPreKeyRecord(22)!!.lastResort)
    }

    @Test
    fun tamperedKyberLastResortFlagFailsClosed() {
        signalStore.saveKyberPreKeyRecord(23, "one-time-kyber".toByteArray(), lastResort = false)
        val stored = database.dao().getSignalKyberPreKeyRecord(23)!!
        database.dao().putSignalKyberPreKeyRecord(stored.copy(isLastResort = true))

        assertThrows(Exception::class.java) { signalStore.loadKyberPreKeyRecord(23) }
        assertThrows(Exception::class.java) { signalStore.consumeKyberPreKey(23, 5, digest(1)) }
    }

    @Test
    fun localSignalIdentityIsEncryptedAndNeverOverwritten() {
        val keyPair = "local-identity-private-material".toByteArray()

        assertTrue(signalStore.createLocalIdentity(SignalLocalIdentityRecord(4242, keyPair)))
        assertFalse(signalStore.createLocalIdentity(SignalLocalIdentityRecord(7, "replacement".toByteArray())))

        val stored = database.dao().getSignalLocalIdentity(1)!!.encryptedRecord
        assertFalse(stored.toString(Charsets.UTF_8).contains("local-identity-private-material"))
        val loaded = signalStore.loadLocalIdentity()!!
        assertEquals(4242, loaded.registrationId)
        assertArrayEquals(keyPair, loaded.serializedIdentityKeyPair)
    }

    @Test
    fun localWipeClearsProfileAndEverySignalRecord() = runBlocking {
        val signalAddress = SignalProtocolAddress("wiped-contact", 1)
        profiles.storeProfile("Test")
        signalStore.saveIdentityRecord(signalAddress, "identity".toByteArray())
        signalStore.saveSessionRecord(signalAddress, "session".toByteArray())
        signalStore.createLocalIdentity(SignalLocalIdentityRecord(99, "local-identity".toByteArray()))
        signalStore.saveKyberPreKeyRecord(31, "last-resort".toByteArray(), lastResort = true)
        signalStore.consumeKyberPreKey(31, 1, ByteArray(32) { 3 })

        profiles.wipeLocalData()

        assertNull(database.dao().getProfile())
        assertNull(signalStore.loadIdentityRecord(signalAddress))
        assertNull(signalStore.loadSessionRecord(signalAddress))
        assertNull(signalStore.loadLocalIdentity())
        // Reuse tracking was wiped too: a fresh key under the same IDs starts clean.
        signalStore.saveKyberPreKeyRecord(31, "last-resort".toByteArray(), lastResort = true)
        assertTrue(signalStore.consumeKyberPreKey(31, 1, ByteArray(32) { 3 }))
    }

    private fun digest(seed: Int) = ByteArray(32) { seed.toByte() }
}
