package com.facedetector.detector

import android.graphics.RectF
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FaceDetectorWrapper(private val source: DetectorSource) {

    private val options: FaceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(
            if (source == DetectorSource.ACCURATE)
                FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE
            else
                FaceDetectorOptions.PERFORMANCE_MODE_FAST
        )
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setMinFaceSize(0.03f)
        .enableTracking()
        .build()

    private val detector: FaceDetector = FaceDetection.getClient(options)

    suspend fun detect(imageProxy: ImageProxy): List<DetectionResult> =
        suspendCancellableCoroutine { cont ->
            try {
                val mediaImage = imageProxy.image
                if (mediaImage == null) {
                    cont.resume(emptyList())
                    return@suspendCancellableCoroutine
                }

                val inputImage = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )

                detector.process(inputImage)
                    .addOnSuccessListener { faces ->
                        val results = faces.map { face -> face.toDetectionResult(source) }
                        cont.resume(results)
                    }
                    .addOnFailureListener { e ->
                        cont.resumeWithException(e)
                    }
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }

    fun close() {
        detector.close()
    }
}

private fun Face.toDetectionResult(source: DetectorSource): DetectionResult {
    val box = boundingBox
    val rectF = RectF(
        box.left.toFloat(),
        box.top.toFloat(),
        box.right.toFloat(),
        box.bottom.toFloat()
    )

    fun landmark(type: Int): Pair<Float, Float>? {
        val lm = getLandmark(type) ?: return null
        return Pair(lm.position.x, lm.position.y)
    }

    return DetectionResult(
        boundingBox = rectF,
        source = source,
        leftEyePosition = landmark(FaceLandmark.LEFT_EYE),
        rightEyePosition = landmark(FaceLandmark.RIGHT_EYE),
        nosePosition = landmark(FaceLandmark.NOSE_BASE),
        leftMouthPosition = landmark(FaceLandmark.MOUTH_LEFT),
        rightMouthPosition = landmark(FaceLandmark.MOUTH_RIGHT),
        trackingId = trackingId
    )
}
