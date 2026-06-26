package com.musicmood.profile

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class RadarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    data class Axis(val label: String, val value: Float, val color: Int)

    private var axes: List<Axis> = emptyList()
    private var animProgress = 1f
    private var dominantColor: Int = 0xFF8E6BFF.toInt()

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33B3A8D9
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x55B3A8D9.toInt()
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }
    private val polygonFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val polygonStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeJoin = Paint.Join.ROUND
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = sp(12f)
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB3A8D9.toInt()
        textSize = sp(10f)
        textAlign = Paint.Align.CENTER
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    fun setData(items: List<Axis>, dominantColor: Int) {
        axes = items
        this.dominantColor = dominantColor
        polygonFillPaint.color   = (dominantColor and 0x00FFFFFF) or 0x55000000
        polygonStrokePaint.color = dominantColor
        dotPaint.color           = dominantColor
        animateIn()
    }

    private fun animateIn() {
        animProgress = 0f
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900
            interpolator = OvershootInterpolator(0.8f)
            addUpdateListener {
                animProgress = it.animatedValue as Float
                invalidate()
            }
        }.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (axes.isEmpty()) return

        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) * 0.65f

        drawGrid(canvas, cx, cy, radius)
        drawAxes(canvas, cx, cy, radius)
        drawPolygon(canvas, cx, cy, radius)
        drawLabels(canvas, cx, cy, radius)
    }

    private fun drawGrid(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        for (i in 1..4) {
            val r = radius * i / 4f
            val path = Path()
            for (j in axes.indices) {
                val angle = angleFor(j)
                val x = cx + r * cos(angle).toFloat()
                val y = cy + r * sin(angle).toFloat()
                if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            canvas.drawPath(path, gridPaint)
        }
    }

    private fun drawAxes(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        for (i in axes.indices) {
            val angle = angleFor(i)
            val x = cx + radius * cos(angle).toFloat()
            val y = cy + radius * sin(angle).toFloat()
            canvas.drawLine(cx, cy, x, y, axisPaint)
        }
    }

    private fun drawPolygon(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val path = Path()
        val dots = mutableListOf<Pair<Float, Float>>()
        for (i in axes.indices) {
            val angle = angleFor(i)
            val value = axes[i].value * animProgress
            val r = radius * value.coerceIn(0f, 1f)
            val x = cx + r * cos(angle).toFloat()
            val y = cy + r * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            dots += x to y
        }
        path.close()
        canvas.drawPath(path, polygonFillPaint)
        canvas.drawPath(path, polygonStrokePaint)
        for ((x, y) in dots) {
            canvas.drawCircle(x, y, dp(4f), dotPaint)
        }
    }

    private fun drawLabels(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val labelRadius = radius + dp(22f)
        for (i in axes.indices) {
            val angle = angleFor(i)
            val x = cx + labelRadius * cos(angle).toFloat()
            val y = cy + labelRadius * sin(angle).toFloat() + dp(4f)
            canvas.drawText(axes[i].label, x, y, labelPaint)
            val pct = (axes[i].value * 100).toInt()
            canvas.drawText("$pct%", x, y + dp(14f), valuePaint)
        }
    }

    private fun angleFor(index: Int): Double {
        val total = axes.size
        return Math.PI * 2 * index / total - Math.PI / 2
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun sp(v: Float) = v * resources.displayMetrics.scaledDensity
}
