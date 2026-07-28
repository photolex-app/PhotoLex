package com.peeyupatel.phototextsearch.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.tasks.await

/**
 * Wraps ML Kit's on-device Barcode Scanning API, run on the same small downsampled bitmap
 * FastTextPreScanner already decodes for its text-presence check -- cheap/fast, near-zero extra
 * decode cost. Receipts, tickets, and boarding passes commonly carry a barcode/QR code, giving
 * an extra exact, structured search dimension for free. Best-effort: any failure just means no
 * barcode signal, same defensive pattern as the other pre-scan helpers.
 */
object BarcodeScanningHelper {

    private const val TAG = "BarcodeScanningHelper"

    data class DetectedBarcode(val text: String, val format: String)

    private val scanner by lazy { BarcodeScanning.getClient() }

    suspend fun scan(bitmap: Bitmap): DetectedBarcode? {
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val barcodes = scanner.process(inputImage).await()
            val first = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() } ?: return null
            DetectedBarcode(first.rawValue!!, formatName(first.format))
        } catch (e: Exception) {
            Log.w(TAG, "Barcode scan failed: ${e.message}")
            null
        }
    }

    private fun formatName(format: Int): String = when (format) {
        Barcode.FORMAT_QR_CODE -> "QR_CODE"
        Barcode.FORMAT_AZTEC -> "AZTEC"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_CODE_128 -> "CODE_128"
        Barcode.FORMAT_EAN_13 -> "EAN_13"
        Barcode.FORMAT_UPC_A -> "UPC_A"
        else -> "OTHER"
    }
}
