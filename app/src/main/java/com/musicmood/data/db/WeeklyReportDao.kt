package com.musicmood.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklyReportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(report: WeeklyReportEntity)

    @Query("SELECT * FROM weekly_report ORDER BY year DESC, week DESC LIMIT 1")
    suspend fun latest(): WeeklyReportEntity?

    @Query("SELECT * FROM weekly_report ORDER BY year DESC, week DESC LIMIT 1")
    fun observeLatest(): Flow<WeeklyReportEntity?>

    @Query("SELECT * FROM weekly_report ORDER BY year DESC, week DESC LIMIT :limit")
    suspend fun recent(limit: Int = 12): List<WeeklyReportEntity>

    @Query("SELECT * FROM weekly_report WHERE year = :year AND week = :week LIMIT 1")
    suspend fun forWeek(year: Int, week: Int): WeeklyReportEntity?
}
