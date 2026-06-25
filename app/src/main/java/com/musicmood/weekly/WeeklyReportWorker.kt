package com.musicmood.weekly

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.musicmood.MusicMoodApp
import com.musicmood.R

/**
 * Worker periodico che ogni domenica genera il report settimanale
 * e invia una notifica all'utente.
 */
class WeeklyReportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val generator = WeeklyReportGenerator(applicationContext)
        val report = generator.generateLastWeek() ?: return Result.success()

        sendNotification(report.dominantMood, report.totalPlays)
        return Result.success()
    }

    private fun sendNotification(dominantMood: String, total: Int) {
        val nm = NotificationManagerCompat.from(applicationContext)
        val notification = NotificationCompat.Builder(
            applicationContext, MusicMoodApp.CHANNEL_WEEKLY
        )
            .setSmallIcon(R.drawable.ic_weekly)
            .setContentTitle("📊 Il tuo Weekly Mood Report è pronto!")
            .setContentText("Questa settimana: $total brani • mood dominante $dominantMood")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        runCatching {
            nm.notify(MusicMoodApp.NOTIFICATION_ID_WEEKLY, notification)
        }
    }

    companion object {
        const val WORK_NAME = "weekly_mood_report"
    }
}
