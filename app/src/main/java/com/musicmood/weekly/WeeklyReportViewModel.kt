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
    val errorMessage: String? = null,
)

class WeeklyReportViewModel(app: Application) : AndroidViewModel(app) {

    private val tag = "WeeklyReportVM"

    private val _ui = MutableStateFlow(WeeklyUi(empty = true))
    val ui: StateFlow<WeeklyUi> = _ui.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val newState = withContext(Dispatchers.IO) {
                runCatching {
                    val dao = AppDatabase.get(getApplication()).weeklyReportDao()
                    val recent = dao.recent(12)
                    val latest = recent.firstOrNull()
                    if (latest == null) {
                        WeeklyUi(empty = true)
                    } else {
                        val previous = recent.drop(1).firstOrNull()
                        val moodPct = buildPercentages(latest)
                        val delta = previous?.let { computeDelta(latest, it) }
                        WeeklyUi(
                            latest          = latest,
                            previous        = previous,
                            moodPercentages = moodPct,
                            deltaVsPrev     = delta,
                            weekLabel       = formatWeekLabel(latest),
                            archive         = recent,
                            empty           = false,
                        )
                    }
                }.getOrElse { t ->
                    Log.e(tag, "load() failed: ${t.message}", t)
                    WeeklyUi(empty = true, errorMessage = t.message)
                }
            }
            _ui.value = newState
        }
    }

    fun generateNow() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    runCatching {
                        WeeklyReportGenerator(getApplication()).generateCurrentWeek()
                    }.onFailure { Log.e(tag, "generateNow() failed: ${it.message}", it) }
                }
                load()
            } catch (t: Throwable) {
                Log.e(tag, "generateNow() outer failed: ${t.message}", t)
                _ui.value = _ui.value.copy(errorMessage = t.message)
            }
        }
    }

    private fun buildPercentages(r: WeeklyReportEntity): List<Triple<String, Int, Int>> {
        return runCatching {
            if (r.moodsJson.isBlank()) return@runCatching emptyList()
            val json = JSONObject(r.moodsJson)
            val total = r.totalPlays.coerceAtLeast(1)
            val list = mutableListOf<Triple<String, Int, Int>>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val mood = keys.next()
                val cnt = json.optInt(mood, 0)
                val pct = (cnt * 100.0 / total).toInt()
                val color = PersonalityTypes.BY_MOOD[mood]?.color ?: 0xFF999999.toInt()
                list += Triple(mood, pct, color)
            }
            list.sortedByDescending { it.second }
        }.getOrElse {
            Log.w(tag, "buildPercentages failed: ${it.message}")
            emptyList()
        }
    }

    private fun computeDelta(latest: WeeklyReportEntity, prev: WeeklyReportEntity): Int? {
        if (prev.totalPlays == 0) return null
        val delta = (latest.totalPlays - prev.totalPlays) * 100.0 / prev.totalPlays
        return delta.toInt()
    }

    private fun formatWeekLabel(r: WeeklyReportEntity): String {
        return runCatching {
            val fmt = SimpleDateFormat("d MMM", Locale.ITALIAN)
            val from = fmt.format(Date(r.fromMs))
            val to   = fmt.format(Date(r.toMs - 1))
            "Settimana ${r.week} • $from – $to"
        }.getOrElse { "Settimana ${r.week}" }
    }

    suspend fun buildShareUri(): android.net.Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val u = _ui.value
            val latest = u.latest ?: return@runCatching null
            WeeklyShareImageRenderer(getApplication()).render(
                WeeklyShareImageRenderer.Data(
                    weekLabel       = u.weekLabel,
                    totalPlays      = latest.totalPlays,
                    dominantMood    = latest.dominantMood,
                    moodPercentages = u.moodPercentages,
                    previousWeekDelta = u.deltaVsPrev,
                )
            )
        }.getOrElse {
            Log.e(tag, "buildShareUri failed: ${it.message}", it)
            null
        }
    }
}
