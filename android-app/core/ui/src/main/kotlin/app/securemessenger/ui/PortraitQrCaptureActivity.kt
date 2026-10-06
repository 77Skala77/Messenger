package app.securemessenger.ui

import android.os.Bundle
import android.view.WindowManager
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.ScanOptions

/**
 * ZXing's default capture activity is landscape. Invitations and verification use
 * this dedicated portrait activity without constraining the rest of the application.
 */
class PortraitQrCaptureActivity : CaptureActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Part of the app: the person's screen-security choice covers it too (DEC-032).
        if (ScreenSecurity.enabled(this)) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

internal fun portraitQrScanOptions(prompt: String): ScanOptions = ScanOptions()
    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
    .setPrompt(prompt)
    .setBeepEnabled(false)
    .setOrientationLocked(true)
    .setCaptureActivity(PortraitQrCaptureActivity::class.java)
