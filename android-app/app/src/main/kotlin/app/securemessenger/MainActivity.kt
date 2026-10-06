package app.securemessenger

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import app.securemessenger.ui.SecureMessengerApp
import app.securemessenger.ui.V2OpenChat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A tapped notification names the chat to open; a recreated activity has handled it already.
        if (savedInstanceState == null) V2OpenChat.take(intent)
        setContent {
            SecureMessengerApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        V2OpenChat.take(intent)
    }
}
