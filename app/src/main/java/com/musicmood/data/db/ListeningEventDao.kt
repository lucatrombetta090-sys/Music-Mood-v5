package com.musicmood.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ListeningEventDao {

    @Insert
    suspend fun insert(event: ListeningEventEntity)

    @Query("""
        SELECT mood, COUNT(*) as cnt
        FROM listening_event
        WHERE played_at >= :fromMs AND played_at < :toMs
        GROUP BY mood
        ORDER BY cnt DESC
    """)
    suspend fun moodCountsInRange(fromMs: Long, toMs: Long): List<MoodCount>

    @Query("""
        SELECT COUNT(*)
        FROM listening_event
        WHERE played_at >= :fromMs AND played_at < :toMs
    """)
    suspend fun totalInRange(fromMs: Long, toMs: Long): Int

    @Query("DELETE FROM listening_event WHERE played_at < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long)

    data class MoodCount(val mood: String, val cnt: Int)
}
