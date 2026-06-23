package com.musicmood

import com.chaquo.python.PyObject

/**
 * Risultato dell'analisi mood lato Kotlin.
 * Mappato dal dict ritornato da music_analyzer.analyze_pcm().
 *
 * Implementazione difensiva: estrae i valori passando dal repr dict-like
 * di Python, senza dipendere dalla firma esatta di asMap()/fromJava().
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

        /**
         * Costruisce MoodAnalysis da un PyObject che rappresenta un dict Python.
         */
        @JvmStatic
        fun fromPy(py: PyObject): MoodAnalysis {
            return MoodAnalysis(
                mood       = pyStr(py, "mood",  "Sconosciuto"),
                confidence = pyDouble(py, "confidence", 0.0),
                valence    = pyDouble(py, "valence",    0.0),
                arousal    = pyDouble(py, "arousal",    0.0),
                tempoBpm   = pyDouble(py, "tempo_bpm",  0.0),
                key        = pyStr(py, "key",   "C"),
                mode       = pyStr(py, "mode",  "major"),
            )
        }

        private fun pyGet(py: PyObject, key: String): PyObject? {
            return try {
                // Chaquopy supporta la subscript syntax dict[key]
                py.callAttr("get", key)
            } catch (t: Throwable) {
                null
            }
        }

        private fun pyStr(py: PyObject, key: String, default: String): String {
            val v = pyGet(py, key) ?: return default
            val s = v.toString()
            return if (s.isEmpty() || s == "None") default else s
        }

        private fun pyDouble(py: PyObject, key: String, default: Double): Double {
            val v = pyGet(py, key) ?: return default
            return v.toString().toDoubleOrNull() ?: default
        }
    }
}
