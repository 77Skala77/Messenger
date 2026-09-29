package app.securemessenger.signalprotocol

import app.securemessenger.domain.TransportClient
import app.securemessenger.signalstore.ConversationV2Store
import app.securemessenger.signalstore.ProtocolTransactionRunner
import app.securemessenger.signalstore.SignalIdentityStoreBoundary
import app.securemessenger.signalstore.SignalKyberPreKeyStoreBoundary
import app.securemessenger.signalstore.SignalLocalIdentityStoreBoundary
import app.securemessenger.signalstore.SignalPreKeyStoreBoundary
import app.securemessenger.signalstore.SignalSessionStoreBoundary
import app.securemessenger.signalstore.SignalSignedPreKeyStoreBoundary

/**
 * Builds the v2 pipeline from neutral storage boundaries, so UI code never touches libsignal types.
 * Call it only when the v2 QA switch is on (DEC-019 O3): it creates this device's libsignal
 * identity if none exists, and nothing else in the app does.
 */
object ConversationV2Wiring {
    fun create(
        localIdentity: SignalLocalIdentityStoreBoundary,
        identities: SignalIdentityStoreBoundary,
        sessions: SignalSessionStoreBoundary,
        preKeys: SignalPreKeyStoreBoundary,
        signedPreKeys: SignalSignedPreKeyStoreBoundary,
        kyberPreKeys: SignalKyberPreKeyStoreBoundary,
        conversations: ConversationV2Store,
        transactions: ProtocolTransactionRunner,
        transport: TransportClient,
        localReceiveQueueId: String,
        localDisplayName: () -> String,
    ): ConversationV2Service {
        LocalSignalIdentityProvisioner(localIdentity).provisionIfAbsent()
        val signalStore = EncryptedSignalProtocolStore(localIdentity, identities, sessions, preKeys, signedPreKeys, kyberPreKeys)
        return ConversationV2Service(
            sessions = SignalSessions(signalStore),
            invitationKeys = InvitationPreKeyFactory(signalStore),
            store = conversations,
            transactions = transactions,
            transport = transport,
            localReceiveQueueId = localReceiveQueueId,
            localDisplayName = localDisplayName,
        )
    }
}
