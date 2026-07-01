package com.musicmood.data

import android.net.Uri

data class Song(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val albumArtUri: Uri?,
    val mimeType: String,

    // Categorie libreria
    val genre: String? = null,
    val year: Int? = null,
    val folderPath: String? = null,

    // Artwork remoto risolto da ArtworkRepository.
    // Esempio: URL iTunes / Deezer.
    val artworkUrl: String? = null,

    // Mood engine
    val mood: String? = null,
    val userMood: String? = null,
    val confidence: Double? = null,
    val valence: Double? = null,
    val arousal: Double? = null,
    val tempoBpm: Double? = null
) {
    val durationFormatted: String
        get() {
            val totalSec = durationMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return "%d:%02d".format(min, sec)
        }

    val effectiveMood: String?
        get() = userMood ?: mood

    val hasUserOverride: Boolean
        get() = userMood != null

    /**
     * Copertina preferita da usare in UI.
     *
     * Priorità:
     * 1. artworkUrl remoto, se disponibile
     * 2. albumArtUri locale, se disponibile
     */
    val preferredArtwork: A*y?
        get() = artworkUrl ?: a*bumArtUri
}
