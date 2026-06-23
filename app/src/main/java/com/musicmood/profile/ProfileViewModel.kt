package com.musicmood.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musicmood.data.MoodRepository
import com.musicmood.data.Song
import com.musicmood.data.withMood
import com.musicmood.library.MediaStoreRepository
import com.musicmood.player.PlayerController
import com.musicmood.stats.PlaylistGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileUiData(
    val totalAnalyzed: Int = 0,
    val archetype: Archetype? = null,
    val moodPercentages: List<Triple<String, Int, Int>> = emptyList(),
    val radarAxes: List<RadarChartView.Axis> = emptyList(),
    val topArtist: String? = null,
    val dailySuggestion: DailyMoodAdvisor.Suggestion = DailyMoodAdvisor.forNow(),
)

class ProfileViewModel(app: Application) : AndroidViewModel(app) {

    private val moodRepo  = MoodRepository.get(app)
    private val mediaRepo = MediaStoreRepository(app)
    private val player    = PlayerController.get(app)

    private val _profile = MutableStateFlow(ProfileUiData())
    val profile: StateFlow<ProfileUiData> = _profile.asStateFlow()

    private val _analyzedSongs = MutableStateFlow<List<Song>>(emptyList())
    val analyzedSongs: StateFlow<List<Song>> = _analyzedSongs.asStateFlow()

    fun load() {
        viewModelScope.launch {
            moodRepo.observeAll().collect { entities ->
                val songsById = withContext(Dispatchers.IO) {
                    mediaRepo.loadAllSongs().associateBy { it.id }
                }
                val merged = entities.mapNotNull { e ->
                    songsById[e.songId]?.withMood(e)
                }
                _analyzedSongs.value = merged
                _profile.value = computeProfile(entities, merged)
            }
        }
    }

    private fun computeProfile(
        entities: List<com.musicmood.data.db.MoodEntity>,
        merged: List<Song>,
    ): ProfileUiData {
        if (entities.isEmpty()) {
            return ProfileUiData(dailySuggestion = DailyMoodAdvisor.forNow())
        }
        val total = entities.size
        val byMood = entities.groupingBy { it.mood }.eachCount()
        val archetype = PersonalityTypes.dominant(byMood)

        // Top mood ordinati, con percentuali e colori
        val moodPct = byMood
            .toList()
            .sortedByDescending { it.second }
            .map { (mood, count) ->
                val pct = (count * 100.0 / total).toInt()
                val color = PersonalityTypes.BY_MOOD[mood]?.color ?: 0xFF999999.toInt()
                Triple(mood, pct, color)
            }

        // Radar a 9 vertici (TUTTI i mood, anche quelli a 0%)
        val maxCount = byMood.values.maxOrNull()?.toFloat() ?: 1f
        val radarAxes = PersonalityTypes.ALL.map { arche ->
            val count = byMood[arche.moodKey] ?: 0
            RadarChartView.Axis(
                label = arche.moodKey,
                value = if (maxCount > 0) count / maxCount else 0f,
                color = arche.color,
            )
        }

        // Top artist tra i brani del mood dominante
        val topArtist = merged
            .filter { it.mood == archetype.moodKey }
            .groupingBy { it.artist.ifBlank { "Sconosciuto" } }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        return ProfileUiData(
            totalAnalyzed    = total,
            archetype        = archetype,
            moodPercentages  = moodPct,
            radarAxes        = radarAxes,
            topArtist        = topArtist,
            dailySuggestion  = DailyMoodAdvisor.forNow(),
        )
    }

    /** Step 7 — Avvia la playlist suggerita in base all'orario. */
    fun playDailySuggestion() {
        val suggestion = _profile.value.dailySuggestion
        val songs = _analyzedSongs.value
        if (songs.isEmpty()) return
        val playlist = DailyMoodAdvisor.generatePlaylist(suggestion, songs)
        if (playlist.isNotEmpty()) {
            player.playSong(playlist.first(), playlist)
        }
    }

    /** Genera l'URI dell'immagine PNG da condividere. */
    suspend fun buildShareUri(): android.net.Uri? = withContext(Dispatchers.IO) {
        val p = _profile.value
        val a = p.archetype ?: return@withContext null
        val renderer = ShareImageRenderer(getApplication())
        renderer.renderAndShare(
            ShareImageRenderer.Profile(
                archetype     = a,
                topMoods      = p.moodPercentages.take(4),
                totalAnalyzed = p.totalAnalyzed,
                topArtist     = p.topArtist,
            )
        )
    }
}
