package com.musicmood.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.musicmood.R
import com.musicmood.data.Song
import kotlinx.coroutines.launch

class LibraryFragment : Fragment(R.layout.fragment_library) {

    private val vm: LibraryViewModel by viewModels()
    private lateinit var adapter: SongAdapter
    private lateinit var progress: ProgressBar
    private lateinit var emptyState: TextView
    private lateinit var counter: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progress   = view.findViewById(R.id.progress)
        emptyState = view.findViewById(R.id.emptyState)
        counter    = view.findViewById(R.id.counter)

        adapter = SongAdapter(onClick = ::onSongClicked)

        view.findViewById<RecyclerView>(R.id.recyclerView).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@LibraryFragment.adapter
            setHasFixedSize(true)
        }

        // Carica libreria
        vm.loadLibrary()

        // Osserva stati
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeLibrary() }
                launch { observeAnalysis() }
            }
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
                    counter.text = "${state.songs.size} brani"
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
                    showResultDialog(state.song, state.result.toReadable())
                    vm.resetAnalysisState()
                }
                is AnalysisUiState.Failed -> {
                    Snackbar.make(requireView(),
                        "Errore analisi: ${state.message}",
                        Snackbar.LENGTH_LONG).show()
                    vm.resetAnalysisState()
                }
            }
        }
    }

    private fun onSongClicked(song: Song) {
        vm.analyze(song)
    }

    private fun showResultDialog(song: Song, body: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("🎯 ${song.title}")
            .setMessage(body)
            .setPositiveButton("Chiudi", null)
            .show()
    }

    private fun com.musicmood.MoodAnalysis.toReadable(): String = buildString {
        appendLine("Mood: $mood")
        appendLine("Confidenza: ${"%.0f".format(confidence * 100)}%")
        appendLine()
        appendLine("Valenza : ${"%+.2f".format(valence)}")
        appendLine("Arousal : ${"%+.2f".format(arousal)}")
        appendLine("Tempo   : ${"%.1f".format(tempoBpm)} BPM")
        append("Tonalità: $key $mode")
    }
}
