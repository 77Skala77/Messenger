package app.securemessenger.signalprotocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Step 1f proofs of the security properties the rewrite is for (REWRITE_PLAN.md exit gate),
 * through this project's store adapter with real libsignal. The attacker is the strongest one:
 * a complete copy of the victim's protocol state (identity, sessions, pre-keys) at some moment,
 * plus every ciphertext the relay ever carried. Runs on the JVM and on a device.
 *
 * Every "cannot" assertion has a matching "can" control, so a broken or empty stolen copy could
 * not make a test pass.
 */
class ForwardSecrecyTest {
    private val alice = TestSignalDevice()
    private val bob = TestSignalDevice()

    @Before
    fun pair() {
        bob.sessions.startSession(ALICE_ON_BOB, alice.invitations.create().toPreKeyBundle())
        read(alice, BOB_ON_ALICE, bob.send(ALICE_ON_BOB, "hello"), "hello")
        read(bob, ALICE_ON_BOB, alice.send(BOB_ON_ALICE, "accepted"), "accepted")
    }

    @Test
    fun forwardSecrecyAFullStateTheftCannotDecryptWhatWasAlreadyRead() {
        val earlier = bob.send(ALICE_ON_BOB, "earlier secret")
        read(alice, BOB_ON_ALICE, earlier, "earlier secret")

        val thief = alice.stolenCopy()
        val control = alice.stolenCopy()

        // Control: the stolen state is genuine, it reads a message sent after the theft.
        read(control, BOB_ON_ALICE, bob.send(ALICE_ON_BOB, "sent after the theft"), "sent after the theft")
        // Forward secrecy: the message that was read before the theft stays unreadable.
        assertUnreadable(thief, BOB_ON_ALICE, earlier)
    }

    @Test
    fun forwardSecrecyHoldsAcrossSeveralRatchetStepsInBothDirections() {
        val captured = mutableListOf<Pair<TestSignalDevice, SignalCiphertext>>()
        repeat(3) { round ->
            val fromBob = bob.send(ALICE_ON_BOB, "bob $round")
            read(alice, BOB_ON_ALICE, fromBob, "bob $round")
            val fromAlice = alice.send(BOB_ON_ALICE, "alice $round")
            read(bob, ALICE_ON_BOB, fromAlice, "alice $round")
            captured += alice to fromBob
            captured += bob to fromAlice
        }
        val stolenAlice = alice.stolenCopy()
        val stolenBob = bob.stolenCopy()

        captured.forEach { (receiver, ciphertext) ->
            if (receiver === alice) assertUnreadable(stolenAlice, BOB_ON_ALICE, ciphertext)
            else assertUnreadable(stolenBob, ALICE_ON_BOB, ciphertext)
        }
        read(stolenAlice, BOB_ON_ALICE, bob.send(ALICE_ON_BOB, "control for alice"), "control for alice")
        read(stolenBob, ALICE_ON_BOB, alice.send(BOB_ON_ALICE, "control for bob"), "control for bob")
    }

    /**
     * The honest boundary of forward secrecy in the Double Ratchet: a message that had not
     * arrived yet when the device was stolen keeps its (skipped) message key in the session, so
     * the thief can read it. Once the real device reads it, that key is gone too.
     */
    @Test
    fun boundaryAMessageNotYetReceivedAtTheftTimeIsReadableByTheThief() {
        val delayed = bob.send(ALICE_ON_BOB, "delayed by the relay")
        read(alice, BOB_ON_ALICE, bob.send(ALICE_ON_BOB, "arrived first"), "arrived first")

        val thief = alice.stolenCopy()
        read(thief, BOB_ON_ALICE, delayed, "delayed by the relay")

        read(alice, BOB_ON_ALICE, delayed, "delayed by the relay")
        assertUnreadable(alice.stolenCopy(), BOB_ON_ALICE, delayed)
    }

    /**
     * Post-compromise security: after the theft, one full round trip of the real devices brings
     * in ratchet keys the thief never saw. Healing needs that full round trip: the thief still
     * reads the first reply, but nothing after it.
     */
    @Test
    fun postCompromiseSecurityOneRoundTripAfterTheTheftLocksTheThiefOut() {
        read(alice, BOB_ON_ALICE, bob.send(ALICE_ON_BOB, "before the theft"), "before the theft")
        val thief = alice.stolenCopy()

        read(bob, ALICE_ON_BOB, alice.send(BOB_ON_ALICE, "a1"), "a1")
        val firstReply = bob.send(ALICE_ON_BOB, "b2")
        read(alice, BOB_ON_ALICE, firstReply, "b2")
        read(bob, ALICE_ON_BOB, alice.send(BOB_ON_ALICE, "a2"), "a2")
        val healed = bob.send(ALICE_ON_BOB, "b3 after healing")
        read(alice, BOB_ON_ALICE, healed, "b3 after healing")

        // A passive thief who kept the stolen state and sees every ciphertext:
        read(thief, BOB_ON_ALICE, firstReply, "b2")
        assertUnreadable(thief, BOB_ON_ALICE, healed)
    }

    private fun TestSignalDevice.send(to: String, text: String): SignalCiphertext = sessions.encrypt(to, text.toByteArray())

    private fun read(device: TestSignalDevice, from: String, ciphertext: SignalCiphertext, expected: String) =
        assertArrayEquals(expected.toByteArray(), device.sessions.decrypt(from, ciphertext))

    private fun assertUnreadable(device: TestSignalDevice, from: String, ciphertext: SignalCiphertext) {
        val failure = assertThrows(SignalSessionException::class.java) { device.sessions.decrypt(from, ciphertext) }
        assertTrue(
            "unexpected failure ${failure.failure}",
            failure.failure == SignalSessionFailure.DUPLICATE_OR_OLD || failure.failure == SignalSessionFailure.INVALID,
        )
    }

    private companion object {
        const val ALICE_ON_BOB = "contact-alice-on-bob"
        const val BOB_ON_ALICE = "contact-bob-on-alice"
    }
}
