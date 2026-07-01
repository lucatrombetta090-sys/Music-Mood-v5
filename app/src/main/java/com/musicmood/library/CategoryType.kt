package com.musicmood.library

import com.musicmood.data.Song

enum class CategoryType {
    SONGS, ARTISTS, ALBUMS, GENRES, YEARS, FOLDERS;

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

enum class SortOrder {
    AZ, ZA, COUNT_DESC, COUNT_ASC;
}

data class CategoryGroup(
    val key: String,
    val title: String,
    val subtitle: String,
    val songCount: Int,
    val totalDurationMs: Long,
    val coverSong: Song?,
    /** Path completo per navigation nested (usato dai folder). */
    val fullPath: String? = null,
    /** True se questo group è un "folder" navigabile in cui si può entrare. */
    val isNavigable: Boolean = false,
)
