package com.musicmood.settings

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.musicmood.R
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val vm: SettingsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val slider = view.findViewById<Slider>(R.id.shiftFactorSlider)
        val valueLabel = view.findViewById<TextView>(R.id.shiftFactorValue)
        val info = view.findViewById<TextView>(R.id.calibrationInfo)

        slider.addOnChangeListener { _, value, _ ->
            vm.setShiftFactor(value)
            valueLabel.text = "Fattore: %.1f".format(value)
        }
        valueLabel.text = "Fattore: %.1f".format(slider.value)

        info.text = getString(R.string.settings_calibration_info)

        view.findViewById<MaterialButton>(R.id.btnApplyCalibration)
            .setOnClickListener {
                vm.applyCalibration()
            }

        view.findViewById<MaterialButton>(R.id.btnClearAnalysis)
            .setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.settings_clear_confirm_title)
                    .setMessage(R.string.settings_clear_confirm_msg)
                    .setPositiveButton(R.string.settings_clear_confirm_yes) { _, _ ->
                        vm.clearAllAnalysis()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }

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
}
