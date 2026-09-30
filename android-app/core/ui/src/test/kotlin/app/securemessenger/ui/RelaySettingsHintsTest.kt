package app.securemessenger.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelaySettingsHintsTest {
    @Test
    fun `relay mode follows the URL scheme`() {
        assertEquals(RelayTestMode.LAN_RELAY, relayTestMode("http://192.168.0.87:8787"))
        assertEquals(RelayTestMode.PUBLIC_RELAY, relayTestMode(" https://relay.example "))
        assertNull(relayTestMode("ftp://relay.example"))
        assertNull(relayTestMode("not a url"))
    }

    @Test
    fun `loopback and bind-all addresses are hinted only on physical phones`() {
        listOf("http://127.0.0.1:8787", "http://localhost:8787", "http://[::1]:8787").forEach { url ->
            assertEquals(RelayClientAddressWarning.PHONE_LOOPBACK, relayClientAddressWarning(url, isPhysicalDevice = true))
            assertNull(relayClientAddressWarning(url, isPhysicalDevice = false))
        }
        assertEquals(
            RelayClientAddressWarning.UNSPECIFIED_BIND_ADDRESS,
            relayClientAddressWarning("http://0.0.0.0:8787", isPhysicalDevice = true),
        )
        assertNull(relayClientAddressWarning("http://192.168.0.87:8787", isPhysicalDevice = true))
    }
}
