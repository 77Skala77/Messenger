package app.securemessenger

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import app.securemessenger.ui.SecureMessengerApp
import app.securemessenger.ui.V2OpenChat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A tapped notification names the chat to open; a recreated activity has handled it already.
        if (savedInstanceState == null) V2OpenChat.take(intent)
        preferSmoothestDisplayMode()
        // Telegram 12's look: the app draws under clear system bars, the chat's wallpaper from edge to
        // edge; every screen keeps its content clear of the bars and the keyboard itself.
        enableEdgeToEdge()
        setContent {
            SecureMessengerApp(backgroundConnection = true)
        }
    }

    /**
     * Asks for the display mode with the highest refresh rate at the screen's current size (120 Hz
     * where the phone has it), so swipes and animations are as smooth as the screen allows. The system
     * may still lower it (battery saver); nothing depends on it.
     */
    private fun preferSmoothestDisplayMode() {
        val display = display ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        if (best.refreshRate > current.refreshRate) {
            window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        V2OpenChat.take(intent)
    }
}
