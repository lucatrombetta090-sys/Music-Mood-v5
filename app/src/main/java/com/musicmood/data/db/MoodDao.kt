package com.musicmood.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodDao {

    @Query("SELECT * FROM mood_analysis")
    fun observeAll(): Flow<List<MoodEntity>>

    @Query("SELECT * FROM mood_analysis WHERE song_id = :songId LIMIT 1")
    suspend fun findById(songId: Long): MoodEntity?

    @Query("SELECT song_id FROM mood_analysis")
    suspend fun getAnalyzedIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MoodEntity)

    @Query("DELETE FROM mood_analysis WHERE song_id = :songId")
    suspend fun delete(songId: Long)

    @Query("DELETE FROM mood_analysis")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM mood_analysis")
    fun observeCount(): Flow<Int>
}
