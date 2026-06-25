package com.musicmood.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.musicmood.data.MoodRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@UnstableApi
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val controller = PlayerController.get(app)
    private val moodRepo = MoodRepository.get(app)

    private val _progress = MutableStateFlow(PlayerProgress(0L, 0L))
    val progress: StateFlow<PlayerProgress> = _progress.asStateFlow()

    private val _currentMood = MutableStateFlow<String?>(null)
    val currentMood: StateFlow<String?> = _currentMood.asStateFlow()

    val playerState = controller.state

    init {
        startProgressTicker()
        observeMoodForCurrentSong()
    }

    private fun startProgressTicker() {
        viewModelScope.launch {
            while (true) {
                _progress.value = controller.getProgress()
                delay(500)
            }
        }
    }

    private fun observeMoodForCurrentSong() {
        viewModelScope.launch {
            controller.state.collect { state ->
                val id = controller.currentSongId()
                _currentMood.value = if (id != null) {
                    moodRepo.findById(id)?.mood
                } else null
            }
        }
    }

    fun toggle() = controller.toggle()
    fun next() = controller.next()
    fun prev() = controller.prev()
    fun seekToMs(positionMs: Long) = controller.seekTo(positionMs)
}

data class PlayerProgress(val positionMs: Long, val durationMs: Long)
