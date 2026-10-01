package app.securemessenger.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.securemessenger.signalprotocol.EncryptedSignalProtocolStore
import app.securemessenger.signalprotocol.LocalSignalIdentityProvisioner
import app.securemessenger.storage.EncryptedSignalStoreRepository
import app.securemessenger.storage.JcaLocalVaultCipher
import app.securemessenger.storage.SecureMessengerDatabase
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.ReusedBaseKeyException
import org.signal.libsignal.protocol.ecc.ECKeyPair

/**
 * Phase 5, second review R1, on the production pair (store adapter over the Room repository): key
 * IDs from a PreKey message above 2^31 reach the store as negative ints (shown in
 * `V2AttackMatrixTest`). They must mean "no such key" (a protocol rejection), never an
 * `IllegalArgumentException` from the repository, which would stall the receive queue.
 */
@RunWith(AndroidJUnit4::class)
class SignalStoreHostileKeyIdInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.databaseBuilder(context, SecureMessengerDatabase::class.java, DATABASE).build()
    private val records = EncryptedSignalStoreRepository(database.dao(), JcaLocalVaultCipher(ByteArray(32) { 5 }))
    private val store = EncryptedSignalProtocolStore(records, records, records, records, records, records)

    @After
    fun cleanUp() {
        database.close()
        context.deleteDatabase(DATABASE)
    }

    @Test
    fun negativeKeyIdsAreUnknownKeysNeverStorageErrors() {
        LocalSignalIdentityProvisioner(records).provisionIfAbsent()
        for (id in listOf(-1, Int.MIN_VALUE, 0x80000000.toInt(), 0xFFFFFFFF.toInt())) {
            assertThrows(InvalidKeyIdException::class.java) { store.loadPreKey(id) }
            assertThrows(InvalidKeyIdException::class.java) { store.loadSignedPreKey(id) }
            assertThrows(InvalidKeyIdException::class.java) { store.loadKyberPreKey(id) }
            assertFalse(store.containsPreKey(id))
            assertFalse(store.containsSignedPreKey(id))
            assertFalse(store.containsKyberPreKey(id))
            assertThrows(ReusedBaseKeyException::class.java) { store.markKyberPreKeyUsed(id, 1, ECKeyPair.generate().publicKey) }
            assertThrows(ReusedBaseKeyException::class.java) { store.markKyberPreKeyUsed(1, id, ECKeyPair.generate().publicKey) }
            store.removePreKey(id)
            store.removeSignedPreKey(id)
            store.removeKyberPreKey(id)
        }
    }

    private companion object {
        const val DATABASE = "hostile-key-id-test.db"
    }
}
