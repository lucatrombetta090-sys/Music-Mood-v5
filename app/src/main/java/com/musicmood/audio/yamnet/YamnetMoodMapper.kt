package com.musicmood.audio.yamnet

/**
 * Mappa le categorie AudioSet di YAMNet sui 9 mood Music-Mood.
 * Per ogni mood definiamo una lista di (categoria, peso).
 * Il punteggio finale del mood è la SOMMA pesata dei punteggi delle sue categorie.
 */
object YamnetMoodMapper {

    /** Mood Music-Mood ordinati. */
    val MOODS = listOf(
        "Festivo", "Energico", "Aggressivo", "Positivo",
        "Concentrazione", "Rilassato", "Romantico",
        "Nostalgico", "Malinconico",
    )

    private data class Weight(val label: String, val w: Float)

    /** Per ogni mood: combinazione lineare di tag AudioSet con relativo peso. */
    private val MOOD_MAP: Map<String, List<Weight>> = mapOf(
        "Festivo" to listOf(
            Weight("Happy music", 1.0f),
            Weight("Dance music", 0.9f),
            Weight("Electronic music", 0.7f),
            Weight("Pop music", 0.6f),
            Weight("Disco", 0.9f),
            Weight("Salsa music", 0.8f),
            Weight("Reggae", 0.5f),
            Weight("Funk", 0.6f),
            Weight("Latin music", 0.7f),
            Weight("Funny music", 0.3f),
        ),
        "Energico" to listOf(
            Weight("Exciting music", 1.0f),
            Weight("Rock music", 0.8f),
            Weight("Heavy metal", 0.6f),
            Weight("Punk rock", 0.7f),
            Weight("Drum and bass", 0.8f),
            Weight("Techno", 0.7f),
            Weight("Hip hop music", 0.6f),
            Weight("Rapping", 0.4f),
        ),
        "Aggressivo" to listOf(
            Weight("Angry music", 1.0f),
            Weight("Heavy metal", 1.0f),
            Weight("Scary music", 0.6f),
            Weight("Punk rock", 0.7f),
            Weight("Hard rock", 0.8f),
            Weight("Grunge", 0.7f),
            Weight("Screaming", 0.5f),
        ),
        "Positivo" to listOf(
            Weight("Happy music", 0.7f),
            Weight("Pop music", 0.8f),
            Weight("Funny music", 0.6f),
            Weight("Children's music", 0.7f),
            Weight("Country", 0.4f),
            Weight("Ska", 0.5f),
        ),
        "Concentrazione" to listOf(
            Weight("Classical music", 0.9f),
            Weight("Ambient music", 1.0f),
            Weight("New-age music", 0.8f),
            Weight("Computer music", 0.6f),
            Weight("Jazz", 0.5f),
            Weight("Piano", 0.6f),
            Weight("Instrumental", 0.5f),
        ),
        "Rilassato" to listOf(
            Weight("Tender music", 0.8f),
            Weight("Ambient music", 0.7f),
            Weight("New-age music", 0.7f),
            Weight("Folk music", 0.5f),
            Weight("Acoustic guitar", 0.6f),
            Weight("Bossa nova", 0.9f),
            Weight("Chant", 0.4f),
        ),
        "Romantico" to listOf(
            Weight("Tender music", 1.0f),
            Weight("Soul music", 0.8f),
            Weight("Rhythm and blues", 0.6f),
            Weight("Pop music", 0.3f),
            Weight("Singing", 0.4f),
            Weight("Background music", 0.2f),
        ),
        "Nostalgico" to listOf(
            Weight("Country", 0.6f),
            Weight("Folk music", 0.7f),
            Weight("Soul music", 0.5f),
            Weight("Blues", 0.7f),
            Weight("Rhythm and blues", 0.5f),
            Weight("Vintage", 0.5f),
            Weight("Music for children", 0.2f),
        ),
        "Malinconico" to listOf(
            Weight("Sad music", 1.0f),
            Weight("Blues", 0.7f),
            Weight("Gospel music", 0.5f),
            Weight("Christian music", 0.4f),
            Weight("Funeral music", 0.9f),
            Weight("Melancholic", 0.8f),
        ),
    )

    data class Prediction(
        val mood: String,
        val confidence: Float,
        val rawScores: Map<String, Float>,  // mood -> punteggio bruto
    )

    /**
     * @param yamnetOutputs mappa "AudioSet label name" -> score [0,1]
     * @return predizione del mood con maggior punteggio
     */
    fun predict(yamnetOutputs: Map<String, Float>): Prediction {
        // Normalizza le chiavi a lowercase per match case-insensitive
        val normalizedOutputs = yamnetOutputs.mapKeys { it.key.lowercase().trim() }

        val rawScores = mutableMapOf<String, Float>()
        for ((mood, weights) in MOOD_MAP) {
            var sum = 0f
            var totalWeight = 0f
            for (w in weights) {
                val score = normalizedOutputs[w.label.lowercase().trim()] ?: 0f
                sum += score * w.w
                totalWeight += w.w
            }
            rawScores[mood] = if (totalWeight > 0) sum / totalWeight else 0f
        }

        val best = rawScores.maxByOrNull { it.value }
        val sumAll = rawScores.values.sum().coerceAtLeast(0.0001f)
        val confidence = ((best?.value ?: 0f) / sumAll).coerceIn(0f, 1f)

        return Prediction(
            mood = best?.key ?: "Concentrazione",
            confidence = confidence,
            rawScores = rawScores,
        )
    }
}
