package com.facedetector.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onFrameReady: (ImageProxy) -> Unit
) {
    private var cameraProvider: ProcessCameraProvider? = null
    val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // Resolution fallback list: try 1080p → 720p → 480p
    private val resolutionCandidates = listOf(
        Size(1920, 1080),
        Size(1280, 720),
        Size(640, 480)
    )

    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return

        val preview = Preview.Builder()
            .setTargetResolution(resolutionCandidates[0])
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(resolutionCandidates[0])
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    onFrameReady(imageProxy)
                }
            }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
        } catch (e: Exception) {
            // Try lower resolution on failure
            tryFallbackResolution(provider, preview, imageAnalysis, cameraSelector, 1)
        }
    }

    private fun tryFallbackResolution(
        provider: ProcessCameraProvider,
        preview: Preview,
        imageAnalysis: ImageAnalysis,
        cameraSelector: CameraSelector,
        resIndex: Int
    ) {
        if (resIndex >= resolutionCandidates.size) return
        val res = resolutionCandidates[resIndex]

        val fallbackPreview = Preview.Builder()
            .setTargetResolution(res)
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val fallbackAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(res)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    onFrameReady(imageProxy)
                }
            }

        try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, cameraSelector, fallbackPreview, fallbackAnalysis)
        } catch (e: Exception) {
            tryFallbackResolution(provider, fallbackPreview, fallbackAnalysis, cameraSelector, resIndex + 1)
        }
    }

    fun shutdown() {
        analysisExecutor.shutdown()
        cameraProvider?.unbindAll()
    }
}
