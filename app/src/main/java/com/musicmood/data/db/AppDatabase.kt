package com.musicmood.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ArtworkCacheEntity::class],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun artworkCacheDao(): ArtworkCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "music_mood_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
