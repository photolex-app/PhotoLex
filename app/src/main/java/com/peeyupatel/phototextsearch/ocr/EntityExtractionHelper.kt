package com.peeyupatel.phototextsearch.ocr

import android.util.Log
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import kotlinx.coroutines.tasks.await

/**
 * Wraps ML Kit's on-device Entity Extraction API to pull structured entities (dates, money
 * amounts, phone numbers, addresses, emails, URLs) out of text that's already been OCR'd -- a
 * post-processing step over existing text, not a new image pass. Best-effort: the per-language
 * entity model needs a one-time network download, so if it isn't available yet this silently
 * returns an empty result rather than blocking or failing OCR indexing, same defensive pattern
 * as QueryTranslator's cross-language search.
 */
object EntityExtractionHelper {

    private const val TAG = "EntityExtractionHelper"

    private val extractor by lazy {
        EntityExtraction.getClient(
            EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
        )
    }

    data class ExtractedEntities(
        val hasMoney: Boolean = false,
        val hasDate: Boolean = false,
        val hasPhone: Boolean = false,
        val hasAddress: Boolean = false,
        val hasEmail: Boolean = false,
        val hasUrl: Boolean = false
    )

    suspend fun extract(text: String): ExtractedEntities {
        if (text.isBlank()) return ExtractedEntities()

        return try {
            extractor.downloadModelIfNeeded().await()

            val params = EntityExtractionParams.Builder(text).build()
            val annotations = extractor.annotate(params).await()

            var hasMoney = false
            var hasDate = false
            var hasPhone = false
            var hasAddress = false
            var hasEmail = false
            var hasUrl = false

            for (annotation in annotations) {
                for (entity in annotation.entities) {
                    when (entity.type) {
                        Entity.TYPE_MONEY -> hasMoney = true
                        Entity.TYPE_DATE_TIME -> hasDate = true
                        Entity.TYPE_PHONE -> hasPhone = true
                        Entity.TYPE_ADDRESS -> hasAddress = true
                        Entity.TYPE_EMAIL -> hasEmail = true
                        Entity.TYPE_URL -> hasUrl = true
                    }
                }
            }

            ExtractedEntities(hasMoney, hasDate, hasPhone, hasAddress, hasEmail, hasUrl)
        } catch (e: Exception) {
            Log.w(TAG, "Entity extraction unavailable/failed, continuing without it: ${e.message}")
            ExtractedEntities()
        }
    }
}
