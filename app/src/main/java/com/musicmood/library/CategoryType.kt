package com.musicmood.library

import com.musicmood.data.Song

enum class CategoryType {
    SONGS,    // tutti i brani
    ARTISTS,  // per artista
    ALBUMS,   // per album
    GENRES,   // per genere
    YEARS,    // per anno
    FOLDERS;  // per cartella di provenienza

    companion object {
        fun fromTabIndex(i: Int): CategoryType = when (i) {
            0 -> SONGS
            1 -> ARTISTS
            2 -> ALBUMS
            3 -> GENRES
            4 -> YEARS
            else -> FOLDERS
        }
    }
}

/**
 * Modello di un raggruppamento (artista, album, genere, anno, cartella).
 */
data class CategoryGroup(
    val key: String,
    val title: String,
    val subtitle: String,
    val songCount: Int,
    val totalDurationMs: Long,
    val coverSong: Song?,
)
