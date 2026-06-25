package com.musicmood.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Calibrazione personalizzata del classificatore mood, calcolata dalla libreria
 * dell'utente. Singleton: c'è una sola riga (id=1).
 */
@Entity(tableName = "calibration")
data class CalibrationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 1,

    @ColumnInfo(name = "avg_valence")
    val avgValence: Double,

    @ColumnInfo(name = "avg_arousal")
    val avgArousal: Double,

    @ColumnInfo(name = "avg_tempo")
    val avgTempo: Double,

    @ColumnInfo(name = "songs_used")
    val songsUsed: Int,

    @ColumnInfo(name = "calibrated_at")
    val calibratedAt: Long = System.currentTimeMillis(),
)
