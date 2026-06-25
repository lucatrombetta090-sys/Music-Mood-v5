package com.musicmood

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.*
import com.musicmood.weekly.WeeklyReportWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MusicMoodApp : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        scheduleWeeklyReport()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ANALYSIS,
                "Analisi mood",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifica di progresso analisi batch della libreria"
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WEEKLY,
                "Weekly Mood Report",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifica settimanale con il report del tuo mood musicale"
            }
        )
    }

    private fun scheduleWeeklyReport() {
        val request = PeriodicWorkRequestBuilder<WeeklyReportWorker>(
            7, TimeUnit.DAYS
        )
            .setInitialDelay(initialDelayToNextMonday(), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WeeklyReportWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Calcola quanto manca al prossimo lunedì alle 09:00. */
    private fun initialDelayToNextMonday(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.WEEK_OF_YEAR, 1)
            }
        }
        return target.timeInMillis - now.timeInMillis
    }

    companion object {
        const val CHANNEL_ANALYSIS         = "mood_analysis"
        const val CHANNEL_WEEKLY           = "weekly_report"
        const val NOTIFICATION_ID_ANALYSIS = 1001
        const val NOTIFICATION_ID_WEEKLY   = 1002
    }
}
