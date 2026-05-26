package app.securemessenger.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

interface LocalVaultCipher {
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray
    fun decrypt(blob: ByteArray, associatedData: ByteArray): ByteArray
}

class AndroidKeystoreVaultCipher(
    private val alias: String = "secure_messenger_local_vault_v1",
) : LocalVaultCipher {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // Android Keystore generates a fresh randomized IV; caller-supplied IVs are not accepted.
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(associatedData)
        return encode(cipher.iv, cipher.doFinal(plaintext))
    }

    override fun decrypt(blob: ByteArray, associatedData: ByteArray): ByteArray {
        val (nonce, ciphertext) = decode(blob)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(ciphertext)
    }

    fun deleteKey() {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(alias)
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128

        internal fun encode(nonce: ByteArray, ciphertext: ByteArray): ByteArray =
            ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { output ->
                    output.writeInt(nonce.size)
                    output.write(nonce)
                    output.writeInt(ciphertext.size)
                    output.write(ciphertext)
                }
                buffer.toByteArray()
            }

        internal fun decode(blob: ByteArray): Pair<ByteArray, ByteArray> =
            DataInputStream(ByteArrayInputStream(blob)).use { input ->
                val nonceSize = input.readInt()
                require(nonceSize == 12) { "Invalid local vault nonce" }
                val nonce = ByteArray(nonceSize).also(input::readFully)
                val ciphertextSize = input.readInt()
                require(ciphertextSize in 16..(16 * 1024 * 1024)) { "Invalid local vault blob" }
                val ciphertext = ByteArray(ciphertextSize).also(input::readFully)
                require(input.available() == 0) { "Trailing local vault bytes" }
                nonce to ciphertext
            }
    }
}

class JcaLocalVaultCipher(private val keyBytes: ByteArray) : LocalVaultCipher {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"))
        cipher.updateAAD(associatedData)
        return AndroidKeystoreVaultCipher.encode(cipher.iv, cipher.doFinal(plaintext))
    }

    override fun decrypt(blob: ByteArray, associatedData: ByteArray): ByteArray {
        val (nonce, ciphertext) = AndroidKeystoreVaultCipher.decode(blob)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(ciphertext)
    }
}
