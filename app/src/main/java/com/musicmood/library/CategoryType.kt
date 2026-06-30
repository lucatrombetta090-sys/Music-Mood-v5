package com.musicmood.library

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
