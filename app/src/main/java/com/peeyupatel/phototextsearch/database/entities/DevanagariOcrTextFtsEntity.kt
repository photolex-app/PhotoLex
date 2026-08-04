package com.peeyupatel.phototextsearch.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

/**
 * FTS4 virtual table for fast full-text search of Devanagari OCR extracted text -- same pattern
 * as OcrTextFtsEntity for the Latin pipeline, just over devanagari_ocr_text. Devanagari search
 * was permanently on the LIKE-scan path until this existed.
 */
@Entity(tableName = "devanagari_ocr_text_fts")
@Fts4(contentEntity = DevanagariOcrTextEntity::class)
data class DevanagariOcrTextFtsEntity(
    @ColumnInfo(name = "extracted_text")
    val extractedText: String
)
