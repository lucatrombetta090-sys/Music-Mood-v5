package com.musicmood.weekly

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musicmood.data.db.AppDatabase
import com.musicmood.data.db.WeeklyReportEntity
import com.musicmood.profile.PersonalityTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WeeklyUi(
    val latest: WeeklyReportEntity? = null,
    val previous: WeeklyReportEntity? = null,
    val moodPercentages: List<Triple<String, Int, Int>> = emptyList(),
    val deltaVsPrev: Int? = null,
    val weekLabel: String = "",
    val archive: List<WeeklyReportEntity> = emptyList(),
    val empty: Boolean = false,
)

class WeeklyReportViewModel(app: Application) : AndroidViewModel(app) {

    private val tag = "WeeklyReportVM"
    private val reportDao = AppDatabase.get(app).weeklyReportDao()

    private val _ui = MutableStateFlow(WeeklyUi())
    val ui: StateFlow<WeeklyUi> = _ui.asStateFlow()

    fun load() {
        viewModelScope.launch {
            try {
                val recent = withContext(Dispatchers.IO) {
                    runCatching { reportDao.recent(12) }
                        .onFailure { Log.e(tag, "recent() failed: ${it.message}", it) }
                        .getOrDefault(emptyList())
                }
                val latest = recent.firstOrNull()
                if (latest == null) {
                    _ui.value = WeeklyUi(empty = true)
                    return@launch
                }
                val previous = recent.drop(1).firstOrNull()
                val moodPct = buildPercentages(latest)
                val delta = previous?.let { computeDelta(latest, it) }
                _ui.value = WeeklyUi(
                    latest          = latest,
                    previous        = previous,
                    moodPercentages = moodPct,
                    deltaVsPrev     = delta,
                    weekLabel       = formatWeekLabel(latest),
                    archive         = recent,
                )
            } catch (t: Throwable) {
                Log.e(tag, "load() failed: ${t.message}", t)
                _ui.value = WeeklyUi(empty = true)
            }
        }
    }

    fun generateNow() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    WeeklyReportGenerator(getApplication())
                        .generateCurrentWeek()
                }
                load()
            } catch (t: Throwable) {
                Log.e(tag, "generateNow() failed: ${t.message}", t)
            }
        }
    }

    private fun buildPercentages(r: WeeklyReportEntity): List<Triple<String, Int, Int>> {
        return try {
            if (r.moodsJson.isBlank()) return emptyList()
            val json = JSONObject(r.moodsJson)
            val total = r.totalPlays.coerceAtLeast(1)
            val list = mutableListOf<Triple<String, Int, Int>>()
            val it = json.keys()
            while (it.hasNext()) {
                val mood = it.next()
                val cnt = json.optInt(mood, 0)
                val pct = (cnt * 100.0 / total).toInt()
                val color = PersonalityTypes.BY_MOOD[mood]?.color ?: 0xFF999999.toInt()
                list += Triple(mood, pct, color)
            }
            list.sortedByDescending { it.second }
        } catch (t: Throwable) {
            Log.w(tag, "buildPercentages() failed: ${t.message}")
            emptyList()
        }
    }

    private fun computeDelta(latest: WeeklyReportEntity, prev: WeeklyReportEntity): Int? {
        if (prev.totalPlays == 0) return null
        val delta = (latest.totalPlays - prev.totalPlays) * 100.0 / prev.totalPlays
        return delta.toInt()
    }

    private fun formatWeekLabel(r: WeeklyReportEntity): String {
        return try {
            val fmt = SimpleDateFormat("d MMM", Locale.ITALIAN)
            val from = fmt.format(Date(r.fromMs))
            val to   = fmt.format(Date(r.toMs - 1))
            "Settimana ${r.week} • $from – $to"
        } catch (_: Throwable) {
            "Settimana ${r.week}"
        }
    }

    suspend fun buildShareUri(): android.net.Uri? = withContext(Dispatchers.IO) {
        try {
            val u = _ui.value
            val latest = u.latest ?: return@withContext null
            WeeklyShareImageRenderer(getApplication()).render(
                WeeklyShareImageRenderer.Data(
                    weekLabel       = u.weekLabel,
                    totalPlays      = latest.totalPlays,
                    dominantMood    = latest.dominantMood,
                    moodPercentages = u.moodPercentages,
                    previousWeekDelta = u.deltaVsPrev,
                )
            )
        } catch (t: Throwable) {
            Log.e(tag, "buildShareUri() failed: ${t.message}", t)
            null
        }
    }
}
