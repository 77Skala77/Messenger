package app.securemessenger.signalprotocol;

import java.util.List;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.state.SignalProtocolStore;
import org.signal.libsignal.protocol.state.impl.InMemorySignalProtocolStore;

/**
 * Boundary for migration to the official Signal Protocol implementation.
 *
 * <p>The running MVP must not instantiate this adapter until all protocol stores are durably
 * encrypted, invitation/prekey exchange is implemented, and the AGPL/licensing gate is cleared.
 */
public final class OfficialSignalDoubleRatchetGate {
    public static final String ARTIFACT_VERSION = "0.94.1";
    public static final boolean MIGRATION_ENABLED = false;

    public static final List<String> BLOCKERS = List.of(
            "Encrypted durable SignalProtocolStore is not implemented",
            "PreKey/PQXDH invitation exchange is not implemented",
            "AGPL-3.0 and unsupported-external-use review is not complete",
            "Independent protocol integration audit is not complete");

    private OfficialSignalDoubleRatchetGate() {}

    public static SignalProtocolStore createInMemoryDevelopmentHarness(int registrationId) {
        if (!MIGRATION_ENABLED) {
            throw new IllegalStateException(
                    "Official Signal protocol migration is disabled until documented security gates are satisfied");
        }
        return new InMemorySignalProtocolStore(IdentityKeyPair.generate(), registrationId);
    }
}
