package com.example.camera.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import com.example.camera.model.DualVideoConfig
import com.example.camera.model.DualVideoLayout
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Real Concurrent Dual Camera Manager.
 *
 * Coordinates real simultaneous camera hardware streams and recording for Dual Video.
 * Queries CameraManager.getConcurrentCameraIds() on Android 11+ (API 30+), with smart
 * fallback to primary rear + front hardware camera sensors.
 *
 * Provides:
 * - Simultaneous camera hardware preview sessions
 * - Real hardware dual video recording with MediaRecorder
 * - Smooth camera swapping
 * - Safe lifecycle management
 */
class DualCameraManager(private val context: Context) {

    companion object {
        private const val TAG = "DualCameraManager"
    }

    private val cameraManager: CameraManager? =
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    private val _config = MutableStateFlow(DualVideoConfig())
    val config: StateFlow<DualVideoConfig> = _config.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0)
    val recordingDurationSeconds: StateFlow<Int> = _recordingDurationSeconds.asStateFlow()

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var primaryCameraDevice: CameraDevice? = null
    private var secondaryCameraDevice: CameraDevice? = null

    private var primarySession: CameraCaptureSession? = null
    private var secondarySession: CameraCaptureSession? = null

    private var primarySurface: Surface? = null
    private var secondarySurface: Surface? = null

    private var primaryMediaRecorder: MediaRecorder? = null
    private var primaryTempFile: File? = null
    private var compositor: DualCameraCompositor? = null

    private var recordingTimerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        // ZERO hardware calls during construction!
        // Dual camera detection is initialized lazily via safeInitializeDualCamera()
        // only after CAMERA permission is confirmed.
    }

    fun safeInitializeDualCamera() {
        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return
            }
            checkHardwareSupport()
        } catch (t: Throwable) {
            Log.w(TAG, "safeInitializeDualCamera ignored failure", t)
        }
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("DualCameraBackground").apply {
                uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { thread, throwable ->
                    Log.e(TAG, "Uncaught exception on DualCameraBackground: ${thread.name}", throwable)
                }
                start()
            }
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join(500)
        } catch (ignored: Exception) {}
        backgroundThread = null
        backgroundHandler = null
    }

    private fun checkHardwareSupport() {
        val mgr = cameraManager ?: run {
            _config.value = _config.value.copy(
                isConcurrentSupported = false,
                statusMessage = "CameraManager service is unavailable"
            )
            return
        }

        startBackgroundThread()

        // 1. Android 11+ Concurrent Camera IDs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val concurrentSets = mgr.concurrentCameraIds
                if (concurrentSets.isNotEmpty()) {
                    val firstSet = concurrentSets.firstOrNull()?.toList() ?: emptyList()
                    val primary = firstSet.getOrNull(0) ?: "0"
                    val secondary = firstSet.getOrNull(1) ?: "1"
                    _config.value = _config.value.copy(
                        isConcurrentSupported = true,
                        primaryCameraId = primary,
                        secondaryCameraId = secondary,
                        statusMessage = "Hardware Concurrent Streaming Active (Cams $primary + $secondary)"
                    )
                    Log.d(TAG, "Concurrent cameras supported: $concurrentSets")
                    return
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Error checking concurrent camera IDs", t)
            }
        }

        // 2. Fallback check for dual physical camera IDs (e.g. Back + Front)
        try {
            val cameraIds = mgr.cameraIdList
            var backId: String? = null
            var frontId: String? = null
            for (id in cameraIds) {
                try {
                    val chars = mgr.getCameraCharacteristics(id)
                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    if (facing == CameraCharacteristics.LENS_FACING_BACK && backId == null) {
                        backId = id
                    } else if (facing == CameraCharacteristics.LENS_FACING_FRONT && frontId == null) {
                        frontId = id
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Error inspecting characteristics for $id", t)
                }
            }
            if (backId != null && frontId != null) {
                _config.value = _config.value.copy(
                    isConcurrentSupported = true,
                    primaryCameraId = backId,
                    secondaryCameraId = frontId,
                    statusMessage = "Dual Hardware Cameras: Rear ($backId) + Front ($frontId)"
                )
                Log.d(TAG, "Dual hardware cameras identified: Rear $backId + Front $frontId")
                return
            } else if (cameraIds.size >= 2) {
                _config.value = _config.value.copy(
                    isConcurrentSupported = true,
                    primaryCameraId = cameraIds[0],
                    secondaryCameraId = cameraIds[1],
                    statusMessage = "Dual Cameras: ${cameraIds[0]} + ${cameraIds[1]}"
                )
                return
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error inspecting camera devices", t)
        }

        _config.value = _config.value.copy(
            isConcurrentSupported = false,
            statusMessage = "Device hardware does not support simultaneous dual-camera streaming."
        )
    }

    fun updateConfig(newConfig: DualVideoConfig) {
        _config.value = newConfig
    }

    fun prepareDualCameras() {
        startBackgroundThread()
        if (primarySurface != null && primaryCameraDevice == null) {
            openPrimaryCamera()
        }
        if (secondarySurface != null && secondaryCameraDevice == null) {
            openSecondaryCamera()
        }
    }

    fun setPrimarySurface(surface: Surface) {
        primarySurface = surface
        startBackgroundThread()
        if (primaryCameraDevice == null) {
            openPrimaryCamera()
        } else {
            createPrimarySession()
        }
    }

    fun setSecondarySurface(surface: Surface) {
        secondarySurface = surface
        startBackgroundThread()
        if (secondaryCameraDevice == null) {
            openSecondaryCamera()
        } else {
            createSecondarySession()
        }
    }

    private fun openPrimaryCamera() {
        val mgr = cameraManager ?: run {
            Log.e(TAG, "CameraManager unavailable for primary camera")
            return
        }
        startBackgroundThread()
        val cameraId = _config.value.primaryCameraId
        try {
            mgr.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    primaryCameraDevice = camera
                    createPrimarySession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    primaryCameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Primary camera error: $error")
                    camera.close()
                    primaryCameraDevice = null
                }
            }, backgroundHandler)
        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission missing for primary camera", e)
        } catch (t: Throwable) {
            Log.e(TAG, "Error opening primary camera $cameraId", t)
        }
    }

    private fun openSecondaryCamera() {
        val mgr = cameraManager ?: run {
            Log.e(TAG, "CameraManager unavailable for secondary camera")
            return
        }
        startBackgroundThread()
        val cameraId = _config.value.secondaryCameraId
        try {
            mgr.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    secondaryCameraDevice = camera
                    createSecondarySession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    secondaryCameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Secondary camera error: $error")
                    camera.close()
                    secondaryCameraDevice = null
                }
            }, backgroundHandler)
        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission missing for secondary camera", e)
        } catch (t: Throwable) {
            Log.e(TAG, "Error opening secondary camera $cameraId", t)
        }
    }

    private fun createPrimarySession(onConfiguredCallback: (() -> Unit)? = null) {
        val camera = primaryCameraDevice ?: return
        val surface = primarySurface ?: return
        if (!surface.isValid) return

        try {
            val surfaces = mutableListOf(surface)
            if (_isRecording.value) {
                compositor?.primarySurface?.let { compSurf ->
                    if (compSurf.isValid) surfaces.add(compSurf)
                }
            }

            camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    primarySession = session
                    updatePrimaryPreviewSettings()
                    onConfiguredCallback?.invoke()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Primary session configuration failed")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create primary session", e)
        }
    }

    private fun createSecondarySession(onConfiguredCallback: (() -> Unit)? = null) {
        val camera = secondaryCameraDevice ?: return
        val surface = secondarySurface ?: return
        if (!surface.isValid) return

        try {
            val surfaces = mutableListOf(surface)
            if (_isRecording.value) {
                compositor?.secondarySurface?.let { compSurf ->
                    if (compSurf.isValid) surfaces.add(compSurf)
                }
            }

            camera.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    secondarySession = session
                    updateSecondaryPreviewSettings()
                    onConfiguredCallback?.invoke()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Secondary session configuration failed")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create secondary session", e)
        }
    }

    private fun updatePrimaryPreviewSettings() {
        val camera = primaryCameraDevice ?: return
        val session = primarySession ?: return
        val surface = primarySurface ?: return
        if (!surface.isValid) return

        try {
            val template = if (_isRecording.value) CameraDevice.TEMPLATE_RECORD else CameraDevice.TEMPLATE_PREVIEW
            val builder = camera.createCaptureRequest(template)
            builder.addTarget(surface)

            if (_isRecording.value) {
                compositor?.primarySurface?.let { compSurf ->
                    if (compSurf.isValid) {
                        builder.addTarget(compSurf)
                    }
                }
            }

            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, _config.value.primaryEv)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, _config.value.primaryZoom)
            }
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.w(TAG, "Error updating primary preview", e)
        }
    }

    private fun updateSecondaryPreviewSettings() {
        val camera = secondaryCameraDevice ?: return
        val session = secondarySession ?: return
        val surface = secondarySurface ?: return
        if (!surface.isValid) return

        try {
            val template = if (_isRecording.value) CameraDevice.TEMPLATE_RECORD else CameraDevice.TEMPLATE_PREVIEW
            val builder = camera.createCaptureRequest(template)
            builder.addTarget(surface)

            if (_isRecording.value) {
                compositor?.secondarySurface?.let { compSurf ->
                    if (compSurf.isValid) {
                        builder.addTarget(compSurf)
                    }
                }
            }

            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, _config.value.secondaryEv)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, _config.value.secondaryZoom)
            }
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.w(TAG, "Error updating secondary preview", e)
        }
    }

    fun setLayout(layout: DualVideoLayout) {
        _config.value = _config.value.copy(layout = layout)
    }

    fun swapCameras() {
        val cur = _config.value
        _config.value = cur.copy(
            primaryCameraId = cur.secondaryCameraId,
            secondaryCameraId = cur.primaryCameraId,
            primaryZoom = cur.secondaryZoom,
            secondaryZoom = cur.primaryZoom,
            primaryEv = cur.secondaryEv,
            secondaryEv = cur.primaryEv
        )
        restartConcurrentStreams()
    }

    fun restartConcurrentStreams() {
        closeStreams()
        startBackgroundThread()
        if (primarySurface != null) openPrimaryCamera()
        if (secondarySurface != null) openSecondaryCamera()
    }

    /**
     * Real Dual Video Hardware Recording with Real-Time Compositing
     */
    fun startRecording(
        onSaved: (Uri) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (_isRecording.value) return
        if (!_config.value.isConcurrentSupported) {
            onError("Simultaneous dual camera recording is not supported by your device hardware.")
            return
        }
        if (primaryCameraDevice == null || secondaryCameraDevice == null) {
            onError("Both cameras must be ready before recording.")
            return
        }

        startBackgroundThread()

        try {
            val tempFile = File(context.cacheDir, "DUAL_VID_${System.currentTimeMillis()}.mp4")
            primaryTempFile = tempFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                try {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                } catch (e: Exception) {
                    Log.w(TAG, "Audio source mic setup fallback", e)
                }
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(tempFile.absolutePath)
                setVideoEncodingBitRate(14_000_000)
                setVideoFrameRate(30)
                setVideoSize(1920, 1080)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                try {
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioSamplingRate(48000)
                    setAudioEncodingBitRate(192000)
                } catch (ignored: Exception) {}
                setOrientationHint(0)
                prepare()
            }
            primaryMediaRecorder = recorder

            // Create compositor connected to MediaRecorder input surface
            val comp = DualCameraCompositor(recorder.surface, 1920, 1080) { _config.value.layout }
            compositor = comp

            _isRecording.value = true

            // Synchronize configuration of both camera sessions
            var primaryConfigured = false
            var secondaryConfigured = false

            fun checkAndStartRecorder() {
                if (primaryConfigured && secondaryConfigured && _isRecording.value) {
                    try {
                        recorder.start()
                        comp.start()
                        _recordingDurationSeconds.value = 0
                        recordingTimerJob?.cancel()
                        recordingTimerJob = scope.launch {
                            while (isActive && _isRecording.value) {
                                delay(1000)
                                _recordingDurationSeconds.value += 1
                            }
                        }
                        Log.d(TAG, "Real dual camera simultaneous recording started successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed starting media recorder or compositor", e)
                        _isRecording.value = false
                        onError(e.message ?: "Failed starting dual recording")
                    }
                }
            }

            createPrimarySession {
                primaryConfigured = true
                checkAndStartRecorder()
            }

            createSecondarySession {
                secondaryConfigured = true
                checkAndStartRecorder()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start dual video recording", e)
            _isRecording.value = false
            compositor?.release()
            compositor = null
            try {
                primaryMediaRecorder?.release()
            } catch (ignored: Exception) {}
            primaryMediaRecorder = null
            onError(e.message ?: "Failed to start dual recording")
        }
    }

    fun stopRecording(onSaved: (Uri?) -> Unit = {}) {
        if (!_isRecording.value) return
        _isRecording.value = false
        recordingTimerJob?.cancel()
        recordingTimerJob = null

        compositor?.stop()

        try {
            primaryMediaRecorder?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "MediaRecorder stop error", e)
        }

        try {
            primaryMediaRecorder?.release()
        } catch (ignored: Exception) {}
        primaryMediaRecorder = null

        compositor?.release()
        compositor = null

        // Recreate sessions for preview only
        createPrimarySession()
        createSecondarySession()

        // Save recorded file to MediaStore
        val tempFile = primaryTempFile
        if (tempFile != null && tempFile.exists() && tempFile.length() > 0) {
            scope.launch(Dispatchers.IO) {
                val savedUri = saveVideoToMediaStore(tempFile)
                withContext(Dispatchers.Main) {
                    onSaved(savedUri)
                }
            }
        } else {
            onSaved(null)
        }
        primaryTempFile = null
    }

    private fun saveVideoToMediaStore(file: File): Uri? {
        val fileName = "VID_DUAL_${System.currentTimeMillis()}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/Camera")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            file.delete()
            return uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving dual video to MediaStore", e)
            return null
        }
    }

    fun closeStreams() {
        try {
            primarySession?.close()
            primarySession = null
        } catch (e: Exception) {
            Log.w(TAG, "Error closing primary session", e)
        }
        try {
            secondarySession?.close()
            secondarySession = null
        } catch (e: Exception) {
            Log.w(TAG, "Error closing secondary session", e)
        }
        try {
            primaryCameraDevice?.close()
            primaryCameraDevice = null
        } catch (e: Exception) {
            Log.w(TAG, "Error closing primary camera", e)
        }
        try {
            secondaryCameraDevice?.close()
            secondaryCameraDevice = null
        } catch (e: Exception) {
            Log.w(TAG, "Error closing secondary camera", e)
        }
    }

    fun release() {
        stopRecording()
        compositor?.release()
        compositor = null
        closeStreams()
        primarySurface?.release()
        primarySurface = null
        secondarySurface?.release()
        secondarySurface = null
        stopBackgroundThread()
    }
}
