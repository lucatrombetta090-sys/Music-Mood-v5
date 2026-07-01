package com.musicmood.data

import android.content.Context
import com.musicmood.MoodAnalysis
import com.musicmood.data.db.AppDatabase
import com.musicmood.data.db.MoodEntity
import kotlinx.coroutines.flow.Flow

class MoodRepository private constructor(context: Context) {

    private val dao = AppDatabase.get(context).moodDao()

    fun observeAll(): Flow<List<MoodEntity>> = dao.observeAll()
    fun observeCount(): Flow<Int> = dao.observeCount()
    fun observeUserMoodCount(): Flow<Int> = dao.observeUserMoodCount()

    suspend fun findById(songId: Long): MoodEntity? = dao.findById(songId)
    suspend fun getAnalyzedIds(): Set<Long> = dao.getAnalyzedIds().toSet()

    suspend fun save(songId: Long, a: MoodAnalysis) {
        // Preserva l'eventuale userMood se già presente
        val existing = dao.findById(songId)
        dao.upsert(
            MoodEntity(
                songId      = songId,
                mood        = a.mood,
                userMood    = existing?.userMood,
                confidence  = a.confidence,
                valence     = a.valence,
                arousal     = a.arousal,
                tempoBpm    = a.tempoBpm,
                musicKey    = a.key,
                mode        = a.mode,
            )
        )
    }

    suspend fun setUserMood(songId: Long, userMood: String): Boolean {
        return dao.setUserMood(songId, userMood) > 0
    }

    suspend fun clearUserMood(songId: Long): Boolean {
        return dao.clearUserMood(songId) > 0
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

/** Estensione: applica dati Room a un Song, inclusi mood DSP + override utente. */
fun Song.withMood(entity: MoodEntity?): Song =
    if (entity == null) this
    else copy(
        mood       = entity.mood,
        userMood   = entity.userMood,
        confidence = entity.confidence,
        valence    = entity.valence,
        arousal    = entity.arousal,
        tempoBpm   = entity.tempoBpm,
    )
