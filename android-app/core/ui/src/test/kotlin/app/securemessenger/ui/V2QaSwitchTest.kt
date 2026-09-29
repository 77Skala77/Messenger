package app.securemessenger.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V2QaSwitchTest {
    @Test
    fun `a stored on value never takes effect unless debug qa tools are allowed`() {
        assertFalse(V2QaSwitch.effective(allowed = false, stored = true))
        assertFalse(V2QaSwitch.effective(allowed = false, stored = false))
        assertFalse(V2QaSwitch.effective(allowed = true, stored = false))
        assertTrue(V2QaSwitch.effective(allowed = true, stored = true))
    }

    @Test
    fun `release policy keeps the switch impossible and the v2 viewmodel refuses to start`() {
        val releasePolicy = File("src/main/res/values/transport_policy.xml").readText()
        val viewModel = File("src/main/kotlin/app/securemessenger/ui/ConversationV2ViewModel.kt").readText()
        val switch = File("src/main/kotlin/app/securemessenger/ui/V2QaSwitch.kt").readText()

        assertTrue(releasePolicy.contains("<bool name=\"enable_debug_qa_tools\">false</bool>"))
        assertTrue(viewModel.contains("if (!allowed) return"))
        assertTrue(switch.contains("if (!allowed) return alwaysOff"))
        assertTrue(switch.contains("if (!allowed) return\n"))
    }

    @Test
    fun `v2 code is reached only through the switch and shows no literal text or logging`() {
        val app = File("src/main/kotlin/app/securemessenger/ui/SecureMessengerApp.kt").readText()
        listOf("V2Screens.kt", "ConversationV2ViewModel.kt", "V2QaSwitch.kt").forEach { name ->
            val source = File("src/main/kotlin/app/securemessenger/ui/$name").readText()
            assertFalse("$name embeds a text literal", source.contains("Text(\""))
            assertFalse("$name logs", source.contains("android.util.Log") || source.contains("println("))
        }
        assertTrue(app.contains("V2QaSwitch.flow(appContext, state.debugQaToolsEnabled)"))
        assertTrue(app.contains("val showV2 = v2Enabled && state.profileCreated"))
    }
}
