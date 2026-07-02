package com.musicmood.about

import android.graphics.Typeface
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment

class AboutFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()

        val scrollView = ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }

        val textView = TextView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            textSize = 15f
            setLineSpacing(dp(4).toFloat(), 1.05f)
            gravity = Gravity.START
            movementMethod = LinkMovementMethod.getInstance()

            setTextColor(
                ContextCompat.getColor(
                    context,
                    android.R.color.white
                )
            )

            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            text = loadReadme()
        }

        scrollView.addView(textView)

        return scrollView
    }

    private fun loadReadme(): CharSequence {
        val readmeText = runCatching {
            requireContext()
                .assets
                .open("README.md")
                .bufferedReader()
                .use { it.readText() }
        }.getOrElse {
            """
            # Music-Mood

            README.md non trovato negli assets dell'app.

            Per abilitare questa schermata:
            1. crea la cartella app/src/main/assets
            2. copia README.md dentro app/src/main/assets/README.md
            3. ricompila l'app

            Da ora in avanti il README va aggiornato a ogni rilascio.
            """.trimIndent()
        }

        return markdownLiteToText(readmeText)
    }

    private fun markdownLiteToText(markdown: String): CharSequence {
        val escaped = markdown
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        val html = escaped
            .replace(
                Regex("^# (.*)$", RegexOption.MULTILINE),
                "<h1>$1</h1>"
            )
            .replace(
                Regex("^## (.*)$", RegexOption.MULTILINE),
                "<h2>$1</h2>"
            )
            .replace(
                Regex("^### (.*)$", RegexOption.MULTILINE),
                "<h3>$1</h3>"
            )
            .replace(
                Regex("^- (.*)$", RegexOption.MULTILINE),
                "• $1"
            )
            .replace("\n", "<br>")

        return HtmlCompat.fromHtml(
            html,
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
