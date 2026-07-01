package com.musicmood.library

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.musicmood.MoodAnalysis
import com.musicmood.R
import com.musicmood.data.ArtworkRepository
import com.musicmood.data.Song
import kotlinx.coroutines.launch
import com.musicmood.mood.MoodPickerDialog
import androidx.appcompat.app.AlertDialog

@UnstableApi
class LibraryFragment : Fragment(R.layout.fragment_library) {

    private val vm: LibraryViewModel by viewModels()
    private lateinit var songAdapter: SongAdapter
    private lateinit var categoryAdapter: CategoryAdapter

    private lateinit var progress: ProgressBar
    private lateinit var emptyState: TextView
    private lateinit var counter: TextView
    private lateinit var chipGroup: ChipGroup
    private lateinit var chipScroll: View
    private lateinit var fabBatch: ExtendedFloatingActionButton
    private lateinit var categoryTabs: TabLayout
    private lateinit var recyclerSongs: RecyclerView
    private lateinit var recyclerCategories: RecyclerView
    private lateinit var groupBanner: MaterialCardView
    private lateinit var groupTitle: TextView
    private lateinit var groupSubtitle: TextView
    private lateinit var btnGroupBack: ImageButton

    private val moodFilters = listOf(
        "Energico", "Positivo", "Aggressivo", "Malinconico",
        "Romantico", "Rilassato", "Nostalgico", "Concentrazione", "Festivo",
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progress           = view.findViewById(R.id.progress)
        emptyState         = view.findViewById(R.id.emptyState)
        counter            = view.findViewById(R.id.counter)
        chipGroup          = view.findViewById(R.id.chipGroup)
        chipScroll         = view.findViewById(R.id.chipScroll)
        fabBatch           = view.findViewById(R.id.fabBatch)
        categoryTabs       = view.findViewById(R.id.categoryTabs)
        recyclerSongs      = view.findViewById(R.id.recyclerView)
        recyclerCategories = view.findViewById(R.id.recyclerCategories)
        groupBanner        = view.findViewById(R.id.groupBanner)
        groupTitle         = view.findViewById(R.id.groupTitle)
        groupSubtitle      = view.findViewById(R.id.groupSubtitle)
        btnGroupBack       = view.findViewById(R.id.btnGroupBack)

        // Long-press sul counter → mostra menu sort ordinamento
        counter.setOnClickListener { showSortMenu(counter) }
        counter.setOnLongClickListener { showSortMenu(counter); true }

        val artworkRepo = ArtworkRepository.get(requireContext())
        songAdapter = SongAdapter(
            onClick = ::onSongClicked,
            onLongClick = ::onSongLongClicked,
        )
        categoryAdapter = CategoryAdapter(onClick = { group ->
            vm.enterGroup(group.key)
        })

        recyclerSongs.layoutManager = LinearLayoutManager(requireContext())
        recyclerSongs.adapter = songAdapter
        recyclerSongs.setHasFixedSize(true)

        recyclerCategories.layoutManager = LinearLayoutManager(requireContext())
        recyclerCategories.adapter = categoryAdapter
        recyclerCategories.setHasFixedSize(true)

        buildChips()
        fabBatch.setOnClickListener { onBatchClicked() }
        btnGroupBack.setOnClickListener { vm.exitGroup() }

        categoryTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                vm.setCategory(CategoryType.fromTabIndex(tab.position))
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                vm.exitGroup()
            }
        })

        vm.loadLibrary()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeLibrary() }
                launch { observeAnalysis() }
                launch { observeAnalyzedCount() }
                launch { observeCategories() }
                launch { observeCategorySelection() }
                launch { observeGroupSelection() }
                launch { observeFolderPathStack() }
            }
        }

        vm.batchWorkInfo.observe(viewLifecycleOwner) { infos ->
            updateBatchUi(infos?.firstOrNull()?.state)
        }
    }

    private fun showSortMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.library_sort_menu, popup.menu)
        val currentOrder = vm.sortOrder.value
        popup.menu.findItem(R.id.sort_az).isChecked = currentOrder == SortOrder.AZ
        popup.menu.findItem(R.id.sort_za).isChecked = currentOrder == SortOrder.ZA
        popup.menu.findItem(R.id.sort_count_desc).isChecked = currentOrder == SortOrder.COUNT_DESC
        popup.menu.findItem(R.id.sort_count_asc).isChecked = currentOrder == SortOrder.COUNT_ASC

        popup.setOnMenuItemClickListener { item ->
            val newOrder = when (item.itemId) {
                R.id.sort_az -> SortOrder.AZ
                R.id.sort_za -> SortOrder.ZA
                R.id.sort_count_desc -> SortOrder.COUNT_DESC
                R.id.sort_count_asc -> SortOrder.COUNT_ASC
                else -> return@setOnMenuItemClickListener false
            }
            vm.setSortOrder(newOrder)
            true
        }
        popup.show()
    }

    private fun buildChips() {
        chipGroup.removeAllViews()
        val allChip = Chip(requireContext()).apply {
            text = "Tutti"
            isCheckable = true
            isChecked = true
            setOnClickListener { vm.setMoodFilter(null) }
        }
        chipGroup.addView(allChip)
        moodFilters.forEach { mood ->
            val chip = Chip(requireContext()).apply {
                text = mood
                isCheckable = true
                setOnClickListener {
                    vm.setMoodFilter(if (isChecked) mood else null)
                }
            }
            chipGroup.addView(chip)
        }
    }

    private suspend fun observeLibrary() {
        vm.library.collect { state ->
            when (state) {
                LibraryUiState.Idle -> Unit
                LibraryUiState.Loading -> {
                    progress.isVisible = true
                    emptyState.isVisible = false
                }
                is LibraryUiState.Loaded -> {
                    progress.isVisible = false
                    songAdapter.submitList(state.songs)
                    val showSongs = state.songs.isNotEmpty() ||
                            (vm.category.value == CategoryType.SONGS ||
                                    vm.groupKey.value != null ||
                                    vm.folderPathStack.value.isNotEmpty())
                    recyclerSongs.isVisible = state.songs.isNotEmpty()
                    emptyState.isVisible = state.songs.isEmpty() &&
                            vm.categories.value.isEmpty()
                }
                is LibraryUiState.Error -> {
                    progress.isVisible = false
                    emptyState.text = "Errore: ${state.message}"
                    emptyState.isVisible = true
                }
            }
        }
    }

    private suspend fun observeCategories() {
        vm.categories.collect { categories ->
            categoryAdapter.submitList(categories)
            recyclerCategories.isVisible = categories.isNotEmpty()
        }
    }

    private suspend fun observeCategorySelection() {
        vm.category.collect { type ->
            chipScroll.isVisible = (type == CategoryType.SONGS)
        }
    }

    private suspend fun observeGroupSelection() {
        vm.groupKey.collect { key ->
            if (key != null && vm.category.value != CategoryType.FOLDERS) {
                groupBanner.isVisible = true
                groupTitle.text = key
                val n = (vm.library.value as? LibraryUiState.Loaded)?.songs?.size ?: 0
                groupSubtitle.text = "$n brani"
            } else if (vm.folderPathStack.value.isEmpty()) {
                groupBanner.isVisible = false
            }
        }
    }

    private suspend fun observeFolderPathStack() {
        vm.folderPathStack.collect { stack ->
            if (stack.isNotEmpty()) {
                groupBanner.isVisible = true
                groupTitle.text = stack.last()
                groupSubtitle.text = "📁 " + stack.joinToString(" / ")
            } else if (vm.groupKey.value == null) {
                groupBanner.isVisible = false
            }
        }
    }

    private suspend fun observeAnalysis() {
        vm.analysis.collect { state ->
            when (state) {
                AnalysisUiState.Idle -> Unit
                is AnalysisUiState.Running -> Snackbar.make(
                    requireView(), "Analisi: ${state.song.title}…",
                    Snackbar.LENGTH_SHORT
                ).show()
                is AnalysisUiState.Done -> {
                    showResultDialog(state.song, state.result)
                    vm.resetAnalysisState()
                }
                is AnalysisUiState.Failed -> {
                    Snackbar.make(requireView(), "Errore: ${state.message}",
                        Snackbar.LENGTH_LONG).show()
                    vm.resetAnalysisState()
                }
            }
        }
    }

    private suspend fun observeAnalyzedCount() {
        vm.analyzedCount.collect { count ->
            val total = (vm.library.value as? LibraryUiState.Loaded)?.songs?.size ?: 0
            counter.text = "$total brani • $count analizzati  ⋮"
        }
    }

    private fun updateBatchUi(state: WorkInfo.State?) {
        fabBatch.text = when (state) {
            WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> getString(R.string.batch_stop)
            else -> getString(R.string.batch_start)
        }
        fabBatch.setIconResource(R.drawable.ic_analyze)
    }

    private fun onSongClicked(song: Song) {
        val currentList = (vm.library.value as? LibraryUiState.Loaded)?.songs ?: listOf(song)
        vm.playSong(song, currentList)
        Snackbar.make(requireView(), "▶ ${song.title}", Snackbar.LENGTH_SHORT).show()
    }

/** Long-press → menu contestuale con analisi / cambio mood. */
    private fun onSongLongClicked(song: Song) {
        val options = arrayOf(
            "🎯 Analizza mood ora",
            "🏷️ Cambia mood manualmente",
        )
        AlertDialog.Builder(requireContext())
            .setTitle(song.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> vm.analyze(song)
                    1 -> showMoodPicker(song)
                }
            }
            .show()
    }

    private fun showMoodPicker(song: Song) {
        MoodPickerDialog(
            context         = requireContext(),
            songTitle       = song.title,
            currentDspMood  = song.mood,
            currentUserMood = song.userMood,
            onPick = { moodChosen ->
                vm.setUserMood(song.id, moodChosen)
                Snackbar.make(requireView(),
                    "Mood aggiornato: $moodChosen",
                    Snackbar.LENGTH_SHORT).show()
            },
            onReset = {
                vm.clearUserMood(song.id)
                Snackbar.make(requireView(),
                    "Mood ripristinato al valore DSP",
                    Snackbar.LENGTH_SHORT).show()
            },
        ).show()
    }

    private fun onBatchClicked() {
        val info = vm.batchWorkInfo.value?.firstOrNull()
        val running = info?.state == WorkInfo.State.RUNNING ||
                info?.state == WorkInfo.State.ENQUEUED
        if (running) {
            vm.cancelBatchAnalysis()
            Snackbar.make(requireView(), R.string.batch_cancelled, Snackbar.LENGTH_SHORT).show()
        } else {
            vm.startBatchAnalysis()
            Snackbar.make(requireView(), R.string.batch_started, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showResultDialog(song: Song, a: MoodAnalysis) {
        val body = buildString {
            appendLine("Mood: ${a.mood}")
            appendLine("Confidenza: ${"%.0f".format(a.confidence * 100)}%")
            appendLine()
            appendLine("Valenza : ${"%+.2f".format(a.valence)}")
            appendLine("Arousal : ${"%+.2f".format(a.arousal)}")
            appendLine("Tempo   : ${"%.1f".format(a.tempoBpm)} BPM")
            append("Tonalità: ${a.key} ${a.mode}")
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("🎯 ${song.title}")
            .setMessage(body)
            .setPositiveButton("Chiudi", null)
            .setNeutralButton("Riproduci") { _, _ -> onSongClicked(song) }
            .show()
    }
}
