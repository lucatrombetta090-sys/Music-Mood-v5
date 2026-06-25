package com.musicmood.weekly

import android.content.Context
import com.musicmood.data.db.AppDatabase
import com.musicmood.data.db.WeeklyReportEntity
import org.json.JSONObject
import java.util.Calendar

/**
 * Calcola il report della settimana attualmente conclusa (lunedì-domenica)
 * a partire dagli eventi di ascolto registrati su Room.
 */
class WeeklyReportGenerator(context: Context) {

    private val db = AppDatabase.get(context)
    private val eventDao  = db.listeningEventDao()
    private val reportDao = db.weeklyReportDao()

    suspend fun generateLastWeek(): WeeklyReportEntity? {
        val (fromMs, toMs, year, week) = lastWeekRange()
        return generateForRange(fromMs, toMs, year, week)
    }

    suspend fun generateCurrentWeek(): WeeklyReportEntity? {
        val (fromMs, toMs, year, week) = currentWeekRange()
        return generateForRange(fromMs, toMs, year, week)
    }

    private suspend fun generateForRange(
        fromMs: Long, toMs: Long, year: Int, week: Int,
    ): WeeklyReportEntity? {
        val moodCounts = eventDao.moodCountsInRange(fromMs, toMs)
        val total = eventDao.totalInRange(fromMs, toMs)

        if (total == 0) return null

        val moodsJson = JSONObject().apply {
            moodCounts.forEach { put(it.mood, it.cnt) }
        }.toString()

        val dominant = moodCounts.firstOrNull()?.mood ?: "Sconosciuto"

        val report = WeeklyReportEntity(
            year         = year,
            week         = week,
            fromMs       = fromMs,
            toMs         = toMs,
            totalPlays   = total,
            dominantMood = dominant,
            moodsJson    = moodsJson,
        )
        reportDao.save(report)
        return report
    }

    private fun currentWeekRange(): WeekRange {
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val from = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 7)
        val to = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return WeekRange(from, to,
            cal.get(Calendar.YEAR),
            cal.get(Calendar.WEEK_OF_YEAR))
    }

    private fun lastWeekRange(): WeekRange {
        val now = currentWeekRange()
        return WeekRange(
            now.fromMs - 7L * 86_400_000L,
            now.fromMs,
            run {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = now.fromMs - 86_400_000L
                }
                cal.get(Calendar.YEAR)
            },
            run {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = now.fromMs - 86_400_000L
                }
                cal.get(Calendar.WEEK_OF_YEAR)
            },
        )
    }

    data class WeekRange(
        val fromMs: Long,
        val toMs: Long,
        val year: Int,
        val week: Int,
    )
}
