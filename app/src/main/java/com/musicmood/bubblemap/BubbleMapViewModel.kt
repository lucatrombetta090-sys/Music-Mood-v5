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

    fun load() {
        viewModelScope.launch {
            moodRepo.observeAll().collect { entities ->
                val titles = withContext(Dispatchers.IO) {
                    mediaRepo.loadAllSongs().associateBy { it.id }
                }
                val items = entities.mapNotNull { e ->
                    val song = titles[e.songId] ?: return@mapNotNull null
                    BubbleMapView.Bubble(
                        songId  = e.songId,
                        title   = song.title,
                        artist  = song.artist,
                        valence = e.valence.toFloat(),
                        arousal = e.arousal.toFloat(),
                        mood    = e.mood,
                        color   = moodColors[e.mood] ?: Color.GRAY,
                    )
                }
                _bubbles.value = items
            }
        }
    }
}
