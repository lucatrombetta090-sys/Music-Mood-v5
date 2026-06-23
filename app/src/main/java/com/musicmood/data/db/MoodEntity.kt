package com.musicmood.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mood_analysis")
data class MoodEntity(
    @PrimaryKey val songId: Long,
    val mood: String,
    val confidence: Double,
    val valence: Double,
    val arousal: Double,
    val tempoBpm: Double,
    val musicKey: String,
    val mode: String,
    val analyzedAt: Long = System.currentTimeMillis(),
    val analyzerVersion: String = "v5",
)
