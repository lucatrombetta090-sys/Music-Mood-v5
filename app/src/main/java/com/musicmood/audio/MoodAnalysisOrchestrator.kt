package com.musicmood.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import com.chaquo.python.Python
import com.musicmood.MoodAnalysis
import com.musicmood.audio.yamnet.YamnetMoodClassifier
import com.musicmood.audio.yamnet.YamnetMoodMapper
import com.musicmood.data.SettingsRepository

class MoodAnalysisOrchestrator(private val context: Context) {

    private val tag = "MoodOrchestrator"
    private val decoder = AudioDecoder(context)
    private val settings = SettingsRepository.get(context)
    private val yamnetMinConfidence = 0.08f

    /** Centroidi di riferimento (valence, arousal, tempo_norm) per ogni mood. */
    private val moodCentroids = mapOf(
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

    data class Result(
        val analysis: MoodAnalysis,
        val source: String,
    )

    fun analyze(uri: Uri, title: String, artist: String, durationMs: Long): Result {
        val startMs = (durationMs / 2).coerceAtLeast(0)
        val analysisWindowMs = settings.analysisWindowSec * 1000L

        if (settings.useYamnet) {
            try {
                val pcm16k = decoder.decodeFloat16k(uri, startMs, analysisWindowMs)
                val yamnet = YamnetMoodClassifier.get(context).classify(pcm16k)
                if (yamnet != null && yamnet.isNotEmpty()) {
                    val pred = YamnetMoodMapper.predict(yamnet)
                    if (pred.confidence >= yamnetMinConfidence) {
                        val analysis = buildAnalysisFromYamnet(pred)
                        return Result(analysis, "yamnet")
                    }
                }
            } catch (t: Throwable) {
                Log.w(tag, "YAMNet failed: ${t.message}")
            }
        }

        return Result(analyzeWithDsp(uri, startMs, analysisWindowMs, title, artist), "dsp")
    }

    /**
     * Calcola valenza/arousal come WEIGHTED AVERAGE dei centroidi mood
     * pesati con i punteggi YAMNet. Così ogni brano ha coordinate distinte
     * e la BubbleMap si distribuisce naturalmente.
     */
    private fun buildAnalysisFromYamnet(pred: YamnetMoodMapper.Prediction): MoodAnalysis {
        val scores = pred.rawScores
        val totalScore = scores.values.sum().coerceAtLeast(0.001f).toDouble()

        var wVal = 0.0
        var wAr = 0.0
        for ((mood, score) in scores) {
            val centroid = moodCentroids[mood] ?: continue
            val weight = score.toDouble()
            wVal += centroid.first * weight
            wAr += centroid.second * weight
        }
        val valence = (wVal / totalScore).coerceIn(-1.0, 1.0)
        val arousal = (wAr / totalScore).coerceIn(-1.0, 1.0)

        return MoodAnalysis(
            mood = pred.mood,
            confidence = pred.confidence.toDouble(),
            valence = valence,
            arousal = arousal,
            tempoBpm = 0.0,
            key = "C",
            mode = "major",
        )
    }

    private fun analyzeWithDsp(
        uri: Uri, startMs: Long, durationMs: Long,
        title: String, artist: String,
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
