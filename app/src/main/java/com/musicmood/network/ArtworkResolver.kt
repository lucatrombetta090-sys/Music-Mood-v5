package com.musicmood.network

import android.util.Log

/**
 * Cerca la copertina di un brano usando iTunes come fonte primaria
 * e Deezer come fallback.
 */
object ArtworkResolver {

    private const val TAG = "ArtworkResolver"

    data class Result(val url: String?, val source: String)

    suspend fun resolve(title: String, artist: String): Result {
        val term = buildQuery(title, artist)
        if (term.isBlank()) return Result(null, "miss")

        // 1. iTunes
        runCatching {
            val r = NetworkClient.itunes.searchSong(term)
            val urlSmall = r.results.firstOrNull()?.artworkUrl100
            if (!urlSmall.isNullOrBlank()) {
                // Upscale a 600x600 (trick documentato di iTunes)
                val urlBig = urlSmall.replace("100x100bb", "600x600bb")
                return Result(urlBig, "itunes")
            }
        }.onFailure { Log.w(TAG, "iTunes failed: ${it.message}") }

        // 2. Deezer fallback
        runCatching {
            val r = NetworkClient.deezer.searchTrack(term)
            val album = r.data.firstOrNull()?.album
            val url = album?.cover_xl ?: album?.cover_big ?: album?.cover_medium ?: album?.cover
            if (!url.isNullOrBlank()) {
                return Result(url, "deezer")
            }
        }.onFailure { Log.w(TAG, "Deezer failed: ${it.message}") }

        return Result(null, "miss")
    }

    private fun buildQuery(title: String, artist: String): String {
        val t = title.cleanQueryToken()
        val a = artist.cleanQueryToken()
        return when {
            t.isNotBlank() && a.isNotBlank() -> "$a $t"
            t.isNotBlank() -> t
            a.isNotBlank() -> a
            else -> ""
        }
    }

    private fun String.cleanQueryToken(): String {
        // Rimuovi tag in parentesi tipo "(feat. X)", "[remix]", "- live", ecc.
        return this
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("\\[.*?]"), "")
            .replace(Regex("\\s-\\s.*$"), "")
            .replace(Regex("\\s+feat\\..*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+ft\\..*$", RegexOption.IGNORE_CASE), "")
            .trim()
    }
}
