package com.musicmood.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Evento di ascolto di un brano: serve per costruire il Weekly Report.
 */
@Entity(tableName = "listening_event")
data class ListeningEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "song_id")
    val songId: Long,

    @ColumnInfo(name = "mood")
    val mood: String,

    @ColumnInfo(name = "played_at")
    val playedAt: Long = System.currentTimeMillis(),
)
