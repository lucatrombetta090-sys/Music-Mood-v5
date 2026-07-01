package com.musicmood.data

import android.content.Context
import android.util.Log
import com.musicmood.data.db.AppDatabase
import com.musicmood.data.db.ArtworkCacheEntity
import com.musicmood.network.ArtworkResolver
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Repository per la gestione delle copertine.
 *
 * Gestisce:
 * - cache locale Room
 * - risoluzione remota tramite ArtworkResolver
 * - protezione da richieste duplicate in parallelo sullo stesso brano
 */
class ArtworkRepository private constructor(context: Context) {

    private val tag = "ArtworkRepo"
    private val dao = AppDatabase.get(context).artworkCacheDao()

    /**
     * Mutex per evitare chiamate multiple contemporanee sulla stessa songId.
     */
    private val inFlight = mutableMapOf<Long, Mutex>()

    /**
     * Restituisce la URL della copertina remota.
     *
     * Logica:
     * 1. Cerca in cache Room.
     * 2. Se non presente, blocca richieste concorrenti sulla stessa songId.
     * 3. Esegue la ricerca remota tramite ArtworkResolver.
     * 4. Salva sempre il risultato in cache:
     *    - URL trovata
     *    - oppure miss, con artworkUrl = null
     */
    suspend fun getOrFetch(
        songId: Long,
        title: String,
        artist: String
    ): String? {
        val cached = runCatching {
            dao.get(songId)
        }.getOrNull()

        if (cached != null) {
            return cached.artworkUrl
        }

        val mutex = synchronized(inFlight) {
            inFlight.getOrPut(songId) { Mutex() }
        }

        return mutex.withLock {
            val recheck = runCatching {
                dao.get(songId)
            }.getOrNull()

            if (recheck != null) {
                return@withLock recheck.artworkUrl
            }

            val result = runCatching {
                ArtworkResolver.resolve(title, artist)
            }.getOrElse { error ->
                Log.w(tag, "Artwork resolve failed for songId=$songId: ${error.message}")
                ArtworkResolver.Result(
                    url = null,
                    source = "miss"
                )
            }

            runCatching {
                dao.upsert(
                    ArtworkCacheEntity(
                        songId = songId,
                        artworkUrl = result.url,
                        source = result.source
                    )
                )
            }.onFailure { error ->
                Log.w(tag, "Artwork cache save failed for songId=$songId: ${error.message}")
            }

            result.url
        }
    }

    suspend fun clearCache() {
        runCatching {
            dao.clearAll()
        }.onFailure { error ->
            Log.w(tag, "Artwork cache clear failed: ${error.message}")
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ArtworkRepository? = null

        fun get(context: Context): ArtworkRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ArtworkRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
