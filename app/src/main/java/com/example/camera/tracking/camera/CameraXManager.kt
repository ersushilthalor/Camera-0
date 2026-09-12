package com.example.camera.tracking.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.camera.tracking.model.TrackingCameraLens
import com.example.camera.tracking.model.TrackingFpsOption
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * CameraX lifecycle manager.
 * Configures ImageAnalysis to continuously extract high-rate frames
 * for AI detection, 3x digital crop rendering, and video recording.
 * Supports Ultra-Wide, Main Wide, and Front cameras with selectable FPS.
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
    private var activeCamera: Camera? = null
    private var activeLens: TrackingCameraLens = TrackingCameraLens.WIDE
    private var activeFpsOption: TrackingFpsOption = TrackingFpsOption.FPS_60
    private var targetWidth = 1080
    private var targetHeight = 1920

    val isUsingFrontCamera: Boolean get() = activeLens.isFront

    fun setViewfinderResolution(width: Int, height: Int) {
        targetWidth = width
        targetHeight = height
        if (cameraProvider != null) {
            bindCameraUseCases()
        }
    }

    fun startCamera(lens: TrackingCameraLens = TrackingCameraLens.WIDE, onReady: (Boolean) -> Unit = {}) {
        this.activeLens = lens
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

    fun setLens(lens: TrackingCameraLens, onReady: (Boolean) -> Unit = {}) {
        if (activeLens == lens && activeCamera != null) return
        activeLens = lens
        if (cameraProvider != null) {
            bindCameraUseCases()
            onReady(true)
        } else {
            startCamera(lens, onReady)
        }
    }

    fun setFps(fpsOption: TrackingFpsOption) {
        activeFpsOption = fpsOption
        if (cameraProvider != null) {
            bindCameraUseCases()
        }
    }

    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val cameraSelector = buildCameraSelector()

        val targetFps = activeFpsOption.targetFps.coerceIn(30, 120)
        val bestFpsRange = getDeviceSupportedFpsRange(targetFps, activeLens.isFront)

        val imageCaptureBuilder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)

        val imageAnalysisBuilder = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setTargetResolution(Size(targetWidth, targetHeight))

        // Request high-frame rate targeting via Camera2Interop using device supported FPS range
        try {
            val extender = Camera2Interop.Extender(imageAnalysisBuilder)
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                bestFpsRange
            )
            Log.d(TAG, "Applied device-supported AE FPS Range: $bestFpsRange (requested $targetFps fps)")
        } catch (e: Exception) {
            Log.w(TAG, "Could not set CONTROL_AE_TARGET_FPS_RANGE on ImageAnalysis: ${e.message}")
        }

        imageCapture = imageCaptureBuilder.build()
        val imageAnalysis = imageAnalysisBuilder.build()

        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            processImageProxy(imageProxy)
        }

        try {
            provider.unbindAll()
            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                imageCapture,
                imageAnalysis
            )
            activeCamera = camera

            // Apply Ultra-Wide zoom or wide angle if requested
            if (activeLens == TrackingCameraLens.ULTRAWIDE) {
                val zoomState = camera.cameraInfo.zoomState.value
                val minRatio = zoomState?.minZoomRatio ?: 1.0f
                val targetRatio = if (minRatio < 1.0f) minRatio else 0.5f.coerceAtLeast(minRatio)
                camera.cameraControl.setZoomRatio(targetRatio)
                Log.d(TAG, "Ultra-Wide zoom applied: minRatio=$minRatio, setRatio=$targetRatio")
            } else if (!activeLens.isFront) {
                camera.cameraControl.setZoomRatio(1.0f)
            }

            Log.d(TAG, "CameraX successfully bound: lens=$activeLens, fps=$activeFpsOption")
        } catch (e: Exception) {
            Log.e(TAG, "CameraX bindToLifecycle error: ${e.message}", e)
        }
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val rawBitmap = imageProxy.toBitmap()

            val orientedBitmap = if (rotationDegrees != 0 || activeLens.isFront) {
                val matrix = Matrix().apply {
                    if (rotationDegrees != 0) postRotate(rotationDegrees.toFloat())
                    if (activeLens.isFront) postScale(-1f, 1f)
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
        val nextLens = if (activeLens.isFront) TrackingCameraLens.WIDE else TrackingCameraLens.FRONT
        setLens(nextLens, onReady)
    }

    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            activeCamera = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping camera: ${e.message}")
        }
    }

    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    private fun buildCameraSelector(): CameraSelector {
        if (activeLens.isFront) {
            return CameraSelector.DEFAULT_FRONT_CAMERA
        }

        if (activeLens == TrackingCameraLens.ULTRAWIDE) {
            val uwId = findUltraWideCameraId()
            if (uwId != null) {
                Log.d(TAG, "Selected physical Ultra Wide camera ID: $uwId")
                return CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .addCameraFilter { cameraInfos ->
                        val matched = cameraInfos.filter { info ->
                            try {
                                androidx.camera.camera2.interop.Camera2CameraInfo.from(info).cameraId == uwId
                            } catch (e: Exception) {
                                false
                            }
                        }
                        if (matched.isNotEmpty()) matched else cameraInfos
                    }
                    .build()
            }
            return CameraSelector.DEFAULT_BACK_CAMERA
        }

        // Default Main Wide (1x)
        val mainId = findMainWideCameraId()
        if (mainId != null) {
            Log.d(TAG, "Selected physical Main Wide camera ID: $mainId")
            return CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .addCameraFilter { cameraInfos ->
                    val matched = cameraInfos.filter { info ->
                        try {
                            androidx.camera.camera2.interop.Camera2CameraInfo.from(info).cameraId == mainId
                        } catch (e: Exception) {
                            false
                        }
                    }
                    if (matched.isNotEmpty()) matched else cameraInfos
                }
                .build()
        }
        return CameraSelector.DEFAULT_BACK_CAMERA
    }

    private fun findUltraWideCameraId(): String? {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager ?: return null
        var bestId: String? = null
        var minFocal = Float.MAX_VALUE

        try {
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                if (facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK) {
                    val focalLengths = chars.get(android.hardware.camera2.CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    val focal = focalLengths?.firstOrNull() ?: 4.0f
                    val sensorSize = chars.get(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                    val fov = if (sensorSize != null && sensorSize.width > 0 && focal > 0) {
                        (2.0 * kotlin.math.atan(sensorSize.width.toDouble() / (2.0 * focal.toDouble())) * (180.0 / Math.PI)).toFloat()
                    } else 0f

                    if (focal <= 2.8f || fov >= 85f) {
                        return id
                    }
                    if (focal < minFocal) {
                        minFocal = focal
                        bestId = id
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error finding physical ultra-wide camera: ${e.message}")
        }
        return if (minFocal < 3.2f) bestId else null
    }

    private fun findMainWideCameraId(): String? {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager ?: return null
        try {
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                if (facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK) {
                    val focalLengths = chars.get(android.hardware.camera2.CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    val focal = focalLengths?.firstOrNull() ?: 4.0f
                    // Typical main wide lens focal length is ~3.5mm - 6.5mm
                    if (focal in 3.4f..7.0f) {
                        return id
                    }
                }
            }
            // Fallback to first back camera
            return cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) ==
                        android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error finding physical main wide camera: ${e.message}")
            return null
        }
    }

    private fun getDeviceSupportedFpsRange(targetFps: Int, isFront: Boolean): Range<Int> {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
                ?: return Range(30, targetFps.coerceAtLeast(30))
            val expectedFacing = if (isFront) {
                android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT
            } else {
                android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
            }

            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                if (facing == expectedFacing) {
                    val ranges = chars.get(android.hardware.camera2.CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                    if (ranges != null && ranges.isNotEmpty()) {
                        // Priority 1: Range with upper bound matching targetFps
                        val exactOrUnder = ranges.filter { it.upper <= targetFps }.maxByOrNull { it.upper }
                        if (exactOrUnder != null) return exactOrUnder
                        // Priority 2: Range closest to targetFps
                        val closest = ranges.minByOrNull { kotlin.math.abs(it.upper - targetFps) }
                        if (closest != null) return closest
                    }
                }
            }
            Range(30, targetFps.coerceAtLeast(30))
        } catch (e: Exception) {
            Log.w(TAG, "Could not query device supported FPS ranges: ${e.message}")
            Range(30, targetFps.coerceAtLeast(30))
        }
    }

    fun release() {
        stopCamera()
        cameraExecutor.shutdown()
    }
}
