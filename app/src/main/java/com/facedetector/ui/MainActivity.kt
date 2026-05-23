package com.facedetector.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.facedetector.camera.ImageSaver
import com.facedetector.databinding.ActivityMainBinding
import com.facedetector.detector.DisplayMode
import com.facedetector.detector.DualDetectorManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var detectorManager: DualDetectorManager
    private var cameraProvider: ProcessCameraProvider? = null

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        } else {
            arrayOf(Manifest.permission.CAMERA)
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            startCameraAndDetection()
        } else {
            Toast.makeText(this, "Permissão de câmera necessária", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setupButtons()
        if (allPermissionsGranted()) startCameraAndDetection()
        else permissionLauncher.launch(requiredPermissions)
    }

    private fun allPermissionsGranted() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun setupButtons() {
        binding.btnDual.setOnClickListener {
            detectorManager.displayMode = DisplayMode.DUAL; updateButtonStates()
        }
        binding.btnAccurate.setOnClickListener {
            detectorManager.displayMode = DisplayMode.ACCURATE; updateButtonStates()
        }
        binding.btnFast.setOnClickListener {
            detectorManager.displayMode = DisplayMode.FAST; updateButtonStates()
        }
        binding.btnLandmarks.setOnClickListener {
            binding.overlayView.showLandmarks = !binding.overlayView.showLandmarks
            binding.btnLandmarks.alpha = if (binding.overlayView.showLandmarks) 1f else 0.5f
        }
    }

    private fun updateButtonStates() {
        val mode = detectorManager.displayMode
        binding.btnDual.alpha     = if (mode == DisplayMode.DUAL)     1f else 0.5f
        binding.btnAccurate.alpha = if (mode == DisplayMode.ACCURATE) 1f else 0.5f
        binding.btnFast.alpha     = if (mode == DisplayMode.FAST)     1f else 0.5f
    }

    private fun startCameraAndDetection() {
        val imageSaver = ImageSaver(applicationContext)

        detectorManager = DualDetectorManager(imageSaver) { results, stats ->
            binding.overlayView.results = results
            binding.hudView.stats = stats
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCamera()
        }, ContextCompat.getMainExecutor(this))

        updateButtonStates()
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(binding.previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()

        imageAnalysis.setAnalyzer(java.util.concurrent.Executors.newSingleThreadExecutor()) { imageProxy ->
            val rotation = imageProxy.imageInfo.rotationDegrees
            val imgW = imageProxy.width
            val imgH = imageProxy.height

            // Calcula a matriz de transformação do espaço da imagem para o espaço da view
            // levando em conta rotação, espelhamento e letterbox
            val matrix = calcTransformMatrix(rotation, imgW, imgH)

            runOnUiThread {
                binding.overlayView.transformMatrix = matrix
            }

            detectorManager.processFrame(imageProxy)
        }

        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun calcTransformMatrix(rotation: Int, imgW: Int, imgH: Int): Matrix {
        val viewW = binding.overlayView.width.toFloat().takeIf { it > 0 } ?: 1f
        val viewH = binding.overlayView.height.toFloat().takeIf { it > 0 } ?: 1f

        val matrix = Matrix()

        // 1. Centraliza a imagem na origem
        matrix.postTranslate(-imgW / 2f, -imgH / 2f)

        // 2. Rotaciona
        matrix.postRotate(rotation.toFloat())

        // 3. Após rotação, calcula dimensões lógicas
        val (logicW, logicH) = if (rotation == 90 || rotation == 270)
            Pair(imgH.toFloat(), imgW.toFloat())
        else
            Pair(imgW.toFloat(), imgH.toFloat())

        // 4. Escala para preencher a view (centerCrop)
        val scale = maxOf(viewW / logicW, viewH / logicH)
        matrix.postScale(scale, scale)

        // 5. Move para o centro da view
        matrix.postTranslate(viewW / 2f, viewH / 2f)

        return matrix
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::detectorManager.isInitialized) detectorManager.shutdown()
        cameraProvider?.unbindAll()
    }
}
