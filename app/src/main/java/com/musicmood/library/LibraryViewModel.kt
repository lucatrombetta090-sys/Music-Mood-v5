package com.musicmood.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musicmood.audio.AudioDecoder
import com.musicmood.data.Song
import com.chaquo.python.Python
import com.musicmood.MoodAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LibraryUiState {
    data object Idle                              : LibraryUiState
    data object Loading                           : LibraryUiState
    data class  Loaded(val songs: List<Song>)     : LibraryUiState
    data class  Error(val message: String)        : LibraryUiState
}

sealed interface AnalysisUiState {
    data object Idle                                       : AnalysisUiState
    data class  Running(val song: Song)                    : AnalysisUiState
    data class  Done(val song: Song, val result: MoodAnalysis) : AnalysisUiState
    data class  Failed(val song: Song, val message: String): AnalysisUiState
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MediaStoreRepository(app)
    private val decoder = AudioDecoder(app)

    private val _library = MutableStateFlow<LibraryUiState>(LibraryUiState.Idle)
    val library: StateFlow<LibraryUiState> = _library.asStateFlow()

    private val _analysis = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Idle)
    val analysis: StateFlow<AnalysisUiState> = _analysis.asStateFlow()

    fun loadLibrary() {
        if (_library.value is LibraryUiState.Loading) return
        viewModelScope.launch {
            _library.value = LibraryUiState.Loading
            try {
                val songs = withContext(Dispatchers.IO) { repo.loadAllSongs() }
                _library.value = LibraryUiState.Loaded(songs)
            } catch (e: Exception) {
                _library.value = LibraryUiState.Error(e.message ?: "Errore sconosciuto")
            }
        }
    }

    fun analyze(song: Song) {
        viewModelScope.launch {
            _analysis.value = AnalysisUiState.Running(song)
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    // 1) Decode PCM (max 30 secondi a partire da metà brano)
                    val pcm = decoder.decodeWindow(
                        uri = song.uri,
                        startMs = (song.durationMs / 2).coerceAtLeast(0),
                        durationMs = 30_000L,
                    )
                    // 2) Chiamata al motore Python
                    val py = Python.getInstance()
                    val module = py.getModule("music_analyzer")
                    val pyResult = module.callAttr(
                        "analyze_pcm",
                        pcm.bytes,
                        pcm.sampleRate,
                        pcm.channels,
                        pcm.durationMs.toInt(),
                        song.title,
                        song.artist,
                    )
                    MoodAnalysis.fromPy(pyResult)
                }
            }
            _analysis.value = result.fold(
                onSuccess = { AnalysisUiState.Done(song, it) },
                onFailure = {
                    it.printStackTrace()
                    AnalysisUiState.Failed(song, it.message ?: "Errore analisi")
                }
            )
        }
    }

    fun resetAnalysisState() { _analysis.value = AnalysisUiState.Idle }
}
