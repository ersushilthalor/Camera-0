package com.example.camera.tracking.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.util.Log
import android.util.Range
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
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
 * CameraX lifecycle & hardware lens manager for AI subject tracking.
 * Configures high-frame rate ImageAnalysis, real optical Ultra-Wide (0.5×),
 * Main Wide (1.0×), and Front Selfie lenses matching normal photo mode mechanism.
 */
class CameraXManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onFrameAvailable: (Bitmap, InputImage) -> Unit
) {
    companion object {
        private const val TAG = "CameraXManager"
    }

    data class UltraWideDeviceInfo(
        val physicalCameraId: String? = null,
        val minOpticalZoomRatio: Float = 0.5f,
        val hasOpticalUltraWide: Boolean = false
    )

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var activeCamera: Camera? = null
    private var activeLens: TrackingCameraLens = TrackingCameraLens.WIDE
    private var activeFpsOption: TrackingFpsOption = TrackingFpsOption.FPS_60
    private var targetWidth = 1080
    private var targetHeight = 1920

    // Hardware ultra-wide detection matching Camera2Engine
    val ultraWideInfo: UltraWideDeviceInfo by lazy {
        detectUltraWideDeviceInfo()
    }

    val isUsingFrontCamera: Boolean get() = activeLens.isFront

    fun getAvailableLenses(): List<TrackingCameraLens> {
        return if (ultraWideInfo.hasOpticalUltraWide) {
            listOf(TrackingCameraLens.ULTRAWIDE, TrackingCameraLens.WIDE, TrackingCameraLens.FRONT)
        } else {
            listOf(TrackingCameraLens.ULTRAWIDE, TrackingCameraLens.WIDE, TrackingCameraLens.FRONT)
        }
    }

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

    /**
     * Switch camera lens (0.5x Ultra-Wide, 1x Main Wide, Front Selfie).
     * Matches normal photo mode mechanism:
     * When switching between 0.5x and 1x on the back camera, dynamically switches
     * the optical sensor via Camera2 CONTROL_ZOOM_RATIO without unbinding or stalling the preview stream.
     */
    fun setLens(lens: TrackingCameraLens, onReady: (Boolean) -> Unit = {}) {
        if (activeLens == lens && activeCamera != null) return
        val prevLens = activeLens
        activeLens = lens

        val activeCam = activeCamera
        val isBothBack = !prevLens.isFront && !lens.isFront
        val isSeparatePhysical = isUsingSeparatePhysicalUltraWide()

        if (activeCam != null && isBothBack && !isSeparatePhysical) {
            // Instant seamless dynamic optical switch on the same logical back camera
            Log.d(TAG, "Dynamic optical lens switch: $prevLens -> $lens")
            applyLensHardwareSettings(activeCam)
            onReady(true)
            return
        }

        // Switching between front and back or switching physical cameras
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

    @OptIn(ExperimentalCamera2Interop::class)
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

        // Set target FPS on ImageAnalysis capture request
        try {
            val extender = Camera2Interop.Extender(imageAnalysisBuilder)
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                bestFpsRange
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not set CONTROL_AE_TARGET_FPS_RANGE: ${e.message}")
        }

        // Configure optical zoom ratio on capture request builders (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !activeLens.isFront) {
            val isUltraWide = activeLens == TrackingCameraLens.ULTRAWIDE
            val targetOpticalRatio = if (isUltraWide) ultraWideInfo.minOpticalZoomRatio else 1.0f
            try {
                val analysisExtender = Camera2Interop.Extender(imageAnalysisBuilder)
                analysisExtender.setCaptureRequestOption(CaptureRequest.CONTROL_ZOOM_RATIO, targetOpticalRatio)

                val captureExtender = Camera2Interop.Extender(imageCaptureBuilder)
                captureExtender.setCaptureRequestOption(CaptureRequest.CONTROL_ZOOM_RATIO, targetOpticalRatio)
                Log.d(TAG, "Configured Camera2Interop CONTROL_ZOOM_RATIO on builders: $targetOpticalRatio")
            } catch (e: Exception) {
                Log.w(TAG, "Failed setting CONTROL_ZOOM_RATIO on builders: ${e.message}")
            }
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
            applyLensHardwareSettings(camera)

            Log.d(TAG, "CameraX successfully bound: lens=$activeLens, fps=$activeFpsOption")
        } catch (e: Exception) {
            Log.e(TAG, "CameraX bindToLifecycle error: ${e.message}", e)
        }
    }

    /**
     * Applies optical zoom ratio to activate physical ultra-wide or wide sensor.
     * Uses both Camera2CameraControl and CameraX setZoomRatio with LiveData observation.
     */
    @OptIn(ExperimentalCamera2Interop::class)
    private fun applyLensHardwareSettings(camera: Camera) {
        val isUltraWide = activeLens == TrackingCameraLens.ULTRAWIDE
        val targetRatio = if (isUltraWide) ultraWideInfo.minOpticalZoomRatio else 1.0f

        // 1. Android 11+ native optical sensor switch via Camera2CameraControl
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !activeLens.isFront) {
            try {
                val c2Control = Camera2CameraControl.from(camera.cameraControl)
                val opts = CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(CaptureRequest.CONTROL_ZOOM_RATIO, targetRatio)
                    .build()
                c2Control.setCaptureRequestOptions(opts)
                Log.d(TAG, "Camera2CameraControl applied CONTROL_ZOOM_RATIO: $targetRatio for lens $activeLens")
            } catch (e: Exception) {
                Log.w(TAG, "Camera2CameraControl setCaptureRequestOptions error: ${e.message}")
            }
        }

        // 2. CameraX setZoomRatio
        try {
            camera.cameraControl.setZoomRatio(targetRatio)
        } catch (e: Exception) {
            Log.w(TAG, "Initial cameraControl.setZoomRatio attempt: ${e.message}")
        }

        // 3. ZoomState observer to enforce the correct optical zoom ratio once CameraX initializes zoom bounds
        camera.cameraInfo.zoomState.removeObservers(lifecycleOwner)
        camera.cameraInfo.zoomState.observe(lifecycleOwner) { zoomState ->
            if (zoomState != null) {
                val minRatio = zoomState.minZoomRatio
                val maxRatio = zoomState.maxZoomRatio
                val desiredRatio = if (activeLens.isFront) {
                    1.0f
                } else if (isUltraWide) {
                    if (targetRatio in minRatio..maxRatio) targetRatio
                    else if (minRatio < 1.0f) minRatio
                    else 0.5f.coerceIn(minRatio, maxRatio)
                } else {
                    1.0f.coerceIn(minRatio, maxRatio)
                }

                if (kotlin.math.abs(zoomState.zoomRatio - desiredRatio) > 0.02f) {
                    camera.cameraControl.setZoomRatio(desiredRatio)
                    Log.d(TAG, "ZoomState observer set ratio to: $desiredRatio (min=$minRatio, max=$maxRatio)")
                }
            }
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

    @OptIn(ExperimentalCamera2Interop::class)
    private fun buildCameraSelector(): CameraSelector {
        if (activeLens.isFront) {
            return CameraSelector.DEFAULT_FRONT_CAMERA
        }

        if (activeLens == TrackingCameraLens.ULTRAWIDE) {
            val uwId = ultraWideInfo.physicalCameraId
            if (uwId != null) {
                val existsInCameraX = try {
                    cameraProvider?.availableCameraInfos?.any {
                        Camera2CameraInfo.from(it).cameraId == uwId
                    } == true
                } catch (e: Exception) {
                    false
                }

                if (existsInCameraX) {
                    Log.d(TAG, "Binding CameraX directly to physical Ultra Wide camera ID: $uwId")
                    return CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .addCameraFilter { cameraInfos ->
                            val matched = cameraInfos.filter { info ->
                                try {
                                    Camera2CameraInfo.from(info).cameraId == uwId
                                } catch (e: Exception) {
                                    false
                                }
                            }
                            if (matched.isNotEmpty()) matched else cameraInfos
                        }
                        .build()
                }
            }
            Log.d(TAG, "Binding to DEFAULT_BACK_CAMERA for Ultra Wide (optical ratio ${ultraWideInfo.minOpticalZoomRatio}x will be applied)")
            return CameraSelector.DEFAULT_BACK_CAMERA
        }

        return CameraSelector.DEFAULT_BACK_CAMERA
    }

    private fun isUsingSeparatePhysicalUltraWide(): Boolean {
        val uwId = ultraWideInfo.physicalCameraId ?: return false
        return try {
            cameraProvider?.availableCameraInfos?.any {
                Camera2CameraInfo.from(it).cameraId == uwId
            } == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Inspects CameraManager matching Camera2Engine:
     * 1. Official IDs + candidate IDs 0..12.
     * 2. CONTROL_ZOOM_RATIO_RANGE (Android 11+) on back cameras for integrated optical ultra-wide (<1.0f).
     * 3. Physical camera focal lengths and sensor size (focal <= 2.8mm, eq35mm <= 23.5mm, FOV >= 85°).
     * 4. Multi-camera physical sub-cameras (Android 9+).
     */
    private fun detectUltraWideDeviceInfo(): UltraWideDeviceInfo {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return UltraWideDeviceInfo()

        var foundPhysicalId: String? = null
        var detectedMinZoom = 0.5f
        var hasOptical = false

        try {
            val officialIds = try { cameraManager.cameraIdList.toList() } catch (t: Throwable) { emptyList() }
            val candidateIds = linkedSetOf<String>()
            candidateIds.addAll(officialIds)
            for (testId in 0..12) {
                val sId = testId.toString()
                if (!candidateIds.contains(sId)) {
                    try {
                        val chars = cameraManager.getCameraCharacteristics(sId)
                        if (chars != null) candidateIds.add(sId)
                    } catch (ignored: Throwable) {}
                }
            }

            for (id in candidateIds) {
                val chars = try { cameraManager.getCameraCharacteristics(id) } catch (t: Throwable) { continue }
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    // Check CONTROL_ZOOM_RATIO_RANGE (Android 11+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val zoomRange = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
                        if (zoomRange != null && zoomRange.lower <= 0.9f) {
                            hasOptical = true
                            detectedMinZoom = zoomRange.lower
                            Log.d(TAG, "Found optical ultra-wide zoom range on camera $id: ${zoomRange.lower}..${zoomRange.upper}")
                        }
                    }

                    // Check focal length & FOV for physical ultra-wide
                    val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: floatArrayOf(4.0f)
                    val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                    val cropFactor = if (sensorSize != null && sensorSize.width > 0) 36f / sensorSize.width else 7f
                    for (focal in focalLengths) {
                        val eq35mm = focal * cropFactor
                        val fov = if (sensorSize != null && sensorSize.width > 0 && focal > 0) {
                            (2.0 * kotlin.math.atan(sensorSize.width.toDouble() / (2.0 * focal.toDouble())) * (180.0 / Math.PI)).toFloat()
                        } else 0f

                        if (eq35mm in 1.0f..23.5f || focal <= 2.8f || fov >= 85f) {
                            foundPhysicalId = id
                            hasOptical = true
                            Log.d(TAG, "Found physical ultra-wide camera $id: focal=${focal}mm, eq35=${eq35mm}mm, fov=${fov}°")
                            break
                        }
                    }

                    // Android 9+ physical sub-cameras inside logical multi-camera
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        try {
                            val physIds = chars.physicalCameraIds
                            for (pId in physIds) {
                                val pChars = cameraManager.getCameraCharacteristics(pId)
                                val pFocals = pChars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: floatArrayOf(4.0f)
                                val pSize = pChars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                                val pCrop = if (pSize != null && pSize.width > 0) 36f / pSize.width else 7f
                                for (pf in pFocals) {
                                    val peq35 = pf * pCrop
                                    if (peq35 in 1.0f..23.5f || pf <= 2.6f) {
                                        foundPhysicalId = pId
                                        hasOptical = true
                                        break
                                    }
                                }
                            }
                        } catch (t: Throwable) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error detecting ultra wide device info: ${e.message}")
        }

        return UltraWideDeviceInfo(
            physicalCameraId = foundPhysicalId,
            minOpticalZoomRatio = detectedMinZoom.coerceIn(0.3f, 0.7f),
            hasOpticalUltraWide = hasOptical
        )
    }

    private fun getDeviceSupportedFpsRange(targetFps: Int, isFront: Boolean): Range<Int> {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return Range(30, targetFps.coerceAtLeast(30))
            val expectedFacing = if (isFront) {
                CameraCharacteristics.LENS_FACING_FRONT
            } else {
                CameraCharacteristics.LENS_FACING_BACK
            }

            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == expectedFacing) {
                    val ranges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                    if (ranges != null && ranges.isNotEmpty()) {
                        val exactOrUnder = ranges.filter { it.upper <= targetFps }.maxByOrNull { it.upper }
                        if (exactOrUnder != null) return exactOrUnder
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
