package com.musicmood.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Report settimanale generato automaticamente.
 * I "moodsJson" è un JSON serializzato del tipo {"Energico":42,"Positivo":28,...}.
 */
@Entity(tableName = "weekly_report")
data class WeeklyReportEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "year")
    val year: Int,

    @ColumnInfo(name = "week")
    val week: Int,

    @ColumnInfo(name = "from_ms")
    val fromMs: Long,

    @ColumnInfo(name = "to_ms")
    val toMs: Long,

    @ColumnInfo(name = "total_plays")
    val totalPlays: Int,

    @ColumnInfo(name = "dominant_mood")
    val dominantMood: String,

    @ColumnInfo(name = "moods_json")
    val moodsJson: String,

    @ColumnInfo(name = "generated_at")
    val generatedAt: Long = System.currentTimeMillis(),
)
