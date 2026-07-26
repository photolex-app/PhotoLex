package com.peeyupatel.phototextsearch.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-created album grouping individual photos by tag rather than by folder path (see the
 * separate, pre-existing folder-combining "Custom Album" feature -- this is intentionally a
 * different concept, named "Curated" to avoid confusion with it). Membership is tracked
 * separately in CuratedAlbumPhotoEntity (a tag/join table), so photos are never copied or
 * moved: a photo can belong to any number of curated albums while still living in its one real
 * MediaStore location.
 */
@Entity(tableName = "curated_album")
data class CuratedAlbumEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "cover_media_id")
    val coverMediaId: Long? = null
)
