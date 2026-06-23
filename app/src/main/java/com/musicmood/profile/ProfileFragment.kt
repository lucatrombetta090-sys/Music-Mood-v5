package com.musicmood.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.musicmood.R
import kotlinx.coroutines.launch

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val vm: ProfileViewModel by viewModels()

    private lateinit var emptyState: TextView
    private lateinit var content: View
    private lateinit var dailyCard: View
    private lateinit var dailyEmoji: TextView
    private lateinit var dailySlot: TextView
    private lateinit var dailyTitle: TextView
    private lateinit var dailyDesc: TextView
    private lateinit var btnPlayDaily: MaterialButton

    private lateinit var radar: RadarChartView
    private lateinit var archetypeEmoji: TextView
    private lateinit var archetypeName: TextView
    private lateinit var archetypeTagline: TextView
    private lateinit var moodBreakdown: LinearLayout
    private lateinit var topArtistText: TextView
    private lateinit var totalText: TextView
    private lateinit var btnShare: MaterialButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        emptyState       = view.findViewById(R.id.emptyState)
        content          = view.findViewById(R.id.content)
        dailyCard        = view.findViewById(R.id.dailyCard)
        dailyEmoji       = view.findViewById(R.id.dailyEmoji)
        dailySlot        = view.findViewById(R.id.dailySlot)
        dailyTitle       = view.findViewById(R.id.dailyTitle)
        dailyDesc        = view.findViewById(R.id.dailyDesc)
        btnPlayDaily     = view.findViewById(R.id.btnPlayDaily)
        radar            = view.findViewById(R.id.radar)
        archetypeEmoji   = view.findViewById(R.id.archetypeEmoji)
        archetypeName    = view.findViewById(R.id.archetypeName)
        archetypeTagline = view.findViewById(R.id.archetypeTagline)
        moodBreakdown    = view.findViewById(R.id.moodBreakdown)
        topArtistText    = view.findViewById(R.id.topArtistText)
        totalText        = view.findViewById(R.id.totalText)
        btnShare         = view.findViewById(R.id.btnShare)

        btnPlayDaily.setOnClickListener {
            vm.playDailySuggestion()
            Snackbar.make(requireView(),
                "▶ Playlist ${vm.profile.value.dailySuggestion.title}",
                Snackbar.LENGTH_SHORT).show()
        }

        btnShare.setOnClickListener { shareProfile() }

        vm.load()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.profile.collect { render(it) }
            }
        }
    }

    private fun render(p: ProfileUiData) {
        // Daily mood card sempre visibile
        with(p.dailySuggestion) {
            dailyEmoji.text = emoji
            dailySlot.text  = timeSlot
            dailyTitle.text = title
            dailyDesc.text  = description
        }

        if (p.archetype == null || p.totalAnalyzed == 0) {
            emptyState.isVisible = true
            content.isVisible = false
            return
        }
        emptyState.isVisible = false
        content.isVisible = true

        // Archetype hero
        archetypeEmoji.text   = p.archetype.emoji
        archetypeName.text    = p.archetype.name
        archetypeTagline.text = "\"${p.archetype.tagline}\""

        // Radar chart
        radar.setData(p.radarAxes, p.archetype.color)

        // Breakdown
        moodBreakdown.removeAllViews()
        p.moodPercentages.take(5).forEach { (mood, pct, color) ->
            val row = layoutInflater.inflate(
                R.layout.item_mood_row, moodBreakdown, false
            )
            row.findViewById<TextView>(R.id.moodLabel).text = mood
            row.findViewById<TextView>(R.id.moodPct).text   = "$pct%"
            row.findViewById<View>(R.id.moodColor).setBackgroundColor(color)
            moodBreakdown.addView(row)
        }

        // Top artist
        topArtistText.text = if (!p.topArtist.isNullOrBlank())
            "Top artist del tuo mood: ${p.topArtist}"
        else "—"

        // Totale
        totalText.text = "Analisi basata su ${p.totalAnalyzed} brani"
    }

    private fun shareProfile() {
        viewLifecycleOwner.lifecycleScope.launch {
            val uri = vm.buildShareUri() ?: run {
                Snackbar.make(requireView(),
                    "Errore generazione immagine",
                    Snackbar.LENGTH_LONG).show()
                return@launch
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT,
                    "🎵 La mia personalità musicale secondo Music-Mood!")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Condividi profilo"))
        }
    }
}
