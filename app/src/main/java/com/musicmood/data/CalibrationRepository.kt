package com.musicmood.data

import android.content.Context
import com.musicmood.data.db.AppDatabase
import com.musicmood.data.db.CalibrationEntity
import com.musicmood.data.db.MoodEntity
import kotlinx.coroutines.flow.Flow

/**
 * Ricalcola valenza/arousal/tempo medi sulla libreria già analizzata
 * e riclassifica i mood usando centroidi spostati di conseguenza.
 *
 * Logica:
 *   - I 9 centroidi originali sono fissi (vedi music_analyzer.py)
 *   - Calibrazione: shift = (avg_user - avg_global)
 *   - I nuovi centroidi diventano: original + shift
 *   - Riassegniamo il mood al nodo più vicino in (valenza, arousal, tempo_norm)
 */
class CalibrationRepository private constructor(context: Context) {

    private val db = AppDatabase.get(context)
    private val moodDao = db.moodDao()
    private val calibrationDao = db.calibrationDao()

    fun observe(): Flow<CalibrationEntity?> = calibrationDao.observe()

    suspend fun current(): CalibrationEntity? = calibrationDao.get()

    /**
     * Calibra e riclassifica tutta la libreria.
     * Restituisce il numero di brani riclassificati.
     */
    suspend fun calibrateAndReclassify(): CalibrationResult {
        val allMoods = moodDao.getAllEntities()
        if (allMoods.size < 50) {
            return CalibrationResult.NotEnoughData(allMoods.size)
        }

        // Media della libreria
        val avgValence = allMoods.map { it.valence }.average()
        val avgArousal = allMoods.map { it.arousal }.average()
        val avgTempo   = allMoods.map { it.tempoBpm }.average()

        // Shift rispetto al centroide "globale" (0,0,0.5 normalizzato)
        val shiftV = avgValence - GLOBAL_AVG_VALENCE
        val shiftA = avgArousal - GLOBAL_AVG_AROUSAL
        val shiftT = (tempoNorm(avgTempo) - GLOBAL_AVG_TEMPO_NORM)

        // Calcola nuovi centroidi
        val newCentroids = ORIGINAL_CENTROIDS.mapValues { (_, c) ->
            Triple(
                (c.first  + shiftV * SHIFT_FACTOR).coerceIn(-1.0, 1.0),
                (c.second + shiftA * SHIFT_FACTOR).coerceIn(-1.0, 1.0),
                (c.third  + shiftT * SHIFT_FACTOR).coerceIn(0.0, 1.0),
            )
        }

        // Riclassifica ogni brano
        val updated = allMoods.map { entity ->
            val tn = tempoNorm(entity.tempoBpm)
            val newMood = newCentroids.minByOrNull { (_, c) ->
                distance3D(entity.valence, entity.arousal, tn, c.first, c.second, c.third)
            }?.key ?: entity.mood
            entity.copy(mood = newMood)
        }

        // Aggiorna in batch
        updated.forEach { moodDao.upsert(it) }

        // Salva calibrazione
        val calibration = CalibrationEntity(
            avgValence = avgValence,
            avgArousal = avgArousal,
            avgTempo   = avgTempo,
            songsUsed  = allMoods.size,
        )
        calibrationDao.save(calibration)

        return CalibrationResult.Success(
            songsReclassified = updated.size,
            calibration       = calibration,
        )
    }

    /**
     * Ripristina la classificazione originale (centroidi non shiftati).
     */
    suspend fun resetCalibration(): Int {
        val allMoods = moodDao.getAllEntities()
        val updated = allMoods.map { entity ->
            val tn = tempoNorm(entity.tempoBpm)
            val newMood = ORIGINAL_CENTROIDS.minByOrNull { (_, c) ->
                distance3D(entity.valence, entity.arousal, tn,
                    c.first, c.second, c.third)
            }?.key ?: entity.mood
            entity.copy(mood = newMood)
        }
        updated.forEach { moodDao.upsert(it) }
        calibrationDao.clear()
        return updated.size
    }

    private fun tempoNorm(bpm: Double): Double {
        val lo = 50.0; val hi = 180.0
        return ((bpm - lo) / (hi - lo)).coerceIn(0.0, 1.0)
    }

    private fun distance3D(
        v1: Double, a1: Double, t1: Double,
        v2: Double, a2: Double, t2: Double,
    ): Double {
        val dv = v2 - v1; val da = a2 - a1; val dt = t2 - t1
        return kotlin.math.sqrt(dv * dv + da * da + dt * dt)
    }

    sealed interface CalibrationResult {
        data class Success(
            val songsReclassified: Int,
            val calibration: CalibrationEntity,
        ) : CalibrationResult

        data class NotEnoughData(val available: Int) : CalibrationResult
    }

    companion object {
        // Costanti dello stesso classificatore originale (devono coincidere)
        private const val GLOBAL_AVG_VALENCE = 0.0
        private const val GLOBAL_AVG_AROUSAL = 0.0
        private const val GLOBAL_AVG_TEMPO_NORM = 0.5

        // Quanto "spostare" i centroidi: 0.5 = compromesso tra libreria e modello
        private const val SHIFT_FACTOR = 0.5

        // Centroidi originali (devono coincidere con _MOOD_CENTROIDS in music_analyzer.py)
        private val ORIGINAL_CENTROIDS = mapOf(
            "Energico"       to Triple( 0.55,  0.85, 0.80),
            "Festivo"        to Triple( 0.75,  0.70, 0.75),
            "Positivo"       to Triple( 0.70,  0.30, 0.55),
            "Aggressivo"     to Triple(-0.35,  0.85, 0.80),
            "Concentrazione" to Triple( 0.10,  0.10, 0.45),
            "Rilassato"      to Triple( 0.40, -0.40, 0.30),
            "Romantico"      to Triple( 0.45, -0.20, 0.40),
            "Nostalgico"     to Triple(-0.15, -0.30, 0.35),
            "Malinconico"    to Triple(-0.55, -0.55, 0.25),
        )

        @Volatile private var INSTANCE: CalibrationRepository? = null
        fun get(context: Context): CalibrationRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: CalibrationRepository(context).also { INSTANCE = it }
            }
    }
}
