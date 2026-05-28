package app.securemessenger.transport

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RelayServerSettingsInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("relay_server_settings_test", Context.MODE_PRIVATE)
    private val settings = RelayServerSettings(preferences)

    @After
    fun clearPreferences() {
        preferences.edit().clear().commit()
    }

    @Test
    fun relayUrlDefaultsToEmulatorAndPersistsSelectedLanEndpoint() {
        assertEquals(RelayServerSettings.DEFAULT_EMULATOR_URL, settings.currentUrl())

        val saved = settings.saveUrl(" http://192.168.1.45:8787/ ")

        assertEquals("http://192.168.1.45:8787", saved)
        assertEquals("http://192.168.1.45:8787", RelayServerSettings(preferences).currentUrl())
    }

    @Test
    fun debugPolicyAcceptsHttpLanAndHttpsPublicTunnelUrls() {
        assertEquals("http://10.0.2.2:8787", settings.saveUrl("http://10.0.2.2:8787"))
        assertEquals("https://temporary-relay.example", settings.saveUrl(" https://temporary-relay.example/ "))
        assertEquals("https://temporary-relay.example", RelayServerSettings(preferences).currentUrl())
    }

    @Test
    fun relayUrlRejectsCredentialsPathsAndUnsupportedSchemes() {
        assertNull(settings.saveUrl("ftp://192.168.1.45:8787"))
        assertNull(settings.saveUrl("http://user:password@192.168.1.45:8787"))
        assertNull(settings.saveUrl("http://192.168.1.45:8787/private"))
        assertEquals(RelayServerSettings.DEFAULT_EMULATOR_URL, settings.currentUrl())
    }

    @Test
    fun releasePolicyBlocksHttpRelayAndAcceptsHttps() {
        val releaseSettings = RelayServerSettings(preferences, allowInsecureHttp = false)

        assertEquals("", releaseSettings.currentUrl())
        assertNull(releaseSettings.saveUrl("http://192.168.1.45:8787"))
        assertNull(releaseSettings.saveUrl("http://10.0.2.2:8787"))
        assertEquals("https://relay.example", releaseSettings.saveUrl("https://relay.example"))
        assertEquals("https://relay.example", releaseSettings.currentUrl())
    }
}
