package com.musicmood.settings

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.musicmood.R
import com.musicmood.about.AboutFragment
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private val vm: SettingsViewModel by viewModels()

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
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        root.addView(pageTitle("Impostazioni"))

        root.addView(
            sectionCard(
                title = "Riproduzione",
                subtitle = "Controlli principali del player"
            ) {
                add(
                    settingRow(
                        title = "Timer spegnimento",
                        value = "No"
                    ) {
                        showChoiceDialog(
                            title = "Timer spegnimento",
                            options = arrayOf("No", "15 minuti", "30 minuti", "45 minuti", "60 minuti")
                        )
                    }
                )

                add(
                    settingRow(
                        title = "Velocità riproduzione",
                        value = "1.0x"
                    ) {
                        showChoiceDialog(
                            title = "Velocità riproduzione",
                            options = arrayOf("0.75x", "1.0x", "1.25x", "1.5x", "2.0x")
                        )
                    }
                )

                add(
                    settingSwitchRow(
                        title = "Dissolvenza incrociata tra brani",
                        subtitle = "Transizione morbida tra una traccia e la successiva",
                        checked = false
                    )
                )

                add(
                    settingSwitchRow(
                        title = "Salta silenzio tra i brani",
                        subtitle = "Riduce automaticamente le pause lunghe",
                        checked = false
                    )
                )

                add(
                    settingSwitchRow(
                        title = "Controlli da schermata di blocco",
                        subtitle = "Mostra i comandi del player nelle notifiche",
                        checked = true
                    )
                )
            }
        )

        root.addView(
            sectionCard(
                title = "Coda e playlist",
                subtitle = "Gestione riproduzione, duplicati e playlist"
            ) {
                add(
                    settingRow(
                        title = "Impostazioni coda",
                        value = "Riproduci tutti i brani"
                    ) {
                        showInfo(
                            "Impostazioni coda",
                            "Qui verranno gestite le regole della coda: ordine normale, casuale, ripeti brano e ripeti playlist."
                        )
                    }
                )

                add(
                    settingSwitchRow(
                        title = "Non consentire brani duplicati",
                        subtitle = "Evita duplicati in coda e nelle playlist",
                        checked = false
                    )
                )

                add(
                    settingRow(
                        title = "Gestisci playlist",
                        value = "Apri"
                    ) {
                        showInfo(
                            "Gestisci playlist",
                            "Funzione prevista: creazione, modifica e pulizia playlist."
                        )
                    }
                )
            }
        )

        root.addView(
            sectionCard(
                title = "Libreria",
                subtitle = "Schede, cartelle e scansione dei brani"
            ) {
                add(
                    settingRow(
                        title = "Gestisci schede",
                        value = "Brani, Artisti, Album, Generi, Anno"
                    ) {
                        showInfo(
                            "Gestisci schede",
                            "Qui potrai scegliere quali tab mostrare nella Libreria."
                        )
                    }
                )

                add(
                    settingRow(
                        title = "Cartelle e sottocartelle",
                        value = "Nested folders"
                    ) {
                        showInfo(
                            "Cartelle e sottocartelle",
                            "Questa funzione verrà corretta creando un vero albero cartelle, utile quando sono presenti molte sottodirectory."
                        )
                    }
                )

                add(
                    settingRow(
                        title = "Ricarica libreria",
                        value = "Scansione MediaStore"
                    ) {
                        Snackbar.make(
                            requireView(),
                            "Ricarica libreria: funzione da collegare",
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        )

        root.addView(
            sectionCard(
                title = "Aspetto",
                subtitle = "Tema, colori e identità visiva"
            ) {
                add(
                    settingSwitchRow(
                        title = "Modalità notte",
                        subtitle = "Usa tema scuro",
                        checked = true
                    )
                )

                add(
                    settingRow(
                        title = "Tema app",
                        value = "Viola / Rosa"
                    ) {
                        showChoiceDialog(
                            title = "Tema app",
                            options = arrayOf("Viola / Rosa", "Blu", "Scuro", "Automatico da copertina")
                        )
                    }
                )

                add(
                    settingRow(
                        title = "Icona applicazione",
                        value = "Icona app personalizzata"
                    ) {
                        showInfo(
                            "Icona applicazione",
                            "Useremo l'immagine 'Icona app' come base per generare le icone Android adattive nei formati mipmap."
                        )
                    }
                )
            }
        )

        root.addView(
            sectionCard(
                title = "Mood",
                subtitle = "Classificazione e correzioni manuali"
            ) {
                add(
                    settingRow(
                        title = "Mood tag manuali",
                        value = "Correggi mood dei brani"
                    ) {
                        showInfo(
                            "Mood tag manuali",
                            "Funzione prevista: correggere manualmente il mood di un brano, ad esempio da Nostalgico a Energico."
                        )
                    }
                )

                add(
                    settingRow(
                        title = "Reset correzioni manuali",
                        value = "Cancella override utente"
                    ) {
                        showInfo(
                            "Reset correzioni manuali",
                            "Questa funzione cancellerà solo i mood impostati manualmente, mantenendo quelli calcolati dal motore."
                        )
                    }
                )

                add(
                    settingSwitchRow(
                        title = "Mostra confidenza analisi",
                        subtitle = "Visualizza lo score del motore mood",
                        checked = true
                    )
                )
            }
        )

        root.addView(
            sectionCard(
                title = "Calibrazione motore mood",
                subtitle = "Regolazione del modello attuale"
            ) {
                val info = TextView(requireContext()).apply {
                    text = getString(R.string.settings_calibration_info)
                    textSize = 14f
                    alpha = 0.82f
                    setPadding(0, dp(4), 0, dp(12))
                }
                add(info)

                val valueLabel = TextView(requireContext()).apply {
                    text = "Fattore: 1.0"
                    textSize = 16f
                    setPadding(0, dp(8), 0, dp(4))
                }
                add(valueLabel)

                val slider = Slider(requireContext()).apply {
                    valueFrom = 0.5f
                    valueTo = 2.0f
                    stepSize = 0.1f
                    value = 1.0f

                    addOnChangeListener { _, value, _ ->
                        vm.setShiftFactor(value)
                        valueLabel.text = "Fattore: %.1f".format(value)
                    }
                }
                add(slider)

                add(
                    primaryButton("Applica calibrazione") {
                        vm.applyCalibration()
                    }
                )

                add(
                    dangerButton("Cancella analisi") {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.settings_clear_confirm_title)
                            .setMessage(R.string.settings_clear_confirm_msg)
                            .setPositiveButton(R.string.settings_clear_confirm_yes) { _, _ ->
                                vm.clearAllAnalysis()
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    }
                )
            }
        )

        root.addView(
            sectionCard(
                title = "Integrazioni",
                subtitle = "Ascolti esterni e report settimanale"
            ) {
                add(
                    settingRow(
                        title = "Last.fm",
                        value = "Non configurato"
                    ) {
                        showInfo(
                            "Last.fm",
                            "Prima integrazione consigliata per importare ascolti esterni tramite scrobbling e generare report settimanali."
                        )
                    }
                )

                add(
                    settingRow(
                        title = "Spotify",
                        value = "Non configurato"
                    ) {
                        showInfo(
                            "Spotify",
                            "Integrazione prevista dopo Last.fm. Richiede OAuth e permesso user-read-recently-played."
                        )
                    }
                )
            }
        )

        root.addView(
            sectionCard(
                title = "Privacy e informazioni",
                subtitle = "Permessi, README e informazioni app"
            ) {
                add(
                    settingRow(
                        title = "Autorizzazioni",
                        value = "Audio, notifiche, rete"
                    ) {
                        showInfo(
                            "Autorizzazioni",
                            "L'app usa i permessi audio per leggere la libreria locale, notifiche per il player e rete per recuperare copertine e integrazioni future."
                        )
                    }
                )

                add(
                    settingRow(
                        title = "Info app",
                        value = "Leggi README"
                    ) {
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.fragmentContainer, AboutFragment())
                            .commit()
                    }
                )
            }
        )

        scrollView.addView(root)
        return scrollView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.message.collect { msg ->
                    if (msg != null) {
                        Snackbar.make(requireView(), msg, Snackbar.LENGTH_SHORT).show()
                        vm.clearMessage()
                    }
                }
            }
        }
    }

    private fun pageTitle(text: String): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(16))
        }
    }

    private fun sectionCard(
        title: String,
        subtitle: String,
        contentBuilder: LinearLayout.() -> Unit
    ): MaterialCardView {
        val card = MaterialCardView(requireContext()).apply {
            radius = dp(22).toFloat()
            cardElevation = dp(2).toFloat()
            useCompatPadding = true
            setContentPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14)
            }
        }

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }

        val titleView = TextView(requireContext()).apply {
            text = title
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        }

        val subtitleView = TextView(requireContext()).apply {
            text = subtitle
            textSize = 13f
            alpha = 0.72f
            setPadding(0, dp(2), 0, dp(12))
        }

        container.addView(titleView)
        container.addView(subtitleView)
        container.contentBuilder()

        card.addView(container)
        return card
    }

    private fun settingRow(
        title: String,
        value: String,
        onClick: () -> Unit
    ): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

        val textColumn = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val titleView = TextView(requireContext()).apply {
            text = title
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }

        val valueView = TextView(requireContext()).apply {
            text = value
            textSize = 13f
            alpha = 0.72f
            setPadding(0, dp(2), 0, 0)
        }

        val arrow = TextView(requireContext()).apply {
            text = "›"
            textSize = 28f
            alpha = 0.55f
            gravity = Gravity.CENTER
        }

        textColumn.addView(titleView)
        textColumn.addView(valueView)

        row.addView(textColumn)
        row.addView(arrow)

        return row
    }

    private fun settingSwitchRow(
        title: String,
        subtitle: String,
        checked: Boolean
    ): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }

        val textColumn = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val titleView = TextView(requireContext()).apply {
            text = title
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }

        val subtitleView = TextView(requireContext()).apply {
            text = subtitle
            textSize = 13f
            alpha = 0.72f
            setPadding(0, dp(2), 0, 0)
        }

        val switchView = SwitchMaterial(requireContext()).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, isChecked ->
                val state = if (isChecked) "attivato" else "disattivato"
                Snackbar.make(requireView(), "$title $state", Snackbar.LENGTH_SHORT).show()
            }
        }

        textColumn.addView(titleView)
        textColumn.addView(subtitleView)

        row.addView(textColumn)
        row.addView(switchView)

        return row
    }

    private fun primaryButton(
        text: String,
        onClick: () -> Unit
    ): MaterialButton {
        return MaterialButton(requireContext()).apply {
            this.text = text
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
        }
    }

    private fun dangerButton(
        text: String,
        onClick: () -> Unit
    ): MaterialButton {
        return MaterialButton(requireContext()).apply {
            this.text = text
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        }
    }

    private fun showChoiceDialog(
        title: String,
        options: Array<String>
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setItems(options) { _, which ->
                Snackbar.make(
                    requireView(),
                    "$title: ${options[which]}",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showInfo(title: String, message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
