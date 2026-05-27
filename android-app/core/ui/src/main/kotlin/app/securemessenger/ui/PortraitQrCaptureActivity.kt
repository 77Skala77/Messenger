package app.securemessenger.ui

import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.ScanOptions

/**
 * ZXing's default capture activity is landscape. Invitations and verification use
 * this dedicated portrait activity without constraining the rest of the application.
 */
class PortraitQrCaptureActivity : CaptureActivity()

internal fun portraitQrScanOptions(prompt: String): ScanOptions = ScanOptions()
    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
    .setPrompt(prompt)
    .setBeepEnabled(false)
    .setOrientationLocked(true)
    .setCaptureActivity(PortraitQrCaptureActivity::class.java)
