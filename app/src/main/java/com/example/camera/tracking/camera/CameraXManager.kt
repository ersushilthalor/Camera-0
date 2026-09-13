package com.example.camera.tracking.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.camera.tracking.model.TrackingCameraLens
import com.example.camera.tracking.model.TrackingFpsOption
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Camera hardware lens & capture session manager for AI Subject Tracking.
 * Implements REAL physical lens switching using Camera2 IDs and lens metadata,
 * supporting the physical ultra-wide camera on Motorola moto g96 5G and all Android devices.
 * Preview, tracking analysis, and final output all stream directly from the active physical sensor.
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
        val hasOpticalZoomRatio: Boolean = false,
        val hasOpticalUltraWide: Boolean = false
    )

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    // Hardware ultra-wide detection matching Camera2Engine & Motorola camera IDs
    val ultraWideInfo: UltraWideDeviceInfo by lazy {
        detectUltraWideDeviceInfo()
    }

    private var activeLens: TrackingCameraLens = TrackingCameraLens.WIDE
    private var activeFpsOption: TrackingFpsOption = TrackingFpsOption.FPS_60
    private var targetWidth = 1080
    private var targetHeight = 1920

    // Camera2 runtime state
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

    private var currentCameraDevice: CameraDevice? = null
    private var currentCaptureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var currentCameraId: String? = null

    private val isCameraRunning = AtomicBoolean(false)
    private val cameraLock = Any()
    private var isProcessingFrame = AtomicBoolean(false)

    val isUsingFrontCamera: Boolean get() = activeLens.isFront

    fun getAvailableLenses(): List<TrackingCameraLens> {
        return if (ultraWideInfo.hasOpticalUltraWide) {
            listOf(TrackingCameraLens.ULTRAWIDE, TrackingCameraLens.WIDE, TrackingCameraLens.FRONT)
        } else {
            listOf(TrackingCameraLens.WIDE, TrackingCameraLens.FRONT)
        }
    }

    fun setViewfinderResolution(width: Int, height: Int) {
        targetWidth = width
        targetHeight = height
        if (isCameraRunning.get()) {
            restartCameraSession()
        }
    }

    fun setFps(fpsOption: TrackingFpsOption) {
        activeFpsOption = fpsOption
        if (isCameraRunning.get()) {
            applySessionSettings()
        }
    }

    fun startCamera(lens: TrackingCameraLens = TrackingCameraLens.WIDE, onReady: (Boolean) -> Unit = {}) {
        this.activeLens = lens
        ensureThreadStarted()
        openSelectedCamera(onReady)
    }

    /**
     * Switch camera lens: 0.5x Ultra-Wide, 1x Main Wide, or Front Selfie.
     * Physically opens the actual physical camera sensor (e.g. Camera ID 2 on Motorola moto g96 5G)
     * and reconfigures the preview, focus, auto-exposure, and stabilization pipeline.
     */
    fun setLens(lens: TrackingCameraLens, onReady: (Boolean) -> Unit = {}) {
        if (lens == TrackingCameraLens.ULTRAWIDE && !ultraWideInfo.hasOpticalUltraWide) {
            Log.w(TAG, "Cannot switch to ULTRAWIDE: Physical ultra-wide lens is not available on this device")
            onReady(false)
            return
        }

        val prevLens = activeLens
        activeLens = lens

        val targetId = resolveCameraIdForLens(lens)
        if (currentCameraDevice != null && currentCameraId == targetId) {
            // Same physical camera ID (e.g. If using optical zoom ratio on main camera)
            Log.d(TAG, "Lens switch on same physical camera $targetId: $prevLens -> $lens")
            applySessionSettings()
            onReady(true)
            return
        }

        // Switching to a different physical sensor (e.g. 1x Main -> Physical Ultra-Wide ID 2)
        Log.d(TAG, "Switching physical camera sensor: $prevLens (ID: $currentCameraId) -> $lens (ID: $targetId)")
        ensureThreadStarted()
        openSelectedCamera(onReady)
    }

    fun toggleCamera(onReady: (Boolean) -> Unit = {}) {
        val nextLens = if (activeLens.isFront) TrackingCameraLens.WIDE else TrackingCameraLens.FRONT
        setLens(nextLens, onReady)
    }

    fun stopCamera() {
        synchronized(cameraLock) {
            isCameraRunning.set(false)
            try {
                currentCaptureSession?.stopRepeating()
                currentCaptureSession?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing capture session: ${e.message}")
            }
            currentCaptureSession = null

            try {
                currentCameraDevice?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing camera device: ${e.message}")
            }
            currentCameraDevice = null

            try {
                imageReader?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing imageReader: ${e.message}")
            }
            imageReader = null
            currentCameraId = null
        }
    }

    fun release() {
        stopCamera()
        cameraThread?.quitSafely()
        try {
            cameraThread?.join(500)
        } catch (ignored: Exception) {}
        cameraThread = null
        cameraHandler = null
    }

    private fun ensureThreadStarted() {
        if (cameraThread == null || !cameraThread!!.isAlive) {
            val thread = HandlerThread("TrackingCamera2Thread")
            thread.start()
            cameraThread = thread
            cameraHandler = Handler(thread.looper)
        }
    }

    private fun resolveCameraIdForLens(lens: TrackingCameraLens): String {
        val mgr = cameraManager ?: return "0"
        return when (lens) {
            TrackingCameraLens.ULTRAWIDE -> {
                // Return actual physical ultra-wide camera ID if discovered
                ultraWideInfo.physicalCameraId ?: findPrimaryBackCameraId(mgr)
            }
            TrackingCameraLens.WIDE -> {
                findPrimaryBackCameraId(mgr)
            }
            TrackingCameraLens.FRONT -> {
                findPrimaryFrontCameraId(mgr)
            }
        }
    }

    private fun findPrimaryBackCameraId(mgr: CameraManager): String {
        try {
            for (id in mgr.cameraIdList) {
                val chars = mgr.getCameraCharacteristics(id)
                if (chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: floatArrayOf(4.0f)
                    val focal = focalLengths.firstOrNull() ?: 4.0f
                    // Exclude ultra-wide focal lengths to ensure 1x main is picked
                    if (focal > 2.8f) {
                        return id
                    }
                }
            }
            for (id in mgr.cameraIdList) {
                val chars = mgr.getCameraCharacteristics(id)
                if (chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    return id
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error finding primary back camera: ${e.message}")
        }
        return "0"
    }

    private fun findPrimaryFrontCameraId(mgr: CameraManager): String {
        try {
            for (id in mgr.cameraIdList) {
                val chars = mgr.getCameraCharacteristics(id)
                if (chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    return id
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error finding primary front camera: ${e.message}")
        }
        return "1"
    }

    private fun openSelectedCamera(onReady: (Boolean) -> Unit) {
        val mgr = cameraManager
        if (mgr == null) {
            onReady(false)
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Camera permission not granted")
            onReady(false)
            return
        }

        val targetId = resolveCameraIdForLens(activeLens)
        val handler = cameraHandler ?: Handler(context.mainLooper)

        handler.post {
            synchronized(cameraLock) {
                stopCamera()
                currentCameraId = targetId

                try {
                    Log.d(TAG, "Opening Camera2 ID: $targetId for lens $activeLens")
                    mgr.openCamera(targetId, object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) {
                            Log.d(TAG, "Camera2 ID $targetId successfully opened")
                            currentCameraDevice = camera
                            isCameraRunning.set(true)
                            createCaptureSession(camera, targetId, onReady)
                        }

                        override fun onDisconnected(camera: CameraDevice) {
                            Log.w(TAG, "Camera2 ID $targetId disconnected")
                            camera.close()
                            if (currentCameraDevice == camera) {
                                currentCameraDevice = null
                                isCameraRunning.set(false)
                            }
                        }

                        override fun onError(camera: CameraDevice, error: Int) {
                            Log.e(TAG, "Camera2 ID $targetId open error: $error")
                            camera.close()
                            if (currentCameraDevice == camera) {
                                currentCameraDevice = null
                                isCameraRunning.set(false)
                            }
                            onReady(false)
                        }
                    }, handler)
                } catch (e: Exception) {
                    Log.e(TAG, "Exception opening camera $targetId: ${e.message}", e)
                    onReady(false)
                }
            }
        }
    }

    private fun createCaptureSession(camera: CameraDevice, cameraId: String, onReady: (Boolean) -> Unit) {
        val mgr = cameraManager ?: return
        val handler = cameraHandler ?: return

        try {
            val chars = mgr.getCameraCharacteristics(cameraId)
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val supportedSizes = map?.getOutputSizes(ImageFormat.YUV_420_888) ?: emptyArray()

            // Select optimal size closest to requested viewfinder target
            val optimalSize = pickOptimalSize(supportedSizes, targetWidth, targetHeight)
            Log.d(TAG, "Selected Camera2 ImageReader size: ${optimalSize.width}x${optimalSize.height} for camera $cameraId")

            imageReader?.close()
            val reader = ImageReader.newInstance(
                optimalSize.width,
                optimalSize.height,
                ImageFormat.YUV_420_888,
                2
            )
            imageReader = reader

            val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            val facing = chars.get(CameraCharacteristics.LENS_FACING) ?: CameraCharacteristics.LENS_FACING_BACK
            val isFront = facing == CameraCharacteristics.LENS_FACING_FRONT

            reader.setOnImageAvailableListener({ r ->
                processImageFromReader(r, sensorOrientation, isFront)
            }, handler)

            val surface = reader.surface
            camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    synchronized(cameraLock) {
                        currentCaptureSession = session
                        applySessionSettings()
                        Log.d(TAG, "Camera2 session configured successfully for lens: $activeLens (ID: $cameraId)")
                        onReady(true)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Camera2 session configuration failed for camera $cameraId")
                    onReady(false)
                }
            }, handler)

        } catch (e: Exception) {
            Log.e(TAG, "Error creating capture session on camera $cameraId: ${e.message}", e)
            onReady(false)
        }
    }

    private fun pickOptimalSize(sizes: Array<Size>, desiredW: Int, desiredH: Int): Size {
        if (sizes.isEmpty()) return Size(1280, 720)
        val targetMax = maxOf(desiredW, desiredH)
        val targetMin = minOf(desiredW, desiredH)

        // Try exact match or match within 1080p / 720p bounds
        val match = sizes.filter {
            val maxD = maxOf(it.width, it.height)
            val minD = minOf(it.width, it.height)
            maxD <= targetMax && minD <= targetMin
        }.maxByOrNull { it.width * it.height }

        return match
            ?: sizes.firstOrNull { maxOf(it.width, it.height) <= 1920 }
            ?: sizes.first()
    }

    /**
     * Reconfigures capture request settings:
     * 1. Continuous autofocus (AF) or Auto/Fixed mode
     * 2. Auto-exposure (AE) and target FPS range
     * 3. Auto-white-balance (AWB)
     * 4. Optical stabilization (OIS) and Video stabilization (EIS)
     * 5. Optical zoom ratio (<1.0x on integrated logical sensors)
     */
    private fun applySessionSettings() {
        val session = currentCaptureSession ?: return
        val camera = currentCameraDevice ?: return
        val mgr = cameraManager ?: return
        val cId = currentCameraId ?: return

        try {
            val chars = mgr.getCameraCharacteristics(cId)
            val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            val surface = imageReader?.surface ?: return
            builder.addTarget(surface)

            // 1. Focus Mode
            val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
            if (afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            } else if (afModes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO)) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            } else if (afModes.isNotEmpty()) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, afModes[0])
            }

            // 2. Auto-Exposure & Target FPS
            val aeModes = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) ?: intArrayOf()
            if (aeModes.contains(CaptureRequest.CONTROL_AE_MODE_ON)) {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }

            val targetFps = activeFpsOption.targetFps.coerceIn(30, 120)
            val fpsRanges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            if (!fpsRanges.isNullOrEmpty()) {
                val matchedRange = fpsRanges.filter { it.upper <= targetFps }.maxByOrNull { it.upper }
                    ?: fpsRanges.minByOrNull { kotlin.math.abs(it.upper - targetFps) }
                if (matchedRange != null) {
                    builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, matchedRange)
                }
            }

            // 3. Auto-White Balance
            val awbModes = chars.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES) ?: intArrayOf()
            if (awbModes.contains(CaptureRequest.CONTROL_AWB_MODE_AUTO)) {
                builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
            }

            // 4. Stabilization (Hardware OIS & EIS)
            val oisModes = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) ?: intArrayOf()
            if (oisModes.contains(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)) {
                builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
            }

            val eisModes = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) ?: intArrayOf()
            if (eisModes.contains(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)) {
                builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
            }

            // 5. Optical Zoom Ratio (if Ultra-Wide is accessed via integrated logical zoom <1.0x on Android 11+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (activeLens == TrackingCameraLens.ULTRAWIDE && ultraWideInfo.physicalCameraId == null) {
                    val ratio = ultraWideInfo.minOpticalZoomRatio
                    builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, ratio)
                    Log.d(TAG, "Configured integrated CONTROL_ZOOM_RATIO: $ratio for Ultra Wide")
                } else {
                    builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, 1.0f)
                }
            }

            session.setRepeatingRequest(builder.build(), null, cameraHandler)
            Log.d(TAG, "Camera2 repeating request updated: lens=$activeLens, fps=$activeFpsOption on camera $cId")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying session settings: ${e.message}", e)
        }
    }

    private fun restartCameraSession() {
        val cam = currentCameraDevice
        val cId = currentCameraId
        if (cam != null && cId != null) {
            createCaptureSession(cam, cId) {}
        }
    }

    /**
     * Reads frames from ImageReader (YUV_420_888), converts to Bitmap with correct orientation,
     * builds ML Kit InputImage, and delivers to the tracking pipeline.
     */
    private fun processImageFromReader(reader: ImageReader, sensorOrientation: Int, isFront: Boolean) {
        val image = try {
            reader.acquireLatestImage()
        } catch (e: Exception) {
            null
        } ?: return

        try {
            if (isProcessingFrame.compareAndSet(false, true)) {
                val bitmap = yuv420888ToBitmap(image)
                val orientedBitmap = if (sensorOrientation != 0 || isFront) {
                    val matrix = Matrix().apply {
                        if (sensorOrientation != 0) postRotate(sensorOrientation.toFloat())
                        if (isFront) postScale(-1f, 1f)
                    }
                    val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    if (transformed != bitmap) {
                        bitmap.recycle()
                    }
                    transformed
                } else {
                    bitmap
                }

                val inputImage = InputImage.fromBitmap(orientedBitmap, 0)
                onFrameAvailable(orientedBitmap, inputImage)
                isProcessingFrame.set(false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error processing camera frame: ${e.message}")
            isProcessingFrame.set(false)
        } finally {
            image.close()
        }
    }

    private var cachedPixels: IntArray? = null
    private var cachedYBytes: ByteArray? = null
    private var cachedUBytes: ByteArray? = null
    private var cachedVBytes: ByteArray? = null

    /**
     * Fast, zero-crash YUV_420_888 to ARGB_8888 Bitmap converter.
     * Uses cached byte and pixel arrays to eliminate GC churn during continuous 60fps tracking.
     */
    private fun yuv420888ToBitmap(image: Image): Bitmap {
        val width = image.width
        val height = image.height

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        var yBytes = cachedYBytes
        if (yBytes == null || yBytes.size < ySize) {
            yBytes = ByteArray(ySize)
            cachedYBytes = yBytes
        }
        yBuffer.get(yBytes, 0, ySize)

        var uBytes = cachedUBytes
        if (uBytes == null || uBytes.size < uSize) {
            uBytes = ByteArray(uSize)
            cachedUBytes = uBytes
        }
        uBuffer.get(uBytes, 0, uSize)

        var vBytes = cachedVBytes
        if (vBytes == null || vBytes.size < vSize) {
            vBytes = ByteArray(vSize)
            cachedVBytes = vBytes
        }
        vBuffer.get(vBytes, 0, vSize)

        val totalPixels = width * height
        var pixels = cachedPixels
        if (pixels == null || pixels.size < totalPixels) {
            pixels = IntArray(totalPixels)
            cachedPixels = pixels
        }

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        var pixelIndex = 0
        for (y in 0 until height) {
            val yOffset = y * yRowStride
            val uvOffset = (y shr 1) * uvRowStride

            for (x in 0 until width) {
                val yVal = (yBytes[yOffset + x * yPixelStride].toInt() and 0xFF)
                val uvIdx = uvOffset + (x shr 1) * uvPixelStride
                val uVal = (uBytes[uvIdx].toInt() and 0xFF) - 128
                val vVal = (vBytes[uvIdx].toInt() and 0xFF) - 128

                val y1192 = 1192 * (yVal - 16).coerceAtLeast(0)
                val r = ((y1192 + 1634 * vVal) shr 10).coerceIn(0, 255)
                val g = ((y1192 - 833 * vVal - 400 * uVal) shr 10).coerceIn(0, 255)
                val b = ((y1192 + 2066 * uVal) shr 10).coerceIn(0, 255)

                pixels[pixelIndex++] = -0x1000000 or (r shl 16) or (g shl 8) or b
            }
        }

        return Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Inspects Camera2 camera IDs and lens metadata matching Camera2Engine:
     * 1. Official IDs + Auxiliary candidate IDs (0..24, 50..55) for Motorola moto g96 5G and other OEM devices.
     * 2. Android 9+ Physical camera IDs inside logical multi-camera.
     * 3. Lens metadata inspection: focal lengths, sensor physical size, FOV, 35mm equivalent focal length.
     * 4. CONTROL_ZOOM_RATIO_RANGE (<1.0x indicates optical ultra-wide zoom).
     */
    private fun detectUltraWideDeviceInfo(): UltraWideDeviceInfo {
        val mgr = cameraManager ?: return UltraWideDeviceInfo()

        var foundPhysicalId: String? = null
        var detectedMinZoom = 0.5f
        var hasOpticalZoomRatio = false
        var hasOptical = false

        try {
            val candidateIds = mutableListOf<String>()
            val officialIds = try { mgr.cameraIdList.toList() } catch (t: Throwable) { emptyList() }
            candidateIds.addAll(officialIds)

            // Discover hidden auxiliary IDs on Motorola and Qualcomm/MediaTek devices
            for (i in 0..24) {
                val sId = i.toString()
                if (!candidateIds.contains(sId)) {
                    try {
                        val chars = mgr.getCameraCharacteristics(sId)
                        if (chars != null) candidateIds.add(sId)
                    } catch (ignored: Throwable) {}
                }
            }
            for (i in 50..55) {
                val sId = i.toString()
                if (!candidateIds.contains(sId)) {
                    try {
                        val chars = mgr.getCameraCharacteristics(sId)
                        if (chars != null) candidateIds.add(sId)
                    } catch (ignored: Throwable) {}
                }
            }

            // Android 9+ Physical multi-camera sub-IDs
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                for (id in candidateIds.toList()) {
                    try {
                        val chars = mgr.getCameraCharacteristics(id)
                        for (pId in chars.physicalCameraIds) {
                            if (!candidateIds.contains(pId)) candidateIds.add(pId)
                        }
                    } catch (ignored: Throwable) {}
                }
            }

            var bestFov = -1f
            for (id in candidateIds) {
                val chars = try { mgr.getCameraCharacteristics(id) } catch (t: Throwable) { continue }
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    // Check CONTROL_ZOOM_RATIO_RANGE (Android 11+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val zoomRange = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
                        if (zoomRange != null && zoomRange.lower <= 0.85f) {
                            hasOpticalZoomRatio = true
                            hasOptical = true
                            detectedMinZoom = zoomRange.lower
                            Log.d(TAG, "Found optical ultra-wide zoom range on camera $id: ${zoomRange.lower}..${zoomRange.upper}")
                        }
                    }

                    // Check if this camera ID is a physical ultra-wide lens
                    val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: floatArrayOf(4.0f)
                    val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                    val cropFactor = if (sensorSize != null && sensorSize.width > 0) 36f / sensorSize.width else 7f

                    for (focal in focalLengths) {
                        val eq35mm = focal * cropFactor
                        val calculatedFov = if (sensorSize != null && sensorSize.width > 0 && focal > 0) {
                            (2.0 * kotlin.math.atan(sensorSize.width.toDouble() / (2.0 * focal.toDouble())) * (180.0 / Math.PI)).toFloat()
                        } else 0f
                        val effectiveFov = if (calculatedFov > 0f) calculatedFov else if (focal <= 2.2f) 118f else 90f

                        // Real physical ultra-wide lens criteria (e.g. Motorola moto g96 5G focal ~1.66-2.2mm, fov ~118°)
                        val isUltraWideLens = ((eq35mm in 1.0f..23.5f) || focal <= 2.8f || calculatedFov >= 85f) && focal > 0.5f
                        if (isUltraWideLens) {
                            if (effectiveFov > bestFov || foundPhysicalId == null) {
                                bestFov = effectiveFov
                                foundPhysicalId = id
                                hasOptical = true
                                Log.d(TAG, "Discovered physical Ultra-Wide camera ID: $id (focal=${focal}mm, eq35=${eq35mm}mm, fov=${effectiveFov}°)")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error detecting ultra-wide device info: ${e.message}")
        }

        val result = UltraWideDeviceInfo(
            physicalCameraId = foundPhysicalId,
            minOpticalZoomRatio = detectedMinZoom.coerceIn(0.3f, 0.7f),
            hasOpticalZoomRatio = hasOpticalZoomRatio,
            hasOpticalUltraWide = hasOptical
        )
        Log.d(TAG, "UltraWide detection result: physicalId=${result.physicalCameraId}, minRatio=${result.minOpticalZoomRatio}, hasOptical=${result.hasOpticalUltraWide}")
        return result
    }
}
