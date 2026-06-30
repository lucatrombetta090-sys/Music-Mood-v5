package com.musicmood.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ArtworkCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ArtworkCacheEntity)

    @Query("SELECT * FROM artwork_cache WHERE song_id = :songId LIMIT 1")
    suspend fun get(songId: Long): ArtworkCacheEntity?

    @Query("SELECT * FROM artwork_cache WHERE song_id IN (:ids)")
    suspend fun getMany(ids: List<Long>): List<ArtworkCacheEntity>

    @Query("DELETE FROM artwork_cache")
    suspend fun clearAll()
}
