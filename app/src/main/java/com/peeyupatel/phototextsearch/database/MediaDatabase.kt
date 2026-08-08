package com.peeyupatel.phototextsearch.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.peeyupatel.phototextsearch.database.daos.DevanagariOcrProgressDao
import com.peeyupatel.phototextsearch.database.daos.DevanagariOcrTextDao
import com.peeyupatel.phototextsearch.database.daos.FavouritedItemEntityDao
import com.peeyupatel.phototextsearch.database.daos.MediaEntityDao
import com.peeyupatel.phototextsearch.database.daos.OcrProgressDao
import com.peeyupatel.phototextsearch.database.daos.OcrTextDao
import com.peeyupatel.phototextsearch.database.daos.SearchHistoryDao
import com.peeyupatel.phototextsearch.database.daos.SecuredMediaItemEntityDao
import com.peeyupatel.phototextsearch.database.daos.TrashedItemEntityDao
import com.peeyupatel.phototextsearch.database.entities.DevanagariOcrProgressEntity
import com.peeyupatel.phototextsearch.database.entities.DevanagariOcrTextEntity
import com.peeyupatel.phototextsearch.database.entities.DevanagariOcrTextFtsEntity
import com.peeyupatel.phototextsearch.database.entities.FavouritedItemEntity
import com.peeyupatel.phototextsearch.database.entities.MediaEntity
import com.peeyupatel.phototextsearch.database.entities.OcrProgressEntity
import com.peeyupatel.phototextsearch.database.entities.OcrTextEntity
import com.peeyupatel.phototextsearch.database.entities.OcrTextFtsEntity
import com.peeyupatel.phototextsearch.database.entities.SearchHistoryEntity
import com.peeyupatel.phototextsearch.database.entities.SecuredItemEntity
import com.peeyupatel.phototextsearch.database.entities.TrashedItemEntity
import com.peeyupatel.phototextsearch.database.migrations.Migration7to8
import com.peeyupatel.phototextsearch.database.migrations.Migration8to9
import com.peeyupatel.phototextsearch.database.migrations.Migration9to10

@Database(entities =
    [
        MediaEntity::class,
        TrashedItemEntity::class,
        FavouritedItemEntity::class,
        SecuredItemEntity::class,
        OcrTextEntity::class,
        OcrProgressEntity::class,
        DevanagariOcrTextEntity::class,
        DevanagariOcrProgressEntity::class,
        OcrTextFtsEntity::class,
        DevanagariOcrTextFtsEntity::class,
        SearchHistoryEntity::class
    ],
    version = 10, // Updated to version 10 to add FTS4 full-text search for Devanagari OCR text
    autoMigrations = [
        AutoMigration(from = 2, to = 3),
        // AutoMigration(from = 4, to = 5) - Manual migration needed
        // AutoMigration(from = 5, to = 6) - Manual migration needed for FTS
        // AutoMigration(from = 7, to = 8) - Manual migration needed for Devanagari OCR
        // AutoMigration(from = 8, to = 9) - Manual migration needed for FTS4 re-enable
        // AutoMigration(from = 9, to = 10) - Manual migration needed for Devanagari FTS4
    ]
)
abstract class MediaDatabase : RoomDatabase() {
    abstract fun mediaEntityDao(): MediaEntityDao
    abstract fun trashedItemEntityDao(): TrashedItemEntityDao
    abstract fun favouritedItemEntityDao(): FavouritedItemEntityDao
    abstract fun securedItemEntityDao(): SecuredMediaItemEntityDao
    abstract fun ocrTextDao(): OcrTextDao
    abstract fun ocrProgressDao(): OcrProgressDao
    abstract fun devanagariOcrTextDao(): DevanagariOcrTextDao
    abstract fun devanagariOcrProgressDao(): DevanagariOcrProgressDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        @Volatile
        private var instance: MediaDatabase? = null

        fun getInstance(context: Context): MediaDatabase {
            return instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }
        }

        /**
         * Closes the current singleton connection and clears it, so the next getInstance() call
         * builds a fresh one. Needed before swapping the underlying .db file out from under Room
         * (index restore) -- Room/SQLite must not have this database open while its file on disk
         * is replaced.
         */
        fun closeAndReset() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }

        private fun build(appContext: Context): MediaDatabase {
            return Room.databaseBuilder(
                appContext,
                MediaDatabase::class.java,
                "media-database"
            )
                .addMigrations(
                    Migration3to4(appContext),
                    Migration4to5(appContext),
                    Migration5to6(appContext),
                    Migration6to7(appContext),
                    Migration7to8,
                    Migration8to9,
                    Migration9to10
                )
                .enableMultiInstanceInvalidation()
                .build()
        }
    }
}


