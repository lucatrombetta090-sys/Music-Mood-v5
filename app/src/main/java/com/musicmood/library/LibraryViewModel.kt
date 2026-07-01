package com.musicmood.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.work.*
import com.chaquo.python.Python
import com.musicmood.MoodAnalysis
import com.musicmood.audio.AudioDecoder
import com.musicmood.audio.MoodAnalysisOrchestrator
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
    data object Idle : LibraryUiState
    data object Loading : LibraryUiState
    data class Loaded(val songs: List<Song>) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

sealed interface AnalysisUiState {
    data object Idle : AnalysisUiState
    data class Running(val song: Song) : AnalysisUiState
    data class Done(val song: Song, val result: MoodAnalysis) : AnalysisUiState
    data class Failed(val song: Song, val message: String) : AnalysisUiState
}

@UnstableApi
class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val mediaRepo = MediaStoreRepository(app)
    private val moodRepo = MoodRepository.get(app)
    private val decoder = AudioDecoder(app)
    private val workManager = WorkManager.getInstance(app)
    private val player = PlayerController.get(app)

    private val _library = MutableStateFlow<LibraryUiState>(LibraryUiState.Idle)
    val library: StateFlow<LibraryUiState> = _library.asStateFlow()

    private val _analysis = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Idle)
    val analysis: StateFlow<AnalysisUiState> = _analysis.asStateFlow()

    private val _selectedMood = MutableStateFlow<String?>(null)
    val selectedMood: StateFlow<String?> = _selectedMood.asStateFlow()

    private val _category = MutableStateFlow(CategoryType.SONGS)
    val category: StateFlow<CategoryType> = _category.asStateFlow()

    private val _groupKey = MutableStateFlow<String?>(null)
    val groupKey: StateFlow<String?> = _groupKey.asStateFlow()

    /** Path corrente durante la navigation nested delle cartelle. */
    private val _folderPathStack = MutableStateFlow<List<String>>(emptyList())
    val folderPathStack: StateFlow<List<String>> = _folderPathStack.asStateFlow()

    private val _categories = MutableStateFlow<List<CategoryGroup>>(emptyList())
    val categories: StateFlow<List<CategoryGroup>> = _categories.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.COUNT_DESC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    val analyzedCount: StateFlow<Int> = moodRepo.observeCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val batchWorkInfo = workManager
        .getWorkInfosForUniqueWorkLiveData(MoodAnalysisWorker.WORK_NAME)

    private var mergedSongs: List<Song> = emptyList()

    fun loadLibrary() {
        if (_library.value is LibraryUiState.Loading) return
        viewModelScope.launch {
            _library.value = LibraryUiState.Loading
            try {
                val rawSongs = withContext(Dispatchers.IO) { mediaRepo.loadAllSongs() }
                moodRepo.observeAll().collect { moodList ->
                    val moodById = moodList.associateBy { it.songId }
                    mergedSongs = rawSongs.map { it.withMood(moodById[it.id]) }
                    refreshUi()
                }
            } catch (e: Exception) {
                _library.value = LibraryUiState.Error(e.message ?: "Errore sconosciuto")
            }
        }
    }

    fun setMoodFilter(mood: String?) {
        _selectedMood.value = mood
        refreshUi()
    }

    fun setCategory(type: CategoryType) {
        _category.value = type
        _groupKey.value = null
        _folderPathStack.value = emptyList()
        refreshUi()
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        refreshUi()
    }

    fun enterGroup(key: String) {
        // Se siamo in FOLDERS, "entrare" in un folder significa aggiungere al path stack
        if (_category.value == CategoryType.FOLDERS) {
            val newStack = _folderPathStack.value + key
            _folderPathStack.value = newStack
        } else {
            _groupKey.value = key
        }
        refreshUi()
    }

    fun exitGroup() {
        if (_category.value == CategoryType.FOLDERS && _folderPathStack.value.isNotEmpty()) {
            _folderPathStack.value = _folderPathStack.value.dropLast(1)
        } else {
            _groupKey.value = null
        }
        refreshUi()
    }

    private fun refreshUi() {
        val songs = mergedSongs
        if (songs.isEmpty()) {
            _library.value = LibraryUiState.Loaded(emptyList())
            _categories.value = emptyList()
            return
        }

        val filteredByMood = applyMoodFilter(songs)

        when (_category.value) {
            CategoryType.SONGS -> {
                _categories.value = emptyList()
                _library.value = LibraryUiState.Loaded(sortSongs(filteredByMood))
            }
            CategoryType.FOLDERS -> {
                renderFoldersNested(filteredByMood)
            }
            else -> {
                val gk = _groupKey.value
                if (gk == null) {
                    _categories.value = buildCategoriesFlat(filteredByMood, _category.value)
                    _library.value = LibraryUiState.Loaded(emptyList())
                } else {
                    val songsInGroup = filteredByMood.filter {
                        keyForSong(it, _category.value) == gk
                    }
                    _library.value = LibraryUiState.Loaded(sortSongs(songsInGroup))
                    _categories.value = emptyList()
                }
            }
        }
    }

    /**
     * Fix #2: naviga le cartelle come nested tree, non piatte.
     * Il currentPath è determinato dallo stack.
     */
    private fun renderFoldersNested(songs: List<Song>) {
        val currentPath = _folderPathStack.value

        // Filtra le canzoni sotto il currentPath
        val songsUnderPath = songs.filter { song ->
            val parts = folderParts(song.folderPath)
            if (currentPath.isEmpty()) true
            else parts.size >= currentPath.size &&
                    parts.take(currentPath.size) == currentPath
        }

        // Se non ci sono sub-folder più profondi, mostra i brani direttamente
        val hasDeeperFolders = songsUnderPath.any {
            folderParts(it.folderPath).size > currentPath.size
        }

        if (!hasDeeperFolders && songsUnderPath.isNotEmpty()) {
            _library.value = LibraryUiState.Loaded(sortSongs(songsUnderPath))
            _categories.value = emptyList()
            return
        }

        // Raggruppa per il "next segment" nel path
        val groups = songsUnderPath
            .filter { folderParts(it.folderPath).size > currentPath.size }
            .groupBy { folderParts(it.folderPath)[currentPath.size] }

        val categoryGroups = groups.map { (segment, list) ->
            val totalDur = list.sumOf { it.durationMs }
            val cover = list.firstOrNull { it.albumArtUri != null } ?: list.first()
            CategoryGroup(
                key = segment,
                title = segment,
                subtitle = "${list.size} brani • ${formatDuration(totalDur)}",
                songCount = list.size,
                totalDurationMs = totalDur,
                coverSong = cover,
                fullPath = (currentPath + segment).joinToString("/"),
                isNavigable = true,
            )
        }

        _categories.value = sortCategories(categoryGroups)
        _library.value = LibraryUiState.Loaded(emptyList())
    }

    private fun folderParts(fullPath: String?): List<String> {
        if (fullPath.isNullOrBlank()) return emptyList()
        return fullPath.split("/", "\\").filter { it.isNotBlank() }
    }

    private fun applyMoodFilter(songs: List<Song>): List<Song> =
        _selectedMood.value?.let { filter -> songs.filter { it.mood == filter } } ?: songs

    private fun keyForSong(s: Song, cat: CategoryType): String = when (cat) {
        CategoryType.ARTISTS -> s.artist.ifBlank { "Sconosciuto" }
        CategoryType.ALBUMS  -> s.album.ifBlank { "Sconosciuto" }
        CategoryType.GENRES  -> (s.genre ?: "Sconosciuto").ifBlank { "Sconosciuto" }
        CategoryType.YEARS   -> s.year?.toString() ?: "Sconosciuto"
        CategoryType.FOLDERS -> (s.folderPath ?: "Sconosciuto").ifBlank { "Sconosciuto" }
        CategoryType.SONGS   -> s.id.toString()
    }

    private fun buildCategoriesFlat(songs: List<Song>, cat: CategoryType): List<CategoryGroup> {
        val grouped = songs.groupBy { keyForSong(it, cat) }
        val list = grouped.map { (key, l) ->
            val totalDur = l.sumOf { it.durationMs }
            val cover = l.firstOrNull { it.albumArtUri != null } ?: l.first()
            CategoryGroup(
                key = key,
                title = key,
                subtitle = "${l.size} brani • ${formatDuration(totalDur)}",
                songCount = l.size,
                totalDurationMs = totalDur,
                coverSong = cover,
            )
        }
        return sortCategories(list)
    }

    private fun sortCategories(items: List<CategoryGroup>): List<CategoryGroup> =
        when (_sortOrder.value) {
            SortOrder.AZ         -> items.sortedBy { it.title.lowercase() }
            SortOrder.ZA         -> items.sortedByDescending { it.title.lowercase() }
            SortOrder.COUNT_DESC -> items.sortedByDescending { it.songCount }
            SortOrder.COUNT_ASC  -> items.sortedBy { it.songCount }
        }

    private fun sortSongs(items: List<Song>): List<Song> =
        when (_sortOrder.value) {
            SortOrder.AZ, SortOrder.COUNT_DESC -> items.sortedBy { it.title.lowercase() }
            SortOrder.ZA, SortOrder.COUNT_ASC  -> items.sortedByDescending { it.title.lowercase() }
        }

    private fun formatDuration(ms: Long): String {
        val sec = ms / 1000
        val h = sec / 3600
        val m = (sec % 3600) / 60
        return if (h > 0) "%dh %02dm".format(h, m) else "%dm".format(m)
    }

    fun analyze(song: Song) {
        viewModelScope.launch {
            _analysis.value = AnalysisUiState.Running(song)
            val cached = moodRepo.findById(song.id)
            if (cached != null) {
                _analysis.value = AnalysisUiState.Done(
                    song,
                    MoodAnalysis(
                        mood = cached.mood,
                        confidence = cached.confidence,
                        valence = cached.valence,
                        arousal = cached.arousal,
                        tempoBpm = cached.tempoBpm,
                        key = cached.musicKey,
                        mode = cached.mode,
                    )
                )
                return@launch
            }
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    MoodAnalysisOrchestrator(getApplication()).analyze(
                        uri = song.uri, title = song.title,
                        artist = song.artist, durationMs = song.durationMs,
                    )
                }
            }
            _analysis.value = result.fold(
                onSuccess = {
                    moodRepo.save(song.id, it.analysis)
                    AnalysisUiState.Done(song, it.analysis)
                },
                onFailure = {
                    it.printStackTrace()
                    AnalysisUiState.Failed(song, it.message ?: "Errore analisi")
                }
            )
        }
    }

    fun resetAnalysisState() { _analysis.value = AnalysisUiState.Idle }

    fun playSong(song: Song, queue: List<Song>) { player.playSong(song, queue) }

    fun startBatchAnalysis() {
        val request = OneTimeWorkRequestBuilder<MoodAnalysisWorker>()
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        workManager.enqueueUniqueWork(
            MoodAnalysisWorker.WORK_NAME, ExistingWorkPolicy.KEEP, request,
        )
    }

    fun cancelBatchAnalysis() {
        workManager.cancelUniqueWork(MoodAnalysisWorker.WORK_NAME)
    }

    fun clearAllAnalysis() {
        viewModelScope.launch { moodRepo.clearAll() }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Mood manuale (override utente)
    // ──────────────────────────────────────────────────────────────────────
    fun setUserMood(songId: Long, mood: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                moodRepo.setUserMood(songId, mood)
            }
            // Il Flow observeAll re-emette e libraryFragment riceve refresh automatico
        }
    }

    fun clearUserMood(songId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                moodRepo.clearUserMood(songId)
            }
        }
    }
}
