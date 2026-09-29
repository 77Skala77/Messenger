package app.securemessenger.signalprotocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.state.PreKeyBundle

/**
 * Invitation pre-keys plus [SignalSessions] over the encrypted-store adapter, with real libsignal
 * PQXDH + Double Ratchet. Runs on the JVM and, through the shared source set, on a device.
 * Each side names its peer by its own local contact ID, as the app will.
 */
class SignalSessionsTest {
    @Test
    fun invitationSessionRoundTripsBetweenLocallyNamedContacts() {
        val alice = TestSignalDevice()
        val bob = TestSignalDevice()
        val invitation = alice.invitations.create()

        bob.sessions.startSession(ALICE_ON_BOB, invitation.toPreKeyBundle())
        val hello = bob.sessions.encrypt(ALICE_ON_BOB, "hello".toByteArray())
        assertEquals(CiphertextMessage.PREKEY_TYPE, hello.type)

        val header = SignalSessions.readPreKeyHeader(hello.bytes)
        assertEquals(bob.store.getIdentityKeyPair().publicKey, header.identityKey)
        assertEquals(invitation.oneTimePreKeyId, header.oneTimePreKeyId)
        assertEquals(invitation.signedPreKeyId, header.signedPreKeyId)

        assertArrayEquals("hello".toByteArray(), alice.sessions.decrypt(BOB_ON_ALICE, hello))
        val reply = alice.sessions.encrypt(BOB_ON_ALICE, "reply".toByteArray())
        assertEquals(CiphertextMessage.WHISPER_TYPE, reply.type)
        assertArrayEquals("reply".toByteArray(), bob.sessions.decrypt(ALICE_ON_BOB, reply))
        val next = bob.sessions.encrypt(ALICE_ON_BOB, "next".toByteArray())
        assertEquals(CiphertextMessage.WHISPER_TYPE, next.type)
        assertArrayEquals("next".toByteArray(), alice.sessions.decrypt(BOB_ON_ALICE, next))

        assertEquals(bob.store.getIdentityKeyPair().publicKey, alice.sessions.identityOf(BOB_ON_ALICE))
        assertEquals(alice.store.getIdentityKeyPair().publicKey, bob.sessions.identityOf(ALICE_ON_BOB))
        // One-time keys are gone after use; the signed pre-key stays until the invitation is cleaned up.
        assertFalse(alice.store.containsPreKey(invitation.oneTimePreKeyId))
        assertFalse(alice.store.containsKyberPreKey(invitation.kyberPreKeyId))
        assertTrue(alice.store.containsSignedPreKey(invitation.signedPreKeyId))
    }

    @Test
    fun preKeyMessagesSentBeforeTheFirstReplyDecryptInAnyOrder() {
        val alice = TestSignalDevice()
        val bob = TestSignalDevice()
        bob.sessions.startSession(ALICE_ON_BOB, alice.invitations.create().toPreKeyBundle())
        val first = bob.sessions.encrypt(ALICE_ON_BOB, "first".toByteArray())
        val second = bob.sessions.encrypt(ALICE_ON_BOB, "second".toByteArray())
        assertEquals(CiphertextMessage.PREKEY_TYPE, second.type)

        assertArrayEquals("second".toByteArray(), alice.sessions.decrypt(BOB_ON_ALICE, second))
        assertArrayEquals("first".toByteArray(), alice.sessions.decrypt(BOB_ON_ALICE, first))
    }

    @Test
    fun oneInvitationStartsAtMostOneSession() {
        val alice = TestSignalDevice()
        val bob = TestSignalDevice()
        val carol = TestSignalDevice()
        val bundle = alice.invitations.create().toPreKeyBundle()
        // Carol saw the same QR code and got her request in first.
        carol.sessions.startSession(ALICE_ON_CAROL, bundle)
        bob.sessions.startSession(ALICE_ON_BOB, bundle)
        val carolHello = carol.sessions.encrypt(ALICE_ON_CAROL, "carol".toByteArray())
        val bobHello = bob.sessions.encrypt(ALICE_ON_BOB, "bob".toByteArray())

        assertArrayEquals("carol".toByteArray(), alice.sessions.decrypt(CAROL_ON_ALICE, carolHello))
        val rejected = assertThrows(SignalSessionException::class.java) {
            alice.sessions.decrypt(BOB_ON_ALICE, bobHello)
        }
        assertEquals(SignalSessionFailure.UNKNOWN_PRE_KEY, rejected.failure)
        assertFalse(alice.sessions.hasSession(BOB_ON_ALICE))
        assertEquals(null, alice.sessions.identityOf(BOB_ON_ALICE))
    }

    @Test
    fun tamperedPreKeySignaturesAreRejectedBeforeAnySessionExists() {
        val alice = TestSignalDevice()
        val bob = TestSignalDevice()
        val invitation = alice.invitations.create()
        val badSignedPreKey = invitation.withSignatures(
            signed = invitation.signedPreKeySignature.flipFirstBit(),
            kyber = invitation.kyberPreKeySignature,
        )
        val badKyberPreKey = invitation.withSignatures(
            signed = invitation.signedPreKeySignature,
            kyber = invitation.kyberPreKeySignature.flipFirstBit(),
        )

        listOf(badSignedPreKey, badKyberPreKey).forEach { bundle ->
            val rejected = assertThrows(SignalSessionException::class.java) {
                bob.sessions.startSession(ALICE_ON_BOB, bundle)
            }
            assertEquals(SignalSessionFailure.INVALID, rejected.failure)
            assertFalse(bob.sessions.hasSession(ALICE_ON_BOB))
        }
    }

    @Test
    fun replayedAndTamperedMessagesAreRejectedWithoutBreakingTheSession() {
        val (alice, bob) = establishedPair()
        val message = bob.sessions.encrypt(ALICE_ON_BOB, "once".toByteArray())
        assertArrayEquals("once".toByteArray(), alice.sessions.decrypt(BOB_ON_ALICE, message))

        val replay = assertThrows(SignalSessionException::class.java) {
            alice.sessions.decrypt(BOB_ON_ALICE, message)
        }
        assertEquals(SignalSessionFailure.DUPLICATE_OR_OLD, replay.failure)

        val genuine = bob.sessions.encrypt(ALICE_ON_BOB, "genuine".toByteArray())
        val tampered = SignalCiphertext(genuine.type, genuine.bytes.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() })
        val forged = assertThrows(SignalSessionException::class.java) {
            alice.sessions.decrypt(BOB_ON_ALICE, tampered)
        }
        assertEquals(SignalSessionFailure.INVALID, forged.failure)
        assertArrayEquals("genuine".toByteArray(), alice.sessions.decrypt(BOB_ON_ALICE, genuine))
    }

    @Test
    fun aDifferentIdentityForAKnownContactIsUntrustedAndLeavesTheSessionIntact() {
        val (alice, bob) = establishedPair()
        val mallory = TestSignalDevice()
        // Mallory answers a second invitation, but routing attributes the message to Bob's contact.
        mallory.sessions.startSession(ALICE_ON_MALLORY, alice.invitations.create().toPreKeyBundle())
        val impostor = mallory.sessions.encrypt(ALICE_ON_MALLORY, "i am bob".toByteArray())

        val rejected = assertThrows(SignalSessionException::class.java) {
            alice.sessions.decrypt(BOB_ON_ALICE, impostor)
        }
        assertEquals(SignalSessionFailure.UNTRUSTED_IDENTITY, rejected.failure)
        assertEquals(bob.store.getIdentityKeyPair().publicKey, alice.sessions.identityOf(BOB_ON_ALICE))
        val stillBob = bob.sessions.encrypt(ALICE_ON_BOB, "still bob".toByteArray())
        assertArrayEquals("still bob".toByteArray(), alice.sessions.decrypt(BOB_ON_ALICE, stillBob))
    }

    @Test
    fun forgettingAContactRemovesItsSessionAndIdentity() {
        val (alice, _) = establishedPair()
        alice.sessions.forget(BOB_ON_ALICE)
        assertFalse(alice.sessions.hasSession(BOB_ON_ALICE))
        assertEquals(null, alice.sessions.identityOf(BOB_ON_ALICE))
        val noSession = assertThrows(SignalSessionException::class.java) {
            alice.sessions.encrypt(BOB_ON_ALICE, "x".toByteArray())
        }
        assertEquals(SignalSessionFailure.NO_SESSION, noSession.failure)
    }

    @Test
    fun startingASecondSessionForTheSameContactIsRefused() {
        val (_, bob) = establishedPair()
        val other = TestSignalDevice()
        assertThrows(IllegalStateException::class.java) {
            bob.sessions.startSession(ALICE_ON_BOB, other.invitations.create().toPreKeyBundle())
        }
    }

    @Test
    fun preKeyHeaderRejectsOrdinaryMessagesAndGarbage() {
        val (alice, _) = establishedPair()
        val ordinary = alice.sessions.encrypt(BOB_ON_ALICE, "reply".toByteArray())
        assertEquals(CiphertextMessage.WHISPER_TYPE, ordinary.type)
        listOf(ordinary.bytes, ByteArray(0), ByteArray(64) { 7 }).forEach { bytes ->
            val rejected = assertThrows(SignalSessionException::class.java) {
                SignalSessions.readPreKeyHeader(bytes)
            }
            assertEquals(SignalSessionFailure.INVALID, rejected.failure)
        }
    }

    @Test
    fun expiredInvitationKeysAreDeletedAndCannotStartASession() {
        val alice = TestSignalDevice()
        val bob = TestSignalDevice()
        val invitation = alice.invitations.create()
        alice.invitations.delete(invitation.signedPreKeyId, invitation.oneTimePreKeyId, invitation.kyberPreKeyId)
        assertFalse(alice.store.containsSignedPreKey(invitation.signedPreKeyId))
        assertFalse(alice.store.containsPreKey(invitation.oneTimePreKeyId))
        assertFalse(alice.store.containsKyberPreKey(invitation.kyberPreKeyId))

        bob.sessions.startSession(ALICE_ON_BOB, invitation.toPreKeyBundle())
        val hello = bob.sessions.encrypt(ALICE_ON_BOB, "late".toByteArray())
        val rejected = assertThrows(SignalSessionException::class.java) {
            alice.sessions.decrypt(BOB_ON_ALICE, hello)
        }
        assertEquals(SignalSessionFailure.UNKNOWN_PRE_KEY, rejected.failure)
        assertFalse(alice.sessions.hasSession(BOB_ON_ALICE))
    }

    /** Bob scanned Alice's invitation, Alice answered once: both sides now use ordinary messages. */
    private fun establishedPair(): Pair<TestSignalDevice, TestSignalDevice> {
        val alice = TestSignalDevice()
        val bob = TestSignalDevice()
        bob.sessions.startSession(ALICE_ON_BOB, alice.invitations.create().toPreKeyBundle())
        alice.sessions.decrypt(BOB_ON_ALICE, bob.sessions.encrypt(ALICE_ON_BOB, "hello".toByteArray()))
        bob.sessions.decrypt(ALICE_ON_BOB, alice.sessions.encrypt(BOB_ON_ALICE, "accepted".toByteArray()))
        return alice to bob
    }

    private fun InvitationPreKeys.withSignatures(signed: ByteArray, kyber: ByteArray): PreKeyBundle =
        PreKeyBundle(
            registrationId,
            SignalSessions.DEVICE_ID,
            oneTimePreKeyId,
            oneTimePreKey,
            signedPreKeyId,
            signedPreKey,
            signed,
            identityKey,
            kyberPreKeyId,
            kyberPreKey,
            kyber,
        )

    private fun ByteArray.flipFirstBit(): ByteArray = copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }

    private companion object {
        const val ALICE_ON_BOB = "contact-alice-on-bob"
        const val ALICE_ON_CAROL = "contact-alice-on-carol"
        const val ALICE_ON_MALLORY = "contact-alice-on-mallory"
        const val BOB_ON_ALICE = "contact-bob-on-alice"
        const val CAROL_ON_ALICE = "contact-carol-on-alice"
    }
}
