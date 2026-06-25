package com.musicmood.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CalibrationDao {
    @Query("SELECT * FROM calibration WHERE id = 1 LIMIT 1")
    suspend fun get(): CalibrationEntity?

    @Query("SELECT * FROM calibration WHERE id = 1 LIMIT 1")
    fun observe(): Flow<CalibrationEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: CalibrationEntity)

    @Query("DELETE FROM calibration")
    suspend fun clear()
}
