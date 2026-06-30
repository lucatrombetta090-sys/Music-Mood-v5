package com.musicmood.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MoodEntity::class,
        CalibrationEntity::class,
        ListeningEventEntity::class,
        WeeklyReportEntity::class,
        ArtworkCacheEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun moodDao(): MoodDao
    abstract fun calibrationDao(): CalibrationDao
    abstract fun listeningEventDao(): ListeningEventDao
    abstract fun weeklyReportDao(): WeeklyReportDao
    abstract fun artworkCacheDao(): ArtworkCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "musicmood.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
``
