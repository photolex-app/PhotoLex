package com.peeyupatel.phototextsearch.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Fast pre-scan + category classification result for a single photo, used to prioritize
 * OCR indexing order (text-containing photos first) and to power Smart Albums browsing.
 * Deliberately kept in its own separate Room database (see ClassificationDatabase) rather
 * than added to MediaDatabase, so it needs no schema migration of the existing, precious
 * OCR/progress data -- this table simply doesn't exist until first accessed.
 */
@Entity(
    tableName = "photo_classification",
    indices = [
        Index(value = ["has_text"]),
        Index(value = ["category"])
    ]
)
data class PhotoClassificationEntity(
    @PrimaryKey
    @ColumnInfo(name = "media_id")
    val mediaId: Long,

    @ColumnInfo(name = "has_text")
    val hasText: Boolean,

    @ColumnInfo(name = "pre_scanned_at")
    val preScannedAt: Long,

    @ColumnInfo(name = "category")
    val category: String? = null,

    @ColumnInfo(name = "categorized_at")
    val categorizedAt: Long? = null,

    /** Difference-hash (dHash) fingerprint of the pre-scan's downsampled bitmap, used to
     * detect near-identical burst/duplicate photos so full OCR can be skipped and the
     * result copied from an already-processed neighbor instead. Null for rows scanned
     * before this field existed. */
    @ColumnInfo(name = "d_hash")
    val dHash: Long? = null,

    /** ML Kit Image Labeling's "does this look like a document" signal from the same
     * pre-scan bitmap, used as a secondary classification signal alongside OCR text.
     * Null for rows scanned before this field existed, or if labeling failed/unavailable. */
    @ColumnInfo(name = "is_likely_document_visually")
    val isLikelyDocumentVisually: Boolean? = null
) {
    companion object {
        const val CATEGORY_RECEIPT = "receipt"
        const val CATEGORY_ID_CARD = "id_card"
        const val CATEGORY_SCREENSHOT = "screenshot"
        const val CATEGORY_DOCUMENT = "document"
    }
}
