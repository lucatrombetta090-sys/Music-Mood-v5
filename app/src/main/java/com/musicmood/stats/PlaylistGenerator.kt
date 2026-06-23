package com.musicmood.stats

import com.musicmood.data.Song

/**
 * Generatore di playlist automatiche basato su feature mood (valenza/arousal/tempo).
 * Ogni "preset" definisce criteri di filtro e ordinamento.
 */
object PlaylistGenerator {

    data class Preset(
        val id: String,
        val name: String,
        val emoji: String,
        val description: String,
        val maxSongs: Int = 25,
        val filter: (Song) -> Boolean,
        val sortBy: (Song) -> Double,
    )

    val PRESETS: List<Preset> = listOf(
        Preset(
            id          = "focus",
            name        = "Focus 30 min",
            emoji       = "🎯",
            description = "Concentrazione, tempo medio, valenza neutra",
            maxSongs    = 8,    // ~30 min con brani da 3-4 min
            filter      = { it.mood == "Concentrazione" || it.mood == "Rilassato" },
            sortBy      = { -(it.arousal ?: 0.0) },  // dal più calmo al più energico
        ),
        Preset(
            id          = "energy",
            name        = "Energy Boost",
            emoji       = "⚡",
            description = "Massima energia, valenza positiva",
            filter      = { it.mood == "Energico" || it.mood == "Festivo" },
            sortBy      = { -(it.arousal ?: 0.0) - (it.valence ?: 0.0) },
        ),
        Preset(
            id          = "sleep",
            name        = "Sleep",
            emoji       = "🌙",
            description = "Bassa energia, atmosfera rilassata",
            filter      = {
                it.mood == "Rilassato" || it.mood == "Romantico" ||
                ((it.arousal ?: 1.0) < -0.3)
            },
            sortBy      = { (it.arousal ?: 0.0) },   // dal più calmo
        ),
        Preset(
            id          = "romantic",
            name        = "Romantic Dinner",
            emoji       = "🕯️",
            description = "Mood romantico, tempo lento-medio",
            filter      = {
                it.mood == "Romantico" ||
                (it.mood == "Rilassato" && (it.valence ?: 0.0) > 0.2)
            },
            sortBy      = { -(it.valence ?: 0.0) },
        ),
        Preset(
            id          = "workout",
            name        = "Workout",
            emoji       = "💪",
            description = "BPM alto, aggressività o energia",
            filter      = {
                ((it.tempoBpm ?: 0.0) > 120.0) &&
                (it.mood == "Energico" || it.mood == "Aggressivo" || it.mood == "Festivo")
            },
            sortBy      = { -(it.tempoBpm ?: 0.0) },
        ),
        Preset(
            id          = "melancholy",
            name        = "Rainy Day",
            emoji       = "🌧️",
            description = "Nostalgia e malinconia",
            filter      = { it.mood == "Malinconico" || it.mood == "Nostalgico" },
            sortBy      = { (it.valence ?: 0.0) },
        ),
        Preset(
            id          = "happy",
            name        = "Good Vibes",
            emoji       = "☀️",
            description = "Tutto ciò che è positivo",
            filter      = {
                it.mood == "Positivo" || it.mood == "Festivo" ||
                ((it.valence ?: 0.0) > 0.5)
            },
            sortBy      = { -(it.valence ?: 0.0) },
        ),
    )

    fun generate(preset: Preset, allSongs: List<Song>): List<Song> {
        return allSongs
            .filter { it.mood != null && preset.filter(it) }
            .sortedBy(preset.sortBy)
            .take(preset.maxSongs)
    }
}
