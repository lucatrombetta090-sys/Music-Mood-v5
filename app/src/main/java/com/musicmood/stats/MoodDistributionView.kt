package com.musicmood.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class MoodDistributionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    data class Slice(val label: String, val count: Int, val color: Int)

    private var slices: List<Slice> = emptyList()
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF333333.toInt()
        textSize = sp(13f)
        isFakeBoldText = true
    }
    private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF666666.toInt()
        textSize = sp(12f)
        textAlign = Paint.Align.RIGHT
    }

    fun setData(items: List<Slice>) {
        slices = items.sortedByDescending { it.count }
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val rowHeight = dp(36f).toInt()
        val padding = dp(16f).toInt()
        val h = padding * 2 + slices.size.coerceAtLeast(1) * rowHeight
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (slices.isEmpty()) return

        val total = slices.sumOf { it.count }.coerceAtLeast(1)
        val maxCount = slices.maxOf { it.count }
        val left = dp(120f)
        val right = width - dp(60f)
        val barAreaWidth = right - left
        val rowHeight = dp(36f)
        val padding = dp(16f)

        slices.forEachIndexed { i, slice ->
            val y = padding + i * rowHeight + rowHeight / 2
            // Label
            canvas.drawText(slice.label, dp(12f), y + dp(5f), labelPaint)

            // Bar
            val ratio = slice.count.toFloat() / maxCount
            val barRight = left + barAreaWidth * ratio
            barPaint.color = (slice.color and 0x00FFFFFF) or 0xCC000000.toInt()
            canvas.drawRoundRect(
                RectF(left, y - dp(12f), barRight, y + dp(12f)),
                dp(6f), dp(6f), barPaint,
            )

            // Count + percentage
            val pct = (slice.count * 100.0 / total).toInt()
            canvas.drawText("${slice.count} ($pct%)", width - dp(8f), y + dp(5f), countPaint)
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun sp(v: Float) = v * resources.displayMetrics.scaledDensity
}
