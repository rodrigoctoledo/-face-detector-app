package com.facedetector.overlay

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.facedetector.detector.DetectionResult
import com.facedetector.detector.DetectorSource

class FaceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val COLOR_ACCURATE = 0xFF1E90FF.toInt()  // Dodger Blue
        const val COLOR_FAST = 0xFF39FF14.toInt()       // Neon Green
        const val COLOR_CONSENSUS = 0xFFFF00FF.toInt()  // Magenta
        private const val OVAL_ALPHA = 56  // ~22% alpha
        private const val CORNER_SIZE = 30f
        private const val STROKE_WIDTH = 3f
        private const val LANDMARK_RADIUS = 5f
    }

    private val ovalFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val ovalStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH + 1f
        strokeCap = Paint.Cap.SQUARE
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        typeface = Typeface.MONOSPACE
    }

    private val landmarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    var results: List<DetectionResult> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var showLandmarks: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    // Scale factors: detection coords → view coords
    private var scaleX = 1f
    private var scaleY = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    var imageWidth: Int = 1
        set(value) { field = value; computeScale() }
    var imageHeight: Int = 1
        set(value) { field = value; computeScale() }

    private fun computeScale() {
        if (width == 0 || height == 0) return
        scaleX = width.toFloat() / imageWidth
        scaleY = height.toFloat() / imageHeight
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeScale()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (result in results) {
            drawFace(canvas, result)
        }
    }

    private fun drawFace(canvas: Canvas, result: DetectionResult) {
        val color = when (result.source) {
            DetectorSource.ACCURATE -> COLOR_ACCURATE
            DetectorSource.FAST -> COLOR_FAST
            DetectorSource.CONSENSUS -> COLOR_CONSENSUS
        }

        val label = when (result.source) {
            DetectorSource.ACCURATE -> "ACCURATE"
            DetectorSource.FAST -> "FAST"
            DetectorSource.CONSENSUS -> "CONSENSUS"
        }

        val box = scaledRect(result.boundingBox)

        // Semi-transparent oval fill
        ovalFillPaint.color = color
        ovalFillPaint.alpha = OVAL_ALPHA
        canvas.drawOval(box, ovalFillPaint)

        // Oval border
        ovalStrokePaint.color = color
        ovalStrokePaint.alpha = 255
        canvas.drawOval(box, ovalStrokePaint)

        // Corner reticle
        cornerPaint.color = color
        drawCorners(canvas, box)

        // Label above bounding box
        labelPaint.color = color
        val labelY = box.top - 8f
        canvas.drawText(label, box.left, labelY, labelPaint)

        // Landmarks
        if (showLandmarks) {
            landmarkPaint.color = color
            result.leftEyePosition?.let { drawLandmark(canvas, it) }
            result.rightEyePosition?.let { drawLandmark(canvas, it) }
            result.nosePosition?.let { drawLandmark(canvas, it) }
            result.leftMouthPosition?.let { drawLandmark(canvas, it) }
            result.rightMouthPosition?.let { drawLandmark(canvas, it) }
        }
    }

    private fun drawCorners(canvas: Canvas, rect: RectF) {
        val c = CORNER_SIZE
        // Top-left
        canvas.drawLine(rect.left, rect.top, rect.left + c, rect.top, cornerPaint)
        canvas.drawLine(rect.left, rect.top, rect.left, rect.top + c, cornerPaint)
        // Top-right
        canvas.drawLine(rect.right - c, rect.top, rect.right, rect.top, cornerPaint)
        canvas.drawLine(rect.right, rect.top, rect.right, rect.top + c, cornerPaint)
        // Bottom-left
        canvas.drawLine(rect.left, rect.bottom - c, rect.left, rect.bottom, cornerPaint)
        canvas.drawLine(rect.left, rect.bottom, rect.left + c, rect.bottom, cornerPaint)
        // Bottom-right
        canvas.drawLine(rect.right - c, rect.bottom, rect.right, rect.bottom, cornerPaint)
        canvas.drawLine(rect.right, rect.bottom - c, rect.right, rect.bottom, cornerPaint)
    }

    private fun drawLandmark(canvas: Canvas, pos: Pair<Float, Float>) {
        val x = pos.first * scaleX + offsetX
        val y = pos.second * scaleY + offsetY
        canvas.drawCircle(x, y, LANDMARK_RADIUS, landmarkPaint)
    }

    private fun scaledRect(rect: android.graphics.RectF): RectF {
        return RectF(
            rect.left * scaleX + offsetX,
            rect.top * scaleY + offsetY,
            rect.right * scaleX + offsetX,
            rect.bottom * scaleY + offsetY
        )
    }
}
