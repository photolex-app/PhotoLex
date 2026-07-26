package com.peeyupatel.phototextsearch.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Tag/join row: "this media ID is a member of this curated album". Deliberately just an ID
 * pair, no photo data duplicated here -- the real photo stays wherever MediaStore has it,
 * this table only records membership so a photo can be tagged into any number of albums.
 */
@Entity(
    tableName = "curated_album_photo",
    primaryKeys = ["album_id", "media_id"],
    indices = [
        Index(value = ["album_id"]),
        Index(value = ["media_id"])
    ]
)
data class CuratedAlbumPhotoEntity(
    @ColumnInfo(name = "album_id")
    val albumId: Long,

    @ColumnInfo(name = "media_id")
    val mediaId: Long,

    @ColumnInfo(name = "added_at")
    val addedAt: Long
)
