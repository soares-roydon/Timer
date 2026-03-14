package com.yor.timer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Renders the clock-face style ring from the screenshots:
 *
 *  - 60 radial tick marks evenly spaced around the circle (no filled arc)
 *  - Ticks in the "elapsed" region are light grey; ticks in the "remaining"
 *    region are blue — this creates the visual progress effect
 *  - A glowing dot indicator that travels along the tick ring as time elapses
 *
 * progress = 1f  → timer just started (all ticks blue, dot at 12 o'clock)
 * progress = 0f  → timer finished    (all ticks grey,  dot at 12 o'clock)
 */
class TimerRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** 1f = full (just started) → 0f = empty (done) */
    var progress: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    // ── Paints ────────────────────────────────────────────────────────────────

    /** Blue tick — remaining time */
    private val tickBluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style     = Paint.Style.STROKE
        color     = 0xFF4A90D9.toInt()
        strokeCap = Paint.Cap.ROUND
    }

    /** Grey tick — elapsed time */
    private val tickGreyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style     = Paint.Style.STROKE
        color     = 0xFFDDE3EA.toInt()
        strokeCap = Paint.Cap.ROUND
    }

    /** Dot centre fill */
    private val dotFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF1A73E8.toInt()
    }

    /** Dot outer glow ring */
    private val dotGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x331A73E8   // semi-transparent blue
        strokeWidth = 0f     // set in onDraw
    }

    // ── Draw ──────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w   = width.toFloat()
        val h   = height.toFloat()
        val cx  = w / 2f
        val cy  = h / 2f
        val dim = min(w, h)

        // Tick geometry
        val tickStroke = dim * 0.012f          // thickness of each tick line
        val ringR      = dim * 0.40f           // radius to mid-point of ticks
        val tickHalf   = dim * 0.045f          // half-length of each tick
        val innerR     = ringR - tickHalf
        val outerR     = ringR + tickHalf

        tickBluePaint.strokeWidth = tickStroke
        tickGreyPaint.strokeWidth = tickStroke

        // How many ticks are "remaining" (blue) out of 60
        val blueTicks = (progress * 60f).toInt().coerceIn(0, 60)

        // Draw 60 tick marks — starting from 12 o'clock (angle = -90°)
        // Tick 0 = top; ticks increase clockwise
        // Blue ticks = indices 0 .. (blueTicks-1)  (the leading / remaining portion)
        for (i in 0 until 60) {
            val angleDeg = i * 6.0 - 90.0          // 6° per tick, start at top
            val rad      = Math.toRadians(angleDeg)
            val cosA     = cos(rad).toFloat()
            val sinA     = sin(rad).toFloat()

            val paint = if (i < blueTicks) tickBluePaint else tickGreyPaint

            canvas.drawLine(
                cx + innerR * cosA, cy + innerR * sinA,
                cx + outerR * cosA, cy + outerR * sinA,
                paint
            )
        }

        // ── Travelling dot ────────────────────────────────────────────────────
        // Dot sits at the boundary between blue and grey ticks
        if (progress > 0.001f && progress < 0.999f) {
            val dotAngleDeg = blueTicks * 6.0 - 90.0
            val dotRad      = Math.toRadians(dotAngleDeg)
            val dotCx       = cx + ringR * cos(dotRad).toFloat()
            val dotCy       = cy + ringR * sin(dotRad).toFloat()

            val dotR    = dim * 0.028f   // radius of the filled dot
            val glowW   = dim * 0.018f   // width of the outer glow ring

            dotGlowPaint.strokeWidth = glowW
            canvas.drawCircle(dotCx, dotCy, dotR + glowW, dotGlowPaint)
            canvas.drawCircle(dotCx, dotCy, dotR, dotFillPaint)
        }
    }
}