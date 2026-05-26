package app.securemessenger.storage

import android.content.Context
import app.securemessenger.crypto.NonceState
import app.securemessenger.crypto.NonceStateStore
import java.util.Base64

class SharedPreferencesNonceStateStore(context: Context) : NonceStateStore {
    private val preferences = context.getSharedPreferences("secure_messenger_nonce_state", Context.MODE_PRIVATE)

    override fun read(scope: String): NonceState? {
        val prefix = preferences.getString("$scope.prefix", null) ?: return null
        val counter = preferences.getLong("$scope.counter", 0L)
        return NonceState(Base64.getDecoder().decode(prefix), counter)
    }

    override fun write(scope: String, state: NonceState) {
        preferences.edit()
            .putString("$scope.prefix", Base64.getEncoder().encodeToString(state.randomPrefix))
            .putLong("$scope.counter", state.lastCounter)
            .commit()
    }
}
