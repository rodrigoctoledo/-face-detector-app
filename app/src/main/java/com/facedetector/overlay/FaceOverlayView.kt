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
        const val COLOR_ACCURATE  = 0xFF1E90FF.toInt()
        const val COLOR_FAST      = 0xFF39FF14.toInt()
        const val COLOR_CONSENSUS = 0xFFFF00FF.toInt()
        private const val OVAL_ALPHA   = 56
        private const val CORNER_SIZE  = 40f
        private const val STROKE_WIDTH = 4f
        private const val LANDMARK_R   = 8f
    }

    private val ovalFillPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ovalStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = STROKE_WIDTH
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = STROKE_WIDTH + 2f; strokeCap = Paint.Cap.SQUARE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 36f; typeface = Typeface.MONOSPACE
        setShadowLayer(4f, 1f, 1f, Color.BLACK)
    }
    private val landmarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    var results: List<DetectionResult> = emptyList()
        set(value) { field = value; invalidate() }

    var showLandmarks: Boolean = true
        set(value) { field = value; invalidate() }

    // Matriz calculada no MainActivity — transforma coords da imagem → coords da view
    var transformMatrix: Matrix = Matrix()
        set(value) { field = value; invalidate() }

    private fun mapRect(rect: RectF): RectF {
        val pts = floatArrayOf(
            rect.left, rect.top,
            rect.right, rect.top,
            rect.right, rect.bottom,
            rect.left, rect.bottom
        )
        transformMatrix.mapPoints(pts)
        val xs = floatArrayOf(pts[0], pts[2], pts[4], pts[6])
        val ys = floatArrayOf(pts[1], pts[3], pts[5], pts[7])
        return RectF(xs.min(), ys.min(), xs.max(), ys.max())
    }

    private fun mapPoint(x: Float, y: Float): PointF {
        val pts = floatArrayOf(x, y)
        transformMatrix.mapPoints(pts)
        return PointF(pts[0], pts[1])
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

        val box = mapRect(result.boundingBox)

        ovalFillPaint.color = color; ovalFillPaint.alpha = OVAL_ALPHA
        canvas.drawOval(box, ovalFillPaint)

        ovalStrokePaint.color = color; ovalStrokePaint.alpha = 255
        canvas.drawOval(box, ovalStrokePaint)

        cornerPaint.color = color
        drawCorners(canvas, box)

        labelPaint.color = color
        canvas.drawText(label, box.left, maxOf(box.top - 10f, labelPaint.textSize), labelPaint)

        if (showLandmarks) {
            landmarkPaint.color = color
            listOfNotNull(
                result.leftEyePosition, result.rightEyePosition,
                result.nosePosition, result.leftMouthPosition, result.rightMouthPosition
            ).forEach { (x, y) ->
                val p = mapPoint(x, y)
                canvas.drawCircle(p.x, p.y, LANDMARK_R, landmarkPaint)
            }
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
}
