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
import com.musicmood.MusicMoodApp
import com.musicmood.R
import com.musicmood.audio.MoodAnalysisOrchestrator
import com.musicmood.data.MoodRepository
import com.musicmood.data.db.MoodEntity
import com.musicmood.library.MediaStoreRepository

class MoodAnalysisWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val tag = "MoodAnalysisWorker"

    override suspend fun doWork(): Result {
        Log.i(tag, "Worker avviato")
        try {
            setForeground(buildForegroundInfo("Preparazione…", 0, 0, true))
        } catch (e: Exception) {
            Log.w(tag, "setForeground failed: ${e.message}")
        }

        val moodRepo: MoodRepository
        val mediaRepo: MediaStoreRepository
        val orchestrator: MoodAnalysisOrchestrator

        try {
            moodRepo = MoodRepository.get(applicationContext)
            mediaRepo = MediaStoreRepository(applicationContext)

            // Python (per DSP fallback)
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(applicationContext))
            }
            Python.getInstance().getModule("music_analyzer")

            orchestrator = MoodAnalysisOrchestrator(applicationContext)
        } catch (t: Throwable) {
            Log.e(tag, "init failed: ${t.message}", t)
            return Result.failure()
        }

        val pending = try {
            val all = mediaRepo.loadAllSongs()
            val analyzed = moodRepo.getAnalyzedIds()
            all.filter { it.id !in analyzed }
        } catch (t: Throwable) {
            Log.e(tag, "Errore caricamento libreria: ${t.message}", t)
            return Result.failure()
        }

        if (pending.isEmpty()) {
            Log.i(tag, "Nessun brano da analizzare")
            return Result.success()
        }

        val total = pending.size
        var done = 0
        var failed = 0
        var yamnetCount = 0
        var dspCount = 0

        for (song in pending) {
            if (isStopped) return Result.success()

            try {
                val result = orchestrator.analyze(
                    uri = song.uri,
                    title = song.title,
                    artist = song.artist,
                    durationMs = song.durationMs,
                )
                moodRepo.saveWithSource(song.id, result.analysis, result.source)
                if (result.source == "yamnet") yamnetCount++ else dspCount++
            } catch (e: OutOfMemoryError) {
                Log.e(tag, "OOM su ${song.title}", e)
                System.gc()
                failed++
            } catch (t: Throwable) {
                Log.w(tag, "Errore su ${song.title}: ${t.message}")
                failed++
            }

            done++
            if (done % 5 == 0 || done == total) {
                try {
                    setForeground(buildForegroundInfo(
                        "YAMNet: $yamnetCount · DSP: $dspCount", done, total, false
                    ))
                } catch (_: Exception) {}
            }
        }

        Log.i(tag, "Completato: ok=${done - failed} yamnet=$yamnetCount dsp=$dspCount failed=$failed")
        return Result.success()
    }

    private fun buildForegroundInfo(title: String, done: Int, total: Int, indeterminate: Boolean): ForegroundInfo {
        val notification = buildNotification(title, done, total, indeterminate)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(MusicMoodApp.NOTIFICATION_ID_ANALYSIS, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(MusicMoodApp.NOTIFICATION_ID_ANALYSIS, notification)
        }
    }

    private fun buildNotification(title: String, done: Int, total: Int, indeterminate: Boolean): Notification {
        val text = if (total > 0) "$done / $total brani" else "Avvio…"
        return NotificationCompat.Builder(applicationContext, MusicMoodApp.CHANNEL_ANALYSIS)
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

    companion object { const val WORK_NAME = "mood_analysis_batch" }
}
