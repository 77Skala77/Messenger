package app.securemessenger.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizationResourcesTest {
    @Test
    fun `russian contains every english user facing string`() {
        val english = resourceNames(File("src/main/res/values/strings.xml"))
        val russian = resourceNames(File("src/main/res/values-ru/strings.xml"))

        assertTrue(english.isNotEmpty())
        assertEquals(english, russian)
    }

    @Test
    fun `compose source does not embed visible text literals or logging`() {
        val source = File("src/main/kotlin/app/securemessenger/ui/SecureMessengerApp.kt").readText()
        val workerSource = File("src/main/kotlin/app/securemessenger/ui/RelayBackgroundSync.kt").readText()
        assertFalse(source.contains("Text(\""))
        assertFalse(source.contains("android.util.Log"))
        assertFalse(source.contains("println("))
        assertFalse(workerSource.contains("android.util.Log"))
        assertFalse(workerSource.contains("println("))
    }

    @Test
    fun `active conversation path remains on prototype session manager`() {
        val source = File("src/main/kotlin/app/securemessenger/ui/SecureMessengerViewModel.kt").readText()

        assertTrue(source.contains("SimplifiedSessionManager"))
        assertFalse(source.contains("SignalSessionManagerAdapter"))
        assertFalse(source.contains("SignalStoreFeatureFlags"))
    }

    @Test
    fun `relay and pairing labels exist in localized resources`() {
        val expected = setOf(
            "relay_server",
            "relay_server_url",
            "check_relay_server",
            "relay_available",
            "relay_unavailable",
            "relay_connection_error",
            "synchronize_now",
            "pairing_waiting",
            "pairing_ready",
            "pairing_blocked",
        )

        assertTrue(resourceNames(File("src/main/res/values/strings.xml")).containsAll(expected))
        assertTrue(resourceNames(File("src/main/res/values-ru/strings.xml")).containsAll(expected))
    }

    private fun resourceNames(file: File): Set<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val strings = document.getElementsByTagName("string")
        return buildSet {
            repeat(strings.length) { index ->
                add(strings.item(index).attributes.getNamedItem("name").nodeValue)
            }
        }
    }
}
