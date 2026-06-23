package com.musicmood.data

import android.content.Context
import com.musicmood.MoodAnalysis
import com.musicmood.data.db.AppDatabase
import com.musicmood.data.db.MoodEntity
import kotlinx.coroutines.flow.Flow

/**
 * Single-source-of-truth per i risultati di analisi mood.
 * Espone l'API ad alto livello usata da ViewModel e Worker.
 */
class MoodRepository private constructor(context: Context) {

    private val dao = AppDatabase.get(context).moodDao()

    fun observeAll(): Flow<List<MoodEntity>> = dao.observeAll()
    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun findById(songId: Long): MoodEntity? = dao.findById(songId)

    suspend fun getAnalyzedIds(): Set<Long> = dao.getAnalyzedIds().toSet()

    suspend fun save(songId: Long, a: MoodAnalysis) {
        dao.upsert(
            MoodEntity(
                songId      = songId,
                mood        = a.mood,
                confidence  = a.confidence,
                valence     = a.valence,
                arousal     = a.arousal,
                tempoBpm    = a.tempoBpm,
                musicKey    = a.key,
                mode        = a.mode,
            )
        )
    }

    suspend fun clearAll() = dao.clearAll()

    companion object {
        @Volatile private var INSTANCE: MoodRepository? = null
        fun get(context: Context): MoodRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: MoodRepository(context).also { INSTANCE = it }
            }
    }
}

/** Estensione di comodo: applica i dati Room a un Song. */
fun Song.withMood(entity: MoodEntity?): Song =
    if (entity == null) this
    else copy(
        mood       = entity.mood,
        confidence = entity.confidence,
        valence    = entity.valence,
        arousal    = entity.arousal,
        tempoBpm   = entity.tempoBpm,
    )
