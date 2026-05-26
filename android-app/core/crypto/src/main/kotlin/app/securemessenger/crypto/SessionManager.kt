package app.securemessenger.crypto

import app.securemessenger.domain.DevicePublicIdentity
import app.securemessenger.domain.Invitation
import app.securemessenger.domain.VerificationStatus
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import javax.crypto.KeyAgreement

data class DeviceKeyMaterial(
    val publicIdentity: DevicePublicIdentity,
    val privateKey: PrivateKey,
)

data class EstablishedSession(
    val conversationId: String,
    val localDeviceId: String,
    val remoteDeviceId: String,
    val rootKey: ByteArray,
    var nextSendingNumber: Long = 1,
)

data class EncryptedSessionMessage(
    val messageNumber: Long,
    val timestampBucket: Long,
    val associatedData: ByteArray,
    val ciphertextPackage: CiphertextPackage,
)

interface SessionManager {
    fun createIdentity(): DeviceKeyMaterial
    fun createInvitation(identity: DevicePublicIdentity, queueId: String): String
    fun acceptInvitation(localIdentity: DeviceKeyMaterial, encodedInvitation: String): EstablishedSession
    fun createSession(localIdentity: DeviceKeyMaterial, remoteIdentity: DevicePublicIdentity): EstablishedSession
    fun encryptMessage(session: EstablishedSession, plaintext: ByteArray, timestampBucket: Long): EncryptedSessionMessage
    fun decryptMessage(session: EstablishedSession, message: EncryptedSessionMessage): ByteArray
    fun decryptStoredMessage(session: EstablishedSession, message: EncryptedSessionMessage, outgoing: Boolean): ByteArray
    fun rotateKeys(session: EstablishedSession): EstablishedSession
    fun detectReplay(conversationId: String, messageNumber: Long)
}

class SimplifiedSessionManager(
    private val suite: EncryptionSuite,
    private val random: SecureRandom = SecureRandom(),
) : SessionManager {
    private val receivedNumbers = mutableMapOf<String, MutableSet<Long>>()

    /**
     * Software identities exist for tests and non-production prototypes only. Android app identity
     * creation is delegated to Android Keystore in the storage module.
     */
    override fun createIdentity(): DeviceKeyMaterial {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"), random)
        return generator.generateKeyPair().asDeviceIdentity()
    }

    override fun createInvitation(identity: DevicePublicIdentity, queueId: String): String =
        InvitationCodec.encode(Invitation(InvitationCodec.VERSION, queueId, identity))

    override fun acceptInvitation(
        localIdentity: DeviceKeyMaterial,
        encodedInvitation: String,
    ): EstablishedSession = createSession(localIdentity, InvitationCodec.decode(encodedInvitation).identity)

    override fun createSession(
        localIdentity: DeviceKeyMaterial,
        remoteIdentity: DevicePublicIdentity,
    ): EstablishedSession {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(localIdentity.privateKey)
        agreement.doPhase(decodePublicKey(remoteIdentity.encodedPublicKey), true)
        val sharedSecret = agreement.generateSecret()
        return try {
            val orderedIds = listOf(localIdentity.publicIdentity.deviceId, remoteIdentity.deviceId).sorted()
            val conversationId = UUID.nameUUIDFromBytes(orderedIds.joinToString(":").toByteArray()).toString()
            val root = Hkdf.sha256(
                inputKeyMaterial = sharedSecret,
                salt = "secure-messenger-mvp-session-v1".toByteArray(),
                info = orderedIds.joinToString(":").toByteArray(),
                outputLength = 32,
            )
            EstablishedSession(
                conversationId = conversationId,
                localDeviceId = localIdentity.publicIdentity.deviceId,
                remoteDeviceId = remoteIdentity.deviceId,
                rootKey = root,
            )
        } finally {
            sharedSecret.fill(0)
        }
    }

    override fun encryptMessage(
        session: EstablishedSession,
        plaintext: ByteArray,
        timestampBucket: Long,
    ): EncryptedSessionMessage {
        val number = session.nextSendingNumber++
        val aad = associatedData(session, number, timestampBucket, outgoing = true)
        val messageKey = deriveMessageKey(
            session.rootKey,
            number,
            session.localDeviceId,
            session.remoteDeviceId,
        )
        return try {
            EncryptedSessionMessage(number, timestampBucket, aad, suite.encrypt(messageKey, plaintext, aad))
        } finally {
            // A derived per-message key must not remain live once AEAD processing completes.
            messageKey.fill(0)
        }
    }

    override fun decryptMessage(
        session: EstablishedSession,
        message: EncryptedSessionMessage,
    ): ByteArray {
        val plaintext = decryptStoredMessage(session, message, outgoing = false)
        // Authentication precedes replay acceptance, so malformed input cannot burn a counter.
        return try {
            detectReplay(session.conversationId, message.messageNumber)
            plaintext
        } catch (failure: Exception) {
            plaintext.fill(0)
            throw failure
        }
    }

    override fun decryptStoredMessage(
        session: EstablishedSession,
        message: EncryptedSessionMessage,
        outgoing: Boolean,
    ): ByteArray {
        val expectedAad = associatedData(session, message.messageNumber, message.timestampBucket, outgoing)
        if (!expectedAad.contentEquals(message.associatedData)) {
            throw InvalidCiphertextException("Message routing associated data does not match")
        }
        val messageKey = deriveMessageKey(
            session.rootKey,
            message.messageNumber,
            if (outgoing) session.localDeviceId else session.remoteDeviceId,
            if (outgoing) session.remoteDeviceId else session.localDeviceId,
        )
        return try {
            suite.decrypt(messageKey, message.ciphertextPackage, expectedAad)
        } finally {
            messageKey.fill(0)
        }
    }

    override fun rotateKeys(session: EstablishedSession): EstablishedSession {
        val rotated = Hkdf.sha256(
            inputKeyMaterial = session.rootKey,
            salt = "secure-messenger-mvp-rotation-v1".toByteArray(),
            info = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(session.nextSendingNumber).array(),
            outputLength = 32,
        )
        return session.copy(rootKey = rotated, nextSendingNumber = 1)
    }

    @Synchronized
    override fun detectReplay(conversationId: String, messageNumber: Long) {
        val received = receivedNumbers.getOrPut(conversationId) { mutableSetOf() }
        if (!received.add(messageNumber)) {
            throw ReplayDetectedException()
        }
    }

    private fun associatedData(
        session: EstablishedSession,
        number: Long,
        timestampBucket: Long,
        outgoing: Boolean,
    ): ByteArray = AssociatedDataBuilder.build(
        MessageAssociatedData(
            protocolVersion = 1,
            conversationId = session.conversationId,
            senderDeviceId = if (outgoing) session.localDeviceId else session.remoteDeviceId,
            recipientDeviceId = if (outgoing) session.remoteDeviceId else session.localDeviceId,
            messageNumber = number,
            timestampBucket = timestampBucket,
            contentType = "text/plain",
            encryptionSuiteId = suite.id,
        ),
    )

    private fun deriveMessageKey(
        rootKey: ByteArray,
        number: Long,
        senderDeviceId: String,
        recipientDeviceId: String,
    ): ByteArray {
        val counter = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(number).array()
        val direction = "$senderDeviceId:$recipientDeviceId".toByteArray()
        val info = direction + counter
        return try {
            Hkdf.sha256(
                inputKeyMaterial = rootKey,
                salt = "secure-messenger-mvp-message-key-v1".toByteArray(),
                info = info,
                outputLength = 32,
            )
        } finally {
            counter.fill(0)
            info.fill(0)
        }
    }

    private fun decodePublicKey(bytes: ByteArray): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))

    private fun KeyPair.asDeviceIdentity(): DeviceKeyMaterial {
        val publicBytes = public.encoded
        return DeviceKeyMaterial(
            publicIdentity = DevicePublicIdentity(
                deviceId = UUID.randomUUID().toString(),
                encodedPublicKey = publicBytes,
                fingerprint = InvitationCodec.fingerprint(publicBytes),
            ),
            privateKey = private,
        )
    }
}

object ContactTrustEvaluator {
    fun statusFor(storedFingerprint: String?, observedFingerprint: String): VerificationStatus =
        when {
            storedFingerprint == null -> VerificationStatus.UNVERIFIED
            storedFingerprint != observedFingerprint -> VerificationStatus.KEY_CHANGED
            else -> VerificationStatus.VERIFIED
        }
}
