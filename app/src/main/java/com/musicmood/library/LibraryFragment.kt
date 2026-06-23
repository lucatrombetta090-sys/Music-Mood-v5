package com.musicmood.library

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.musicmood.MoodAnalysis
import com.musicmood.R
import com.musicmood.data.Song
import kotlinx.coroutines.launch

class LibraryFragment : Fragment(R.layout.fragment_library) {

    private val vm: LibraryViewModel by viewModels()
    private lateinit var adapter: SongAdapter
    private lateinit var progress: ProgressBar
    private lateinit var emptyState: TextView
    private lateinit var counter: TextView
    private lateinit var chipGroup: ChipGroup
    private lateinit var fabBatch: ExtendedFloatingActionButton

    private val moodFilters = listOf(
        "Energico", "Positivo", "Aggressivo", "Malinconico",
        "Romantico", "Rilassato", "Nostalgico", "Concentrazione", "Festivo",
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progress   = view.findViewById(R.id.progress)
        emptyState = view.findViewById(R.id.emptyState)
        counter    = view.findViewById(R.id.counter)
        chipGroup  = view.findViewById(R.id.chipGroup)
        fabBatch   = view.findViewById(R.id.fabBatch)

        adapter = SongAdapter(onClick = ::onSongClicked)

        view.findViewById<RecyclerView>(R.id.recyclerView).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@LibraryFragment.adapter
            setHasFixedSize(true)
        }

        buildChips()
        fabBatch.setOnClickListener { onBatchClicked() }

        vm.loadLibrary()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeLibrary() }
                launch { observeAnalysis() }
                launch { observeAnalyzedCount() }
            }
        }

        vm.batchWorkInfo.observe(viewLifecycleOwner) { infos ->
            val info = infos?.firstOrNull()
            updateBatchUi(info?.state)
        }
    }

    private fun buildChips() {
        chipGroup.removeAllViews()

        // Chip "Tutti"
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
                LibraryUiState.Idle    -> Unit
                LibraryUiState.Loading -> {
                    progress.visibility = View.VISIBLE
                    emptyState.visibility = View.GONE
                }
                is LibraryUiState.Loaded -> {
                    progress.visibility = View.GONE
                    adapter.submitList(state.songs)
                    emptyState.visibility =
                        if (state.songs.isEmpty()) View.VISIBLE else View.GONE
                }
                is LibraryUiState.Error -> {
                    progress.visibility = View.GONE
                    emptyState.text = "Errore: ${state.message}"
                    emptyState.visibility = View.VISIBLE
                }
            }
        }
    }

    private suspend fun observeAnalysis() {
        vm.analysis.collect { state ->
            when (state) {
                AnalysisUiState.Idle -> Unit
                is AnalysisUiState.Running -> {
                    Snackbar.make(requireView(),
                        "Analisi: ${state.song.title}…",
                        Snackbar.LENGTH_SHORT).show()
                }
                is AnalysisUiState.Done -> {
                    showResultDialog(state.song, state.result)
                    vm.resetAnalysisState()
                }
                is AnalysisUiState.Failed -> {
                    Snackbar.make(requireView(),
                        "Errore: ${state.message}",
                        Snackbar.LENGTH_LONG).show()
                    vm.resetAnalysisState()
                }
            }
        }
    }

    private suspend fun observeAnalyzedCount() {
        vm.analyzedCount.collect { count ->
            val current = vm.library.value
            val total = (current as? LibraryUiState.Loaded)?.songs?.size ?: 0
            counter.text = "$total brani • $count analizzati"
        }
    }

    private fun updateBatchUi(state: WorkInfo.State?) {
        when (state) {
            WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
                fabBatch.text = getString(R.string.batch_stop)
                fabBatch.setIconResource(R.drawable.ic_analyze)
            }
            else -> {
                fabBatch.text = getString(R.string.batch_start)
                fabBatch.setIconResource(R.drawable.ic_analyze)
            }
        }
    }

    private fun onSongClicked(song: Song) {
        // Tap singolo → riproduci.
        // Long-press → analizza (vedi sotto).
        val currentList = (vm.library.value as? LibraryUiState.Loaded)?.songs ?: emptyList()
        vm.playSong(song, currentList)
    }

    private fun onBatchClicked() {
        val info = vm.batchWorkInfo.value?.firstOrNull()
        val running = info?.state == WorkInfo.State.RUNNING ||
                      info?.state == WorkInfo.State.ENQUEUED
        if (running) {
            vm.cancelBatchAnalysis()
            Snackbar.make(requireView(), R.string.batch_cancelled,
                Snackbar.LENGTH_SHORT).show()
        } else {
            vm.startBatchAnalysis()
            Snackbar.make(requireView(), R.string.batch_started,
                Snackbar.LENGTH_SHORT).show()
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
            .setNeutralButton("Rianalizza") { _, _ ->
                vm.clearAllAnalysis()  // reset solo se vuoi rifare tutto
            }
            .show()
    }
}
