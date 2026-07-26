package com.peeyupatel.phototextsearch.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.peeyupatel.phototextsearch.database.entities.CuratedAlbumEntity
import com.peeyupatel.phototextsearch.database.entities.CuratedAlbumPhotoEntity

@Dao
interface CuratedAlbumDao {

    @Insert
    suspend fun insertAlbum(album: CuratedAlbumEntity): Long

    @Query("SELECT * FROM curated_album ORDER BY created_at DESC")
    suspend fun getAllAlbums(): List<CuratedAlbumEntity>

    @Query("SELECT * FROM curated_album WHERE id = :albumId")
    suspend fun getAlbum(albumId: Long): CuratedAlbumEntity?

    @Query("UPDATE curated_album SET name = :name WHERE id = :albumId")
    suspend fun renameAlbum(albumId: Long, name: String)

    @Query("UPDATE curated_album SET cover_media_id = :mediaId WHERE id = :albumId")
    suspend fun setCover(albumId: Long, mediaId: Long?)

    @Query("DELETE FROM curated_album WHERE id = :albumId")
    suspend fun deleteAlbum(albumId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addPhotos(photos: List<CuratedAlbumPhotoEntity>)

    @Query("DELETE FROM curated_album_photo WHERE album_id = :albumId AND media_id IN (:mediaIds)")
    suspend fun removePhotos(albumId: Long, mediaIds: List<Long>)

    @Query("SELECT media_id FROM curated_album_photo WHERE album_id = :albumId ORDER BY added_at DESC")
    suspend fun getMediaIds(albumId: Long): List<Long>

    @Query("SELECT COUNT(*) FROM curated_album_photo WHERE album_id = :albumId")
    suspend fun getPhotoCount(albumId: Long): Int

    @Query("SELECT album_id FROM curated_album_photo WHERE media_id = :mediaId")
    suspend fun getAlbumIdsForMedia(mediaId: Long): List<Long>
}
