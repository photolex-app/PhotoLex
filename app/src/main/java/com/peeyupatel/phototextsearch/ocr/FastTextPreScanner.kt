package com.peeyupatel.phototextsearch.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Fast "does this photo have any text at all" pre-scan, used to prioritize which photos get
 * full, detailed OCR extraction first. Runs the same ML Kit Latin text recognizer already used
 * for full extraction (OcrTextExtractor), but against a much smaller, heavily downscaled bitmap
 * -- ML Kit's processing time scales with pixel count, so a ~400px-max-dimension image is
 * meaningfully faster to both decode and recognize than the ~1024px used for full extraction.
 * This is a binary presence check only (not multi-language, not stored text) -- it does not
 * replace full extraction, just decides its priority order.
 */
class FastTextPreScanner(private val context: Context) {

    companion object {
        private const val TAG = "FastTextPreScanner"
        private const val PRESCAN_MAX_DIMENSION = 400
        private const val PRESCAN_TIMEOUT_MS = 1500L

        /** Hamming distance (out of 64 bits) below which two dHashes are treated as the
         * same/near-identical photo. Deliberately strict/conservative -- missing a real
         * duplicate just means it gets OCR'd normally, but a false match would wrongly copy
         * another photo's text onto this one, so this errs toward under-triggering. */
        const val DUPLICATE_HAMMING_THRESHOLD = 4

        fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    data class PreScanResult(
        val hasText: Boolean?,
        val dHash: Long?,
        val isLikelyDocument: Boolean? = null,
        val barcode: BarcodeScanningHelper.DetectedBarcode? = null
    )

    /**
     * Returns true if the image appears to contain any text at all, false if not (or if the
     * scan couldn't be completed -- callers should treat scan failure as "unknown", not as a
     * confident "no text", so photos aren't silently deprioritized just because of a decode/
     * recognition error; see hasTextOrNull()).
     */
    suspend fun hasTextOrNull(uri: Uri): Boolean? {
        val bitmap = loadSmallBitmap(uri) ?: return null
        return try {
            if (bitmap.width <= 0 || bitmap.height <= 0) return null
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val result = withTimeoutOrNull(PRESCAN_TIMEOUT_MS) { recognizeText(inputImage) }
            result?.textBlocks?.isNotEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Pre-scan failed for $uri: ${e.message}")
            null
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    /**
     * Same text-presence scan as hasTextOrNull(), plus a dHash fingerprint, an image-labeling
     * "does this look like a document" signal, and a barcode/QR scan, all computed from the
     * same already-decoded downsampled bitmap (near-zero extra decode cost) so callers get all
     * four signals from a single small decode instead of separate passes.
     */
    suspend fun scanWithHash(uri: Uri): PreScanResult {
        val bitmap = loadSmallBitmap(uri) ?: return PreScanResult(null, null)
        return try {
            if (bitmap.width <= 0 || bitmap.height <= 0) return PreScanResult(null, null)
            val dHash = try {
                computeDHash(bitmap)
            } catch (e: Exception) {
                Log.w(TAG, "dHash computation failed for $uri: ${e.message}")
                null
            }
            // Independent signals off the same decoded bitmap -- run concurrently rather than
            // sequentially so wall time is bounded by the slowest, not the sum of all three.
            coroutineScope {
                val isLikelyDocumentDeferred = async { ImageLabelingHelper.isLikelyDocument(bitmap) }
                val barcodeDeferred = async { BarcodeScanningHelper.scan(bitmap) }
                val textDeferred = async {
                    val inputImage = InputImage.fromBitmap(bitmap, 0)
                    withTimeoutOrNull(PRESCAN_TIMEOUT_MS) { recognizeText(inputImage) }
                }
                val isLikelyDocument = isLikelyDocumentDeferred.await()
                val barcode = barcodeDeferred.await()
                val result = textDeferred.await()
                PreScanResult(result?.textBlocks?.isNotEmpty(), dHash, isLikelyDocument, barcode)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pre-scan failed for $uri: ${e.message}")
            PreScanResult(null, null)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    /**
     * Difference hash (dHash): shrink to a 9x8 grayscale grid and record, for each row, whether
     * each pixel is brighter than the pixel to its right -- 64 bits total. Cheap, and tolerant
     * of resize/compression differences between near-identical shots while still sensitive to
     * genuinely different content.
     */
    private fun computeDHash(source: Bitmap): Long {
        val small = Bitmap.createScaledBitmap(source, 9, 8, true)
        try {
            var hash = 0L
            var bitIndex = 0
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    val left = grayscale(small.getPixel(x, y))
                    val right = grayscale(small.getPixel(x + 1, y))
                    if (left > right) {
                        hash = hash or (1L shl bitIndex)
                    }
                    bitIndex++
                }
            }
            return hash
        } finally {
            if (small != source && !small.isRecycled) small.recycle()
        }
    }

    private fun grayscale(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (r + g + b) / 3
    }

    private suspend fun recognizeText(inputImage: InputImage) = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text> { continuation ->
        recognizer.process(inputImage)
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }

    private fun loadSmallBitmap(uri: Uri): Bitmap? =
        BitmapDownsampler.loadSmallBitmap(context, uri, PRESCAN_MAX_DIMENSION)

    fun cleanup() {
        recognizer.close()
    }
}
