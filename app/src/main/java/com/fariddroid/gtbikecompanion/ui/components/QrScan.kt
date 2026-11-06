package com.fariddroid.gtbikecompanion.ui.components

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Opens the Google Play services QR scanner (its own camera screen, so the app needs no camera permission).
 * Cancelling calls neither callback; [onUnavailable] means the phone cannot scan (no Play services module).
 */
fun startQrScan(context: Context, onText: (String) -> Unit, onUnavailable: () -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { barcode -> onText(barcode.rawValue.orEmpty()) }
        .addOnFailureListener { onUnavailable() }
}
