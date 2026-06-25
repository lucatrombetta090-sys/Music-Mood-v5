package com.musicmood.weekly

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
import com.musicmood.profile.PersonalityTypes
import kotlinx.coroutines.launch

class WeeklyReportFragment : Fragment(R.layout.fragment_weekly_report) {

    private val vm: WeeklyReportViewModel by viewModels()

    private lateinit var emptyState: TextView
    private lateinit var content: View
    private lateinit var weekLabel: TextView
    private lateinit var totalText: TextView
    private lateinit var deltaText: TextView
    private lateinit var dominantEmoji: TextView
    private lateinit var dominantText: TextView
    private lateinit var moodBreakdown: LinearLayout
    private lateinit var btnShare: MaterialButton
    private lateinit var btnGenerate: MaterialButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        emptyState     = view.findViewById(R.id.emptyState)
        content        = view.findViewById(R.id.content)
        weekLabel      = view.findViewById(R.id.weekLabel)
        totalText      = view.findViewById(R.id.totalText)
        deltaText      = view.findViewById(R.id.deltaText)
        dominantEmoji  = view.findViewById(R.id.dominantEmoji)
        dominantText   = view.findViewById(R.id.dominantText)
        moodBreakdown  = view.findViewById(R.id.moodBreakdown)
        btnShare       = view.findViewById(R.id.btnShare)
        btnGenerate    = view.findViewById(R.id.btnGenerate)

        btnShare.setOnClickListener { share() }
        btnGenerate.setOnClickListener {
            vm.generateNow()
            Snackbar.make(requireView(),
                "Report generato!", Snackbar.LENGTH_SHORT).show()
        }

        vm.load()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.ui.collect { render(it) }
            }
        }
    }

    private fun render(u: WeeklyUi) {
        if (u.empty || u.latest == null) {
            emptyState.isVisible = true
            content.isVisible = false
            return
        }
        emptyState.isVisible = false
        content.isVisible = true

        weekLabel.text  = u.weekLabel
        totalText.text  = "${u.latest.totalPlays} brani"
        deltaText.text  = u.deltaVsPrev?.let { d ->
            val sign = if (d >= 0) "+" else ""
            val arrow = if (d >= 0) "↑" else "↓"
            "$arrow $sign$d% vs settimana scorsa"
        } ?: "—"

        val archetype = PersonalityTypes.BY_MOOD[u.latest.dominantMood]
        dominantEmoji.text = archetype?.emoji ?: "🎵"
        dominantText.text  = u.latest.dominantMood

        moodBreakdown.removeAllViews()
        u.moodPercentages.take(5).forEach { (mood, pct, color) ->
            val row = layoutInflater.inflate(
                R.layout.item_mood_row, moodBreakdown, false
            )
            row.findViewById<TextView>(R.id.moodLabel).text = mood
            row.findViewById<TextView>(R.id.moodPct).text   = "$pct%"
            row.findViewById<View>(R.id.moodColor).setBackgroundColor(color)
            moodBreakdown.addView(row)
        }
    }

    private fun share() {
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
                    "📊 Il mio Weekly Mood Report secondo Music-Mood!")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Condividi report"))
        }
    }
}
