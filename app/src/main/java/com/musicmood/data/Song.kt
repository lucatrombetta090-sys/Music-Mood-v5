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

    val mood: String? = null,          // mood DSP originale
    val userMood: String? = null,      // override manuale
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

    /** Il mood effettivo mostrato ovunque: userMood ha priorità sul mood DSP. */
    val effectiveMood: String?
        get() = userMood ?: mood

    /** True se l'utente ha applicato un override manuale. */
    val hasUserOverride: Boolean
        get() = userMood != null
}
