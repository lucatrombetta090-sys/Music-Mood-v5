package com.musicmood.profile

import android.graphics.Color

/**
 * I 9 archetipi musicali, mappati 1:1 sui mood del classificatore v5.
 * Ogni archetipo ha nome, emoji, tagline e colore.
 */
data class Archetype(
    val moodKey: String,
    val name: String,
    val emoji: String,
    val tagline: String,
    val color: Int,
)

object PersonalityTypes {

    val ALL: List<Archetype> = listOf(
        Archetype("Energico",       "The Sparkle",          "⚡",
            "Vivi al massimo, ami i ritmi che ti fanno muovere",
            Color.parseColor("#FF5722")),
        Archetype("Festivo",        "The Party Soul",       "🎉",
            "La tua vita è una festa, la musica è la colonna sonora",
            Color.parseColor("#FFC107")),
        Archetype("Positivo",       "The Sunshine",         "☀️",
            "Trasformi ogni momento in luce",
            Color.parseColor("#FFEB3B")),
        Archetype("Aggressivo",     "The Rebel",            "🔥",
            "Energia pura, intensità senza filtri",
            Color.parseColor("#B71C1C")),
        Archetype("Concentrazione", "The Focused Mind",     "🎯",
            "Cerchi profondità, ami la concentrazione",
            Color.parseColor("#3F51B5")),
        Archetype("Rilassato",      "The Zen Master",       "🌿",
            "Calma e bellezza, ovunque tu vada",
            Color.parseColor("#4CAF50")),
        Archetype("Romantico",      "The Hopeless Romantic","💕",
            "Vivi di emozioni e dettagli intimi",
            Color.parseColor("#E91E63")),
        Archetype("Nostalgico",     "The Time Traveler",    "🌅",
            "Trovi bellezza nei ricordi e nel passato",
            Color.parseColor("#9C27B0")),
        Archetype("Malinconico",    "The Deep Thinker",     "🌧️",
            "Profondità, introspezione, anime sensibili",
            Color.parseColor("#37474F")),
    )

    /** Mappa moodKey -> Archetype per lookup veloce. */
    val BY_MOOD: Map<String, Archetype> = ALL.associateBy { it.moodKey }

    /** Restituisce l'archetipo dominante data la distribuzione mood. */
    fun dominant(distribution: Map<String, Int>): Archetype {
        val top = distribution.maxByOrNull { it.value }?.key
            ?: return ALL.first()
        return BY_MOOD[top] ?: ALL.first()
    }
}
