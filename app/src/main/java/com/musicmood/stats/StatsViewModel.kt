package com.musicmood.stats

import android.app.Application
import android.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musicmood.data.MoodRepository
import com.musicmood.data.Song
import com.musicmood.data.withMood
import com.musicmood.library.MediaStoreRepository
import com.musicmood.player.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StatsUiData(
    val totalAnalyzed: Int = 0,
    val distribution: List<MoodDistributionView.Slice> = emptyList(),
    val avgBpm: Double = 0.0,
    val avgValence: Double = 0.0,
    val avgArousal: Double = 0.0,
    val topKey: String = "—",
    val topArtists: List<Pair<String, Int>> = emptyList(),
)

class StatsViewModel(app: Application) : AndroidViewModel(app) {

    private val moodRepo  = MoodRepository.get(app)
    private val mediaRepo = MediaStoreRepository(app)
    private val player    = PlayerController.get(app)

    private val _stats = MutableStateFlow(StatsUiData())
    val stats: StateFlow<StatsUiData> = _stats.asStateFlow()

    private val _analyzedSongs = MutableStateFlow<List<Song>>(emptyList())
    val analyzedSongs: StateFlow<List<Song>> = _analyzedSongs.asStateFlow()

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

    fun load() {
        viewModelScope.launch {
            moodRepo.observeAll().collect { entities ->
                val songs = withContext(Dispatchers.IO) {
                    mediaRepo.loadAllSongs().associateBy { it.id }
                }
                val merged = entities.mapNotNull { e ->
                    songs[e.songId]?.withMood(e)
                }
                _analyzedSongs.value = merged
                _stats.value = computeStats(entities, merged)
            }
        }
    }

    private fun computeStats(
        entities: List<com.musicmood.data.db.MoodEntity>,
        merged: List<Song>,
    ): StatsUiData {
        if (entities.isEmpty()) return StatsUiData()

        val byMood = entities.groupBy { it.mood }
        val distribution = byMood.map { (mood, list) ->
            MoodDistributionView.Slice(
                label = mood,
                count = list.size,
                color = moodColors[mood] ?: Color.GRAY,
            )
        }

        val avgBpm     = entities.map { it.tempoBpm }.average()
        val avgValence = entities.map { it.valence }.average()
        val avgArousal = entities.map { it.arousal }.average()

        val topKey = entities.groupBy { "${it.musicKey} ${it.mode}" }
            .maxByOrNull { it.value.size }?.key ?: "—"

        val topArtists = merged
            .groupingBy { it.artist.ifBlank { "Sconosciuto" } }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(5)

        return StatsUiData(
            totalAnalyzed = entities.size,
            distribution  = distribution,
            avgBpm        = avgBpm,
            avgValence    = avgValence,
            avgArousal    = avgArousal,
            topKey        = topKey,
            topArtists    = topArtists,
        )
    }

    fun playPlaylist(preset: PlaylistGenerator.Preset) {
        val items = PlaylistGenerator.generate(preset, _analyzedSongs.value)
        if (items.isNotEmpty()) {
            player.playSong(items.first(), items)
        }
    }
}
