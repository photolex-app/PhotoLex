package com.peeyupatel.phototextsearch.database

import androidx.room.AutoMigration
import androidx.room.Database
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
import com.peeyupatel.phototextsearch.database.entities.FavouritedItemEntity
import com.peeyupatel.phototextsearch.database.entities.MediaEntity
import com.peeyupatel.phototextsearch.database.entities.OcrProgressEntity
import com.peeyupatel.phototextsearch.database.entities.OcrTextEntity
import com.peeyupatel.phototextsearch.database.entities.OcrTextFtsEntity
import com.peeyupatel.phototextsearch.database.entities.SearchHistoryEntity
import com.peeyupatel.phototextsearch.database.entities.SecuredItemEntity
import com.peeyupatel.phototextsearch.database.entities.TrashedItemEntity
import com.peeyupatel.phototextsearch.database.migrations.Migration7to8

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
        // OcrTextFtsEntity::class, // Temporarily disabled for build compatibility
        SearchHistoryEntity::class
    ],
    version = 8, // Updated to version 8 for Devanagari OCR
    autoMigrations = [
        AutoMigration(from = 2, to = 3),
        // AutoMigration(from = 4, to = 5) - Manual migration needed
        // AutoMigration(from = 5, to = 6) - Manual migration needed for FTS
        // AutoMigration(from = 7, to = 8) - Manual migration needed for Devanagari OCR
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
}


