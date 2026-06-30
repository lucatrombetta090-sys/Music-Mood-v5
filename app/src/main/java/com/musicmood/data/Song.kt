package com.musicmood.data

import android.net.Uri

/**
 * Modello di dominio di un brano della libreria locale.
 * Le feature mood (mood, valence, arousal) sono popolate dopo l'analisi DSP.
 */
data class Song(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String? = null,    // ← verifica/aggiungi
    val year: Int? = null,        // ← verifica/aggiungi
    val durationMs: Long,
    val albumArtUri: android.net.Uri? = null,
    val mimeType: String,
    val folderPath: String? = null,  // opzionale

    // Risultati dell'analisi (null finché non analizzato)
    val mood: String? = null,
    val confidence: Double? = null,
    val valence: Double? = null,
    val arousal: Double? = null,
    val tempoBpm: Double? = null,
) {
    val durationFormatted: String
        get() {
            val totalSec = durationMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return "%d:%02d".format(min, sec)
        }
}
