package com.musicmood

import com.chaquo.python.PyObject

/**
 * Risultato dell'analisi mood lato Kotlin.
 * Mappato 1:1 dal dict ritornato da music_analyzer.analyze_pcm().
 */
data class MoodAnalysis(
    val mood: String,
    val confidence: Double,
    val valence: Double,
    val arousal: Double,
    val tempoBpm: Double,
    val key: String,
    val mode: String,
) {
    companion object {
        fun fromPy(py: PyObject): MoodAnalysis {
            val m = py.asMap()
            fun d(k: String) = m[PyObject.fromJava(k)]?.toString()?.toDoubleOrNull() ?: 0.0
            fun s(k: String) = m[PyObject.fromJava(k)]?.toString().orEmpty()
            return MoodAnalysis(
                mood       = s("mood").ifEmpty { "Sconosciuto" },
                confidence = d("confidence"),
                valence    = d("valence"),
                arousal    = d("arousal"),
                tempoBpm   = d("tempo_bpm"),
                key        = s("key").ifEmpty { "C" },
                mode       = s("mode").ifEmpty { "major" },
            )
        }
    }
}
