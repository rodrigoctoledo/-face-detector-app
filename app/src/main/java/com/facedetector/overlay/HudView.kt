package com.facedetector.overlay

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.facedetector.detector.DualDetectorManager

class HudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint().apply {
        color = Color.argb(160, 0, 0, 0)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 32f
        typeface = Typeface.MONOSPACE
    }

    private val accentPaintAccurate = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = FaceOverlayView.COLOR_ACCURATE
        textSize = 32f
        typeface = Typeface.MONOSPACE
    }

    private val accentPaintFast = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = FaceOverlayView.COLOR_FAST
        textSize = 32f
        typeface = Typeface.MONOSPACE
    }

    private val accentPaintConsensus = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = FaceOverlayView.COLOR_CONSENSUS
        textSize = 32f
        typeface = Typeface.MONOSPACE
    }

    private val padding = 16f
    private val lineHeight = 38f
    private val bgRect = RectF()

    var stats: DualDetectorManager.Stats? = null
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = stats ?: return

        val lines = listOf(
            "PIPELINE : ${s.pipelineFps.format(1)} fps",
            "ACCURATE : ${s.accurateFps.format(1)} fps  |  faces: ${s.accurateCount}",
            "FAST     : ${s.fastFps.format(1)} fps  |  faces: ${s.fastCount}",
            "CONSENSUS: faces ${s.consensusCount}",
            "MODE     : ${s.displayMode.name}"
        )

        val totalHeight = padding * 2 + lines.size * lineHeight
        bgRect.set(0f, 0f, width.toFloat(), totalHeight)
        canvas.drawRect(bgRect, bgPaint)

        var y = padding + textPaint.textSize
        for ((i, line) in lines.withIndex()) {
            val paint = when (i) {
                1 -> accentPaintAccurate
                2 -> accentPaintFast
                3 -> accentPaintConsensus
                else -> textPaint
            }
            canvas.drawText(line, padding, y, paint)
            y += lineHeight
        }
    }

    private fun Float.format(digits: Int) = "%.${digits}f".format(this)
}
