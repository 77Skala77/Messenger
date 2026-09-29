package app.securemessenger.signalprotocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ReusedBaseKeyException
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord

/**
 * Exercises the adapter with real libsignal PQXDH + Double Ratchet sessions. Needs libsignal's
 * native library on the test JVM, which libsignal-client ships for desktop platforms.
 */
class EncryptedSignalProtocolStoreTest {
    private val aliceAddress = SignalProtocolAddress("alice", 1)
    private val bobAddress = SignalProtocolAddress("bob", 1)

    @Test
    fun pqxdhSessionRoundTripsThroughTheStoreAdapter() {
        val alice = provisionedStore()
        val bob = provisionedStore()
        val bundle = publishOneTimeBundle(bob)

        SessionBuilder(alice, alice, alice, alice, bobAddress, aliceAddress).process(bundle)
        val aliceCipher = SessionCipher(alice, alice, alice, alice, alice, aliceAddress, bobAddress)
        val bobCipher = SessionCipher(bob, bob, bob, bob, bob, bobAddress, aliceAddress)

        val first = aliceCipher.encrypt("hello bob".toByteArray())
        assertEquals(CiphertextMessage.PREKEY_TYPE, first.type)
        assertArrayEquals("hello bob".toByteArray(), bobCipher.decrypt(PreKeySignalMessage(first.serialize())))

        val reply = bobCipher.encrypt("hello alice".toByteArray())
        assertEquals(CiphertextMessage.WHISPER_TYPE, reply.type)
        assertArrayEquals("hello alice".toByteArray(), aliceCipher.decrypt(SignalMessage(reply.serialize())))

        // Both sides persisted their ratchet state through the adapter.
        assertTrue(alice.containsSession(bobAddress))
        assertTrue(bob.containsSession(aliceAddress))
    }

    @Test
    fun oneTimePreKeysAreConsumedAndAPreKeyMessageCannotBeReplayed() {
        val alice = provisionedStore()
        val bob = provisionedStore()
        val bundle = publishOneTimeBundle(bob)
        SessionBuilder(alice, alice, alice, alice, bobAddress, aliceAddress).process(bundle)
        val first = SessionCipher(alice, alice, alice, alice, alice, aliceAddress, bobAddress)
            .encrypt("once".toByteArray())
        val bobCipher = SessionCipher(bob, bob, bob, bob, bob, bobAddress, aliceAddress)

        bobCipher.decrypt(PreKeySignalMessage(first.serialize()))

        assertFalse(bob.containsPreKey(PRE_KEY_ID))
        assertFalse(bob.containsKyberPreKey(KYBER_PRE_KEY_ID))
        assertTrue(bob.containsSignedPreKey(SIGNED_PRE_KEY_ID))
        assertThrows(Exception::class.java) { bobCipher.decrypt(PreKeySignalMessage(first.serialize())) }
    }

    @Test
    fun oneTimeKyberPreKeyCanBeMarkedUsedOnlyOnce() {
        val bob = provisionedStore()
        bob.storeKyberPreKey(8, kyberRecord(bob, 8))

        bob.markKyberPreKeyUsed(8, SIGNED_PRE_KEY_ID, ECKeyPair.generate().publicKey)

        assertFalse(bob.containsKyberPreKey(8))
        assertThrows(ReusedBaseKeyException::class.java) {
            bob.markKyberPreKeyUsed(8, SIGNED_PRE_KEY_ID, ECKeyPair.generate().publicKey)
        }
    }

    @Test
    fun lastResortKyberPreKeyRejectsARepeatedBaseKeyButStaysAvailable() {
        val bob = provisionedStore()
        bob.storeLastResortKyberPreKey(7, kyberRecord(bob, 7))
        val baseKey = ECKeyPair.generate().publicKey

        bob.markKyberPreKeyUsed(7, SIGNED_PRE_KEY_ID, baseKey)
        assertThrows(ReusedBaseKeyException::class.java) {
            bob.markKyberPreKeyUsed(7, SIGNED_PRE_KEY_ID, baseKey)
        }
        bob.markKyberPreKeyUsed(7, SIGNED_PRE_KEY_ID, ECKeyPair.generate().publicKey)
        assertTrue(bob.containsKyberPreKey(7))
    }

    @Test
    fun changedRemoteIdentityIsUntrustedInBothDirections() {
        val store = provisionedStore()
        val original = IdentityKeyPair.generate().publicKey
        val changed = IdentityKeyPair.generate().publicKey

        assertTrue(store.isTrustedIdentity(bobAddress, original, IdentityKeyStore.Direction.SENDING))
        assertEquals(IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED, store.saveIdentity(bobAddress, original))
        for (direction in IdentityKeyStore.Direction.values()) {
            assertTrue(store.isTrustedIdentity(bobAddress, original, direction))
            assertFalse(store.isTrustedIdentity(bobAddress, changed, direction))
        }
        assertEquals(IdentityKeyStore.IdentityChange.REPLACED_EXISTING, store.saveIdentity(bobAddress, changed))
    }

    @Test
    fun localIdentityIsProvisionedOnceAndAMissingIdentityFailsClosed() {
        val records = InMemorySignalRecords()
        val store = storeOver(records)
        assertThrows(IllegalStateException::class.java) { store.getIdentityKeyPair() }

        assertTrue(LocalSignalIdentityProvisioner(records).provisionIfAbsent())
        val identity = store.getIdentityKeyPair().publicKey
        val registrationId = store.getLocalRegistrationId()

        assertFalse(LocalSignalIdentityProvisioner(records).provisionIfAbsent())
        assertEquals(identity, store.getIdentityKeyPair().publicKey)
        assertEquals(registrationId, store.getLocalRegistrationId())
        assertTrue(registrationId in 1..16380)
    }

    private fun storeOver(records: InMemorySignalRecords) =
        EncryptedSignalProtocolStore(records, records, records, records, records, records)

    private fun provisionedStore(): EncryptedSignalProtocolStore {
        val records = InMemorySignalRecords()
        LocalSignalIdentityProvisioner(records).provisionIfAbsent()
        return storeOver(records)
    }

    /** Mirrors libsignal's own PQXDHBundleFactory, but through the adapter. */
    private fun publishOneTimeBundle(store: EncryptedSignalProtocolStore): PreKeyBundle {
        val identity = store.getIdentityKeyPair()
        val preKeyPair = ECKeyPair.generate()
        val signedPreKeyPair = ECKeyPair.generate()
        val signedPreKeySignature = identity.privateKey.calculateSignature(signedPreKeyPair.publicKey.serialize())
        val kyberRecord = kyberRecord(store, KYBER_PRE_KEY_ID)

        store.storePreKey(PRE_KEY_ID, PreKeyRecord(PRE_KEY_ID, preKeyPair))
        store.storeSignedPreKey(
            SIGNED_PRE_KEY_ID,
            SignedPreKeyRecord(SIGNED_PRE_KEY_ID, System.currentTimeMillis(), signedPreKeyPair, signedPreKeySignature),
        )
        store.storeKyberPreKey(KYBER_PRE_KEY_ID, kyberRecord)

        return PreKeyBundle(
            store.getLocalRegistrationId(),
            1,
            PRE_KEY_ID,
            preKeyPair.publicKey,
            SIGNED_PRE_KEY_ID,
            signedPreKeyPair.publicKey,
            signedPreKeySignature,
            identity.publicKey,
            KYBER_PRE_KEY_ID,
            kyberRecord.keyPair.publicKey,
            kyberRecord.signature,
        )
    }

    private fun kyberRecord(store: EncryptedSignalProtocolStore, id: Int): KyberPreKeyRecord {
        val keyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val signature = store.getIdentityKeyPair().privateKey.calculateSignature(keyPair.publicKey.serialize())
        return KyberPreKeyRecord(id, System.currentTimeMillis(), keyPair, signature)
    }

    private companion object {
        const val PRE_KEY_ID = 31
        const val SIGNED_PRE_KEY_ID = 32
        const val KYBER_PRE_KEY_ID = 33
    }
}
