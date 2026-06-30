package com.musicmood.audio.yamnet

import android.content.Context
import android.util.Log
import com.google.mediapipe.framework.MediaPipeException
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifierResult
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions
import java.util.concurrent.atomic.AtomicReference

/**
 * Esegue YAMNet (via MediaPipe Tasks Audio) su un buffer PCM float32 mono 16 kHz
 * e ritorna la mappa "label" -> "score" delle top categorie.
 *
 * Il modello yamnet.tflite deve essere in app/src/main/assets/.
 */
class YamnetMoodClassifier(private val context: Context) {

    private val tag = "YamnetClassifier"
    private val classifierRef = AtomicReference<AudioClassifier?>(null)

    private fun ensureClassifier(): AudioClassifier? {
        val existing = classifierRef.get()
        if (existing != null) return existing

        return try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET)
                .build()

            val options = AudioClassifier.AudioClassifierOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.AUDIO_CLIPS)
                .setMaxResults(15)
                .build()

            val created = AudioClassifier.createFromOptions(context, options)
            classifierRef.set(created)
            created
        } catch (t: Throwable) {
            Log.e(tag, "Failed to create AudioClassifier: ${t.message}", t)
            null
        }
    }

    /**
     * @param pcmMono16k PCM float32 mono a 16 kHz, valori [-1, +1]
     * @return Mappa "categoria AudioSet" -> "score" delle top categorie, oppure null se errore.
     */
    fun classify(pcmMono16k: FloatArray): Map<String, Float>? {
        if (pcmMono16k.isEmpty()) return null
        val classifier = ensureClassifier() ?: return null

        return try {
            val audioFormat = AudioData.AudioDataFormat.builder()
                .setNumOfChannels(1)
                .setSampleRate(16_000f)
                .build()
            val audioData = AudioData.create(audioFormat, pcmMono16k.size)
            audioData.load(pcmMono16k)

            val result: AudioClassifierResult = classifier.classify(audioData)

            // Aggreghiamo le categorie su tutte le finestre temporali
            // prendendo lo score MASSIMO osservato per ogni label.
            val maxByLabel = mutableMapOf<String, Float>()
            for (cls in result.classificationResults()) {
                for (entry in cls.classifications()) {
                    for (cat in entry.categories()) {
                        val name = cat.categoryName().orEmpty()
                        val score = cat.score()
                        val prev = maxByLabel[name] ?: -1f
                        if (score > prev) maxByLabel[name] = score
                    }
                }
            }
            if (maxByLabel.isEmpty()) null else maxByLabel
        } catch (e: MediaPipeException) {
            Log.e(tag, "MediaPipe exception: ${e.message}", e)
            null
        } catch (t: Throwable) {
            Log.e(tag, "classify failed: ${t.message}", t)
            null
        }
    }

    fun close() {
        classifierRef.getAndSet(null)?.close()
    }

    companion object {
        private const val MODEL_ASSET = "yamnet.tflite"

        @Volatile private var INSTANCE: YamnetMoodClassifier? = null
        fun get(context: Context): YamnetMoodClassifier =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: YamnetMoodClassifier(context.applicationContext)
                    .also { INSTANCE = it }
            }
    }
}
