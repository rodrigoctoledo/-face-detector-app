package com.facedetector.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.facedetector.camera.CameraManager
import com.facedetector.camera.ImageSaver
import com.facedetector.databinding.ActivityMainBinding
import com.facedetector.detector.DisplayMode
import com.facedetector.detector.DualDetectorManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraManager: CameraManager
    private lateinit var detectorManager: DualDetectorManager

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
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (cameraGranted) {
            startCameraAndDetection()
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setupButtons()

        if (allPermissionsGranted()) {
            startCameraAndDetection()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun allPermissionsGranted() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun setupButtons() {
        binding.btnDual.setOnClickListener {
            detectorManager.displayMode = DisplayMode.DUAL
            updateButtonStates()
        }
        binding.btnAccurate.setOnClickListener {
            detectorManager.displayMode = DisplayMode.ACCURATE
            updateButtonStates()
        }
        binding.btnFast.setOnClickListener {
            detectorManager.displayMode = DisplayMode.FAST
            updateButtonStates()
        }
        binding.btnLandmarks.setOnClickListener {
            binding.overlayView.showLandmarks = !binding.overlayView.showLandmarks
            binding.btnLandmarks.alpha = if (binding.overlayView.showLandmarks) 1f else 0.5f
        }
    }

    private fun updateButtonStates() {
        val mode = detectorManager.displayMode
        binding.btnDual.alpha = if (mode == DisplayMode.DUAL) 1f else 0.5f
        binding.btnAccurate.alpha = if (mode == DisplayMode.ACCURATE) 1f else 0.5f
        binding.btnFast.alpha = if (mode == DisplayMode.FAST) 1f else 0.5f
    }

    private fun startCameraAndDetection() {
        val imageSaver = ImageSaver(applicationContext)

        detectorManager = DualDetectorManager(imageSaver) { results, stats ->
            binding.overlayView.results = results
            binding.hudView.stats = stats

            // Pass image dimensions for correct scaling
            if (results.isNotEmpty()) {
                // imageWidth/Height updated in camera callback below
            }
        }

        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            previewView = binding.previewView
        ) { imageProxy ->
            // Update overlay scale once we know image dimensions
            val w = imageProxy.width
            val h = imageProxy.height
            runOnUiThread {
                binding.overlayView.imageWidth = w
                binding.overlayView.imageHeight = h
            }
            detectorManager.processFrame(imageProxy)
        }

        cameraManager.startCamera()
        updateButtonStates()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::detectorManager.isInitialized) detectorManager.shutdown()
        if (::cameraManager.isInitialized) cameraManager.shutdown()
    }
}
