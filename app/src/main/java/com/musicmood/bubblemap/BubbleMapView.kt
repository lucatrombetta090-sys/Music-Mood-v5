package com.musicmood.bubblemap

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import com.musicmood.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class BubbleMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    data class Bubble(
        val songId: Long,
        val title: String,
        val artist: String,
        val valence: Float,
        val arousal: Float,
        val mood: String,
        val color: Int,
    )

    private val bubbles = mutableListOf<Bubble>()
    private var onBubbleTap: ((Bubble) -> Unit)? = null

    private val bgPaint   = Paint(Paint.ANTI_ALIAS_FLAG)
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88B3A8D9.toInt(); strokeWidth = 2f
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33B3A8D9.toInt(); strokeWidth = 1f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB3A8D9.toInt()  // text_secondary chiaro
        textSize = sp(11f)
        isFakeBoldText = true
    }
    private val moodLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()  // bianco pieno
        textSize = sp(14f)
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 0f, 0f, 0xCC000000.toInt())  // ombra per leggibilità
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    private val moodAnchors = listOf(
        Triple("Energico",       0.55f,  0.85f),
        Triple("Festivo",        0.75f,  0.70f),
        Triple("Positivo",       0.70f,  0.30f),
        Triple("Aggressivo",    -0.35f,  0.85f),
        Triple("Concentrazione", 0.10f,  0.10f),
        Triple("Rilassato",      0.40f, -0.40f),
        Triple("Romantico",      0.45f, -0.20f),
        Triple("Nostalgico",    -0.15f, -0.30f),
        Triple("Malinconico",   -0.55f, -0.55f),
    )

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                scale = (scale * d.scaleFactor).coerceIn(0.5f, 5f)
                invalidate()
                return true
            }
        }
    )
    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent,
                                  dx: Float, dy: Float): Boolean {
                offsetX -= dx; offsetY -= dy
                invalidate(); return true
            }
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                hitTest(e.x, e.y)?.let { onBubbleTap?.invoke(it) }
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                scale = 1f; offsetX = 0f; offsetY = 0f
                invalidate(); return true
            }
        }
    )

    init {
        bgPaint.color = ContextCompat.getColor(context, android.R.color.transparent)
    }

    fun setBubbles(items: List<Bubble>) {
        bubbles.clear(); bubbles.addAll(items); invalidate()
    }
    fun setOnBubbleTap(cb: (Bubble) -> Unit) { onBubbleTap = cb }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2 + offsetX
        val cy = h / 2 + offsetY
        val r = min(w, h) / 2f * 0.92f * scale

        for (i in -4..4) {
            val v = i / 4f
            canvas.drawLine(cx - r, cy - v * r, cx + r, cy - v * r, gridPaint)
            canvas.drawLine(cx + v * r, cy - r, cx + v * r, cy + r, gridPaint)
        }
        canvas.drawLine(cx - r, cy, cx + r, cy, axisPaint)
        canvas.drawLine(cx, cy - r, cx, cy + r, axisPaint)

        canvas.drawText("Valenza →", cx + r - dp(80f), cy - dp(8f), labelPaint)
        canvas.drawText("Arousal ↑", cx + dp(8f), cy - r + dp(16f), labelPaint)
        canvas.drawText("− calmo",   cx + dp(8f), cy + r - dp(8f), labelPaint)
        canvas.drawText("− neg.",    cx - r + dp(8f), cy + dp(16f), labelPaint)

        moodAnchors.forEach { (label, v, a) ->
            val x = cx + v * r
            val y = cy - a * r
            canvas.drawText(label, x, y - dp(16f), moodLabelPaint)
        }

        bubbles.forEach { b ->
            val x = cx + b.valence * r
            val y = cy - b.arousal * r
            bubblePaint.color = (b.color and 0x00FFFFFF) or 0xCC000000.toInt()
            canvas.drawCircle(x, y, dp(5f), bubblePaint)
        }
    }

    private fun hitTest(x: Float, y: Float): Bubble? {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2 + offsetX
        val cy = h / 2 + offsetY
        val r = min(w, h) / 2f * 0.92f * scale
        val touchRadius = dp(16f)
        return bubbles.minByOrNull { b ->
            val bx = cx + b.valence * r
            val by = cy - b.arousal * r
            distance(x, y, bx, by)
        }?.takeIf { b ->
            val bx = cx + b.valence * r
            val by = cy - b.arousal * r
            distance(x, y, bx, by) < touchRadius
        }
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1; val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun sp(v: Float) = v * resources.displayMetrics.scaledDensity
}
