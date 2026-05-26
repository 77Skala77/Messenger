package app.securemessenger.signalprotocol;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class OfficialSignalDoubleRatchetGateTest {
    @Test
    public void officialSignalReplacementRemainsDisabledUntilDurableStoreAndAuditExist() {
        assertFalse(OfficialSignalDoubleRatchetGate.MIGRATION_ENABLED);
        assertThrows(
                IllegalStateException.class,
                () -> OfficialSignalDoubleRatchetGate.createInMemoryDevelopmentHarness(1));
    }
}
