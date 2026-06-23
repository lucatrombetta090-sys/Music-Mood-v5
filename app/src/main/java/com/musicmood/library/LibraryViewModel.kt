package com.musicmood.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.chaquo.python.Python
import com.musicmood.MoodAnalysis
import com.musicmood.audio.AudioDecoder
import com.musicmood.data.MoodRepository
import com.musicmood.data.Song
import com.musicmood.data.withMood
import com.musicmood.player.PlayerController
import com.musicmood.worker.MoodAnalysisWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LibraryUiState {
    data object Idle                           : LibraryUiState
    data object Loading                        : LibraryUiState
    data class  Loaded(val songs: List<Song>)  : LibraryUiState
    data class  Error(val message: String)     : LibraryUiState
}

sealed interface AnalysisUiState {
    data object Idle                                                  : AnalysisUiState
    data class  Running(val song: Song)                               : AnalysisUiState
    data class  Done(val song: Song, val result: MoodAnalysis)        : AnalysisUiState
    data class  Failed(val song: Song, val message: String)           : AnalysisUiState
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val mediaRepo   = MediaStoreRepository(app)
    private val moodRepo    = MoodRepository.get(app)
    private val decoder     = AudioDecoder(app)
    private val workManager = WorkManager.getInstance(app)
    private val player      = PlayerController.get(app)

    private val _library = MutableStateFlow<LibraryUiState>(LibraryUiState.Idle)
    val library: StateFlow<LibraryUiState> = _library.asStateFlow()

    private val _analysis = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Idle)
    val analysis: StateFlow<AnalysisUiState> = _analysis.asStateFlow()

    private val _selectedMood = MutableStateFlow<String?>(null)
    val selectedMood: StateFlow<String?> = _selectedMood.asStateFlow()

    /** Numero totale di brani analizzati (per badge UI). */
    val analyzedCount: StateFlow<Int> = moodRepo.observeCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Stato del job WorkManager (LiveData per compatibilità con observe). */
    val batchWorkInfo = workManager
        .getWorkInfosForUniqueWorkLiveData(MoodAnalysisWorker.WORK_NAME)

    private var rawSongs: List<Song> = emptyList()

    // ──────────────────────────────────────────────────────────────────────
    // Caricamento libreria
    // ──────────────────────────────────────────────────────────────────────
    fun loadLibrary() {
        if (_library.value is LibraryUiState.Loading) return
        viewModelScope.launch {
            _library.value = LibraryUiState.Loading
            try {
                val songs = withContext(Dispatchers.IO) { mediaRepo.loadAllSongs() }
                rawSongs = songs

                // Merge in tempo reale con i dati di mood salvati su Room
                moodRepo.observeAll().collect { moodList ->
                    val moodById = moodList.associateBy { it.songId }
                    val merged = rawSongs.map { it.withMood(moodById[it.id]) }
                    _library.value = LibraryUiState.Loaded(applyFilter(merged))
                }
            } catch (e: Exception) {
                _library.value = LibraryUiState.Error(e.message ?: "Errore sconosciuto")
            }
        }
    }

    private fun applyFilter(songs: List<Song>): List<Song> =
        when (val filter = _selectedMood.value) {
            null -> songs
            else -> songs.filter { it.mood == filter }
        }

    fun setMoodFilter(mood: String?) {
        _selectedMood.value = mood
        val current = _library.value
        if (current is LibraryUiState.Loaded) {
            _library.value = LibraryUiState.Loaded(applyFilter(
                rawSongs.map { raw -> current.songs.find { it.id == raw.id } ?: raw }
            ))
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Analisi singola "on-tap" (cache-aware)
    // ──────────────────────────────────────────────────────────────────────
    fun analyze(song: Song) {
        viewModelScope.launch {
            _analysis.value = AnalysisUiState.Running(song)

            // 1) Cache hit?
            val cached = moodRepo.findById(song.id)
            if (cached != null) {
                _analysis.value = AnalysisUiState.Done(
                    song,
                    MoodAnalysis(
                        mood       = cached.mood,
                        confidence = cached.confidence,
                        valence    = cached.valence,
                        arousal    = cached.arousal,
                        tempoBpm   = cached.tempoBpm,
                        key        = cached.musicKey,
                        mode       = cached.mode,
                    )
                )
                return@launch
            }

            // 2) Cache miss → calcola e salva
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val pcm = decoder.decodeWindow(
                        uri        = song.uri,
                        startMs    = (song.durationMs / 2).coerceAtLeast(0),
                        durationMs = 30_000L,
                    )
                    val py = Python.getInstance()
                    val module = py.getModule("music_analyzer")
                    val pyResult = module.callAttr(
                        "analyze_pcm",
                        pcm.bytes, pcm.sampleRate, pcm.channels,
                        pcm.durationMs.toInt(),
                        song.title, song.artist,
                    )
                    MoodAnalysis.fromPy(pyResult)
                }
            }
            _analysis.value = result.fold(
                onSuccess = {
                    moodRepo.save(song.id, it)
                    AnalysisUiState.Done(song, it)
                },
                onFailure = {
                    it.printStackTrace()
                    AnalysisUiState.Failed(song, it.message ?: "Errore analisi")
                }
            )
        }
    }

    fun resetAnalysisState() { _analysis.value = AnalysisUiState.Idle }

    // ──────────────────────────────────────────────────────────────────────
    // Riproduzione (Step 3 — Media3)
    // ──────────────────────────────────────────────────────────────────────
    fun playSong(song: Song, queue: List<Song>) {
        player.playSong(song, queue)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Analisi batch (Step 2 — WorkManager)
    // ──────────────────────────────────────────────────────────────────────
    fun startBatchAnalysis() {
        val request = OneTimeWorkRequestBuilder<MoodAnalysisWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        workManager.enqueueUniqueWork(
            MoodAnalysisWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelBatchAnalysis() {
        workManager.cancelUniqueWork(MoodAnalysisWorker.WORK_NAME)
    }

    fun clearAllAnalysis() {
        viewModelScope.launch { moodRepo.clearAll() }
    }
}
