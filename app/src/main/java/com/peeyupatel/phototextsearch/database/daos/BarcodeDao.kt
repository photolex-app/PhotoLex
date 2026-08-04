package com.peeyupatel.phototextsearch.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.peeyupatel.phototextsearch.database.entities.BarcodeEntity

@Dao
interface BarcodeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BarcodeEntity)

    @Query("SELECT * FROM photo_barcode WHERE media_id = :mediaId")
    suspend fun getByMediaId(mediaId: Long): BarcodeEntity?

    @Query("SELECT media_id FROM photo_barcode WHERE barcode_text LIKE '%' || :query || '%'")
    suspend fun searchMediaIds(query: String): List<Long>

    /** Cleans up barcode rows for photos that no longer exist in MediaStore -- see
     * MediaContentObserver's reconciliation pass. */
    @Query("DELETE FROM photo_barcode WHERE media_id IN (:mediaIds)")
    suspend fun deleteByMediaIds(mediaIds: List<Long>)
}
