package com.musicmood

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.Configuration

class MusicMoodApp : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ANALYSIS,
            "Analisi mood",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifica di progresso analisi batch della libreria"
        }
        getSystemService(Context.NOTIFICATION_SERVICE)
            .let { it as NotificationManager }
            .createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ANALYSIS = "mood_analysis"
        const val NOTIFICATION_ID_ANALYSIS = 1001
    }
}
