package com.musicmood.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ArtworkRepository(context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val artworkCacheDao = db.artworkCacheDao()

    suspend fun getArtwork(path: String): String? {
        return withContext(Dispatchers.IO) {
            artworkCacheDao.getArtwork(path)
        }
    }

    suspend fun saveArtwork(path: String, artworkUri: String) {
        withContext(Dispatchers.IO) {
            val entity = ArtworkCacheEntity(
                path = path,
                artworkUri = artworkUri
            )
            artworkCacheDao.insert(entity)
        }
    }
}
