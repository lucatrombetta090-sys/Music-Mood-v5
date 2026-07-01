package com.musicmood.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "artwork_cache")
data class ArtworkCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "song_id")
    val songId: Long,

    @ColumnInfo(name = "artwork_url")
    val artworkUrl: String?,   // null = "cercato e non trovato"

    @ColumnInfo(name = "source")
    val source: String,        // "itunes" | "deezer" | "miss"

    @ColumnInfo(name = "fetched_at")
    val fetchedAt: Long = System.currentTimeMillis(),
)
