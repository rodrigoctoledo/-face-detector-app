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
        const val COLOR_ACCURATE = 0xFF1E90FF.toInt()
        const val COLOR_FAST = 0xFF39FF14.toInt()
        const val COLOR_CONSENSUS = 0xFFFF00FF.toInt()
        private const val OVAL_ALPHA = 56
        private const val CORNER_SIZE = 40f
        private const val STROKE_WIDTH = 4f
        private const val LANDMARK_RADIUS = 8f
    }

    private val ovalFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ovalStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH + 2f
        strokeCap = Paint.Cap.SQUARE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 36f
        typeface = Typeface.MONOSPACE
        setShadowLayer(4f, 1f, 1f, Color.BLACK)
    }
    private val landmarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    var results: List<DetectionResult> = emptyList()
        set(value) { field = value; invalidate() }

    var showLandmarks: Boolean = true
        set(value) { field = value; invalidate() }

    var imageWidth: Int = 640
    var imageHeight: Int = 480
    var imageRotation: Int = 0

    private fun scaledRect(rect: RectF): RectF {
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        // After rotation, the effective image dimensions swap for 90/270
        val (effW, effH) = if (imageRotation == 90 || imageRotation == 270)
            Pair(imageHeight.toFloat(), imageWidth.toFloat())
        else
            Pair(imageWidth.toFloat(), imageHeight.toFloat())

        val scale = maxOf(viewW / effW, viewH / effH)
        val dx = (viewW - effW * scale) / 2f
        val dy = (viewH - effH * scale) / 2f

        // Transform box coordinates based on rotation
        val left: Float
        val top: Float
        val right: Float
        val bottom: Float

        when (imageRotation) {
            90 -> {
                left  = viewH - rect.bottom * scale - dy + dx - (viewH - viewW) / 2f
                top   = rect.left * scale + dx - (viewW - viewH) / 2f
                right = viewH - rect.top * scale - dy + dx - (viewH - viewW) / 2f
                bottom = rect.right * scale + dx - (viewW - viewH) / 2f
                return RectF(
                    rect.left * scale + dx,
                    viewH - rect.bottom * scale - dy,
                    rect.right * scale + dx,
                    viewH - rect.top * scale - dy
                )
            }
            270 -> {
                return RectF(
                    viewW - rect.right * scale - dx,
                    rect.top * scale + dy,
                    viewW - rect.left * scale - dx,
                    rect.bottom * scale + dy
                )
            }
            180 -> {
                return RectF(
                    viewW - rect.right * scale - dx,
                    viewH - rect.bottom * scale - dy,
                    viewW - rect.left * scale - dx,
                    viewH - rect.top * scale - dy
                )
            }
            else -> {
                return RectF(
                    rect.left * scale + dx,
                    rect.top * scale + dy,
                    rect.right * scale + dx,
                    rect.bottom * scale + dy
                )
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (result in results) drawFace(canvas, result)
    }

    private fun drawFace(canvas: Canvas, result: DetectionResult) {
        val color = when (result.source) {
            DetectorSource.ACCURATE  -> COLOR_ACCURATE
            DetectorSource.FAST      -> COLOR_FAST
            DetectorSource.CONSENSUS -> COLOR_CONSENSUS
        }
        val label = when (result.source) {
            DetectorSource.ACCURATE  -> "ACCURATE"
            DetectorSource.FAST      -> "FAST"
            DetectorSource.CONSENSUS -> "CONSENSUS"
        }

        val box = scaledRect(result.boundingBox)

        ovalFillPaint.color = color
        ovalFillPaint.alpha = OVAL_ALPHA
        canvas.drawOval(box, ovalFillPaint)

        ovalStrokePaint.color = color
        ovalStrokePaint.alpha = 255
        canvas.drawOval(box, ovalStrokePaint)

        cornerPaint.color = color
        drawCorners(canvas, box)

        labelPaint.color = color
        canvas.drawText(label, box.left, box.top - 10f, labelPaint)

        if (showLandmarks) {
            landmarkPaint.color = color
            result.leftEyePosition?.let  { drawLandmark(canvas, it) }
            result.rightEyePosition?.let { drawLandmark(canvas, it) }
            result.nosePosition?.let     { drawLandmark(canvas, it) }
            result.leftMouthPosition?.let  { drawLandmark(canvas, it) }
            result.rightMouthPosition?.let { drawLandmark(canvas, it) }
        }
    }

    private fun drawCorners(canvas: Canvas, r: RectF) {
        val c = CORNER_SIZE
        canvas.drawLine(r.left, r.top, r.left + c, r.top, cornerPaint)
        canvas.drawLine(r.left, r.top, r.left, r.top + c, cornerPaint)
        canvas.drawLine(r.right - c, r.top, r.right, r.top, cornerPaint)
        canvas.drawLine(r.right, r.top, r.right, r.top + c, cornerPaint)
        canvas.drawLine(r.left, r.bottom - c, r.left, r.bottom, cornerPaint)
        canvas.drawLine(r.left, r.bottom, r.left + c, r.bottom, cornerPaint)
        canvas.drawLine(r.right - c, r.bottom, r.right, r.bottom, cornerPaint)
        canvas.drawLine(r.right, r.bottom - c, r.right, r.bottom, cornerPaint)
    }

    private fun drawLandmark(canvas: Canvas, pos: Pair<Float, Float>) {
        val scaled = scaledRect(RectF(pos.first, pos.second, pos.first, pos.second))
        canvas.drawCircle(scaled.left, scaled.top, LANDMARK_RADIUS, landmarkPaint)
    }
}
