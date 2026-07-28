package com.peeyupatel.phototextsearch.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Cheap pre-check for the opt-in Devanagari OCR pass: runs a small downsampled bitmap through
 * the actual Devanagari recognizer (the Latin recognizer used by the shared FastTextPreScanner
 * can't reliably read Devanagari glyphs at all, so its hasText flag alone isn't a safe signal
 * for this), then asks ML Kit Language Identification whether the resulting text sample is
 * confidently NOT Hindi -- mirroring the existing Latin no-text skip, but for language instead
 * of presence. Conservative: only skips full extraction when confident; anything uncertain
 * (blank sample, identification failure, low confidence) still gets full-resolution Devanagari
 * OCR, same "never guess" policy as the existing hasText-based skip.
 */
class DevanagariLanguageGate(private val context: Context) {

    companion object {
        private const val TAG = "DevanagariLanguageGate"
        private const val PRESCAN_MAX_DIMENSION = 400
        private const val PRESCAN_TIMEOUT_MS = 1500L
    }

    private val recognizer: TextRecognizer =
        TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    private val languageIdentifier: LanguageIdentifier = LanguageIdentification.getClient()

    /** True only when confident this photo's text is NOT Hindi/Devanagari -- safe to skip. */
    suspend fun isConfidentlyNotDevanagari(uri: Uri): Boolean {
        val text = try {
            withTimeoutOrNull(PRESCAN_TIMEOUT_MS) { quickRecognizedText(uri) }
        } catch (e: Exception) {
            Log.w(TAG, "Language pre-check scan failed for $uri: ${e.message}")
            null
        }

        if (text.isNullOrBlank()) return false

        return try {
            val languageCode = languageIdentifier.identifyLanguage(text).await()
            // "und" = undetermined -- treat as uncertain, not as "confidently not Hindi"
            languageCode != "hi" && languageCode != "und"
        } catch (e: Exception) {
            Log.w(TAG, "Language identification failed: ${e.message}")
            false
        }
    }

    private suspend fun quickRecognizedText(uri: Uri): String? {
        val bitmap = loadSmallBitmap(uri) ?: return null
        return try {
            if (bitmap.width <= 0 || bitmap.height <= 0) return null
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            recognizeText(inputImage).text
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private suspend fun recognizeText(inputImage: InputImage) =
        suspendCancellableCoroutine<com.google.mlkit.vision.text.Text> { continuation ->
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
            Log.w(TAG, "Failed to load small bitmap for language pre-check: $uri", e)
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
