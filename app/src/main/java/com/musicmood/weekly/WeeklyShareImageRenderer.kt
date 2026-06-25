package com.musicmood.weekly

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import androidx.core.content.FileProvider
import com.musicmood.profile.PersonalityTypes
import java.io.File
import java.io.FileOutputStream

/**
 * Genera l'immagine PNG 1080×1920 stile Spotify Wrapped Weekly per il share.
 */
class WeeklyShareImageRenderer(private val context: Context) {

    data class Data(
        val weekLabel: String,        // "Settimana 12 • 2026"
        val totalPlays: Int,
        val dominantMood: String,
        val moodPercentages: List<Triple<String, Int, Int>>, // (mood, pct, color)
        val previousWeekDelta: Int?,  // +12% vs settimana scorsa
    )

    fun render(data: Data): android.net.Uri? {
        val bitmap = drawBitmap(data)
        return saveAndExpose(bitmap)
    }

    private fun drawBitmap(d: Data): Bitmap {
        val w = 1080; val h = 1920
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val color = PersonalityTypes.BY_MOOD[d.dominantMood]?.color ?: 0xFF6750A4.toInt()
        drawGradient(c, w, h, color)

        // Decorazioni
        val decor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0x33FFFFFF
            style = Paint.Style.FILL
        }
        c.drawCircle(w * 0.85f, h * 0.10f, 240f, decor)
        c.drawCircle(w * 0.10f, h * 0.30f, 160f, decor)
        c.drawCircle(w * 0.90f, h * 0.85f, 200f, decor)

        // Header
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0xCCFFFFFF.toInt()
            textSize = 44f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        c.drawText("WEEKLY MOOD REPORT", w / 2f, 180f, headerPaint)

        // Settimana label
        val weekPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textSize = 64f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        c.drawText(d.weekLabel, w / 2f, 280f, weekPaint)

        // Numero totale brani — big
        val bigNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textSize = 280f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        c.drawText("${d.totalPlays}", w / 2f, 550f, bigNumberPaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0xCCFFFFFF.toInt()
            textSize = 48f
            textAlign = Paint.Align.CENTER
        }
        c.drawText("brani ascoltati", w / 2f, 630f, subPaint)

        // Delta vs settimana scorsa
        d.previousWeekDelta?.let { delta ->
            val arrow = if (delta >= 0) "↑" else "↓"
            val sign = if (delta >= 0) "+" else ""
            val deltaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE
                textSize = 56f
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }
            c.drawText("$arrow $sign$delta% vs settimana scorsa",
                w / 2f, 720f, deltaPaint)
        }

        // Dominant mood card
        val cardTop = 820f
        val cardBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0x33FFFFFF
            style = Paint.Style.FILL
        }
        c.drawRoundRect(RectF(80f, cardTop, w - 80f, cardTop + 200f),
            40f, 40f, cardBg)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0xCCFFFFFF.toInt()
            textSize = 36f
            textAlign = Paint.Align.CENTER
        }
        c.drawText("Mood dominante della settimana", w / 2f, cardTop + 70f, labelPaint)

        val moodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textSize = 84f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val emoji = PersonalityTypes.BY_MOOD[d.dominantMood]?.emoji ?: ""
        c.drawText("$emoji ${d.dominantMood}", w / 2f, cardTop + 160f, moodPaint)

        // Distribuzione mood
        val distTop = 1080f
        val rowTop = distTop + 30f
        val rowHeight = 100f
        val barLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textSize = 38f
            isFakeBoldText = true
        }
        val barPct = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textSize = 38f
            isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
        }
        val barBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0x33000000
            style = Paint.Style.FILL
        }
        val barFg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        d.moodPercentages.take(4).forEachIndexed { i, (mood, pct, mc) ->
            val y = rowTop + i * rowHeight
            c.drawText(mood, 130f, y + 12f, barLabel)
            c.drawText("$pct%", w - 130f, y + 12f, barPct)
            val barTop = y + 32f
            val barBottom = barTop + 18f
            c.drawRoundRect(130f, barTop, w - 130f, barBottom, 10f, 10f, barBg)
            val barEnd = 130f + (w - 260f) * (pct / 100f)
            barFg.color = mc
            c.drawRoundRect(130f, barTop, barEnd, barBottom, 10f, 10f, barFg)
        }

        // Watermark
        val watermark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0x99FFFFFF.toInt()
            textSize = 30f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        c.drawText("Generated by Music-Mood", w / 2f, 1850f, watermark)

        return bmp
    }

    private fun drawGradient(c: Canvas, w: Int, h: Int, color: Int) {
        val darker = darken(color, 0.55f)
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(color, darker),
        ).apply { setBounds(0, 0, w, h) }
        gradient.draw(c)
    }

    private fun darken(color: Int, factor: Float): Int {
        val r = (Color.red(color)   * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color)  * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun saveAndExpose(bmp: Bitmap): android.net.Uri? {
        return try {
            val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
            val file = File(dir, "weekly_report.png")
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
