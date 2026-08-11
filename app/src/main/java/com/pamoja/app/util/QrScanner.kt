package com.pamoja.app.util

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Scans a QR code using Google's Code Scanner.
 *
 * Chosen over CameraX plus ML Kit because it needs **no CAMERA permission**:
 * the scanning UI runs inside Google Play services, not in our process. That
 * removes a permission request, a rationale screen, a denied state and a
 * permanently-denied state from a flow whose entire job is to be faster than
 * typing a link.
 *
 * The trade is a dependency on Play services and a one-time module download on
 * first use. Both are already true of this app.
 */
object QrScanner {

    /** The user closed the scanner. A choice, not a failure. */
    class Cancelled : Exception("QR scan cancelled")

    /**
     * Opens the scanner and returns the raw scanned text.
     *
     * Deliberately returns the raw value rather than an invite code. Deciding
     * whether a string is a Pamoja invite belongs to [InviteLink], and keeping
     * that out of here means the scanner has no opinion about what it scanned.
     */
    fun scan(context: Context, onResult: (Result<String>) -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()

        GmsBarcodeScanning.getClient(context, options)
            .startScan()
            .addOnSuccessListener { barcode ->
                val value = barcode.rawValue
                if (value.isNullOrBlank()) {
                    onResult(Result.failure(Exception("Empty QR code")))
                } else {
                    onResult(Result.success(value))
                }
            }
            .addOnCanceledListener { onResult(Result.failure(Cancelled())) }
            .addOnFailureListener { e ->
                // Dismissing the scanner arrives here as an ApiException with
                // CANCELED rather than through the cancel listener, so it is
                // normalised instead of being shown to the user as an error.
                val cancelled = e is ApiException &&
                    e.statusCode == CommonStatusCodes.CANCELED
                onResult(Result.failure(if (cancelled) Cancelled() else e))
            }
    }
}
