package com.peeyupatel.phototextsearch.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.tasks.await

/**
 * Wraps ML Kit's on-device Image Labeling API as a secondary "does this look like a document"
 * signal, run on the same small downsampled bitmap FastTextPreScanner already decodes for its
 * text-presence check -- near-zero extra decode cost since no new bitmap is loaded for this.
 * Best-effort: any failure just means no visual signal is available; PhotoCategoryClassifier
 * still works from OCR text alone either way.
 */
object ImageLabelingHelper {

    private const val TAG = "ImageLabelingHelper"
    private const val CONFIDENCE_THRESHOLD = 0.6f

    /** ML Kit's generic ~400-label set has no literal "Receipt" label, but these commonly fire
     * on photographed documents/receipts/forms and are a reasonable proxy signal. */
    private val documentLikeLabels = setOf("paper", "document", "text", "whiteboard", "font")

    private val labeler by lazy { ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS) }

    suspend fun isLikelyDocument(bitmap: Bitmap): Boolean? {
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val labels = labeler.process(inputImage).await()
            labels.any { it.confidence >= CONFIDENCE_THRESHOLD && it.text.lowercase() in documentLikeLabels }
        } catch (e: Exception) {
            Log.w(TAG, "Image labeling failed: ${e.message}")
            null
        }
    }
}
