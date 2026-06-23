package com.musicmood.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
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

    private val decoder = AudioDecoder(appContext)
    private val mediaRepo = MediaStoreRepository(appContext)
    private val moodRepo  = MoodRepository.get(appContext)

    override suspend fun doWork(): Result {
        setForeground(buildForegroundInfo(0, 0))

        // 1) Inizializza Python se necessario
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(applicationContext))
        }
        val module = Python.getInstance().getModule("music_analyzer")

        // 2) Filtra solo i brani non ancora analizzati
        val allSongs = mediaRepo.loadAllSongs()
        val analyzed = moodRepo.getAnalyzedIds()
        val pending = allSongs.filter { it.id !in analyzed }

        if (pending.isEmpty()) return Result.success()

        val total = pending.size
        var done = 0
        var failed = 0

        for (song in pending) {
            if (isStopped) return Result.success()

            runCatching {
                val pcm = decoder.decodeWindow(
                    uri = song.uri,
                    startMs = (song.durationMs / 2).coerceAtLeast(0),
                    durationMs = 30_000L,
                )
                val pyResult = module.callAttr(
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
            }.onFailure { failed++ }

            done++
            setForeground(buildForegroundInfo(done, total))
        }

        return Result.success()
    }

    private fun buildForegroundInfo(done: Int, total: Int): ForegroundInfo {
        val text = if (total == 0) "Avvio analisi…"
                   else "Brani analizzati: $done / $total"

        val notification = NotificationCompat.Builder(
            applicationContext, MusicMoodApp.CHANNEL_ANALYSIS
        )
            .setContentTitle("Music-Mood — Analisi libreria")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_analyze)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                if (total > 0) setProgress(total, done, false)
                else setProgress(0, 0, true)
            }
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                MusicMoodApp.NOTIFICATION_ID_ANALYSIS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(MusicMoodApp.NOTIFICATION_ID_ANALYSIS, notification)
        }
    }

    companion object {
        const val WORK_NAME = "mood_analysis_batch"
    }
}
