package com.musicmood.library

import com.musicmood.data.Song

enum class CategoryType {
    SONGS,    // tutti i brani (vista classica)
    ARTISTS,  // raggruppato per artista
    ALBUMS,   // raggruppato per album
    GENRES,   // raggruppato per genere
    YEARS;    // raggruppato per anno

    companion object {
        fun fromTabIndex(i: Int): CategoryType = when (i) {
            0 -> SONGS
            1 -> ARTISTS
            2 -> ALBUMS
            3 -> GENRES
            else -> YEARS
        }
    }
}

/**
 * Modello di un raggruppamento (artista, album, genere, anno).
 */
data class CategoryGroup(
    val key: String,          // chiave univoca (es. "Katy Perry")
    val title: String,        // testo principale
    val subtitle: String,     // testo secondario (es. "44 brani • 2h 15m")
    val songCount: Int,
    val totalDurationMs: Long,
    val coverSong: Song?,     // un brano rappresentativo (per la copertina)
)
