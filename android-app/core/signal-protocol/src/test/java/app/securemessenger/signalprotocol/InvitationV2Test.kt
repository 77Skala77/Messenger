package app.securemessenger.signalprotocol

import java.security.SecureRandom
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InvitationV2Test {
    private val random = SecureRandom()

    @Test
    fun publishedInvitationOpensAndStartsAWorkingSession() {
        val alice = TestSignalDevice()
        val bob = TestSignalDevice()
        val bundle = bundleFor(alice)
        val published = InvitationV2.publish(bundle, random)
        val text = published.code.encode()

        assertTrue(text.startsWith("smp-invite-v2:"))
        // Small enough for a low-density QR code (v1 was about 250 characters).
        assertTrue("QR text is ${text.length} characters", text.length <= 160)
        assertEquals(published.mailboxQueueId, InvitationV2.decodeCode(text).mailboxQueueId)
        assertEquals(text, InvitationV2.decodeCode(text).encode())

        val relayed = listOf(ByteArray(40) { 1 }, published.sealedBundle, ByteArray(3000) { 2 })
        val opened = InvitationV2.open(InvitationV2.decodeCode(text), relayed, NOW)
        assertEquals(bundle.receiveQueueId, opened.receiveQueueId)
        assertEquals(bundle.routingTag, opened.routingTag)
        assertEquals(bundle.expiresAtEpochMillis, opened.expiresAtEpochMillis)
        assertEquals(alice.store.getIdentityKeyPair().publicKey, opened.preKeys.identityKey)

        bob.sessions.startSession(ALICE_ON_BOB, opened.preKeys.toPreKeyBundle())
        val hello = bob.sessions.encrypt(ALICE_ON_BOB, "hello".toByteArray())
        assertArrayEquals("hello".toByteArray(), alice.sessions.decrypt(BOB_ON_ALICE, hello))
    }

    @Test
    fun codeTextRejectsOtherFormatsAndDamage() {
        val text = InvitationV2.publish(bundleFor(TestSignalDevice()), random).code.encode()
        val body = Base64.getUrlDecoder().decode(text.removePrefix(InvitationV2.PREFIX))
        val wrongVersion = InvitationV2.PREFIX + base64Url(body.copyOf().also { it[0] = 3 })

        expectCodeRejected(InvitationRejection.UNSUPPORTED_VERSION, "smp-invite-v1:AAAA")
        expectCodeRejected(InvitationRejection.UNSUPPORTED_VERSION, "smp-invite-v2-signal-pqxdh-experimental:AAAA")
        expectCodeRejected(InvitationRejection.UNSUPPORTED_VERSION, wrongVersion)
        expectCodeRejected(InvitationRejection.MALFORMED, "hello")
        expectCodeRejected(InvitationRejection.MALFORMED, text.dropLast(1))
        expectCodeRejected(InvitationRejection.MALFORMED, text + "A")
        expectCodeRejected(InvitationRejection.MALFORMED, text + "=")
        expectCodeRejected(InvitationRejection.MALFORMED, text.substring(0, 20) + " " + text.substring(20))
        expectCodeRejected(InvitationRejection.MALFORMED, text.substring(0, 20) + "*" + text.substring(21))
        expectCodeRejected(InvitationRejection.MALFORMED, InvitationV2.PREFIX + "A".repeat(300))
    }

    @Test
    fun relayCannotSubstituteTheBundleEvenWithTheSealKey() {
        val alice = TestSignalDevice()
        val published = InvitationV2.publish(bundleFor(alice), random)
        val code = published.code

        // Someone who photographed the QR code knows the seal key and seals their own bundle with
        // it: the blob is well formed and opens with that key, but the commitment rejects it.
        val mallory = TestSignalDevice()
        val forged = InvitationV2.seal(bundleFor(mallory), code.sealKey(), random)
        expectOpenRejected(InvitationRejection.NOT_FOUND, code, listOf(forged))
        expectOpenRejected(InvitationRejection.NOT_FOUND, code, listOf(forged, forged.copyOf()))

        val flipped = published.sealedBundle.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte() }
        expectOpenRejected(InvitationRejection.NOT_FOUND, code, listOf(flipped))
        expectOpenRejected(InvitationRejection.NOT_FOUND, code, emptyList())

        // Right commitment, wrong key: the code itself is damaged, which must not be ignored.
        val body = Base64.getUrlDecoder().decode(code.encode().removePrefix(InvitationV2.PREFIX))
        body[1 + 16] = (body[1 + 16].toInt() xor 1).toByte()
        val wrongKey = InvitationV2.decodeCode(InvitationV2.PREFIX + base64Url(body))
        expectOpenRejected(InvitationRejection.SEAL_MISMATCH, wrongKey, listOf(published.sealedBundle))
    }

    @Test
    fun expiredOrOverlongInvitationsAreRejected() {
        val device = TestSignalDevice()
        val expired = InvitationV2.publish(bundleFor(device, expiresAt = NOW - 60 * 60 * 1000L), random)
        expectOpenRejected(InvitationRejection.EXPIRED, expired.code, listOf(expired.sealedBundle))

        val overlong = InvitationV2.publish(bundleFor(device, expiresAt = NOW + 30L * 24 * 60 * 60 * 1000), random)
        expectOpenRejected(InvitationRejection.MALFORMED, overlong.code, listOf(overlong.sealedBundle))
    }

    @Test
    fun bundleCodecRoundTripsAndRejectsEveryDeviation() {
        val bundle = bundleFor(TestSignalDevice())
        val encoded = InvitationBundleCodec.encode(bundle)
        val decoded = InvitationBundleCodec.decode(encoded)
        assertArrayEquals(encoded, InvitationBundleCodec.encode(decoded))

        expectBundleRejected(InvitationRejection.MALFORMED, encoded.copyOf(encoded.size - 1))
        expectBundleRejected(InvitationRejection.MALFORMED, encoded + byteArrayOf(0))
        expectBundleRejected(InvitationRejection.MALFORMED, encoded.copyOf().also { it[0] = 0 })
        expectBundleRejected(InvitationRejection.UNSUPPORTED_VERSION, encoded.copyOf().also { it[4] = 2 })
        expectBundleRejected(InvitationRejection.MALFORMED, ByteArray(InvitationBundleCodec.MAX_BYTES + 1))
        // Registration ID 0 (bytes 5..8).
        expectBundleRejected(InvitationRejection.MALFORMED, encoded.copyOf().also { it.fill(0, 5, 9) })
        // Identity key length prefix (bytes 9..10) changed from 33 to 32.
        expectBundleRejected(InvitationRejection.MALFORMED, encoded.copyOf().also { it[10] = 32 })
        // Identity key type byte (byte 11) is not a Curve25519 key type.
        expectBundleRejected(InvitationRejection.INVALID_KEYS, encoded.copyOf().also { it[11] = 0x7F })

        val badSignature = bundle.withPreKeys(
            bundle.preKeys.copyWith(signedPreKeySignature = bundle.preKeys.signedPreKeySignature.flipFirstBit()),
        )
        expectBundleRejected(InvitationRejection.INVALID_KEYS, InvitationBundleCodec.encode(badSignature))
        val badKyberSignature = bundle.withPreKeys(
            bundle.preKeys.copyWith(kyberPreKeySignature = bundle.preKeys.kyberPreKeySignature.flipFirstBit()),
        )
        expectBundleRejected(InvitationRejection.INVALID_KEYS, InvitationBundleCodec.encode(badKyberSignature))
        // A signature over the wrong key: Kyber's signature placed on the signed EC pre-key.
        val swapped = bundle.withPreKeys(
            bundle.preKeys.copyWith(signedPreKeySignature = bundle.preKeys.kyberPreKeySignature),
        )
        expectBundleRejected(InvitationRejection.INVALID_KEYS, InvitationBundleCodec.encode(swapped))
    }

    @Test
    fun invalidQueueIdsCannotBeEncoded() {
        val preKeys = TestSignalDevice().invitations.create()
        listOf("short", "has space in it", "slash/is/not/allowed", "x".repeat(129)).forEach { queueId ->
            assertThrows(IllegalArgumentException::class.java) {
                InvitationBundleCodec.encode(InvitationBundle(preKeys, queueId, RandomId.random(random), NOW))
            }
        }
    }

    private fun bundleFor(device: TestSignalDevice, expiresAt: Long = NOW + InvitationV2.DEFAULT_LIFETIME_MILLIS) =
        InvitationBundle(
            preKeys = device.invitations.create(),
            receiveQueueId = "7d1c0c1e-4f7a-4c55-9a8e-2b5d3f1e0a91",
            routingTag = RandomId.random(random),
            expiresAtEpochMillis = expiresAt,
        )

    private fun InvitationBundle.withPreKeys(preKeys: InvitationPreKeys) =
        InvitationBundle(preKeys, receiveQueueId, routingTag, expiresAtEpochMillis)

    private fun InvitationPreKeys.copyWith(
        signedPreKeySignature: ByteArray = this.signedPreKeySignature,
        kyberPreKeySignature: ByteArray = this.kyberPreKeySignature,
    ) = InvitationPreKeys(
        registrationId, identityKey, signedPreKeyId, signedPreKey, signedPreKeySignature,
        oneTimePreKeyId, oneTimePreKey, kyberPreKeyId, kyberPreKey, kyberPreKeySignature,
    )

    private fun expectCodeRejected(reason: InvitationRejection, text: String) {
        val rejected = assertThrows(InvitationRejectedException::class.java) { InvitationV2.decodeCode(text) }
        assertEquals(reason, rejected.reason)
    }

    private fun expectOpenRejected(reason: InvitationRejection, code: InvitationCode, candidates: List<ByteArray>) {
        val rejected = assertThrows(InvitationRejectedException::class.java) { InvitationV2.open(code, candidates, NOW) }
        assertEquals(reason, rejected.reason)
    }

    private fun expectBundleRejected(reason: InvitationRejection, bytes: ByteArray) {
        val rejected = assertThrows(InvitationRejectedException::class.java) { InvitationBundleCodec.decode(bytes) }
        assertEquals(reason, rejected.reason)
    }

    private fun ByteArray.flipFirstBit(): ByteArray = copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }

    private fun base64Url(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val ALICE_ON_BOB = "contact-alice-on-bob"
        const val BOB_ON_ALICE = "contact-bob-on-alice"
    }
}
