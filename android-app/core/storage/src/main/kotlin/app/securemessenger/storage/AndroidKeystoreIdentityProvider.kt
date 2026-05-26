package app.securemessenger.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.securemessenger.crypto.DeviceKeyMaterial
import app.securemessenger.crypto.InvitationCodec
import app.securemessenger.domain.DevicePublicIdentity
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.ECGenParameterSpec
import java.util.UUID

class AndroidKeystoreIdentityProvider(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun loadOrCreate(): DeviceKeyMaterial {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!store.containsAlias(IDENTITY_ALIAS)) {
            createIdentityKey()
        }
        val privateKey = store.getKey(IDENTITY_ALIAS, null) as PrivateKey
        val publicKey = store.getCertificate(IDENTITY_ALIAS).publicKey.encoded
        val deviceId = preferences.getString(DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString(DEVICE_ID, it).apply()
        }
        return DeviceKeyMaterial(
            DevicePublicIdentity(deviceId, publicKey, InvitationCodec.fingerprint(publicKey)),
            privateKey,
        )
    }

    fun deleteIdentity() {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(IDENTITY_ALIAS)
        preferences.edit().clear().apply()
    }

    private fun createIdentityKey() {
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        generator.initialize(
            KeyGenParameterSpec.Builder(IDENTITY_ALIAS, KeyProperties.PURPOSE_AGREE_KEY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .build(),
        )
        generator.generateKeyPair()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val IDENTITY_ALIAS = "secure_messenger_device_ecdh_v1"
        const val PREFERENCES = "secure_messenger_identity"
        const val DEVICE_ID = "device_id"
    }
}
