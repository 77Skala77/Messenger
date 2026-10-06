package app.securemessenger.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizationResourcesTest {
    private val uiSources = listOf(
        "SecureMessengerApp.kt",
        "SecureMessengerViewModel.kt",
        "V2Screens.kt",
        "ChatList.kt",
        "ChatListModel.kt",
        "ConversationScreen.kt",
        "ConversationModel.kt",
        "V2Notifications.kt",
        "NotificationRules.kt",
        "NotificationUi.kt",
        "ScreenSecurity.kt",
        "SettingsScreen.kt",
        "TelegramTopBar.kt",
        "TelegramPopupMenu.kt",
        "TelegramAlertDialog.kt",
        "TelegramScreenStack.kt",
        "ConversationV2ViewModel.kt",
        "V2BackgroundSync.kt",
        "LegacyReset.kt",
        "V2Availability.kt",
    ).map { File("src/main/kotlin/app/securemessenger/ui/$it") }

    @Test
    fun `russian contains every english user facing string`() {
        val english = resourceNames(File("src/main/res/values/strings.xml"))
        val russian = resourceNames(File("src/main/res/values-ru/strings.xml"))

        assertTrue(english.isNotEmpty())
        assertEquals(english, russian)
    }

    @Test
    fun `ui sources embed no visible text literals and no logging`() {
        uiSources.forEach { source ->
            val content = source.readText()
            assertFalse("${source.name} embeds a text literal", content.contains("Text(\""))
            assertFalse("${source.name} logs", content.contains("android.util.Log"))
            assertFalse("${source.name} prints", content.contains("println("))
        }
    }

    @Test
    fun `the legacy prototype protocol is gone from the app`() {
        val settings = File("../../settings.gradle.kts").readText()
        assertFalse(settings.contains(":core:crypto"))
        assertFalse(settings.contains(":core:delivery"))
        uiSources.forEach { source ->
            val content = source.readText()
            listOf("SimplifiedSessionManager", "StandardAead", "InvitationCodec", "DeliveryPipeline").forEach { legacy ->
                assertFalse("${source.name} still references $legacy", content.contains(legacy))
            }
        }
    }

    @Test
    fun `v2 is debug only and release policy stays closed`() {
        val releasePolicy = File("src/main/res/values/transport_policy.xml").readText()
        val debugPolicy = File("src/debug/res/values/transport_policy.xml").readText()

        assertTrue(releasePolicy.contains("<bool name=\"enable_v2_protocol\">false</bool>"))
        assertTrue(debugPolicy.contains("<bool name=\"enable_v2_protocol\">true</bool>"))
        assertTrue(releasePolicy.contains("<bool name=\"allow_insecure_relay_http\">false</bool>"))
        assertTrue(debugPolicy.contains("<bool name=\"allow_insecure_relay_http\">true</bool>"))
        assertTrue(releasePolicy.contains("<bool name=\"enable_debug_qa_tools\">false</bool>"))
        assertTrue(debugPolicy.contains("<bool name=\"enable_debug_qa_tools\">true</bool>"))
    }

    @Test
    fun `release shell never builds the pipeline and the chat viewmodel refuses without v2`() {
        val app = File("src/main/kotlin/app/securemessenger/ui/SecureMessengerApp.kt").readText()
        val viewModel = File("src/main/kotlin/app/securemessenger/ui/ConversationV2ViewModel.kt").readText()
        val worker = File("src/main/kotlin/app/securemessenger/ui/V2BackgroundSync.kt").readText()

        assertTrue(app.contains("if (state.v2Available)"))
        assertTrue(app.contains("NotForMessagingScreen"))
        assertTrue(viewModel.contains("if (!available) return"))
        assertTrue(worker.contains("if (!V2Availability.isAvailable(applicationContext)) return Result.success()"))
        assertTrue(worker.contains("if (!V2Availability.isAvailable(application)) return"))
    }

    @Test
    fun `message status labels distinguish relay acceptance from end to end delivery`() {
        val english = stringValues(File("src/main/res/values/strings.xml"))
        val russian = stringValues(File("src/main/res/values-ru/strings.xml"))

        assertEquals("Waiting for sync", english.getValue("status_pending"))
        assertEquals("Ожидает синхронизации", russian.getValue("status_pending"))
        assertEquals("Sent to delivery server", english.getValue("status_sent"))
        assertEquals("Отправлено на сервер доставки", russian.getValue("status_sent"))
        assertEquals("Delivered", english.getValue("status_delivered"))
        assertEquals("Доставлено", russian.getValue("status_delivered"))
        assertEquals("Waiting for sync", english.getValue("status_failed"))
    }

    @Test
    fun `http warnings are wired and the settings use fully selectable rows`() {
        // The settings moved to their own file in the Telegram port; the rules are checked over both.
        val source = File("src/main/kotlin/app/securemessenger/ui/SecureMessengerApp.kt").readText() +
            File("src/main/kotlin/app/securemessenger/ui/SettingsScreen.kt").readText()
        val v2 = File("src/main/kotlin/app/securemessenger/ui/V2Screens.kt").readText()
        val scanner = File("src/main/kotlin/app/securemessenger/ui/PortraitQrCaptureActivity.kt").readText()
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(source.contains("R.string.http_relay_debug_warning"))
        assertTrue(source.contains("R.string.https_relay_required"))
        assertTrue(source.contains("relayClientAddressWarning"))
        assertTrue(source.contains("SelectableOptionRow("))
        assertTrue(source.contains(".selectable("))
        assertTrue(source.contains("role = Role.RadioButton"))
        assertTrue(source.contains("RadioButton(selected = selected, onClick = null"))
        assertEquals(1, Regex("portraitQrScanOptions\\(scanPrompt\\)").findAll(v2).count())
        assertTrue(scanner.contains("setOrientationLocked(true)"))
        assertTrue(scanner.contains("setCaptureActivity(PortraitQrCaptureActivity::class.java)"))
        assertTrue(manifest.contains("android:screenOrientation=\"portrait\""))
    }

    @Test
    fun `destructive actions are confirmed and the debug reset is debug only`() {
        val app = File("src/main/kotlin/app/securemessenger/ui/SecureMessengerApp.kt").readText() +
            File("src/main/kotlin/app/securemessenger/ui/SettingsScreen.kt").readText()
        val viewModel = File("src/main/kotlin/app/securemessenger/ui/SecureMessengerViewModel.kt").readText()

        assertTrue(app.contains("showTestResetDialog"))
        assertTrue(app.contains("showDeleteDialog"))
        assertTrue(app.contains("R.string.reset_test_data_warning"))
        assertTrue(app.contains("onResetTestData()"))
        assertTrue(viewModel.contains("fun resetTestDataForQa()"))
        assertTrue(viewModel.contains("if (!mutableState.value.debugQaToolsEnabled) return"))
    }

    @Test
    fun `app locale host is appcompat activity and the legacy reset runs at startup`() {
        val activity = File("../../app/src/main/kotlin/app/securemessenger/MainActivity.kt").readText()
        val application = File("../../app/src/main/kotlin/app/securemessenger/SecureMessengerApplication.kt").readText()
        val appManifest = File("../../app/src/main/AndroidManifest.xml").readText()

        assertTrue(activity.contains("AppCompatActivity"))
        assertTrue(application.contains("LegacyReset.runOnce(this)"))
        assertFalse(appManifest.contains("android:name=\".MainActivity\"\n            android:screenOrientation"))
        assertTrue(appManifest.contains("android.permission.CAMERA"))
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

    private fun stringValues(file: File): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val strings = document.getElementsByTagName("string")
        return buildMap {
            repeat(strings.length) { index ->
                val item = strings.item(index)
                put(item.attributes.getNamedItem("name").nodeValue, item.textContent)
            }
        }
    }
}
