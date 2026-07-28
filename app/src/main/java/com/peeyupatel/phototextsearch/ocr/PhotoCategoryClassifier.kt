package com.peeyupatel.phototextsearch.ocr

import com.peeyupatel.phototextsearch.database.entities.PhotoClassificationEntity

/**
 * Simple keyword/pattern based category classifier -- deliberately NOT a separate ML model.
 * Runs on text that's already been extracted by full OCR (this is a zero-extra-cost pass,
 * just pattern matching on a string), bucketing photos into rough categories for Smart Albums.
 */
object PhotoCategoryClassifier {

    private val idCardKeywords = listOf(
        "government of india", "date of birth", "\\bdob\\b", "permanent account number",
        "election commission", "unique identification", "\\bvid\\b", "epic no"
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val receiptKeywords = listOf(
        "\\btotal\\b", "\\binvoice\\b", "\\bamount\\b", "gst", "₹", "rs\\.", "subtotal",
        "\\btax\\b", "receipt no", "bill no", "quantity", "\\bqty\\b"
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    /**
     * @param imageWidth/imageHeight optional pixel dimensions, used as a secondary signal for
     * screenshots (which commonly match a device's exact screen resolution) -- pass 0/0 if
     * unknown, the classifier still works from text content alone.
     * @param entities optional ML Kit entity-extraction result over the same text -- a detected
     * money amount or phone number counts as an extra receipt/ID-card signal alongside the
     * keyword matches, catching receipts whose OCR text shows an amount (e.g. "$45.00") without
     * ever containing an explicit word like "total"/"invoice"/"gst". Purely additive: passing
     * null (or an all-false result) keeps behavior identical to the pure keyword classifier.
     * @param visuallyLikelyDocument optional ML Kit Image Labeling signal from the same photo's
     * pre-scan bitmap ("Paper"/"Document"/"Text"/"Whiteboard" labels) -- lowers the plain-text-
     * length bar for the DOCUMENT category when the photo also visually looks like a document,
     * catching shorter documents that the text-length-alone heuristic would otherwise miss.
     */
    fun classify(
        extractedText: String,
        imageWidth: Int = 0,
        imageHeight: Int = 0,
        entities: EntityExtractionHelper.ExtractedEntities? = null,
        visuallyLikelyDocument: Boolean? = null
    ): String? {
        if (extractedText.isBlank()) return null

        val idCardMatches = idCardKeywords.count { it.containsMatchIn(extractedText) } +
            (if (entities?.hasAddress == true) 1 else 0)
        val receiptMatches = receiptKeywords.count { it.containsMatchIn(extractedText) } +
            (if (entities?.hasMoney == true) 1 else 0)
        val trimmedLength = extractedText.trim().length
        val documentLengthThreshold = if (visuallyLikelyDocument == true) 40 else 80

        return when {
            idCardMatches >= 2 -> PhotoClassificationEntity.CATEGORY_ID_CARD
            receiptMatches >= 2 -> PhotoClassificationEntity.CATEGORY_RECEIPT
            isLikelyScreenshotDimensions(imageWidth, imageHeight) -> PhotoClassificationEntity.CATEGORY_SCREENSHOT
            trimmedLength >= documentLengthThreshold -> PhotoClassificationEntity.CATEGORY_DOCUMENT
            else -> null
        }
    }

    /** Common phone/tablet screen aspect ratios tend to be tall and narrow (or the exact
     * inverse in landscape) -- a rough heuristic, not a precise device-resolution database. */
    private fun isLikelyScreenshotDimensions(width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        val longer = maxOf(width, height).toFloat()
        val shorter = minOf(width, height).toFloat()
        val ratio = longer / shorter
        return ratio in 1.6f..2.5f && longer >= 1280
    }

    fun categoryDisplayName(category: String): String = when (category) {
        PhotoClassificationEntity.CATEGORY_RECEIPT -> "Bills & Receipts"
        PhotoClassificationEntity.CATEGORY_ID_CARD -> "ID Cards"
        PhotoClassificationEntity.CATEGORY_SCREENSHOT -> "Screenshots"
        PhotoClassificationEntity.CATEGORY_DOCUMENT -> "Documents"
        else -> category.replaceFirstChar { it.uppercase() }
    }
}
