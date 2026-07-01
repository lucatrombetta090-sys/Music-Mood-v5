package com.musicmood.mood

import android.app.Dialog
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.musicmood.profile.PersonalityTypes

/**
 * Dialog per selezionare/cambiare il mood di un brano.
 * Callback: onPick(mood) oppure onReset() se preme "Reset al DSP".
 */
class MoodPickerDialog(
    private val context: Context,
    private val songTitle: String,
    private val currentDspMood: String?,
    private val currentUserMood: String?,
    private val onPick: (String) -> Unit,
    private val onReset: () -> Unit,
) {

    fun show(): Dialog {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }

        // Info stato attuale
        val currentText = TextView(context).apply {
            text = buildStatusText()
            textSize = 13f
            alpha = 0.75f
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(currentText)

        // Grid di 9 pulsanti mood (3 colonne)
        val grid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val moods = PersonalityTypes.ALL
        val cols = 3
        var row: LinearLayout? = null
        moods.forEachIndexed { index, arch ->
            if (index % cols == 0) {
                row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                grid.addView(row)
            }
            val btn = MaterialButton(context).apply {
                text = "${arch.emoji}\n${arch.moodKey}"
                textSize = 11f
                isAllCaps = false
                minHeight = dp(64)
                cornerRadius = dp(12)
                setPadding(dp(4), dp(4), dp(4), dp(4))
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
                backgroundTintList = null
                background = GradientDrawable().apply {
                    setColor(arch.color)
                    cornerRadius = dp(12).toFloat()
                }
                setTextColor(0xFFFFFFFF.toInt())
                if (arch.moodKey == (currentUserMood ?: currentDspMood)) {
                    background.setColorFilter(0xFFFFFFFF.toInt(), PorterDuff.Mode.OVERLAY)
                    alpha = 1.0f
                }
                setOnClickListener {
                    onPick(arch.moodKey)
                    dialogRef?.dismiss()
                }
            }
            row?.addView(btn)
        }
        // Riempie l'ultima riga se non completa (3 pulsanti max)
        val lastCount = moods.size % cols
        if (lastCount != 0) {
            repeat(cols - lastCount) {
                row?.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
            }
        }

        root.addView(grid)

        val builder = AlertDialog.Builder(context)
            .setTitle("🏷️ Cambia mood")
            .setView(root)
            .setNegativeButton("Annulla", null)

        if (currentUserMood != null) {
            builder.setNeutralButton("Reset al DSP") { _, _ -> onReset() }
        }

        val dialog = builder.create()
        dialogRef = dialog
        dialog.show()
        return dialog
    }

    private var dialogRef: Dialog? = null

    private fun buildStatusText(): String {
        return buildString {
            append("🎵 ").append(songTitle).appendLine()
            appendLine()
            append("Mood DSP: ").append(currentDspMood ?: "—")
            if (currentUserMood != null) {
                appendLine()
                append("Mood utente: ").append(currentUserMood).append(" ✏️")
            }
        }
    }

    private fun dp(v: Int): Int =
        (v * context.resources.displayMetrics.density).toInt()
}
