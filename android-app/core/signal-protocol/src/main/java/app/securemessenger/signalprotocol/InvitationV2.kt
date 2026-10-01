package app.securemessenger.signalprotocol

import app.securemessenger.domain.RelayQueueCredentials
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kdf.HKDF
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.util.Medium

enum class InvitationRejection {
    MALFORMED,
    UNSUPPORTED_VERSION,

    /** No relayed blob matches the QR commitment (not uploaded yet, withheld, or substituted). */
    NOT_FOUND,

    /** A blob matches the commitment but the QR key cannot open it: the code itself is wrong. */
    SEAL_MISMATCH,
    EXPIRED,

    /** Unparseable keys or a pre-key signature that does not verify. */
    INVALID_KEYS,
}

class InvitationRejectedException(val reason: InvitationRejection, cause: Throwable? = null) :
    Exception("Invitation rejected: $reason", cause)

/**
 * What one invitation offers (DEC-019): its pre-keys, where the inviter receives, and the routing
 * tag the inviter assigned for this contact. Public data only.
 */
class InvitationBundle(
    val preKeys: InvitationPreKeys,
    val receiveQueueId: String,
    val routingTag: RandomId,
    val expiresAtEpochMillis: Long,
)

/**
 * The QR code content (DEC-024): one 32-byte secret and a commitment to the sealed bundle. The seal
 * key and the mailbox's read secret are derived from the secret with HKDF under distinct labels.
 */
class InvitationCode internal constructor(
    private val secret: ByteArray,
    private val sealedBundleDigest: ByteArray,
) {
    init {
        require(secret.size == InvitationV2.SECRET_BYTES)
        require(sealedBundleDigest.size == InvitationV2.DIGEST_BYTES)
    }

    /** Read access to the relay queue that holds the sealed bundle. */
    val mailbox: RelayQueueCredentials = RelayQueueCredentials.fromReadSecret(InvitationV2.derive(secret, InvitationV2.MAILBOX_READ_INFO))

    /** Text for the QR code and for copying: `smp-invite-v3:` + base64url, about 100 characters. */
    fun encode(): String =
        InvitationV2.PREFIX + InvitationV2.base64Url(
            byteArrayOf(InvitationV2.FORMAT_VERSION.toByte()) + secret + sealedBundleDigest,
        )

    internal fun commitsTo(sealedBundle: ByteArray): Boolean =
        MessageDigest.isEqual(InvitationV2.sealedBundleDigest(sealedBundle), sealedBundleDigest)

    internal fun sealKey(): ByteArray = InvitationV2.derive(secret, InvitationV2.SEAL_KEY_INFO)

    /** Never prints the secret or the mailbox. */
    override fun toString(): String = "InvitationCode(redacted)"
}

class PublishedInvitation(
    val code: InvitationCode,
    /** Upload [sealedBundle] to this relay queue, expiring at [expiresAtEpochMillis]. */
    val mailboxQueueId: String,
    val sealedBundle: ByteArray,
    val expiresAtEpochMillis: Long,
)

/**
 * Invitation (DEC-019 O1, T8; code format v3 since DEC-024). The inviter seals the bundle with a
 * single-use AES-256-GCM key and uploads it to a relay mailbox that only the QR secret can read; the
 * QR code carries that secret (the seal key and the mailbox read secret are HKDF-derived from it)
 * and a domain-separated SHA-256 digest of the exact sealed bytes. The relay learns nothing from
 * the bundle and cannot substitute it; a person who sees the QR code can read the public bundle but
 * cannot substitute it either, because the digest binds the bytes.
 *
 * `smp-invite-v1:` and `smp-invite-v2:` codes are refused as unsupported versions.
 */
object InvitationV2 {
    const val PREFIX = "smp-invite-v3:"
    const val DEFAULT_LIFETIME_MILLIS = 24 * 60 * 60 * 1000L
    const val MAX_LIFETIME_MILLIS = 7 * 24 * 60 * 60 * 1000L

    internal const val FORMAT_VERSION = 3
    internal const val SECRET_BYTES = 32
    internal const val SEAL_KEY_BYTES = 32
    internal const val DIGEST_BYTES = 32
    internal val SEAL_KEY_INFO = "smp-invite-v3/seal-key".toByteArray()
    internal val MAILBOX_READ_INFO = "smp-invite-v3/mailbox-read".toByteArray()

    private const val CODE_BODY_BYTES = 1 + SECRET_BYTES + DIGEST_BYTES
    private const val MAX_CODE_CHARS = 256
    private const val SEAL_VERSION = 1
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    internal const val CLOCK_SKEW_MILLIS = 10 * 60 * 1000L
    private val SEAL_AAD = "smp-invite-v3/sealed-bundle".toByteArray()
    private val DIGEST_DOMAIN = "smp-invite-v3/sealed-bundle-digest\u0000".toByteArray()

    fun publish(bundle: InvitationBundle, random: SecureRandom = SecureRandom()): PublishedInvitation {
        val secret = ByteArray(SECRET_BYTES).also(random::nextBytes)
        val sealKey = derive(secret, SEAL_KEY_INFO)
        val sealed = try {
            seal(bundle, sealKey, random)
        } finally {
            sealKey.fill(0)
        }
        val code = InvitationCode(secret = secret, sealedBundleDigest = sealedBundleDigest(sealed))
        return PublishedInvitation(code, code.mailbox.queueId, sealed, bundle.expiresAtEpochMillis)
    }

    /** HKDF-SHA256 (libsignal) with a distinct label per derived key. */
    internal fun derive(secret: ByteArray, info: ByteArray): ByteArray = HKDF.deriveSecrets(secret, info, SEAL_KEY_BYTES)

    fun decodeCode(text: String): InvitationCode {
        if (text.length > MAX_CODE_CHARS) throw InvitationRejectedException(InvitationRejection.MALFORMED)
        if (!text.startsWith(PREFIX)) {
            val reason = if (text.startsWith("smp-invite-")) {
                InvitationRejection.UNSUPPORTED_VERSION
            } else {
                InvitationRejection.MALFORMED
            }
            throw InvitationRejectedException(reason)
        }
        val payload = text.removePrefix(PREFIX)
        val body = try {
            Base64.getUrlDecoder().decode(payload)
        } catch (failure: IllegalArgumentException) {
            throw InvitationRejectedException(InvitationRejection.MALFORMED, failure)
        }
        // One text per invitation: no padding, and no other spelling of the last character (the
        // decoder ignores the unused low bits there, so four spellings would decode alike).
        if (base64Url(body) != payload) throw InvitationRejectedException(InvitationRejection.MALFORMED)
        if (body.size != CODE_BODY_BYTES) throw InvitationRejectedException(InvitationRejection.MALFORMED)
        if (body[0].toInt() != FORMAT_VERSION) throw InvitationRejectedException(InvitationRejection.UNSUPPORTED_VERSION)
        var offset = 1
        fun take(count: Int) = body.copyOfRange(offset, offset + count).also { offset += count }
        return InvitationCode(take(SECRET_BYTES), take(DIGEST_BYTES))
    }

    /**
     * Picks the relayed blob that matches the code's commitment, opens and validates it. Blobs
     * that do not match are ignored: the relay may hold anything in a mailbox.
     */
    fun open(code: InvitationCode, candidates: List<ByteArray>, nowEpochMillis: Long): InvitationBundle {
        val sealed = candidates.firstOrNull { it.size in MIN_SEALED_BYTES..MAX_SEALED_BYTES && code.commitsTo(it) }
            ?: throw InvitationRejectedException(InvitationRejection.NOT_FOUND)
        val bundleBytes = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val nonce = sealed.copyOfRange(1, 1 + NONCE_BYTES)
            val key = code.sealKey()
            try {
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            } finally {
                key.fill(0)
            }
            if (sealed[0].toInt() != SEAL_VERSION) throw IllegalArgumentException("Unknown seal version")
            cipher.updateAAD(SEAL_AAD)
            cipher.doFinal(sealed, 1 + NONCE_BYTES, sealed.size - 1 - NONCE_BYTES)
        } catch (failure: Exception) {
            throw InvitationRejectedException(InvitationRejection.SEAL_MISMATCH, failure)
        }
        val bundle = InvitationBundleCodec.decode(bundleBytes)
        if (nowEpochMillis > bundle.expiresAtEpochMillis + CLOCK_SKEW_MILLIS) {
            throw InvitationRejectedException(InvitationRejection.EXPIRED)
        }
        if (bundle.expiresAtEpochMillis > nowEpochMillis + MAX_LIFETIME_MILLIS + CLOCK_SKEW_MILLIS) {
            throw InvitationRejectedException(InvitationRejection.MALFORMED)
        }
        return bundle
    }

    /** Format: seal version, 12-byte random nonce, AES-256-GCM ciphertext and tag. */
    internal fun seal(bundle: InvitationBundle, sealKey: ByteArray, random: SecureRandom): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sealKey, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(SEAL_AAD)
        return byteArrayOf(SEAL_VERSION.toByte()) + nonce + cipher.doFinal(InvitationBundleCodec.encode(bundle))
    }

    internal fun sealedBundleDigest(sealed: ByteArray): ByteArray = sha256(DIGEST_DOMAIN, sealed)

    internal fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private const val MIN_SEALED_BYTES = 1 + NONCE_BYTES + TAG_BITS / 8 + 1
    private const val MAX_SEALED_BYTES = 1 + NONCE_BYTES + InvitationBundleCodec.MAX_BYTES + TAG_BITS / 8
}

/** Strict binary format of the sealed bundle's plaintext. Exact key sizes; no trailing bytes. */
internal object InvitationBundleCodec {
    const val MAX_BYTES = 4096
    private val MAGIC = byteArrayOf(0x53, 0x4D, 0x50, 0x42) // "SMPB"
    private const val VERSION = 1
    private const val EC_PUBLIC_KEY_BYTES = 33
    private const val KYBER_1024_PUBLIC_KEY_BYTES = 1569
    private const val SIGNATURE_BYTES = 64
    private const val MAX_REGISTRATION_ID = 16380

    fun encode(bundle: InvitationBundle): ByteArray {
        val keys = bundle.preKeys
        return V2Writer()
            .raw(MAGIC)
            .u8(VERSION)
            .i32(keys.registrationId)
            .field16(keys.identityKey.serialize())
            .i32(keys.signedPreKeyId)
            .field16(keys.signedPreKey.serialize())
            .field16(keys.signedPreKeySignature)
            .i32(keys.oneTimePreKeyId)
            .field16(keys.oneTimePreKey.serialize())
            .i32(keys.kyberPreKeyId)
            .field16(keys.kyberPreKey.serialize())
            .field16(keys.kyberPreKeySignature)
            .field16(V2Text.queueIdBytes(bundle.receiveQueueId))
            .raw(bundle.routingTag.toByteArray())
            .i64(bundle.expiresAtEpochMillis)
            .toByteArray()
    }

    fun decode(bytes: ByteArray): InvitationBundle {
        if (bytes.size > MAX_BYTES) throw InvitationRejectedException(InvitationRejection.MALFORMED)
        val reader = V2Reader(bytes)
        try {
            reader.expect(MAGIC)
        } catch (failure: MalformedV2Exception) {
            throw InvitationRejectedException(InvitationRejection.MALFORMED, failure)
        }
        val version = try {
            reader.u8()
        } catch (failure: MalformedV2Exception) {
            throw InvitationRejectedException(InvitationRejection.MALFORMED, failure)
        }
        if (version != VERSION) throw InvitationRejectedException(InvitationRejection.UNSUPPORTED_VERSION)
        val fields = try {
            RawFields(
                registrationId = reader.i32(),
                identityKey = reader.field16(EC_PUBLIC_KEY_BYTES, EC_PUBLIC_KEY_BYTES),
                signedPreKeyId = reader.i32(),
                signedPreKey = reader.field16(EC_PUBLIC_KEY_BYTES, EC_PUBLIC_KEY_BYTES),
                signedPreKeySignature = reader.field16(SIGNATURE_BYTES, SIGNATURE_BYTES),
                oneTimePreKeyId = reader.i32(),
                oneTimePreKey = reader.field16(EC_PUBLIC_KEY_BYTES, EC_PUBLIC_KEY_BYTES),
                kyberPreKeyId = reader.i32(),
                kyberPreKey = reader.field16(KYBER_1024_PUBLIC_KEY_BYTES, KYBER_1024_PUBLIC_KEY_BYTES),
                kyberPreKeySignature = reader.field16(SIGNATURE_BYTES, SIGNATURE_BYTES),
                receiveQueueId = V2Text.readQueueId(reader),
                routingTag = RandomId.fromBytes(reader.raw(RandomId.SIZE_BYTES)),
                expiresAtEpochMillis = reader.i64(),
            ).also { reader.requireEnd() }
        } catch (failure: MalformedV2Exception) {
            throw InvitationRejectedException(InvitationRejection.MALFORMED, failure)
        }
        val keyIds = listOf(fields.signedPreKeyId, fields.oneTimePreKeyId, fields.kyberPreKeyId)
        if (fields.registrationId !in 1..MAX_REGISTRATION_ID ||
            keyIds.any { it !in 1..Medium.MAX_VALUE } ||
            fields.expiresAtEpochMillis <= 0
        ) {
            throw InvitationRejectedException(InvitationRejection.MALFORMED)
        }
        val preKeys = try {
            InvitationPreKeys(
                registrationId = fields.registrationId,
                identityKey = IdentityKey(fields.identityKey),
                signedPreKeyId = fields.signedPreKeyId,
                signedPreKey = ECPublicKey(fields.signedPreKey),
                signedPreKeySignature = fields.signedPreKeySignature,
                oneTimePreKeyId = fields.oneTimePreKeyId,
                oneTimePreKey = ECPublicKey(fields.oneTimePreKey),
                kyberPreKeyId = fields.kyberPreKeyId,
                kyberPreKey = KEMPublicKey(fields.kyberPreKey),
                kyberPreKeySignature = fields.kyberPreKeySignature,
            )
        } catch (failure: InvalidKeyException) {
            throw InvitationRejectedException(InvitationRejection.INVALID_KEYS, failure)
        } catch (failure: IllegalArgumentException) {
            throw InvitationRejectedException(InvitationRejection.INVALID_KEYS, failure)
        }
        // libsignal verifies both signatures again when a session is built; checking here gives a
        // clear rejection before any session state is touched.
        val signer = preKeys.identityKey.publicKey
        if (!signer.verifySignature(preKeys.signedPreKey.serialize(), preKeys.signedPreKeySignature) ||
            !signer.verifySignature(preKeys.kyberPreKey.serialize(), preKeys.kyberPreKeySignature)
        ) {
            throw InvitationRejectedException(InvitationRejection.INVALID_KEYS)
        }
        return InvitationBundle(preKeys, fields.receiveQueueId, fields.routingTag, fields.expiresAtEpochMillis)
    }

    private class RawFields(
        val registrationId: Int,
        val identityKey: ByteArray,
        val signedPreKeyId: Int,
        val signedPreKey: ByteArray,
        val signedPreKeySignature: ByteArray,
        val oneTimePreKeyId: Int,
        val oneTimePreKey: ByteArray,
        val kyberPreKeyId: Int,
        val kyberPreKey: ByteArray,
        val kyberPreKeySignature: ByteArray,
        val receiveQueueId: String,
        val routingTag: RandomId,
        val expiresAtEpochMillis: Long,
    )
}
