package com.musicmood.data

import android.content.Context
import android.content.SharedPreferences

class SettingsRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("musicmood_settings", Context.MODE_PRIVATE)

    var calibrationShiftFactor: Float
        get() = prefs.getFloat(KEY_SHIFT_FACTOR, 0.5f)
        set(v) { prefs.edit().putFloat(KEY_SHIFT_FACTOR, v).apply() }

    var themeMode: Int
        get() = prefs.getInt(KEY_THEME, MODE_SYSTEM)
        set(v) { prefs.edit().putInt(KEY_THEME, v).apply() }

    var autoBatchAnalysis: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BATCH, false)
        set(v) { prefs.edit().putBoolean(KEY_AUTO_BATCH, v).apply() }

    var weeklyReportEnabled: Boolean
        get() = prefs.getBoolean(KEY_WEEKLY_ENABLED, true)
        set(v) { prefs.edit().putBoolean(KEY_WEEKLY_ENABLED, v).apply() }

    var analysisWindowSec: Int
        get() = prefs.getInt(KEY_WINDOW_SEC, 30)
        set(v) { prefs.edit().putInt(KEY_WINDOW_SEC, v).apply() }

    /** Se true, usa YAMNet (TFLite) come classificatore primario, DSP come fallback. */
    var useYamnet: Boolean
        get() = prefs.getBoolean(KEY_USE_YAMNET, true)
        set(v) { prefs.edit().putBoolean(KEY_USE_YAMNET, v).apply() }

    companion object {
        const val MODE_LIGHT  = 1
        const val MODE_DARK   = 2
        const val MODE_SYSTEM = 0

        private const val KEY_SHIFT_FACTOR   = "shift_factor"
        private const val KEY_THEME          = "theme_mode"
        private const val KEY_AUTO_BATCH     = "auto_batch"
        private const val KEY_WEEKLY_ENABLED = "weekly_enabled"
        private const val KEY_WINDOW_SEC     = "window_sec"
        private const val KEY_USE_YAMNET     = "use_yamnet"

        @Volatile private var INSTANCE: SettingsRepository? = null
        fun get(context: Context): SettingsRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context).also { INSTANCE = it }
            }
    }
}
