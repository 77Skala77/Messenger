package app.securemessenger.ui

import android.content.ComponentName
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.zxing.client.android.Intents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortraitQrScannerInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun scannerOptionsTargetDedicatedPortraitCaptureActivityAndLockOrientation() {
        val intent = portraitQrScanOptions("prompt").createScanIntent(context)

        assertEquals(PortraitQrCaptureActivity::class.java.name, intent.component?.className)
        assertTrue(intent.getBooleanExtra(Intents.Scan.ORIENTATION_LOCKED, false))
    }

    @Test
    fun scannerActivityManifestIsPortraitOnly() {
        val activityInfo = context.packageManager.getActivityInfo(
            ComponentName(context, PortraitQrCaptureActivity::class.java),
            0,
        )

        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activityInfo.screenOrientation)
    }
}
