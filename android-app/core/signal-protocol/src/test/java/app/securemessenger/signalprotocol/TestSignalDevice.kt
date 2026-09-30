package app.securemessenger.signalprotocol

/** One device's libsignal state over the in-memory record double. Tests only. */
class TestSignalDevice(
    private val records: InMemorySignalRecords =
        InMemorySignalRecords().also { LocalSignalIdentityProvisioner(it).provisionIfAbsent() },
) {
    val store = EncryptedSignalProtocolStore(records, records, records, records, records, records)
    val sessions = SignalSessions(store)
    val invitations = InvitationPreKeyFactory(store)

    /** A complete, independent copy of this device's protocol state, taken now. */
    fun stolenCopy(): TestSignalDevice = TestSignalDevice(records.deepCopy())
}
