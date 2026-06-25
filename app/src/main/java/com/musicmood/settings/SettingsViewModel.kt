package com.musicmood.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musicmood.data.CalibrationRepository
import com.musicmood.data.MoodRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val moodRepo  = MoodRepository.get(app)
    private val calibRepo = CalibrationRepository.get(app)

    private val _shiftFactor = MutableStateFlow(0.5f)
    val shiftFactor: StateFlow<Float> = _shiftFactor.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setShiftFactor(value: Float) { _shiftFactor.value = value }

    fun applyCalibration() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                calibRepo.calibrateAndReclassify()
            }
            _message.value = when (result) {
                is CalibrationRepository.CalibrationResult.Success ->
                    "✅ Calibrazione applicata su ${result.songsReclassified} brani"
                is CalibrationRepository.CalibrationResult.NotEnoughData ->
                    "⚠️ Servono almeno 50 brani analizzati"
            }
        }
    }

    fun clearAllAnalysis() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { moodRepo.clearAll() }
            _message.value = "🗑️ Analisi cancellata"
        }
    }

    fun clearMessage() { _message.value = null }
}
