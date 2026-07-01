package com.musicmood.bubblemap

import android.app.Application
import android.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musicmood.data.MoodRepository
import com.musicmood.library.MediaStoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue
import kotlin.random.Random

class BubbleMapViewModel(app: Application) : AndroidViewModel(app) {

    private val moodRepo = MoodRepository.get(app)
    private val mediaRepo = MediaStoreRepository(app)

    private val _bubbles = MutableStateFlow<List<BubbleMapView.Bubble>>(emptyList())
    val bubbles: StateFlow<List<BubbleMapView.Bubble>> = _bubbles.asStateFlow()

    private val moodColors = mapOf(
        "Energico"       to Color.parseColor("#FF5722"),
        "Festivo"        to Color.parseColor("#FFC107"),
        "Positivo"       to Color.parseColor("#FFEB3B"),
        "Aggressivo"     to Color.parseColor("#B71C1C"),
        "Concentrazione" to Color.parseColor("#3F51B5"),
        "Rilassato"      to Color.parseColor("#4CAF50"),
        "Romantico"      to Color.parseColor("#E91E63"),
        "Nostalgico"     to Color.parseColor("#9C27B0"),
        "Malinconico"    to Color.parseColor("#37474F"),
    )

    /** Centroidi per detect di "sintetico". */
    private val centroids = mapOf(
        "Energico"       to (0.55f to 0.85f),
        "Festivo"        to (0.75f to 0.70f),
        "Positivo"       to (0.70f to 0.30f),
        "Aggressivo"     to (-0.35f to 0.85f),
        "Concentrazione" to (0.10f to 0.10f),
        "Rilassato"      to (0.40f to -0.40f),
        "Romantico"      to (0.45f to -0.20f),
        "Nostalgico"     to (-0.15f to -0.30f),
        "Malinconico"    to (-0.55f to -0.55f),
    )

    fun load() {
        viewModelScope.launch {
            moodRepo.observeAll().collect { entities ->
                val titles = withContext(Dispatchers.IO) {
                    mediaRepo.loadAllSongs().associateBy { it.id }
                }
                val items = entities.mapNotNull { e ->
                    val song = titles[e.songId] ?: return@mapNotNull null
                    val (vJ, aJ) = jitterIfSynthetic(
                        e.songId, e.mood, e.valence.toFloat(), e.arousal.toFloat()
                    )
                    BubbleMapView.Bubble(
                        songId  = e.songId,
                        title   = song.title,
                        artist  = song.artist,
                        valence = vJ,
                        arousal = aJ,
                        mood    = e.mood,
                        color   = moodColors[e.mood] ?: Color.GRAY,
                    )
                }
                _bubbles.value = items
            }
        }
    }

    /**
     * Se il brano ha coordinate esattamente uguali al centroide del suo mood,
     * significa che è stato analizzato con YAMNet vecchio (coords sintetiche).
     * Aggiungo jitter deterministico basato su songId così la posizione è stabile.
     */
    private fun jitterIfSynthetic(
        songId: Long, mood: String, valence: Float, arousal: Float
    ): Pair<Float, Float> {
        val centroid = centroids[mood] ?: return valence to arousal
        val isSynthetic =
            (valence - centroid.first).absoluteValue < 0.005f &&
            (arousal - centroid.second).absoluteValue < 0.005f
        if (!isSynthetic) return valence to arousal

        // jitter deterministico basato su songId (stesso brano → stessa posizione ogni volta)
        val rng = Random(songId)
        val jV = (rng.nextFloat() - 0.5f) * 0.20f  // ±0.10
        val jA = (rng.nextFloat() - 0.5f) * 0.20f
        return (valence + jV).coerceIn(-1f, 1f) to (arousal + jA).coerceIn(-1f, 1f)
    }
}
