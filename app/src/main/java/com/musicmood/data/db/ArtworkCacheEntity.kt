package com.musicmood.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "artwork_cache")
data class ArtworkCacheEntity(
    @PrimaryKey val path: String,
    val artworkUri: String
)
