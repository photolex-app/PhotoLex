package com.peeyupatel.phototextsearch.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

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

    private suspend fun recognizeText(inputImage: InputImage) = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text> { continuation ->
        recognizer.process(inputImage)
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }

    private fun loadSmallBitmap(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(inputStream, null, boundsOptions)

                val sampleSize = calculateSampleSize(boundsOptions, PRESCAN_MAX_DIMENSION, PRESCAN_MAX_DIMENSION)

                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val loadOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    BitmapFactory.decodeStream(stream, null, loadOptions)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load small bitmap for pre-scan: $uri", e)
            null
        }
    }

    private fun calculateSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun cleanup() {
        recognizer.close()
    }
}
