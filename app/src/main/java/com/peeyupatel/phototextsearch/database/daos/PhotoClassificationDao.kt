package com.peeyupatel.phototextsearch.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.peeyupatel.phototextsearch.database.entities.PhotoClassificationEntity

@Dao
interface PhotoClassificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PhotoClassificationEntity)

    @Query("SELECT * FROM photo_classification WHERE media_id = :mediaId")
    suspend fun getByMediaId(mediaId: Long): PhotoClassificationEntity?

    /** Batch lookup, used to sort a candidate pool of media IDs by has-text priority. */
    @Query("SELECT * FROM photo_classification WHERE media_id IN (:mediaIds)")
    suspend fun getByMediaIds(mediaIds: List<Long>): List<PhotoClassificationEntity>

    @Query("SELECT media_id FROM photo_classification WHERE category = :category ORDER BY categorized_at DESC")
    suspend fun getMediaIdsByCategory(category: String): List<Long>

    @Query("SELECT DISTINCT category FROM photo_classification WHERE category IS NOT NULL")
    suspend fun getDistinctCategories(): List<String>

    @Query("SELECT COUNT(*) FROM photo_classification WHERE category = :category")
    suspend fun getCategoryCount(category: String): Int
}
