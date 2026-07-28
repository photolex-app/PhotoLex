package com.peeyupatel.phototextsearch.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 8 to 9: re-enable FTS4 full-text search over ocr_text.
 *
 * The virtual table DDL and content-sync triggers below are copied verbatim from Room's
 * generated schema (app/schemas/.../9.json) for OcrTextFtsEntity, since Room only emits this
 * SQL automatically on a fresh install -- a migration from an existing version 8 database has
 * to issue it by hand and must match exactly or Room's schema validation fails on next open.
 * Non-destructive: 'rebuild' populates the new FTS index from the existing ocr_text rows
 * without touching or losing any previously-extracted OCR text.
 */
val Migration8to9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        android.util.Log.d("Migration8to9", "🔄 === STARTING MIGRATION 8 TO 9 (FTS4) ===")

        try {
            database.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS `ocr_text_fts` USING FTS4(`extracted_text` TEXT NOT NULL, content=`ocr_text`)"
            )

            database.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_ocr_text_fts_BEFORE_UPDATE BEFORE UPDATE ON `ocr_text` BEGIN DELETE FROM `ocr_text_fts` WHERE `docid`=OLD.`rowid`; END"
            )
            database.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_ocr_text_fts_BEFORE_DELETE BEFORE DELETE ON `ocr_text` BEGIN DELETE FROM `ocr_text_fts` WHERE `docid`=OLD.`rowid`; END"
            )
            database.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_ocr_text_fts_AFTER_UPDATE AFTER UPDATE ON `ocr_text` BEGIN INSERT INTO `ocr_text_fts`(`docid`, `extracted_text`) VALUES (NEW.`rowid`, NEW.`extracted_text`); END"
            )
            database.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_ocr_text_fts_AFTER_INSERT AFTER INSERT ON `ocr_text` BEGIN INSERT INTO `ocr_text_fts`(`docid`, `extracted_text`) VALUES (NEW.`rowid`, NEW.`extracted_text`); END"
            )

            // Backfill the FTS index from every existing ocr_text row -- existing OCR data
            // in ocr_text itself is untouched by any of this.
            database.execSQL("INSERT INTO ocr_text_fts(ocr_text_fts) VALUES('rebuild')")

            android.util.Log.d("Migration8to9", "✅ === MIGRATION 8 TO 9 COMPLETED SUCCESSFULLY ===")
        } catch (e: Exception) {
            android.util.Log.e("Migration8to9", "❌ Migration 8 to 9 failed", e)
            throw e
        }
    }
}
