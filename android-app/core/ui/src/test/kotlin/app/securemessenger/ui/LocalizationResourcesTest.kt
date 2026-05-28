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
        val diagnosticsSource = File("src/main/kotlin/app/securemessenger/ui/SafeDeliveryDiagnostics.kt").readText()
        assertFalse(source.contains("Text(\""))
        assertFalse(source.contains("android.util.Log"))
        assertFalse(source.contains("println("))
        assertFalse(workerSource.contains("android.util.Log"))
        assertFalse(workerSource.contains("println("))
        assertFalse(diagnosticsSource.contains("android.util.Log"))
        assertFalse(diagnosticsSource.contains("println("))
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
            "show_invitation_qr",
            "copy_invitation_code",
            "pairing_waiting",
            "pairing_ready",
            "pairing_blocked",
            "pairing_incomplete_instruction",
            "pairing_ready_confirmation",
            "http_relay_debug_warning",
            "https_relay_required",
            "public_relay_tunnel_notice",
            "http_debug_lan_only",
            "use_https_for_different_networks",
            "relay_phone_loopback_warning",
            "relay_unspecified_address_warning",
            "qa_preflight_title",
            "qa_preflight_relay_mode",
            "relay_mode_lan",
            "relay_mode_public",
            "public_relay_available",
            "run_qa_preflight",
            "qa_preflight_ready",
            "qa_preflight_https_ready",
            "qa_preflight_phone_loopback",
            "qa_preflight_unspecified_relay",
            "reset_test_data",
            "reset_test_data_warning",
            "diagnostics_title",
            "diagnostics_safe_notice",
            "copy_diagnostic_report",
            "clear_diagnostics",
            "diagnostics_work_status",
        )

        assertTrue(resourceNames(File("src/main/res/values/strings.xml")).containsAll(expected))
        assertTrue(resourceNames(File("src/main/res/values-ru/strings.xml")).containsAll(expected))
    }

    @Test
    fun `http warning is wired and insecure relay resource is debug only`() {
        val source = File("src/main/kotlin/app/securemessenger/ui/SecureMessengerApp.kt").readText()
        val releasePolicy = File("src/main/res/values/transport_policy.xml").readText()
        val debugPolicy = File("src/debug/res/values/transport_policy.xml").readText()

        assertTrue(source.contains("R.string.http_relay_debug_warning"))
        assertTrue(source.contains("R.string.https_relay_required"))
        assertTrue(source.contains("R.string.pairing_incomplete_instruction"))
        assertTrue(source.contains("R.string.pairing_ready_confirmation"))
        assertTrue(releasePolicy.contains("<bool name=\"allow_insecure_relay_http\">false</bool>"))
        assertTrue(debugPolicy.contains("<bool name=\"allow_insecure_relay_http\">true</bool>"))
        assertTrue(releasePolicy.contains("<bool name=\"enable_debug_qa_tools\">false</bool>"))
        assertTrue(debugPolicy.contains("<bool name=\"enable_debug_qa_tools\">true</bool>"))
    }

    @Test
    fun `settings use fully selectable option rows and portrait scanner options`() {
        val source = File("src/main/kotlin/app/securemessenger/ui/SecureMessengerApp.kt").readText()
        val scanner = File("src/main/kotlin/app/securemessenger/ui/PortraitQrCaptureActivity.kt").readText()
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(source.contains("SelectableOptionRow("))
        assertTrue(source.contains(".selectable("))
        assertTrue(source.contains("role = Role.RadioButton"))
        assertTrue(source.contains("RadioButton(selected = selected, onClick = null"))
        assertEquals(2, Regex("portraitQrScanOptions\\(scanPrompt\\)").findAll(source).count())
        assertTrue(scanner.contains("setOrientationLocked(true)"))
        assertTrue(scanner.contains("setCaptureActivity(PortraitQrCaptureActivity::class.java)"))
        assertTrue(manifest.contains("android:screenOrientation=\"portrait\""))
    }

    @Test
    fun `app locale host is appcompat activity without locking messenger orientation`() {
        val activity = File("../../app/src/main/kotlin/app/securemessenger/MainActivity.kt").readText()
        val appManifest = File("../../app/src/main/AndroidManifest.xml").readText()

        assertTrue(activity.contains("AppCompatActivity"))
        assertFalse(appManifest.contains("android:name=\".MainActivity\"\n            android:screenOrientation"))
        assertTrue(appManifest.contains("android.permission.CAMERA"))
    }

    @Test
    fun `debug reset is confirmed and preflight is wired without enabling gated crypto`() {
        val app = File("src/main/kotlin/app/securemessenger/ui/SecureMessengerApp.kt").readText()
        val viewModel = File("src/main/kotlin/app/securemessenger/ui/SecureMessengerViewModel.kt").readText()

        assertTrue(app.contains("showTestResetDialog"))
        assertTrue(app.contains("R.string.show_invitation_qr"))
        assertTrue(app.contains("R.string.copy_invitation_code"))
        assertTrue(app.contains("clipboardManager.setText(AnnotatedString(invitationCode))"))
        assertTrue(app.contains("R.string.reset_test_data_warning"))
        assertTrue(app.contains("onResetTestData()"))
        assertTrue(app.contains("onRunPreflight"))
        assertTrue(app.contains("relayClientAddressWarning"))
        assertTrue(viewModel.contains("fun resetTestDataForQa()"))
        assertTrue(viewModel.contains("if (!debugQaToolsEnabled) return"))
        assertTrue(viewModel.contains("fun runTwoDevicePreflight()"))
        assertFalse(viewModel.contains("SignalSessionManagerAdapter"))
    }

    @Test
    fun `logcat qa scripts are executable redacting helpers and log output is ignored`() {
        val start = File("../../scripts/start_qa_logcat.sh")
        val analyze = File("../../scripts/analyze_qa_logcat.sh")
        val ignore = File("../../../.gitignore").readText()

        assertTrue(start.isFile)
        assertTrue(analyze.isFile)
        assertTrue(start.canExecute())
        assertTrue(analyze.canExecute())
        assertTrue(start.readText().contains("analyze_qa_logcat.sh\" --redact-stream"))
        assertTrue(analyze.readText().contains("[REDACTED]"))
        assertTrue(ignore.contains("qa-logcat*.txt"))
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
