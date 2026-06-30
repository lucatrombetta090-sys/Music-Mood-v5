package com.musicmood.weekly

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
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

class WeeklyReportFragment : Fragment() {

    private val tag = "WeeklyReportFragment"
    private val vm: WeeklyReportViewModel by viewModels()

    private var emptyState: View? = null
    private var content: View? = null
    private var weekLabel: TextView? = null
    private var totalText: TextView? = null
    private var deltaText: TextView? = null
    private var dominantEmoji: TextView? = null
    private var dominantText: TextView? = null
    private var moodBreakdown: LinearLayout? = null
    private var btnShare: MaterialButton? = null
    private var btnGenerate: MaterialButton? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            inflater.inflate(R.layout.fragment_weekly_report, container, false)
        } catch (t: Throwable) {
            Log.e(tag, "onCreateView failed: ${t.message}", t)
            // Fallback: ritorna una View vuota in modo da non crashare l'app
            TextView(requireContext()).apply {
                text = "Errore caricamento Weekly Report"
                setPadding(48, 48, 48, 48)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
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

            btnShare?.setOnClickListener {
                runCatching { share() }
                    .onFailure { Log.e(tag, "share click failed: ${it.message}", it) }
            }

            btnGenerate?.setOnClickListener {
                runCatching {
                    vm.generateNow()
                    Snackbar.make(view, "Report generato!", Snackbar.LENGTH_SHORT).show()
                }.onFailure { Log.e(tag, "generate click failed: ${it.message}", it) }
            }

            // Carica solo DOPO che la view è pronta
            vm.load()

            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    vm.ui.collect { state ->
                        runCatching { render(state) }
                            .onFailure { Log.e(tag, "render failed: ${it.message}", it) }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(tag, "onViewCreated outer failed: ${t.message}", t)
            // Mostra l'empty state come fallback
            emptyState?.visibility = View.VISIBLE
            content?.visibility = View.GONE
        }
    }

    private fun render(u: WeeklyUi) {
        if (u.empty || u.latest == null) {
            emptyState?.visibility = View.VISIBLE
            content?.visibility = View.GONE
            return
        }
        emptyState?.visibility = View.GONE
        content?.visibility = View.VISIBLE

        weekLabel?.text  = u.weekLabel
        totalText?.text  = "${u.latest.totalPlays} brani"
        deltaText?.text  = u.deltaVsPrev?.let { d ->
            val sign = if (d >= 0) "+" else ""
            val arrow = if (d >= 0) "↑" else "↓"
            "$arrow $sign$d% vs settimana scorsa"
        } ?: "—"

        val archetype = PersonalityTypes.BY_MOOD[u.latest.dominantMood]
        dominantEmoji?.text = archetype?.emoji ?: "🎵"
        dominantText?.text  = u.latest.dominantMood

        val breakdown = moodBreakdown ?: return
        breakdown.removeAllViews()
        u.moodPercentages.take(5).forEach { (mood, pct, color) ->
            try {
                val row = layoutInflater.inflate(R.layout.item_mood_row, breakdown, false)
                row.findViewById<TextView>(R.id.moodLabel)?.text = mood
                row.findViewById<TextView>(R.id.moodPct)?.text   = "$pct%"
                row.findViewById<View>(R.id.moodColor)?.setBackgroundColor(color)
                breakdown.addView(row)
            } catch (t: Throwable) {
                Log.w(tag, "row failed for $mood: ${t.message}")
            }
        }
    }

    private fun share() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val uri = vm.buildShareUri() ?: run {
                    Snackbar.make(requireView(),
                        "Nessun report da condividere",
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
            } catch (t: Throwable) {
                Log.e(tag, "share failed: ${t.message}", t)
                try {
                    Snackbar.make(requireView(),
                        "Errore condivisione",
                        Snackbar.LENGTH_LONG).show()
                } catch (_: Throwable) { /* niente da fare */ }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        emptyState = null
        content = null
        weekLabel = null
        totalText = null
        deltaText = null
        dominantEmoji = null
        dominantText = null
        moodBreakdown = null
        btnShare = null
        btnGenerate = null
    }
}
