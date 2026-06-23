package com.musicmood.profile

import com.musicmood.data.Song
import com.musicmood.stats.PlaylistGenerator
import java.util.Calendar

/**
 * Step 7 — Suggerisce un mood/playlist in base all'ora corrente.
 */
object DailyMoodAdvisor {

    data class Suggestion(
        val timeSlot: String,
        val emoji: String,
        val title: String,
        val description: String,
        val presetId: String,
    )

    fun forNow(): Suggestion = forHour(currentHour())

    fun forHour(hour: Int): Suggestion = when (hour) {
        in 6..11  -> Suggestion(
            timeSlot    = "Mattina",
            emoji       = "🌅",
            title       = "Energizing",
            description = "Un boost di energia per iniziare la giornata",
            presetId    = "energy",
        )
        in 12..17 -> Suggestion(
            timeSlot    = "Pomeriggio",
            emoji       = "☀️",
            title       = "Focus",
            description = "Concentrazione e produttività",
            presetId    = "focus",
        )
        in 18..21 -> Suggestion(
            timeSlot    = "Sera",
            emoji       = "🌆",
            title       = "Relax",
            description = "Rallenta e goditi il momento",
            presetId    = "happy",
        )
        else      -> Suggestion(
            timeSlot    = "Notte",
            emoji       = "🌙",
            title       = "Calm",
            description = "Suoni morbidi per chiudere la giornata",
            presetId    = "sleep",
        )
    }

    fun generatePlaylist(suggestion: Suggestion, songs: List<Song>): List<Song> {
        val preset = PlaylistGenerator.PRESETS.firstOrNull { it.id == suggestion.presetId }
            ?: return emptyList()
        return PlaylistGenerator.generate(preset, songs)
    }

    private fun currentHour(): Int =
        Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
}
