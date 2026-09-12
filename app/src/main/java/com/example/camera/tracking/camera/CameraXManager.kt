package com.example.camera.tracking.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * CameraX lifecycle manager.
 * Configures ImageAnalysis to continuously extract high-rate frames
 * for AI detection, 3x digital crop rendering, and video recording.
 */
class CameraXManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onFrameAvailable: (Bitmap, InputImage) -> Unit
) {
    companion object {
        private const val TAG = "CameraXManager"
    }

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var isUsingFrontCamera = false
    private var targetWidth = 1080
    private var targetHeight = 1920

    fun setViewfinderResolution(width: Int, height: Int) {
        targetWidth = width
        targetHeight = height
        if (cameraProvider != null) {
            bindCameraUseCases()
        }
    }

    fun startCamera(useFrontCamera: Boolean = false, onReady: (Boolean) -> Unit = {}) {
        this.isUsingFrontCamera = useFrontCamera
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
                onReady(true)
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed: ${e.message}", e)
                onReady(false)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val cameraSelector = if (isUsingFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setTargetResolution(Size(targetWidth, targetHeight))
            .build()

        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            processImageProxy(imageProxy)
        }

        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                imageCapture,
                imageAnalysis
            )
            Log.d(TAG, "CameraX successfully bound with ImageCapture and ImageAnalysis")
        } catch (e: Exception) {
            Log.e(TAG, "CameraX bindToLifecycle error: ${e.message}", e)
        }
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val rawBitmap = imageProxy.toBitmap()

            val orientedBitmap = if (rotationDegrees != 0 || isUsingFrontCamera) {
                val matrix = Matrix().apply {
                    if (rotationDegrees != 0) postRotate(rotationDegrees.toFloat())
                    if (isUsingFrontCamera) postScale(-1f, 1f)
                }
                val transformed = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                if (transformed != rawBitmap) {
                    rawBitmap.recycle()
                }
                transformed
            } else {
                rawBitmap
            }

            // Create ML Kit InputImage
            val inputImage = InputImage.fromBitmap(orientedBitmap, 0)
            onFrameAvailable(orientedBitmap, inputImage)
        } catch (e: Exception) {
            Log.w(TAG, "Error processing frame: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    fun toggleCamera(onReady: (Boolean) -> Unit = {}) {
        startCamera(!isUsingFrontCamera, onReady)
    }

    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping camera: ${e.message}")
        }
    }

    fun release() {
        stopCamera()
        cameraExecutor.shutdown()
    }
}
