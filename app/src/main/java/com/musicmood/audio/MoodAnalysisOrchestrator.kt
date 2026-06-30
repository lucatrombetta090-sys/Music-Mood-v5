package com.musicmood.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import com.chaquo.python.Python
import com.musicmood.MoodAnalysis
import com.musicmood.audio.yamnet.YamnetMoodClassifier
import com.musicmood.audio.yamnet.YamnetMoodMapper
import com.musicmood.data.SettingsRepository

/**
 * Tenta YAMNet → se confidenza adeguata, usa quel risultato.
 * Altrimenti fa fallback al DSP Python.
 *
 * @return Pair(analysis, source) dove source è "yamnet" o "dsp"
 */
class MoodAnalysisOrchestrator(private val context: Context) {

    private val tag = "MoodOrchestrator"

    private val decoder = AudioDecoder(context)
    private val settings = SettingsRepository.get(context)

    /** Soglia minima di confidenza YAMNet per accettare il risultato. */
    private val yamnetMinConfidence = 0.08f

    data class Result(
        val analysis: MoodAnalysis,
        val source: String,    // "yamnet" o "dsp"
    )

    /**
     * Analizza una finestra del brano. Prova YAMNet, fallback DSP.
     */
    fun analyze(
        uri: Uri,
        title: String,
        artist: String,
        durationMs: Long,
    ): Result {
        val startMs = (durationMs / 2).coerceAtLeast(0)
        val analysisWindowMs = settings.analysisWindowSec * 1000L

        // ─── Tentativo 1: YAMNet ───
        if (settings.useYamnet) {
            try {
                val pcm16k = decoder.decodeFloat16k(uri, startMs, analysisWindowMs)
                val yamnet = YamnetMoodClassifier.get(context).classify(pcm16k)
                if (yamnet != null && yamnet.isNotEmpty()) {
                    val pred = YamnetMoodMapper.predict(yamnet)
                    if (pred.confidence >= yamnetMinConfidence) {
                        val analysis = buildAnalysisFromYamnet(pred)
                        return Result(analysis, "yamnet")
                    } else {
                        Log.d(tag, "YAMNet confidence too low (${pred.confidence}), fallback to DSP")
                    }
                }
            } catch (t: Throwable) {
                Log.w(tag, "YAMNet failed: ${t.message}")
            }
        }

        // ─── Fallback: DSP Python ───
        return Result(analyzeWithDsp(uri, startMs, analysisWindowMs, title, artist), "dsp")
    }

    private fun buildAnalysisFromYamnet(pred: YamnetMoodMapper.Prediction): MoodAnalysis {
        // YAMNet non ci dà valenza/arousal/BPM esplicite; stimo da mood per coerenza UI
        val (valence, arousal) = synthCoordsForMood(pred.mood)
        return MoodAnalysis(
            mood = pred.mood,
            confidence = pred.confidence.toDouble(),
            valence = valence,
            arousal = arousal,
            tempoBpm = 0.0,        // non disponibile da YAMNet
            key = "C",
            mode = "major",
        )
    }

    /** Coordinate (valence, arousal) "tipiche" del centroide per popolare la BubbleMap. */
    private fun synthCoordsForMood(mood: String): Pair<Double, Double> = when (mood) {
        "Energico"        -> 0.55 to 0.85
        "Festivo"         -> 0.75 to 0.70
        "Positivo"        -> 0.70 to 0.30
        "Aggressivo"      -> -0.35 to 0.85
        "Concentrazione"  -> 0.10 to 0.10
        "Rilassato"       -> 0.40 to -0.40
        "Romantico"       -> 0.45 to -0.20
        "Nostalgico"      -> -0.15 to -0.30
        "Malinconico"     -> -0.55 to -0.55
        else              -> 0.0 to 0.0
    }

    private fun analyzeWithDsp(
        uri: Uri,
        startMs: Long,
        durationMs: Long,
        title: String,
        artist: String,
    ): MoodAnalysis {
        val pcm = decoder.decodeWindow(uri, startMs, durationMs)
        val py = Python.getInstance()
        val module = py.getModule("music_analyzer")
        val pyResult = module.callAttr(
            "analyze_pcm",
            pcm.bytes, pcm.sampleRate, pcm.channels,
            pcm.durationMs.toInt(),
            title, artist,
        )
        return MoodAnalysis.fromPy(pyResult)
    }
}
