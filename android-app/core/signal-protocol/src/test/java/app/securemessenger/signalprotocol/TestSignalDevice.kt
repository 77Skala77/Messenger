package app.securemessenger.signalprotocol

/** One device's libsignal state over the in-memory record double. Tests only. */
class TestSignalDevice {
    private val records = InMemorySignalRecords().also { LocalSignalIdentityProvisioner(it).provisionIfAbsent() }
    val store = EncryptedSignalProtocolStore(records, records, records, records, records, records)
    val sessions = SignalSessions(store)
    val invitations = InvitationPreKeyFactory(store)
}
