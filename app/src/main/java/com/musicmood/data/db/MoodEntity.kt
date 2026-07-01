package com.musicmood.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mood_analysis")
data class MoodEntity(
    @PrimaryKey
    @ColumnInfo(name = "song_id")
    val songId: Long,

    @ColumnInfo(name = "mood")
    val mood: String,

    @ColumnInfo(name = "user_mood")
    val userMood: String? = null,

    @ColumnInfo(name = "confidence")
    val confidence: Double,

    @ColumnInfo(name = "valence")
    val valence: Double,

    @ColumnInfo(name = "arousal")
    val arousal: Double,

    @ColumnInfo(name = "tempo_bpm")
    val tempoBpm: Double,

    @ColumnInfo(name = "music_key")
    val musicKey: String,

    @ColumnInfo(name = "mode")
    val mode: String,

    @ColumnInfo(name = "analyzed_at")
    val analyzedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "analyzer_version")
    val analyzerVersion: String = "v5",
)
