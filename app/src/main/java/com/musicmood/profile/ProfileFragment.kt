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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.musicmood.R
import com.musicmood.weekly.WeeklyReportFragment
import kotlinx.coroutines.launch

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val vm: ProfileViewModel by viewModels()

    private lateinit var emptyState: TextView
    private lateinit var content: View
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
    private lateinit var calibrationStatus: TextView
    private lateinit var btnCalibrate: MaterialButton
    private lateinit var btnWeekly: MaterialButton
    private lateinit var btnShare: MaterialButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        emptyState        = view.findViewById(R.id.emptyState)
        content           = view.findViewById(R.id.content)
        dailyEmoji        = view.findViewById(R.id.dailyEmoji)
        dailySlot         = view.findViewById(R.id.dailySlot)
        dailyTitle        = view.findViewById(R.id.dailyTitle)
        dailyDesc         = view.findViewById(R.id.dailyDesc)
        btnPlayDaily      = view.findViewById(R.id.btnPlayDaily)
        radar             = view.findViewById(R.id.radar)
        archetypeEmoji    = view.findViewById(R.id.archetypeEmoji)
        archetypeName     = view.findViewById(R.id.archetypeName)
        archetypeTagline  = view.findViewById(R.id.archetypeTagline)
        moodBreakdown     = view.findViewById(R.id.moodBreakdown)
        topArtistText     = view.findViewById(R.id.topArtistText)
        totalText         = view.findViewById(R.id.totalText)
        calibrationStatus = view.findViewById(R.id.calibrationStatus)
        btnCalibrate      = view.findViewById(R.id.btnCalibrate)
        btnWeekly         = view.findViewById(R.id.btnWeekly)
        btnShare          = view.findViewById(R.id.btnShare)

        btnPlayDaily.setOnClickListener {
            vm.playDailySuggestion()
            Snackbar.make(requireView(),
                "▶ Playlist ${vm.profile.value.dailySuggestion.title}",
                Snackbar.LENGTH_SHORT).show()
        }

        btnShare.setOnClickListener { shareProfile() }
        btnCalibrate.setOnClickListener { onCalibrateClicked() }
        btnWeekly.setOnClickListener { openWeeklyReport() }

        vm.load()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.profile.collect { render(it) }
            }
        }
    }

    private fun render(p: ProfileUiData) {
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

        archetypeEmoji.text   = p.archetype.emoji
        archetypeName.text    = p.archetype.name
        archetypeTagline.text = "\"${p.archetype.tagline}\""

        radar.setData(p.radarAxes, p.archetype.color)

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

        topArtistText.text = if (!p.topArtist.isNullOrBlank())
            "Top artist del tuo mood: ${p.topArtist}"
        else "—"

        totalText.text = "Analisi basata su ${p.totalAnalyzed} brani"

        calibrationStatus.text = if (p.isCalibrated)
            "✅ Classificatore calibrato sulla tua libreria"
        else
            "💡 Suggerimento: calibra il classificatore per risultati più accurati"

        btnCalibrate.text = if (p.isCalibrated)
            getString(R.string.profile_calibrate_reset)
        else
            getString(R.string.profile_calibrate)
        btnCalibrate.isEnabled = !p.calibrating

        p.calibrationMessage?.let { msg ->
            Snackbar.make(requireView(), msg, Snackbar.LENGTH_LONG).show()
            vm.clearCalibrationMessage()
        }
    }

    private fun onCalibrateClicked() {
        val isCalibrated = vm.profile.value.isCalibrated
        if (isCalibrated) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_calibrate_reset_title)
                .setMessage(R.string.profile_calibrate_reset_msg)
                .setPositiveButton("Reset") { _, _ -> vm.resetCalibration() }
                .setNegativeButton("Annulla", null)
                .show()
        } else {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_calibrate_title)
                .setMessage(R.string.profile_calibrate_msg)
                .setPositiveButton("Calibra") { _, _ -> vm.calibrate() }
                .setNegativeButton("Annulla", null)
                .show()
        }
    }

    private fun openWeeklyReport() {
        parentFragmentManager.beginTransaction()
            .replace((view?.parent as? View)?.id ?: return,
                WeeklyReportFragment())
            .addToBackStack("weekly")
            .commit()
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
