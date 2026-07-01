package com.musicmood.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ArtworkCacheDao {

    @Query("SELECT artworkUri FROM artwork_cache WHERE path = :path LIMIT 1")
    suspend fun getArtwork(path: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ArtworkCacheEntity)
}
