package com.musicmood.settings

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {

    private val prefs by lazy {
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()

        val rootScroll = ScrollView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            fillViewport = true
        }

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        root.addView(header("Impostazioni"))
        root.addView(description("Configura aspetto, libreria, mood engine, BubbleMap, copertine, player e dati locali."))

        root.addView(sectionTitle("Aspetto"))
        root.addView(
            spinnerRow(
                key = KEY_THEME_MODE,
                title = "Tema applicazione",
                subtitle = "Scegli il tema dell'app",
                labels = listOf("Sistema", "Chiaro", "Scuro"),
                values = listOf("system", "light", "dark"),
                defaultValue = "system"
            )
        )
        root.addView(
            switchRow(
                key = KEY_DYNAMIC_COLORS,
                title = "Colori dinamici",
                subtitle = "Usa colori adattivi quando supportati dal dispositivo",
                defaultValue = true
            )
        )
        root.addView(
            switchRow(
                key = KEY_COMPACT_MODE,
                title = "Modalità compatta",
                subtitle = "Riduce spaziature e dimensioni degli elementi nelle liste",
                defaultValue = false
            )
        )

        root.addView(sectionTitle("Libreria musicale"))
        root.addView(
            switchRow(
                key = KEY_SCAN_ON_START,
                title = "Scansione all'avvio",
                subtitle = "Aggiorna automaticamente la libreria quando apri l'app",
                defaultValue = false
            )
        )
        root.addView(
            switchRow(
                key = KEY_INCLUDE_SHORT_TRACKS,
                title = "Includi tracce brevi",
                subtitle = "Mostra anche intro, jingle e tracce di durata ridotta",
                defaultValue = false
            )
        )
        root.addView(
            spinnerRow(
                key = KEY_DEFAULT_SORT,
                title = "Ordinamento predefinito",
                subtitle = "Scegli come ordinare i brani nella libreria",
                labels = listOf("Titolo", "Artista", "Album", "Durata", "Mood", "Anno"),
                values = listOf("title", "artist", "album", "duration", "mood", "year"),
                defaultValue = "title"
            )
        )

        root.addView(sectionTitle("Mood engine"))
        root.addView(
            switchRow(
                key = KEY_AUTO_ANALYZE,
                title = "Analisi automatica mood",
                subtitle = "Analizza i brani non ancora classificati",
                defaultValue = true
            )
        )
        root.addView(
            switchRow(
                key = KEY_USE_USER_MOOD,
                title = "Priorità mood manuale",
                subtitle = "Usa il mood scelto manualmente al posto di quello calcolato",
                defaultValue = true
            )
        )
        root.addView(
            spinnerRow(
                key = KEY_ANALYSIS_PROFILE,
                title = "Profilo analisi",
                subtitle = "Bilancia velocità e accuratezza dell'analisi",
                labels = listOf("Veloce", "Bilanciato", "Accurato"),
                values = listOf("fast", "balanced", "accurate"),
                defaultValue = "balanced"
            )
        )
        root.addView(
            switchRow(
                key = KEY_SHOW_CONFIDENCE,
                title = "Mostra confidenza mood",
                subtitle = "Visualizza il livello di affidabilità della classificazione",
                defaultValue = true
            )
        )

        root.addView(sectionTitle("BubbleMap"))
        root.addView(
            switchRow(
                key = KEY_BUBBLEMAP_ENABLED,
                title = "Abilita BubbleMap",
                subtitle = "Mostra i brani nello spazio valenza/arousal",
                defaultValue = true
            )
        )
        root.addView(
            switchRow(
                key = KEY_BUBBLEMAP_JITTER,
                title = "Riduci sovrapposizione punti",
                subtitle = "Applica un leggero spostamento visuale ai punti molto vicini",
                defaultValue = true
            )
        )
        root.addView(
            switchRow(
                key = KEY_BUBBLEMAP_HEATMAP,
                title = "Modalità densità",
                subtitle = "Evidenzia aree con molti brani sovrapposti",
                defaultValue = false
            )
        )
        root.addView(
            spinnerRow(
                key = KEY_BUBBLEMAP_SIZE,
                title = "Dimensione bolle",
                subtitle = "Scegli la dimensione visuale dei punti",
                labels = listOf("Piccola", "Media", "Grande"),
                values = listOf("small", "medium", "large"),
                defaultValue = "medium"
            )
        )

        root.addView(sectionTitle("Copertine"))
        root.addView(
            switchRow(
                key = KEY_REMOTE_ARTWORK,
                title = "Ricerca copertine online",
                subtitle = "Cerca copertine mancanti tramite sorgenti remote",
                defaultValue = true
            )
        )
        root.addView(
            switchRow(
                key = KEY_ARTWORK_CACHE,
                title = "Cache copertine",
                subtitle = "Memorizza le copertine trovate per ridurre le ricerche successive",
                defaultValue = true
            )
        )
        root.addView(
            actionRow(
                title = "Svuota cache copertine",
                subtitle = "Rimuove la cache locale delle copertine",
                buttonText = "Svuota"
            ) {
                Toast.makeText(
                    requireContext(),
                    "Cache copertine: funzione da collegare ad ArtworkRepository",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        root.addView(sectionTitle("Player"))
        root.addView(
            switchRow(
                key = KEY_MINI_PLAYER,
                title = "Mini-player persistente",
                subtitle = "Mostra il mini-player quando un brano è in riproduzione",
                defaultValue = true
            )
        )
        root.addView(
            switchRow(
                key = KEY_RESUME_PLAYBACK,
                title = "Riprendi ultima riproduzione",
                subtitle = "Mantiene il brano corrente e la posizione quando possibile",
                defaultValue = true
            )
        )
        root.addView(
            switchRow(
                key = KEY_SHOW_ARTWORK_PLAYER,
                title = "Mostra copertina nel player",
                subtitle = "Visualizza la cover nel mini-player e nel player completo",
                defaultValue = true
            )
        )

        root.addView(sectionTitle("Privacy e dati"))
        root.addView(
            lockedSwitchRow(
                key = KEY_LOCAL_ANALYSIS_ONLY,
                title = "Analisi solo locale",
                subtitle = "Esegue l'analisi audio sul dispositivo",
                defaultValue = true
            )
        )
        root.addView(
            actionRow(
                title = "Cancella dati analisi",
                subtitle = "Rimuove mood, statistiche e report salvati localmente",
                buttonText = "Cancella"
            ) {
                Toast.makeText(
                    requireContext(),
                    "Cancellazione dati analisi: funzione da collegare ai DAO Room",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        root.addView(sectionTitle("Info app"))
        root.addView(
            infoRow(
                title = "README",
                subtitle = readReadmeSummary()
            ) {
                openRepository()
            }
        )
        root.addView(
            infoRow(
                title = "Versione app",
                subtitle = getAppVersionLabel()
            )
        )
        root.addView(
            infoRow(
                title = "Licenza",
                subtitle = "MIT"
            )
        )

        rootScroll.addView(root)
        return rootScroll
    }

    private fun header(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF222222.toInt())
            setPadding(0, 0, 0, dp(8))
        }
    }

    private fun description(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 14f
            setTextColor(0xFF666666.toInt())
            setPadding(0, 0, 0, dp(18))
        }
    }

    private fun sectionTitle(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF333333.toInt())
            setPadding(0, dp(18), 0, dp(8))
        }
    }

    private fun switchRow(
        key: String,
        title: String,
        subtitle: String,
        defaultValue: Boolean
    ): View {
        val ctx = requireContext()

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val textContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val titleView = TextView(ctx).apply {
            text = title
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF222222.toInt())
        }

        val subtitleView = TextView(ctx).apply {
            text = subtitle
            textSize = 13f
            setTextColor(0xFF777777.toInt())
            setPadding(0, dp(3), dp(12), 0)
        }

        val switchView = Switch(ctx).apply {
            isChecked = prefs.getBoolean(key, defaultValue)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(key, checked).apply()
            }
        }

        textContainer.addView(titleView)
        textContainer.addView(subtitleView)

        row.addView(textContainer)
        row.addView(switchView)

        return row
    }

    private fun lockedSwitchRow(
        key: String,
        title: String,
        subtitle: String,
        defaultValue: Boolean
    ): View {
        val view = switchRow(key, title, subtitle, defaultValue)
        view.isEnabled = false
        view.alpha = 0.65f
        return view
    }

    private fun spinnerRow(
        key: String,
        title: String,
        subtitle: String,
        labels: List<String>,
        values: List<String>,
        defaultValue: String
    ): View {
        val ctx = requireContext()
        val currentValue = prefs.getString(key, defaultValue) ?: defaultValue

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val titleView = TextView(ctx).apply {
            text = title
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF222222.toInt())
        }

        val subtitleView = TextView(ctx).apply {
            text = subtitle
            textSize = 13f
            setTextColor(0xFF777777.toInt())
            setPadding(0, dp(3), 0, dp(8))
        }

        val spinner = Spinner(ctx).apply {
            adapter = ArrayAdapter(
                ctx,
                android.R.layout.simple_spinner_dropdown_item,
                labels
            )

            val selectedIndex = values.indexOf(currentValue).takeIf { it >= 0 } ?: 0
            setSelection(selectedIndex)

            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val selectedValue = values.getOrNull(position) ?: defaultValue
                    prefs.edit().putString(key, selectedValue).apply()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // No action required.
                }
            }
        }

        row.addView(titleView)
        row.addView(subtitleView)
        row.addView(spinner)

        return row
    }

    private fun actionRow(
        title: String,
        subtitle: String,
        buttonText: String,
        action: () -> Unit
    ): View {
        val ctx = requireContext()

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val textContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val titleView = TextView(ctx).apply {
            text = title
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF222222.toInt())
        }

        val subtitleView = TextView(ctx).apply {
            text = subtitle
            textSize = 13f
            setTextColor(0xFF777777.toInt())
            setPadding(0, dp(3), dp(12), 0)
        }

        val button = Button(ctx).apply {
            text = buttonText
            setOnClickListener {
                action()
            }
        }

        textContainer.addView(titleView)
        textContainer.addView(subtitleView)

        row.addView(textContainer)
        row.addView(button)

        return row
    }

    private fun infoRow(
        title: String,
        subtitle: String,
        action: (() -> Unit)? = null
    ): View {
        val ctx = requireContext()

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            if (action != null) {
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    action()
                }
            }
        }

        val titleView = TextView(ctx).apply {
            text = title
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF222222.toInt())
        }

        val subtitleView = TextView(ctx).apply {
            text = subtitle
            textSize = 13f
            setTextColor(0xFF777777.toInt())
            setPadding(0, dp(3), 0, 0)
        }

        row.addView(titleView)
        row.addView(subtitleView)

        return row
    }

    private fun readReadmeSummary(): String {
        return runCatching {
            requireContext()
                .assets
                .open("README.md")
                .bufferedReader()
                .use { reader ->
                    reader.readText()
                }
                .lineSequence()
                .map { line ->
                    line.trim()
                }
                .filter { line ->
                    line.isNotEmpty()
                }
                .take(6)
                .joinToString(separator = "\n")
        }.getOrElse {
            "README.md non trovato negli assets. Tocca per aprire il repository."
        }
    }

    private fun openRepository() {
        val uri = Uri.parse(REPOSITORY_URL)
        val intent = Intent(Intent.ACTION_VIEW, uri)

        runCatching {
            startActivity(intent)
        }.onFailure {
            Toast.makeText(
                requireContext(),
                "Impossibile aprire il repository",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun getAppVersionLabel(): String {
        return runCatching {
            val packageInfo = requireContext()
                .packageManager
                .getPackageInfo(requireContext().packageName, 0)

            val versionName = packageInfo.versionName ?: "N/D"
            "Versione $versionName"
        }.getOrElse {
            "Versione non disponibile"
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val PREFS_NAME = "music_mood_settings"

        private const val REPOSITORY_URL =
            "https://github.com/lucatrombetta090-sys/Music-Mood-v5"

        const val KEY_THEME_MODE = "settings_theme_mode"
        const val KEY_DYNAMIC_COLORS = "settings_dynamic_colors"
        const val KEY_COMPACT_MODE = "settings_compact_mode"

        const val KEY_SCAN_ON_START = "settings_scan_on_start"
        const val KEY_INCLUDE_SHORT_TRACKS = "settings_include_short_tracks"
        const val KEY_DEFAULT_SORT = "settings_default_sort"

        const val KEY_AUTO_ANALYZE = "settings_auto_analyze"
        const val KEY_USE_USER_MOOD = "settings_use_user_mood"
        const val KEY_ANALYSIS_PROFILE = "settings_analysis_profile"
        const val KEY_SHOW_CONFIDENCE = "settings_show_confidence"

        const val KEY_BUBBLEMAP_ENABLED = "settings_bubblemap_enabled"
        const val KEY_BUBBLEMAP_JITTER = "settings_bubblemap_jitter"
        const val KEY_BUBBLEMAP_HEATMAP = "settings_bubblemap_heatmap"
        const val KEY_BUBBLEMAP_SIZE = "settings_bubblemap_size"

        const val KEY_REMOTE_ARTWORK = "settings_remote_artwork"
        const val KEY_ARTWORK_CACHE = "settings_artwork_cache"
        const val KEY_CLEAR_ARTWORK_CACHE = "settings_clear_artwork_cache"

        const val KEY_MINI_PLAYER = "settings_mini_player"
        const val KEY_RESUME_PLAYBACK = "settings_resume_playback"
        const val KEY_SHOW_ARTWORK_PLAYER = "settings_show_artwork_player"

        const val KEY_LOCAL_ANALYSIS_ONLY = "settings_local_analysis_only"
        const val KEY_CLEAR_ANALYSIS_DATA = "settings_clear_analysis_data"

        const val KEY_README = "settings_readme"
        const val KEY_APP_VERSION = "settings_app_version"
        const val KEY_LICENSE = "settings_license"
    }
}
