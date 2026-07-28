package com.peeyupatel.phototextsearch.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Barcode/QR payload detected during the pre-scan pass, kept in its own small table (like
 * PhotoClassificationEntity) rather than in MediaDatabase -- one photo can only have one row
 * here (the first/most prominent barcode found), used purely as an extra searchable text
 * dimension alongside OCR'd text (receipts, tickets, boarding passes commonly carry a barcode).
 */
@Entity(
    tableName = "photo_barcode",
    indices = [Index(value = ["barcode_text"])]
)
data class BarcodeEntity(
    @PrimaryKey
    @ColumnInfo(name = "media_id")
    val mediaId: Long,

    @ColumnInfo(name = "barcode_text")
    val barcodeText: String,

    @ColumnInfo(name = "format")
    val format: String? = null,

    @ColumnInfo(name = "scanned_at")
    val scannedAt: Long
)
