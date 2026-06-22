package com.musicmood

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

class MainActivity : AppCompatActivity() {

    private lateinit var engineStatus: TextView
    private lateinit var btnRunTest: Button
    private lateinit var resultPlaceholder: TextView
    private lateinit var resultPanel: LinearLayout
    private lateinit var moodValue: TextView
    private lateinit var confidenceValue: TextView
    private lateinit var featuresValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind views
        engineStatus       = findViewById(R.id.engineStatus)
        btnRunTest         = findViewById(R.id.btnRunTest)
        resultPlaceholder  = findViewById(R.id.resultPlaceholder)
        resultPanel        = findViewById(R.id.resultPanel)
        moodValue          = findViewById(R.id.moodValue)
        confidenceValue    = findViewById(R.id.confidenceValue)
        featuresValue      = findViewById(R.id.featuresValue)

        btnRunTest.setOnClickListener { runMoodAnalysisTest() }

        initializePython()
    }

    /** Inizializza Chaquopy in background per non bloccare il main thread. */
    private fun initializePython() {
        engineStatus.text = getString(R.string.engine_status_loading)

        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    if (!Python.isStarted()) {
                        Python.start(AndroidPlatform(this@MainActivity))
                    }
                    // Smoke test: importa il modulo
                    Python.getInstance().getModule("music_analyzer")
                    true
                }.getOrElse { e ->
                    e.printStackTrace()
                    engineStatus.text = "${getString(R.string.engine_status_error)}\n${e.message}"
                    false
                }
            }
            if (ok) {
                engineStatus.text = getString(R.string.engine_status_ready)
                btnRunTest.isEnabled = true
            }
        }
    }

    /** Genera un segnale audio di test e lo passa a music_analyzer.analyze_pcm. */
    private fun runMoodAnalysisTest() {
        btnRunTest.isEnabled = false
        btnRunTest.text = getString(R.string.btn_running)

        lifecycleScope.launch {
            val analysis = withContext(Dispatchers.Default) {
                runCatching {
                    val sampleRate = 44_100
                    val durationSec = 10
                    val pcm = generateTestSignal(sampleRate, durationSec)

                    val py = Python.getInstance()
                    val module = py.getModule("music_analyzer")
                    val result = module.callAttr(
                        "analyze_pcm",
                        pcm,            // bytes
                        sampleRate,     // int
                        1,              // channels
                        durationSec * 1000, // duration_ms
                        "test_signal",  // title
                        "synthetic"     // artist
                    )
                    MoodAnalysis.fromPy(result)
                }.getOrElse { e ->
                    e.printStackTrace()
                    null
                }
            }

            if (analysis != null) {
                showResult(analysis)
            } else {
                resultPlaceholder.text = "⚠️ Errore durante l'analisi (vedi logcat)"
            }

            btnRunTest.text = getString(R.string.btn_run_test)
            btnRunTest.isEnabled = true
        }
    }

    private fun showResult(a: MoodAnalysis) {
        resultPlaceholder.visibility = View.GONE
        resultPanel.visibility = View.VISIBLE

        moodValue.text = "🎯 ${a.mood}"
        confidenceValue.text = "Confidenza: ${"%.0f".format(a.confidence * 100)}%"
        featuresValue.text = buildString {
            appendLine("Valenza : ${"%+.2f".format(a.valence)}")
            appendLine("Arousal : ${"%+.2f".format(a.arousal)}")
            appendLine("Tempo   : ${"%.1f".format(a.tempoBpm)} BPM")
            append("Tonalità: ${a.key} ${a.mode}")
        }
    }

    /**
     * Genera 10 s di PCM 16-bit mono: sinusoide a 880 Hz + click ritmici a 120 BPM.
     * Ci aspettiamo dall'analizzatore: mood "Energico" o "Festivo".
     */
    private fun generateTestSignal(sampleRate: Int, durationSec: Int): ByteArray {
        val nSamples = sampleRate * durationSec
        val samplesPerClick = sampleRate / 2   // 120 BPM = 2 click/sec
        val buffer = ByteArray(nSamples * 2)   // 16-bit = 2 byte/sample

        for (i in 0 until nSamples) {
            val t = i.toDouble() / sampleRate
            val sine = 0.4 * sin(2.0 * PI * 880.0 * t)
            val click = if (i % samplesPerClick < 50) 0.5 else 0.0
            val mixed = (sine + click).coerceIn(-1.0, 1.0)
            val sample = (mixed * 32_767).toInt().toShort()

            // Little-endian
            buffer[i * 2]     = (sample.toInt() and 0xFF).toByte()
            buffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        return buffer
    }
}
