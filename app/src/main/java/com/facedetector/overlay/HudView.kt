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
        color = Color.argb(200, 0, 0, 0)
    }

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 38f
        typeface = Typeface.MONOSPACE
        setShadowLayer(3f, 1f, 1f, Color.BLACK)
    }

    private val accuratePaint = Paint(basePaint).apply { color = Color.rgb(30, 144, 255) }
    private val fastPaint     = Paint(basePaint).apply { color = Color.rgb(57, 255, 20) }
    private val consensusPaint= Paint(basePaint).apply { color = Color.rgb(255, 0, 255) }

    private val pad  = 20f
    private val lineH = 46f

    var stats: DualDetectorManager.Stats? = null
        set(value) { field = value; invalidate() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = (pad * 2 + 5 * lineH).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = stats ?: drawPlaceholder(canvas).also { return }

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        var y = pad + basePaint.textSize
        canvas.drawText("PIPELINE : ${s.pipelineFps.fmt(1)} fps", pad, y, basePaint);     y += lineH
        canvas.drawText("ACCURATE : ${s.accurateFps.fmt(1)} fps  |  faces: ${s.accurateCount}", pad, y, accuratePaint); y += lineH
        canvas.drawText("FAST     : ${s.fastFps.fmt(1)} fps  |  faces: ${s.fastCount}",     pad, y, fastPaint);      y += lineH
        canvas.drawText("CONSENSO : faces ${s.consensusCount}",                              pad, y, consensusPaint); y += lineH
        canvas.drawText("MODO     : ${s.displayMode.name}",                                  pad, y, basePaint)
    }

    private fun drawPlaceholder(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        canvas.drawText("Iniciando...", pad, pad + basePaint.textSize, basePaint)
    }

    private fun Float.fmt(d: Int) = "%.${d}f".format(this)
}
