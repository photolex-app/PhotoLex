package com.peeyupatel.phototextsearch.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.peeyupatel.phototextsearch.database.daos.BarcodeDao
import com.peeyupatel.phototextsearch.database.daos.CuratedAlbumDao
import com.peeyupatel.phototextsearch.database.daos.PhotoClassificationDao
import com.peeyupatel.phototextsearch.database.entities.BarcodeEntity
import com.peeyupatel.phototextsearch.database.entities.CuratedAlbumEntity
import com.peeyupatel.phototextsearch.database.entities.CuratedAlbumPhotoEntity
import com.peeyupatel.phototextsearch.database.entities.PhotoClassificationEntity

/**
 * Independent Room database (separate .db file from MediaDatabase) holding fast text-presence
 * pre-scan results, category classifications, detected barcodes, and user-created curated
 * albums. Kept separate deliberately: it needs no migration of the existing OCR/progress data
 * in MediaDatabase.
 */
@Database(
    entities = [
        PhotoClassificationEntity::class,
        CuratedAlbumEntity::class,
        CuratedAlbumPhotoEntity::class,
        BarcodeEntity::class
    ],
    // NOTE: IndexBackupHelper.CLASSIFICATION_DB_SCHEMA_VERSION must be bumped to match whenever
    // this changes, or every backup made by this app version gets wrongly rejected as
    // "incompatible" when restored, even on the exact same app build.
    version = 6
)
abstract class ClassificationDatabase : RoomDatabase() {
    abstract fun photoClassificationDao(): PhotoClassificationDao
    abstract fun curatedAlbumDao(): CuratedAlbumDao
    abstract fun barcodeDao(): BarcodeDao

    companion object {
        @Volatile
        private var instance: ClassificationDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `curated_album` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `cover_media_id` INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `curated_album_photo` (
                        `album_id` INTEGER NOT NULL,
                        `media_id` INTEGER NOT NULL,
                        `added_at` INTEGER NOT NULL,
                        PRIMARY KEY(`album_id`, `media_id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_curated_album_photo_album_id` ON `curated_album_photo` (`album_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_curated_album_photo_media_id` ON `curated_album_photo` (`media_id`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `photo_classification` ADD COLUMN `d_hash` INTEGER")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `photo_classification` ADD COLUMN `is_likely_document_visually` INTEGER")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `photo_barcode` (
                        `media_id` INTEGER PRIMARY KEY NOT NULL,
                        `barcode_text` TEXT NOT NULL,
                        `format` TEXT,
                        `scanned_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_barcode_barcode_text` ON `photo_barcode` (`barcode_text`)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Speeds up getRecentWithHash()'s "WHERE d_hash IS NOT NULL ORDER BY
                // pre_scanned_at DESC" (run once per OCR batch) -- additive only, no data change.
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_classification_d_hash_pre_scanned_at` ON `photo_classification` (`d_hash`, `pre_scanned_at`)")
            }
        }

        fun getInstance(context: Context): ClassificationDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ClassificationDatabase::class.java,
                    "photo-classification-database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build().also { instance = it }
            }
        }

        /**
         * Closes the current singleton connection and clears it, so the next getInstance() call
         * builds a fresh one. See MediaDatabase.closeAndReset() for why this is needed before an
         * index restore replaces the underlying .db file.
         */
        fun closeAndReset() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }
    }
}
