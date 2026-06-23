package com.musicmood.stats

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.musicmood.R
import com.musicmood.export.CsvExporter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatsFragment : Fragment(R.layout.fragment_stats) {

    private val vm: StatsViewModel by viewModels()

    private lateinit var totalText: TextView
    private lateinit var distribution: MoodDistributionView
    private lateinit var avgBpmText: TextView
    private lateinit var avgValenceText: TextView
    private lateinit var avgArousalText: TextView
    private lateinit var topKeyText: TextView
    private lateinit var topArtistsText: TextView
    private lateinit var playlistContainer: LinearLayout
    private lateinit var emptyState: View
    private lateinit var btnExport: MaterialButton

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            res.data?.data?.let { uri -> performExport(uri) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        totalText        = view.findViewById(R.id.totalText)
        distribution     = view.findViewById(R.id.distribution)
        avgBpmText       = view.findViewById(R.id.avgBpmText)
        avgValenceText   = view.findViewById(R.id.avgValenceText)
        avgArousalText   = view.findViewById(R.id.avgArousalText)
        topKeyText       = view.findViewById(R.id.topKeyText)
        topArtistsText   = view.findViewById(R.id.topArtistsText)
        playlistContainer = view.findViewById(R.id.playlistContainer)
        emptyState       = view.findViewById(R.id.emptyState)
        btnExport        = view.findViewById(R.id.btnExport)

        btnExport.setOnClickListener { startExportFlow() }

        buildPlaylistCards()
        vm.load()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.stats.collect { renderStats(it) }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Rendering
    // ──────────────────────────────────────────────────────────────────────
    private fun renderStats(s: StatsUiData) {
        if (s.totalAnalyzed == 0) {
            emptyState.visibility = View.VISIBLE
            return
        }
        emptyState.visibility = View.GONE

        totalText.text       = "${s.totalAnalyzed} brani analizzati"
        distribution.setData(s.distribution)
        avgBpmText.text      = "%.1f BPM".format(s.avgBpm)
        avgValenceText.text  = "%+.2f".format(s.avgValence)
        avgArousalText.text  = "%+.2f".format(s.avgArousal)
        topKeyText.text      = s.topKey
        topArtistsText.text  = if (s.topArtists.isEmpty()) "—"
            else s.topArtists.joinToString("\n") { "${it.first} (${it.second})" }
    }

    private fun buildPlaylistCards() {
        playlistContainer.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        PlaylistGenerator.PRESETS.forEach { preset ->
            val card = inflater.inflate(
                R.layout.item_playlist_card, playlistContainer, false
            ) as ViewGroup
            card.findViewById<TextView>(R.id.playlistName).text =
                "${preset.emoji} ${preset.name}"
            card.findViewById<TextView>(R.id.playlistDesc).text = preset.description
            card.setOnClickListener {
                vm.playPlaylist(preset)
                Snackbar.make(requireView(),
                    "▶ ${preset.name}", Snackbar.LENGTH_SHORT).show()
            }
            playlistContainer.addView(card)
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Export CSV
    // ──────────────────────────────────────────────────────────────────────
    private fun startExportFlow() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "music_mood_$timestamp.csv")
        }
        exportLauncher.launch(intent)
    }

    private fun performExport(uri: android.net.Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val exporter = CsvExporter(requireContext())
            val result = exporter.exportTo(uri)
            result.fold(
                onSuccess = { count ->
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("✅ Export completato")
                        .setMessage("$count brani esportati nel file CSV.")
                        .setPositiveButton("OK", null)
                        .show()
                },
                onFailure = { err ->
                    Snackbar.make(requireView(),
                        "Errore export: ${err.message}",
                        Snackbar.LENGTH_LONG).show()
                }
            )
        }
    }
}
