package com.musicmood.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = requireContext()

        preferenceScreen = preferenceManager.createPreferenceScreen(ctx).apply {

            addPreference(
                PreferenceCategory(ctx).apply {
                    title = "Aspetto"

                    addPreference(
                        ListPreference(ctx).apply {
                            key = KEY_THEME_MODE
                            title = "Tema applicazione"
                            summary = "Scegli il tema dell'app"
                            entries = arrayOf("Sistema", "Chiaro", "Scuro")
                            entryValues = arrayOf("system", "light", "dark")
                            setDefaultValue("system")
                        }
                    )

                    addPreference(
                        SwitchPreferenceCompat(ctx).apply {
                            key = KEY_DYNAMIC_COLORS
                            title = "Colori dinamici"
                            summary = "Usa colori adattivi quando supportati dal dispositivo"
                            setDefaultValue(true)
                        }
                    )

                    addPreference(
                        SwitchPreferenceCompat(ctx).apply {
                            key = KEY_COMPACT_MODE
                            title = "Modalità compatta"
                            summary = "Riduce spaziature e dimensioni degli elementi nelle liste"
                            setDefaultValue(false)
                        }
                    )
                }
            )

            addPreference(
                PreferenceCategory(ctx).apply {
                    title = "Libreria musicale"

                    addPreference(
                        SwitchPreferenceCompat(ctx).apply {
                            key = KEY_SCAN_ON_START
                            title = "Scansione all'avvio"
                            summary = "Aggiorna automaticamente la libreria quando apri l'app"
                            setDefaultValue(false)
                        }
                    )

                    addPreference(
                        SwitchPreferenceCompat(ctx).apply {
                            key = KEY_INCLUDE_SHORT_TRACKS
                            title = "Includi tracce brevi"
                            summary = "Mostra anche intro, jingle e tracce di durata ridotta"
                            setDefaultValue(false)
                        }
                    )

                    addPreference(
                        ListPreference(ctx).apply {
                            key = KEY_DEFAULT_SORT
                            title = "Ordinamento predefinito"
                            summary = "Scegli come ordinare i brani nella libreria"
                            entries = arrayOf(
                                "Titolo",
                                "Artista",
                                "Album",
                                "Durata",
                                "Mood",
                                "Anno"
                            )
                            entryValues = arrayOf(
                                "title",
                                "artist",
                                "album",
                                "duration",
                                "mood",
                                "year"
                            )
                            setDefaultValue("title")
                        }
                    )
                }
            )

            addPreference(
                PreferenceCategory(ctx).apply {
                    title = "Mood engine"

                    addPreference(
                        SwitchPreferenceCompat(ctx).apply {
                            key = KEY_AUTO_ANALYZE
                            title = "Analisi automatica mood"
                            summary = "Analizza i brani non ancora classificati"
                            setDefaultValue(true)
                        }
                    )

                    addPreference(
                        SwitchPreferenceCompat(ctx).apply {
                            key = KEY_USE_USER_MOOD
                            title = "Priorità mood manuale"
                            summary = "Usa il mood scelto manualmente al posto di quello calcolato"
                            setDefaultValue(true)
                        }
                    )

                    addPreference(
                        ListPreference(ctx).apply {
                            key = KEY_ANALYSIS_PROFILE
                            title = "Profilo analisi"
                            summary = "Bilancia velocità e accuratezza dell'analisi"
                            entries = arrayOf(
                                "Veloce",
                                "Bilanciato",
                                "Accurato"
                            )
                            entryValues = arrayOf(
                                "fast",
                                "balanced",
                                "accurate"
                            )
                            setDefaultValue("balanced")
                        }
                    )

                    addPreference(
                        SwitchPreferenceCompat(ctx).apply {
                            key = KEY_SHOW_CONFIDENCE
                            title = "Mostra confidenza mood"
                            summary = "Visualizza il livello di affidabilità della classificazione"
                            setDefaultValue(true)
                        }
                    )
                }
            )

            addPreference(
                PreferenceCategory(ctx).apply {
                    title = "BubbleMap"

                    addPreference(
                        SwitchPreferenceCompat(ctx).apply {
                            key = KEY_BUBBLEMAP_ENABLED
                            title = "Abilita BubbleMap"
                            summary = "Mostra i brani nello spazio valenza/arousal"
                            setDefaultValue(true)
                        }
                    )

                    addPreference(
                        SwitchPreferenceCompat(ctx).apply {
                            key = KEY_BUBBLEMAP_JITTER
                            title = "Riduci sovrapposizione punti"
                            summary = "Applica un leggero spostamento visuale ai punti molto vicini"
                            setDefaultValue(true)
                        }
                    )

                    addPreference(
                        SwitchPreferenceCompat(ctx).apply {
                            key = KEY_BUBBLEMAP_HEATMAP
                            title = "Modalità densità"
                            summary = "Evidenzia aree con molti brani sovrapposti"
                            setDefaultValue(false)
                        }
                    )

                    addPreference(
                        ListPreference(ctx).apply {
                            key = KEY_BUBBLEMAP_SIZE
                            title = "Dimensione bolle"
                            summary = "Scegli la dimensione visuale dei punti"
                            entries = arrayOf("Piccola", "Media", "Grande")
                            entryValues = arrayOf("small", "medium", "large")
                            setDefaultValue("medium")
                        }
                    )
                }
            )

            addPreference(
                PreferenceCategory(ctx).apply {
                    title = "Copertine"

                    addPreference(
                        SwitchPreferenceCompat(ctx).apply {
                            key = KEY_REMOTE_ARTWORK
                            title = "Ricerca copertine online"
                            summary = "Cerca copertine mancanti tramite sorgenti remote"
                            setDefaultValue(true)
                        }
                    )

                    addPreference(
                        SwitchPreferenceCompat(ctx).apply {
                            key = KEY_ARTWORK_CACHE
                            title = "Cache copertine"
                            summary = "Memorizza le copertine trovate per ridurre le ricerche successive"
                            setDefaultValue(true)
                        }
                    )

                    addPreference(
                        Preference(ctx).apply {
                            key = KEY_CLEAR_ARTWORK_CACHE
                            title = "Svuota cache copertine"
                            summary = "Rimuove la cache locale delle copertine"
                            setOnPreferenceClickListener {
                                Toast.makeText(
                                    requireContext(),
                                    "Cache copertine: funzione pronta per collegamento al repository",
                                    Toast.LENGTH_SHORT
                                ).show()
                                true
                            }
                        }
                    )
                }
            )

            addPreference(
                PreferenceCategory(ctx).apply {
                    title = "Player"

                    addPreference(
                        SwitchPreferenceCompat(ctx).apply {
                            key = KEY_MINI_PLAYER
                            title = "Mini-player persistente"
                            summary = "Mostra il mini-player quando un brano è in riproduzione"
                            setDefaultValue(true)
                        }
                    )

                    addPreference(
                        SwitchPreferenceCompat(ctx).apply {
                            key = KEY_RESUME_PLAYBACK
                            title = "Riprendi ultima riproduzione"
                            summary = "Mantiene il brano corrente e la posizione quando possibile"
                            setDefaultValue(true)
                        }
                    )

                    addPreference(
                        SwitchPreferenceCompat(ctx).apply {
                            key = KEY_SHOW_ARTWORK_PLAYER
                            title = "Mostra copertina nel player"
                            summary = "Visualizza la cover nel mini-player e nel player completo"
                            setDefaultValue(true)
                        }
                    )
                }
            )

            addPreference(
                PreferenceCategory(ctx).apply {
                    title = "Privacy e dati"

                    addPreference(
                        SwitchPreferenceCompat(ctx).apply {
                            key = KEY_LOCAL_ANALYSIS_ONLY
                            title = "Analisi solo locale"
                            summary = "Esegue l'analisi audio sul dispositivo"
                            setDefaultValue(true)
                            isEnabled = false
                        }
                    )

                    addPreference(
                        Preference(ctx).apply {
                            key = KEY_CLEAR_ANALYSIS_DATA
                            title = "Cancella dati analisi"
                            summary = "Rimuove mood, statistiche e report salvati localmente"
                            setOnPreferenceClickListener {
                                Toast.makeText(
                                    requireContext(),
                                    "Cancellazione dati analisi: da collegare ai DAO Room",
                                    Toast.LENGTH_SHORT
                                ).show()
                                true
                            }
                        }
                    )
                }
            )

            addPreference(
                PreferenceCategory(ctx).apply {
                    title = "Info app"

                    addPreference(
                        Preference(ctx).apply {
                            key = KEY_README
                            title = "README"
                            summary = readReadmeSummary()
                            setOnPreferenceClickListener {
                                openRepository()
                                true
                            }
                        }
                    )

                    addPreference(
                        Preference(ctx).apply {
                            key = KEY_APP_VERSION
                            title = "Versione app"
                            summary = getAppVersionLabel()
                        }
                    )

                    addPreference(
                        Preference(ctx).apply {
                            key = KEY_LICENSE
                            title = "Licenza"
                            summary = "MIT"
                        }
                    )
                }
            )
        }
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
                .map { it.trim() }
                .filter { it.isNotEmpty() }
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

    companion object {
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
