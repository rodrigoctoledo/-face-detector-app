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
        private const val OVAL_ALPHA    = 56
        private const val CORNER_SIZE   = 40f
        private const val STROKE_WIDTH  = 4f
        private const val LANDMARK_RADIUS = 8f
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

    var imageWidth: Int  = 640
    var imageHeight: Int = 480
    var imageRotation: Int = 0  // graus: 0, 90, 180, 270

    /**
     * Converte coordenadas do espaço da imagem (saída do ML Kit) para
     * coordenadas da View, levando em conta a rotação da câmera.
     *
     * O ML Kit devolve coordenadas no espaço do sensor (imageWidth x imageHeight).
     * A câmera traseira em portrait normalmente tem rotation = 90.
     */
    private fun toViewCoord(px: Float, py: Float): PointF {
        val vw = width.toFloat()
        val vh = height.toFloat()
        val iw = imageWidth.toFloat()
        val ih = imageHeight.toFloat()

        // normaliza para [0,1] no espaço rotacionado
        val nx: Float
        val ny: Float
        when (imageRotation) {
            90 -> {
                // sensor landscape → view portrait
                // x_norm = py/ih,  y_norm = 1 - px/iw
                nx = py / ih
                ny = 1f - px / iw
            }
            270 -> {
                nx = 1f - py / ih
                ny = px / iw
            }
            180 -> {
                nx = 1f - px / iw
                ny = 1f - py / ih
            }
            else -> {          // 0° — sensor já alinhado com a view
                nx = px / iw
                ny = py / ih
            }
        }

        // escala para a view com letterbox/pillarbox
        val scaleX: Float
        val scaleY: Float
        // após rotação, qual é a largura/altura lógica da imagem?
        val logicW: Float
        val logicH: Float
        if (imageRotation == 90 || imageRotation == 270) {
            logicW = ih; logicH = iw
        } else {
            logicW = iw; logicH = ih
        }
        val scale = maxOf(vw / logicW, vh / logicH)
        val dx = (vw - logicW * scale) / 2f
        val dy = (vh - logicH * scale) / 2f

        return PointF(nx * logicW * scale + dx, ny * logicH * scale + dy)
    }

    private fun scaledRect(rect: RectF): RectF {
        val tl = toViewCoord(rect.left,  rect.top)
        val br = toViewCoord(rect.right, rect.bottom)
        return RectF(
            minOf(tl.x, br.x), minOf(tl.y, br.y),
            maxOf(tl.x, br.x), maxOf(tl.y, br.y)
        )
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

        ovalFillPaint.color  = color; ovalFillPaint.alpha = OVAL_ALPHA
        canvas.drawOval(box, ovalFillPaint)

        ovalStrokePaint.color = color; ovalStrokePaint.alpha = 255
        canvas.drawOval(box, ovalStrokePaint)

        cornerPaint.color = color
        drawCorners(canvas, box)

        labelPaint.color = color
        canvas.drawText(label, box.left, box.top - 10f, labelPaint)

        if (showLandmarks) {
            landmarkPaint.color = color
            result.leftEyePosition?.let  { (x, y) ->
                val p = toViewCoord(x, y); canvas.drawCircle(p.x, p.y, LANDMARK_RADIUS, landmarkPaint)
            }
            result.rightEyePosition?.let { (x, y) ->
                val p = toViewCoord(x, y); canvas.drawCircle(p.x, p.y, LANDMARK_RADIUS, landmarkPaint)
            }
            result.nosePosition?.let     { (x, y) ->
                val p = toViewCoord(x, y); canvas.drawCircle(p.x, p.y, LANDMARK_RADIUS, landmarkPaint)
            }
            result.leftMouthPosition?.let  { (x, y) ->
                val p = toViewCoord(x, y); canvas.drawCircle(p.x, p.y, LANDMARK_RADIUS, landmarkPaint)
            }
            result.rightMouthPosition?.let { (x, y) ->
                val p = toViewCoord(x, y); canvas.drawCircle(p.x, p.y, LANDMARK_RADIUS, landmarkPaint)
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
