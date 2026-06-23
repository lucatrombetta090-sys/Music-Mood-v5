package com.musicmood.worker

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.musicmood.MoodAnalysis
import com.musicmood.MusicMoodApp
import com.musicmood.R
import com.musicmood.audio.AudioDecoder
import com.musicmood.data.MoodRepository
import com.musicmood.library.MediaStoreRepository

class MoodAnalysisWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val tag = "MoodAnalysisWorker"

    override suspend fun doWork(): Result {
        Log.i(tag, "Worker avviato")

        // ---- 1. Promuovi a foreground PRIMA di qualsiasi operazione pesante ----
        try {
            setForeground(buildForegroundInfo("Preparazione…", 0, 0, true))
        } catch (e: Exception) {
            // Se non possiamo diventare foreground (permesso notifica negato),
            // continuiamo lo stesso ma in background normale
            Log.w(tag, "setForeground failed (continuo in background): ${e.message}")
        }

        // ---- 2. Inizializza dipendenze in try/catch ----
        val moodRepo: MoodRepository
        val mediaRepo: MediaStoreRepository
        val decoder: AudioDecoder
        val pyModule: com.chaquo.python.PyObject

        try {
            moodRepo  = MoodRepository.get(applicationContext)
            mediaRepo = MediaStoreRepository(applicationContext)
            decoder   = AudioDecoder(applicationContext)

            // Inizializza Python in modo idempotente
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(applicationContext))
            }
            pyModule = Python.getInstance().getModule("music_analyzer")
            Log.i(tag, "Dipendenze pronte")
        } catch (e: Throwable) {
            Log.e(tag, "Errore inizializzazione: ${e.message}", e)
            return Result.failure()
        }

        // ---- 3. Carica i brani e filtra quelli già analizzati ----
        val pending = try {
            val all = mediaRepo.loadAllSongs()
            val analyzed = moodRepo.getAnalyzedIds()
            all.filter { it.id !in analyzed }
        } catch (e: Throwable) {
            Log.e(tag, "Errore caricamento libreria: ${e.message}", e)
            return Result.failure()
        }

        if (pending.isEmpty()) {
            Log.i(tag, "Nessun brano da analizzare")
            return Result.success()
        }

        val total = pending.size
        Log.i(tag, "Brani da analizzare: $total")

        // ---- 4. Loop principale con gestione errori per-brano ----
        var done = 0
        var failed = 0

        for (song in pending) {
            if (isStopped) {
                Log.i(tag, "Worker fermato dall'utente")
                return Result.success()
            }

            try {
                val pcm = decoder.decodeWindow(
                    uri = song.uri,
                    startMs = (song.durationMs / 2).coerceAtLeast(0L),
                    durationMs = 30_000L,
                )

                val pyResult = pyModule.callAttr(
                    "analyze_pcm",
                    pcm.bytes,
                    pcm.sampleRate,
                    pcm.channels,
                    pcm.durationMs.toInt(),
                    song.title,
                    song.artist,
                )
                val analysis = MoodAnalysis.fromPy(pyResult)
                moodRepo.save(song.id, analysis)

            } catch (e: OutOfMemoryError) {
                Log.e(tag, "OOM su ${song.title}, forzo GC", e)
                System.gc()
                failed++
            } catch (e: Throwable) {
                Log.w(tag, "Errore su ${song.title}: ${e.message}")
                failed++
            }

            done++

            // Aggiorna notifica ogni 5 brani per non spammare il sistema
            if (done % 5 == 0 || done == total) {
                try {
                    setForeground(
                        buildForegroundInfo(
                            "Analisi in corso",
                            done, total, false
                        )
                    )
                } catch (_: Exception) {
                    // se notifica non disponibile, ignora
                }
            }
        }

        Log.i(tag, "Worker completato: ok=${done - failed} failed=$failed")
        return Result.success()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notifica
    // ─────────────────────────────────────────────────────────────────────────
    private fun buildForegroundInfo(
        title: String,
        done: Int,
        total: Int,
        indeterminate: Boolean,
    ): ForegroundInfo {
        val notification = buildNotification(title, done, total, indeterminate)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                MusicMoodApp.NOTIFICATION_ID_ANALYSIS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(
                MusicMoodApp.NOTIFICATION_ID_ANALYSIS,
                notification,
            )
        }
    }

    private fun buildNotification(
        title: String,
        done: Int,
        total: Int,
        indeterminate: Boolean,
    ): Notification {
        val text = if (total > 0) "$done / $total brani" else "Avvio…"

        return NotificationCompat.Builder(
            applicationContext, MusicMoodApp.CHANNEL_ANALYSIS
        )
            .setContentTitle("🎵 Music-Mood")
            .setContentText(text)
            .setSubText(title)
            .setSmallIcon(R.drawable.ic_analyze)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                if (indeterminate) setProgress(0, 0, true)
                else if (total > 0) setProgress(total, done, false)
            }
            .build()
    }

    companion object {
        const val WORK_NAME = "mood_analysis_batch"
    }
}
