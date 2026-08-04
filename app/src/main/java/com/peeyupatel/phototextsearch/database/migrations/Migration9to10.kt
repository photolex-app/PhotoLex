package com.peeyupatel.phototextsearch.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 9 to 10: add FTS4 full-text search over devanagari_ocr_text.
 *
 * Same pattern as Migration8to9 (which did this for the Latin ocr_text table) -- Devanagari
 * search was left on the LIKE-scan path the whole time since no equivalent virtual table
 * existed. The DDL and content-sync triggers below mirror Room's generated schema for
 * OcrTextFtsEntity exactly, just renamed for devanagari_ocr_text/devanagari_ocr_text_fts, since
 * Room only emits this SQL automatically on a fresh install -- a migration from an existing
 * version 9 database has to issue it by hand and must match exactly or Room's schema validation
 * fails on next open. Non-destructive: 'rebuild' populates the new FTS index from the existing
 * devanagari_ocr_text rows without touching or losing any previously-extracted OCR text.
 */
val Migration9to10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        android.util.Log.d("Migration9to10", "🔄 === STARTING MIGRATION 9 TO 10 (Devanagari FTS4) ===")

        try {
            database.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS `devanagari_ocr_text_fts` USING FTS4(`extracted_text` TEXT NOT NULL, content=`devanagari_ocr_text`)"
            )

            database.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_devanagari_ocr_text_fts_BEFORE_UPDATE BEFORE UPDATE ON `devanagari_ocr_text` BEGIN DELETE FROM `devanagari_ocr_text_fts` WHERE `docid`=OLD.`rowid`; END"
            )
            database.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_devanagari_ocr_text_fts_BEFORE_DELETE BEFORE DELETE ON `devanagari_ocr_text` BEGIN DELETE FROM `devanagari_ocr_text_fts` WHERE `docid`=OLD.`rowid`; END"
            )
            database.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_devanagari_ocr_text_fts_AFTER_UPDATE AFTER UPDATE ON `devanagari_ocr_text` BEGIN INSERT INTO `devanagari_ocr_text_fts`(`docid`, `extracted_text`) VALUES (NEW.`rowid`, NEW.`extracted_text`); END"
            )
            database.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_devanagari_ocr_text_fts_AFTER_INSERT AFTER INSERT ON `devanagari_ocr_text` BEGIN INSERT INTO `devanagari_ocr_text_fts`(`docid`, `extracted_text`) VALUES (NEW.`rowid`, NEW.`extracted_text`); END"
            )

            // Backfill the FTS index from every existing devanagari_ocr_text row -- existing
            // OCR data in devanagari_ocr_text itself is untouched by any of this.
            database.execSQL("INSERT INTO devanagari_ocr_text_fts(devanagari_ocr_text_fts) VALUES('rebuild')")

            android.util.Log.d("Migration9to10", "✅ === MIGRATION 9 TO 10 COMPLETED SUCCESSFULLY ===")
        } catch (e: Exception) {
            android.util.Log.e("Migration9to10", "❌ Migration 9 to 10 failed", e)
            throw e
        }
    }
}
