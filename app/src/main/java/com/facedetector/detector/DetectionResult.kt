package com.facedetector.detector

import android.graphics.RectF

enum class DetectorSource {
    ACCURATE,
    FAST,
    CONSENSUS
}

data class DetectionResult(
    val boundingBox: RectF,
    val source: DetectorSource,
    val leftEyePosition: Pair<Float, Float>? = null,
    val rightEyePosition: Pair<Float, Float>? = null,
    val nosePosition: Pair<Float, Float>? = null,
    val leftMouthPosition: Pair<Float, Float>? = null,
    val rightMouthPosition: Pair<Float, Float>? = null,
    val trackingId: Int? = null
)
