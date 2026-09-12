package com.example.camera.tracking.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.camera.tracking.camera.CameraXManager
import com.example.camera.tracking.engine.AspectRatioCropEngine
import com.example.camera.tracking.engine.CropController
import com.example.camera.tracking.engine.DigitalGimbalEngine
import com.example.camera.tracking.engine.TrackedVideoRecorder
import com.example.camera.tracking.ml.AdaptiveTrackingLearner
import com.example.camera.tracking.ml.SubjectTracker
import com.example.camera.tracking.model.*
import com.example.camera.tracking.util.MediaStorageHelper
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

typealias CameraTrackingUiState = com.example.camera.tracking.model.CameraTrackingUiState

/**
 * Central State & Pipeline Orchestrator for AI Subject Tracking.
 * Synchronizes:
 * 1. CameraX High-rate sensor frames
 * 2. ML Kit Vision Detection & 2nd-Order Kinematic Predictor
 * 3. 3x Dynamic Crop Window with Aspect-Ratio Safety
 * 4. Hardware Gyro Digital Gimbal (EIS)
 * 5. MediaStore Auto-Save & Hardware Video Encoding
 */
class CameraTrackingViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "CameraTrackingVM"
    }

    private val _uiState = MutableStateFlow(CameraTrackingUiState())
    val uiState: StateFlow<CameraTrackingUiState> = _uiState.asStateFlow()

    // Engines
    val cropController = CropController(targetZoom = 1.0f)
    val digitalGimbalEngine = DigitalGimbalEngine(application)
    private val videoRecorder = TrackedVideoRecorder()

    private var cameraXManager: CameraXManager? = null

    val subjectTracker = SubjectTracker { status, activeSub, candidates ->
        _uiState.update { current ->
            current.copy(
                trackingStatus = status,
                activeSubject = activeSub,
                allDetections = candidates
            )
        }
    }

    // Frame references
    @Volatile
    private var latestSourceBitmap: Bitmap? = null
    private var isProcessingMlFrame = false

    // Timing & FPS metrics
    private var lastFrameTimeNs = System.nanoTime()
    private var frameCount = 0
    private var lastFpsCalcTimeMs = SystemClock.uptimeMillis()

    // Recording duration timer
    private var recordingTimerJob: Job? = null

    // Continuous 60fps smoothing loop
    private var smoothingLoopJob: Job? = null

    // Adaptive tracking learner instance
    private var adaptiveLearner: AdaptiveTrackingLearner? = null

    init {
        // Start continuous crop smoothing and kinematic prediction loop
        startSmoothingLoop()
    }

    /**
     * Initializes CameraX with the host LifecycleOwner.
     */
    fun initCamera(lifecycleOwner: LifecycleOwner, context: Context) {
        if (cameraXManager != null) return

        if (adaptiveLearner == null) {
            val learner = AdaptiveTrackingLearner(context.applicationContext)
            adaptiveLearner = learner
            subjectTracker.adaptiveLearner = learner
            _uiState.update { it.copy(learnedSubjectsCount = learner.getLearnedProfilesCount()) }
        }

        digitalGimbalEngine.isEnabled = _uiState.value.isGimbalEnabled
        digitalGimbalEngine.start()

        val manager = CameraXManager(
            context = context,
            lifecycleOwner = lifecycleOwner,
            onFrameAvailable = { bitmap, inputImage ->
                onNewCameraFrame(bitmap, inputImage)
            }
        )
        cameraXManager = manager
        manager.setFps(_uiState.value.selectedFpsOption)
        _uiState.update { it.copy(availableLenses = manager.getAvailableLenses()) }
        manager.startCamera(lens = _uiState.value.selectedLens)
    }

    private fun startSmoothingLoop() {
        smoothingLoopJob?.cancel()
        smoothingLoopJob = viewModelScope.launch(Dispatchers.Default) {
            var lastLoopTime = System.nanoTime()
            while (isActive) {
                val now = System.nanoTime()
                val dt = ((now - lastLoopTime) / 1_000_000_000f).coerceIn(0.005f, 0.05f)
                lastLoopTime = now

                // Update continuous kinematics for active tracking
                subjectTracker.updateContinuousKinematics(dt)

                // Update Digital Gimbal shake compensation
                val gimbalState = digitalGimbalEngine.updateFrame(dt)
                cropController.gimbalOffsetX = gimbalState.offsetX
                cropController.gimbalOffsetY = gimbalState.offsetY

                // Step crop controller motion smoothing
                val activeSub = subjectTracker.getActiveSubject()
                val isTracking = subjectTracker.getTrackingStatus() == TrackingStatus.TRACKING_LOCKED ||
                        subjectTracker.getTrackingStatus() == TrackingStatus.OCCLUDED_PREDICTING

                if (isTracking && activeSub != null) {
                    cropController.setTarget(
                        PointF(activeSub.bounds.centerX, activeSub.bounds.centerY),
                        activeTracking = true,
                        desiredZoom = 3.0f
                    )
                } else if (!cropController.isCinematicPanActive) {
                    // Manual tracking only: When no subject is locked, maintain wide view (1.0x).
                    // Tracking must start only when the user explicitly taps a subject.
                    cropController.setTarget(null, activeTracking = false, desiredZoom = 1.0f)
                }

                val newCrop = cropController.update(dt)

                // Record frame if recording is active
                latestSourceBitmap?.let { bmp ->
                    if (videoRecorder.isRecordingActive() && !bmp.isRecycled) {
                        videoRecorder.recordFrame(bmp, newCrop)
                    }
                }

                val learnedCount = adaptiveLearner?.getLearnedProfilesCount() ?: 0

                _uiState.update {
                    it.copy(
                        cropWindow = newCrop,
                        currentZoom = newCrop.zoomFactor,
                        gimbalState = gimbalState,
                        isCinematicPanActive = cropController.isCinematicPanActive,
                        cinematicPanProgress = cropController.cinematicPanProgress,
                        learnedSubjectsCount = learnedCount
                    )
                }

                val delayMs = _uiState.value.selectedFpsOption.loopDelayMs
                delay(delayMs)
            }
        }
    }

    private fun onNewCameraFrame(bitmap: Bitmap, inputImage: InputImage) {
        latestSourceBitmap = bitmap

        // Measure FPS
        frameCount++
        val nowMs = SystemClock.uptimeMillis()
        if (nowMs - lastFpsCalcTimeMs >= 1000) {
            val fps = frameCount
            frameCount = 0
            lastFpsCalcTimeMs = nowMs
            _uiState.update { it.copy(fps = fps) }
        }

        // Run ML Kit Object Tracking asynchronously without blocking camera stream
        if (!isProcessingMlFrame) {
            isProcessingMlFrame = true
            subjectTracker.processFrame(
                image = inputImage,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
                sourceBitmap = bitmap
            ) {
                isProcessingMlFrame = false
            }
        }

        _uiState.update { it.copy(currentFrame = bitmap) }
    }

    /**
     * User taps the viewfinder to track an object or person.
     * [normX, normY] are in normalized source frame coordinates [0..1].
     */
    fun onTapToTrack(normX: Float, normY: Float) {
        subjectTracker.selectSubjectAt(
            srcX = normX,
            srcY = normY,
            allCurrentDetections = _uiState.value.allDetections,
            sourceBitmap = latestSourceBitmap
        )
    }

    /**
     * Unlocks subject tracking and zooms back out to 1.0x wide.
     */
    fun unlockTracking() {
        subjectTracker.unlock()
        cropController.setTarget(null, activeTracking = false, desiredZoom = 1.0f)
    }

    /**
     * Handles shutter click: captures photo or starts/stops video recording.
     */
    fun onShutterClicked(context: Context) {
        if (_uiState.value.captureMode == CameraCaptureMode.PHOTO) {
            captureTrackedPhoto(context)
        } else {
            toggleVideoRecording(context)
        }
    }

    private fun captureTrackedPhoto(context: Context) {
        val srcBitmap = latestSourceBitmap ?: return
        if (srcBitmap.isRecycled) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(flashFeedback = true) }

            val crop = _uiState.value.cropWindow
            val targetAspect = _uiState.value.aspectRatio.ratioValue

            // Uniformly crop to exact 3x tracking region with zero distortion
            val croppedBitmap = AspectRatioCropEngine.cropBitmap(srcBitmap, crop, targetAspect)

            // Automatically save to Gallery (DCIM/Camera3X)
            val (savedUri, filePath) = MediaStorageHelper.savePhotoToGallery(
                context = context,
                bitmap = croppedBitmap,
                aspectRatioLabel = _uiState.value.aspectRatio.label
            )

            val mediaItem = CapturedMediaItem(
                uri = savedUri ?: Uri.EMPTY,
                galleryUri = savedUri,
                filePath = filePath,
                isVideo = false,
                thumbnailBitmap = croppedBitmap,
                aspectRatioLabel = _uiState.value.aspectRatio.label,
                resolutionLabel = "${croppedBitmap.width}x${croppedBitmap.height}",
                subjectLabel = _uiState.value.activeSubject?.label
            )

            _uiState.update {
                it.copy(
                    lastCapturedMedia = mediaItem,
                    reviewingMediaItem = mediaItem
                )
            }

            delay(120)
            _uiState.update { it.copy(flashFeedback = false) }

            // Tracking stops only when the final output frame is completed/saved
            unlockTracking()
        }
    }

    private fun toggleVideoRecording(context: Context) {
        if (_uiState.value.isRecording) {
            stopVideoRecording(context)
        } else {
            startVideoRecording(context)
        }
    }

    private fun startVideoRecording(context: Context) {
        val tempVideoFile = File(context.cacheDir, "temp_rec_${System.currentTimeMillis()}.mp4")
        val success = videoRecorder.start(
            destinationFile = tempVideoFile,
            resolution = _uiState.value.videoResolution,
            targetAspect = _uiState.value.aspectRatio.ratioValue
        )

        if (success) {
            _uiState.update { it.copy(isRecording = true, recordingDurationSec = 0) }
            recordingTimerJob = viewModelScope.launch {
                var sec = 0
                while (isActive && _uiState.value.isRecording) {
                    delay(1000)
                    sec++
                    _uiState.update { it.copy(recordingDurationSec = sec) }
                }
            }
        }
    }

    private fun stopVideoRecording(context: Context) {
        recordingTimerJob?.cancel()
        val recordedFile = videoRecorder.stop()
        _uiState.update { it.copy(isRecording = false, recordingDurationSec = 0) }

        if (recordedFile != null && recordedFile.exists()) {
            viewModelScope.launch(Dispatchers.IO) {
                // Automatically save to Gallery (DCIM/Camera3X)
                val (savedUri, thumbnail, filePath) = MediaStorageHelper.saveVideoToGallery(
                    context = context,
                    sourceVideoFile = recordedFile,
                    resolutionLabel = _uiState.value.videoResolution.label
                )

                val mediaItem = CapturedMediaItem(
                    uri = savedUri ?: Uri.fromFile(recordedFile),
                    galleryUri = savedUri,
                    filePath = filePath,
                    isVideo = true,
                    thumbnailBitmap = thumbnail,
                    aspectRatioLabel = _uiState.value.aspectRatio.label,
                    resolutionLabel = _uiState.value.videoResolution.label,
                    subjectLabel = _uiState.value.activeSubject?.label
                )

                _uiState.update {
                    it.copy(
                        lastCapturedMedia = mediaItem,
                        reviewingMediaItem = mediaItem
                    )
                }

                // Tracking stops only when the final output frame is completed/saved
                unlockTracking()
            }
        }
    }

    fun setCaptureMode(mode: CameraCaptureMode) {
        _uiState.update { it.copy(captureMode = mode) }
    }

    fun setAspectRatio(ratio: TrackingAspectRatio) {
        _uiState.update { it.copy(aspectRatio = ratio) }
    }

    fun cycleAspectRatio() {
        val values = TrackingAspectRatio.values()
        val nextIdx = (values.indexOf(_uiState.value.aspectRatio) + 1) % values.size
        setAspectRatio(values[nextIdx])
    }

    fun setTrackingIntensity(intensity: Float) {
        cropController.trackingIntensity = intensity
        subjectTracker.trackingSpeedIntensity = intensity
        _uiState.update { it.copy(trackingIntensity = intensity) }
    }

    fun setVideoResolution(res: VideoResolution) {
        _uiState.update { it.copy(videoResolution = res) }
    }

    fun setViewfinderResolution(res: ViewfinderResolution) {
        _uiState.update { it.copy(viewfinderResolution = res) }
        cameraXManager?.setViewfinderResolution(res.width, res.height)
    }

    fun toggleGimbal(enabled: Boolean) {
        digitalGimbalEngine.isEnabled = enabled
        _uiState.update { it.copy(isGimbalEnabled = enabled) }
    }

    fun setGimbalSensitivity(sens: Float) {
        digitalGimbalEngine.sensitivity = sens
        _uiState.update { it.copy(gimbalSensitivity = sens) }
    }

    fun startCinematicPan(durationSec: Float) {
        cropController.startCinematicPan(durationSec = durationSec, leftToRight = true) {
            _uiState.update { it.copy(isCinematicPanActive = false, cinematicPanProgress = 0f) }
        }
        _uiState.update {
            it.copy(
                isCinematicPanActive = true,
                cinematicPanDurationSec = durationSec,
                cinematicPanProgress = 0f
            )
        }
    }

    fun stopCinematicPan() {
        cropController.stopCinematicPan()
        _uiState.update { it.copy(isCinematicPanActive = false, cinematicPanProgress = 0f) }
    }

    fun flipCamera() {
        val nextLens = if (_uiState.value.selectedLens.isFront) TrackingCameraLens.WIDE else TrackingCameraLens.FRONT
        setCameraLens(nextLens)
    }

    fun setCameraLens(lens: TrackingCameraLens) {
        _uiState.update { it.copy(selectedLens = lens, isFrontCamera = lens.isFront) }
        cameraXManager?.setLens(lens)
    }

    fun setTrackingFps(fpsOption: TrackingFpsOption) {
        _uiState.update { it.copy(selectedFpsOption = fpsOption) }
        cameraXManager?.setFps(fpsOption)
        startSmoothingLoop()
    }

    fun clearLearnedSubjects() {
        adaptiveLearner?.clearLearnedProfiles()
        _uiState.update { it.copy(learnedSubjectsCount = 0) }
    }

    fun openMediaReview(item: CapturedMediaItem) {
        _uiState.update { it.copy(reviewingMediaItem = item) }
    }

    fun closeMediaReview() {
        _uiState.update { it.copy(reviewingMediaItem = null) }
    }

    fun setSettingsOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isSettingsOpen = isOpen) }
    }

    override fun onCleared() {
        super.onCleared()
        smoothingLoopJob?.cancel()
        recordingTimerJob?.cancel()
        subjectTracker.close()
        digitalGimbalEngine.stop()
        cameraXManager?.release()
    }
}
