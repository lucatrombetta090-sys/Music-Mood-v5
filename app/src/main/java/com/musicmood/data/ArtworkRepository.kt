package com.musicmood.data

import android.content.Context
import android.util.Log
import com.musicmood.data.db.AppDatabase
import com.musicmood.data.db.ArtworkCacheEntity
import com.musicmood.network.ArtworkResolver
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Wrapper che gestisce cache locale + chiamata di rete.
 */
class ArtworkRepository private constructor(context: Context) {

    private val tag = "ArtworkRepo"
    private val dao = AppDatabase.get(context).artworkCacheDao()

    // Mutex per evitare di lanciare la stessa richiesta più volte in parallelo
    private val inFlight = mutableMapOf<Long, Mutex>()

    /**
     * Restituisce la URL della copertina remota (iTunes/Deezer).
     * Se non in cache, fa la richiesta. Se la richiesta fallisce o non trova nulla,
     * memorizza "miss" così non riproviamo all'infinito.
     */
    suspend fun getOrFetch(songId: Long, title: String, artist: String): String? {
        // 1. cache
        val cached = runCatching { dao.get(songId) }.getOrNull()
        if (cached != null) return cached.artworkUrl

        // 2. lock per songId per evitare duplicati
        val mutex = synchronized(inFlight) {
            inFlight.getOrPut(songId) { Mutex() }
        }
        mutex.withLock {
            // ricontrolla dopo il lock
            val recheck = runCatching { dao.get(songId) }.getOrNull()
            if (recheck != null) return recheck.artworkUrl

            // 3. fetch
            val result = runCatching {
                ArtworkResolver.resolve(title, artist)
            }.getOrElse {
                Log.w(tag, "resolve failed: ${it.message}")
                ArtworkResolver.Result(null, "miss")
            }

            runCatching {
                dao.upsert(
                    ArtworkCacheEntity(
                        songId = songId,
                        artworkUrl = result.url,
                        source = result.source,
                    )
                )
            }
            return result.url
        }
    }

    suspend fun clearCache() = runCatching { dao.clearAll() }

    companion object {
        @Volatile private var INSTANCE: ArtworkRepository? = null
        fun get(context: Context): ArtworkRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ArtworkRepository(context).also { INSTANCE = it }
            }
    }
}
