package com.facedetector.detector

import android.graphics.RectF
import androidx.camera.core.ImageProxy
import com.facedetector.camera.ImageSaver
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class DisplayMode { DUAL, ACCURATE, FAST }

class DualDetectorManager(
    private val imageSaver: ImageSaver,
    private val onResultsReady: (List<DetectionResult>, Stats) -> Unit
) {
    data class Stats(
        val pipelineFps: Float,
        val accurateFps: Float,
        val fastFps: Float,
        val accurateCount: Int,
        val fastCount: Int,
        val consensusCount: Int,
        val displayMode: DisplayMode
    )

    private val accurateDetector = FaceDetectorWrapper(DetectorSource.ACCURATE)
    private val fastDetector     = FaceDetectorWrapper(DetectorSource.FAST)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var frameCount = 0
    private var lastPipelineTime = System.currentTimeMillis()
    private var pipelineFps = 0f

    private val accurateFrameCount = AtomicInteger(0)
    private val fastFrameCount     = AtomicInteger(0)
    private val lastAccurateTime   = AtomicLong(System.currentTimeMillis())
    private val lastFastTime       = AtomicLong(System.currentTimeMillis())
    private var accurateFps = 0f
    private var fastFps     = 0f

    private var cachedAccurateResults: List<DetectionResult> = emptyList()
    private var localFrameCount = 0

    var displayMode: DisplayMode = DisplayMode.DUAL

    fun processFrame(imageProxy: ImageProxy) {
        localFrameCount++
        val now = System.currentTimeMillis()
        val elapsed = now - lastPipelineTime
        frameCount++
        if (elapsed >= 1000) {
            pipelineFps = frameCount * 1000f / elapsed
            frameCount = 0
            lastPipelineTime = now
        }

        val runAccurate = (localFrameCount % 3 == 0) || cachedAccurateResults.isEmpty()

        val imageBytes: ByteArray? = try { imageProxyToJpegBytes(imageProxy) } catch (e: Exception) { null }

        scope.launch {
            try {
                val fastDeferred    = async { fastDetector.detect(imageProxy) }
                val accurateDeferred = if (runAccurate) async { accurateDetector.detect(imageProxy) } else null

                val fastResults = fastDeferred.await()
                updateFps(fastFrameCount, lastFastTime) { fastFps = it }

                val accurateResults = if (runAccurate) {
                    val r = accurateDeferred!!.await()
                    cachedAccurateResults = r
                    updateFps(accurateFrameCount, lastAccurateTime) { accurateFps = it }
                    r
                } else {
                    cachedAccurateResults
                }

                val merged   = mergeResults(accurateResults, fastResults)
                val filtered = filterByDisplayMode(merged)

                if (merged.isNotEmpty() && imageBytes != null) {
                    launch(Dispatchers.IO) { imageSaver.saveFrame(imageBytes, 0) }
                }

                val stats = Stats(
                    pipelineFps   = pipelineFps,
                    accurateFps   = accurateFps,
                    fastFps       = fastFps,
                    accurateCount = accurateResults.size,
                    fastCount     = fastResults.size,
                    consensusCount = merged.count { it.source == DetectorSource.CONSENSUS },
                    displayMode   = displayMode
                )

                withContext(Dispatchers.Main) { onResultsReady(filtered, stats) }

            } catch (e: Exception) {
                // ignora erros de frame descartado
            } finally {
                imageProxy.close()
            }
        }
    }

    private fun updateFps(counter: AtomicInteger, lastTime: AtomicLong, setter: (Float) -> Unit) {
        val count   = counter.incrementAndGet()
        val now     = System.currentTimeMillis()
        val elapsed = now - lastTime.get()
        if (elapsed >= 1000) {
            setter(count * 1000f / elapsed)
            counter.set(0)
            lastTime.set(now)
        }
    }

    private fun imageProxyToJpegBytes(imageProxy: ImageProxy): ByteArray {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21  = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        val yuvImage = android.graphics.YuvImage(
            nv21, android.graphics.ImageFormat.NV21,
            imageProxy.width, imageProxy.height, null
        )
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height), 90, out
        )
        return out.toByteArray()
    }

    private fun mergeResults(
        accurateList: List<DetectionResult>,
        fastList: List<DetectionResult>
    ): List<DetectionResult> {
        val result      = mutableListOf<DetectionResult>()
        val fastMatched = BooleanArray(fastList.size)
        for (acc in accurateList) {
            var bestIou = 0f; var bestIdx = -1
            for ((i, fast) in fastList.withIndex()) {
                val iou = computeIoU(acc.boundingBox, fast.boundingBox)
                if (iou > bestIou) { bestIou = iou; bestIdx = i }
            }
            if (bestIou > 0.35f && bestIdx >= 0) {
                fastMatched[bestIdx] = true
                result.add(acc.copy(source = DetectorSource.CONSENSUS))
            } else {
                result.add(acc)
            }
        }
        for ((i, fast) in fastList.withIndex()) {
            if (!fastMatched[i]) result.add(fast)
        }
        return result
    }

    private fun filterByDisplayMode(results: List<DetectionResult>) = when (displayMode) {
        DisplayMode.DUAL     -> results
        DisplayMode.ACCURATE -> results.filter { it.source == DetectorSource.ACCURATE || it.source == DetectorSource.CONSENSUS }
        DisplayMode.FAST     -> results.filter { it.source == DetectorSource.FAST     || it.source == DetectorSource.CONSENSUS }
    }

    private fun computeIoU(a: RectF, b: RectF): Float {
        val iL = maxOf(a.left, b.left); val iT = maxOf(a.top, b.top)
        val iR = minOf(a.right, b.right); val iB = minOf(a.bottom, b.bottom)
        if (iR <= iL || iB <= iT) return 0f
        val inter = (iR - iL) * (iB - iT)
        return inter / (a.width() * a.height() + b.width() * b.height() - inter)
    }

    fun shutdown() {
        scope.cancel()
        accurateDetector.close()
        fastDetector.close()
    }
}
