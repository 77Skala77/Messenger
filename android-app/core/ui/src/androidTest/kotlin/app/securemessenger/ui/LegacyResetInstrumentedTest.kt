package app.securemessenger.ui

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.securemessenger.storage.JcaLocalVaultCipher
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** DEC-021: the explicit legacy reset outside the database (Keystore key and preferences). */
@RunWith(AndroidJUnit4::class)
class LegacyResetInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Before
    fun freshInstall() = clearEverything()

    @After
    fun cleanUp() = clearEverything()

    @Test
    fun legacyKeyAndPreferencesAreRemovedOnceAndTheNoticeIsShownOnce() {
        createLegacyIdentityKey()
        LegacyReset.LEGACY_PREFERENCES.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().putString("legacy", "value").commit()
        }
        assertTrue(keyStore.containsAlias(LegacyReset.LEGACY_IDENTITY_ALIAS))

        LegacyReset.runOnce(context)

        assertFalse(keyStore.containsAlias(LegacyReset.LEGACY_IDENTITY_ALIAS))
        LegacyReset.LEGACY_PREFERENCES.forEach { name ->
            assertTrue("$name survived", context.getSharedPreferences(name, Context.MODE_PRIVATE).all.isEmpty())
        }
        assertTrue(LegacyReset.isNoticePending(context))

        LegacyReset.dismissNotice(context)
        // A later start never repeats the reset or the notice, even if legacy-looking data appeared.
        createLegacyIdentityKey()
        LegacyReset.runOnce(context)
        assertTrue(keyStore.containsAlias(LegacyReset.LEGACY_IDENTITY_ALIAS))
        assertFalse(LegacyReset.isNoticePending(context))
    }

    @Test
    fun aCleanInstallGetsNoNotice() {
        LegacyReset.runOnce(context)

        assertFalse(LegacyReset.isNoticePending(context))
        assertFalse(LegacyReset.isRelayNoticePending(context))
        assertFalse(keyStore.containsAlias(LegacyReset.LEGACY_IDENTITY_ALIAS))
    }

    /** DEC-024: a pre-v3 plain queue ID is removed once, with one notice; the new secret survives. */
    @Test
    fun theOldPlainQueueIdIsRemovedOnceWithOneNotice() {
        queuePreferences().edit()
            .putString(V2QueueCredentialStore.OLD_QUEUE_ID, "v2-00000000-0000-0000-0000-000000000000")
            .commit()

        LegacyReset.runOnce(context)

        assertFalse(queuePreferences().contains(V2QueueCredentialStore.OLD_QUEUE_ID))
        assertTrue(LegacyReset.isRelayNoticePending(context))
        LegacyReset.dismissRelayNotice(context)
        queuePreferences().edit().putString(V2QueueCredentialStore.OLD_QUEUE_ID, "late").commit()
        LegacyReset.runOnce(context)
        assertFalse(LegacyReset.isRelayNoticePending(context))
    }

    @Test
    fun theQueueReadSecretIsStoredEncryptedAndSurvivesReopening() {
        val vault = JcaLocalVaultCipher(ByteArray(32) { 9 })
        val created = V2QueueCredentialStore(context, vault).getOrCreate()
        val reopened = V2QueueCredentialStore(context, vault).getOrCreate()

        assertEquals(created.queueId, reopened.queueId)
        val stored = queuePreferences().all.values.joinToString()
        assertFalse("the bearer secret is stored in clear", stored.contains(created.bearer()))
        assertFalse(stored.contains(created.queueId))
    }

    private fun queuePreferences() = context.getSharedPreferences(V2QueueCredentialStore.PREFERENCES, Context.MODE_PRIVATE)

    private fun createLegacyIdentityKey() {
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        generator.initialize(
            KeyGenParameterSpec.Builder(LegacyReset.LEGACY_IDENTITY_ALIAS, KeyProperties.PURPOSE_AGREE_KEY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .build(),
        )
        generator.generateKeyPair()
    }

    private fun clearEverything() {
        if (keyStore.containsAlias(LegacyReset.LEGACY_IDENTITY_ALIAS)) keyStore.deleteEntry(LegacyReset.LEGACY_IDENTITY_ALIAS)
        (LegacyReset.LEGACY_PREFERENCES + "secure_messenger_legacy_reset" + V2QueueCredentialStore.PREFERENCES).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
