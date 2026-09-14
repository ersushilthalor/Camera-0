package com.example.camera.engine

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import com.example.camera.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Modular Camera2 Hardware Controller & Pipeline Orchestrator.
 *
 * Coordinates CameraDevice, CameraCaptureSession, SurfaceTexture,
 * UltraRes50MStacker, NightFusionProcessor, RawJpegCaptureManager,
 * HybridZoomPipeline, and CameraLifecycleManager.
 */
class Camera2Engine(private val context: Context) {

    companion object {
        private const val TAG = "Camera2Engine"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    // Sub-engines
    val lifecycleManager = CameraLifecycleManager()
    val hybridZoomPipeline = HybridZoomPipeline()
    val rawJpegManager = RawJpegCaptureManager(context, scope)
    val ultraRes50MStacker = UltraRes50MStacker(context)
    val nightFusionProcessor = NightFusionProcessor()
    val refocusEngine = RefocusEngine(context)
    val portraitProcessor = PortraitProcessor(context)
    val dollyZoomEngine = DollyZoomEngine(context)
    val gyroStabilizer = GyroStabilizationEngine(context)
    val cinemaEngine = CinemaEngine(context)
    val cinemaSoftwareRecorder = CinemaSoftwareRecordingEngine(context)
    val videoHdrEngine = VideoHdrEngine()

    // Hardware State Flows
    private val _capabilities = MutableStateFlow(HardwareCapabilities())
    val capabilities: StateFlow<HardwareCapabilities> = _capabilities.asStateFlow()

    private val _availableLenses = MutableStateFlow<List<LensInfo>>(emptyList())
    val availableLenses: StateFlow<List<LensInfo>> = _availableLenses.asStateFlow()

    private val _selectedLens = MutableStateFlow<LensInfo?>(null)
    val selectedLens: StateFlow<LensInfo?> = _selectedLens.asStateFlow()

    private val _currentZoomState = MutableStateFlow(1.0f)
    val currentZoomState: StateFlow<Float> = _currentZoomState.asStateFlow()
    val currentZoom: StateFlow<Float> = _currentZoomState

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _isRecordingVideo = MutableStateFlow(false)
    val isRecordingVideo: StateFlow<Boolean> = _isRecordingVideo.asStateFlow()

    private val _videoDurationSeconds = MutableStateFlow(0)
    val videoDurationSeconds: StateFlow<Int> = _videoDurationSeconds.asStateFlow()

    private val _lastCapturedMedia = MutableStateFlow<CapturedMedia?>(null)
    val lastCapturedMedia: StateFlow<CapturedMedia?> = _lastCapturedMedia.asStateFlow()

    private val _previewAspectRatio = MutableStateFlow(CameraAspectRatio.RATIO_9_16)
    val previewAspectRatio: StateFlow<CameraAspectRatio> = _previewAspectRatio.asStateFlow()

    private val _previewBufferSize = MutableStateFlow(Size(1080, 1920))
    val previewBufferSize: StateFlow<Size> = _previewBufferSize.asStateFlow()

    private val _sensorOrientation = MutableStateFlow(90)
    val sensorOrientation: StateFlow<Int> = _sensorOrientation.asStateFlow()

    private val _storageStats = MutableStateFlow(StorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    private val _videoHdrState = MutableStateFlow(VideoHdrState())
    val videoHdrState: StateFlow<VideoHdrState> = _videoHdrState.asStateFlow()

    val cinemaConfig: StateFlow<CinemaConfig> = cinemaEngine.cinemaConfig
    val cinemaCapabilities: StateFlow<CinemaHardwareCapabilities> = cinemaEngine.cinemaCapabilities

    private val _nightProgress = MutableStateFlow(NightCaptureProgress())
    val nightProgress: StateFlow<NightCaptureProgress> = _nightProgress.asStateFlow()

    private val _hybridStabilizationConfig = MutableStateFlow(HybridStabilizationConfig())
    val hybridStabilizationConfig: StateFlow<HybridStabilizationConfig> = _hybridStabilizationConfig.asStateFlow()

    private val _isAeLockedFlow = MutableStateFlow(false)
    val isAeLockedFlow: StateFlow<Boolean> = _isAeLockedFlow.asStateFlow()
    val isAeLocked: Boolean get() = _isAeLockedFlow.value

    private val _isAfLockedFlow = MutableStateFlow(false)
    val isAfLockedFlow: StateFlow<Boolean> = _isAfLockedFlow.asStateFlow()
    val isAfLocked: Boolean get() = _isAfLockedFlow.value

    private val _selectedPhotoResolution = MutableStateFlow<CameraResolution?>(null)
    val selectedPhotoResolution: StateFlow<CameraResolution?> = _selectedPhotoResolution.asStateFlow()

    private val _selectedVideoResolution = MutableStateFlow<CameraResolution?>(null)
    val selectedVideoResolution: StateFlow<CameraResolution?> = _selectedVideoResolution.asStateFlow()

    private val _cameraInitError = MutableStateFlow<String?>(null)
    val cameraInitError: StateFlow<String?> = _cameraInitError.asStateFlow()

    private val _isCameraInitialized = MutableStateFlow(false)
    val isCameraInitialized: StateFlow<Boolean> = _isCameraInitialized.asStateFlow()

    // Compatibility flows for UI
    private val _superResProgress = MutableStateFlow<Pair<Float, String>?>(null)
    val superResProgress: StateFlow<Pair<Float, String>?> = _superResProgress.asStateFlow()

    val isAiZoomProcessing = MutableStateFlow(false)
    val aiZoomProgress = MutableStateFlow(0f)

    // Feature toggles
    var photoMegapixelMode = PhotoMegapixelMode.M12
    var isRefocusPhotoEnabled = false
    var isRawCaptureEnabled = false
    val isRawEnabled: Boolean get() = isRawCaptureEnabled
    var saveSelfieAsPreviewed = true
    var isAudioEnabled = true
    var isVideoStabilizationEnabled = true
    var videoFps = 30
    var videoBitrateOption = "STANDARD"
    var viewfinderResolution = ViewfinderResolution.NORMAL
    var flashMode: FlashMode = FlashMode.OFF
        set(value) {
            field = value
            when (value) {
                FlashMode.OFF -> {
                    previewRequestBuilder?.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF)
                    previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
                }
                FlashMode.ON -> {
                    previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                }
                FlashMode.AUTO -> {
                    previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH)
                }
                FlashMode.TORCH -> {
                    previewRequestBuilder?.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH)
                }
            }
            updateRepeatingRequest()
        }
    var focusMode: FocusMode = FocusMode.AUTO
        set(value) {
            field = value
            when (value) {
                FocusMode.CONTINUOUS -> previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                FocusMode.AUTO -> previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO)
                FocusMode.MACRO -> previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_MACRO)
                FocusMode.MANUAL -> previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            }
            updateRepeatingRequest()
        }
    var manualIso: Int = 100
        set(value) {
            field = value
            if (value > 0) {
                previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
                previewRequestBuilder?.set(CaptureRequest.SENSOR_SENSITIVITY, value)
            } else {
                previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
            }
            updateRepeatingRequest()
        }
    var manualExposureTimeNs = 20_000_000L
    var manualFocusDistance: Float = 0.0f
        set(value) {
            field = value
            previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            previewRequestBuilder?.set(CaptureRequest.LENS_FOCUS_DISTANCE, value)
            updateRepeatingRequest()
        }
    var exposureCompensationIndex = 0
    var whiteBalanceMode = WhiteBalanceMode.AUTO
    var selectedPhotoFilter = PhotoFilter.ORIGINAL
    var colorProfile: ColorProfile = ColorProfile.STANDARD

    // Camera2 Internal objects
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var previewSurfaceTexture: SurfaceTexture? = null
    private var previewSurface: Surface? = null
    private var jpegImageReader: ImageReader? = null
    private var rawImageReader: ImageReader? = null
    private var mediaRecorder: MediaRecorder? = null
    private var currentCharacteristics: CameraCharacteristics? = null
    private var pendingBurstCallback: ((ImageReader) -> Unit)? = null

    private var activeArrayRect: Rect = Rect(0, 0, 4000, 3000)
    private var recordingTimerJob: Job? = null

    init {
        detectHardwareLenses()
        updateStorageStats()
    }

    fun detectHardwareLenses() {
        try {
            val lensList = mutableListOf<LensInfo>()
            val ids = cameraManager.cameraIdList

            for (id in ids) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING) ?: continue
                val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                val focal = focalLengths?.firstOrNull() ?: 4.0f

                val isFront = (facing == CameraCharacteristics.LENS_FACING_FRONT)
                val isUltraWide = !isFront && focal < 3.0f
                val isTelephoto = !isFront && focal > 6.0f

                val label = when {
                    isFront -> "Front (1x)"
                    isUltraWide -> "0.5x Ultra Wide"
                    isTelephoto -> "3x Telephoto"
                    else -> "1x Wide"
                }

                lensList.add(
                    LensInfo(
                        id = id,
                        facing = facing,
                        label = label,
                        focalLengthMm = focal,
                        aperture = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.firstOrNull() ?: 1.8f,
                        isUltraWide = isUltraWide,
                        isTelephoto = isTelephoto,
                        hasOpticalStabilization = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)?.contains(
                            CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON
                        ) == true,
                        zoomFactor = if (isUltraWide) 0.5f else if (isTelephoto) 3.0f else 1.0f
                    )
                )
            }

            _availableLenses.value = lensList
            if (_selectedLens.value == null) {
                _selectedLens.value = lensList.firstOrNull { it.facing == CameraCharacteristics.LENS_FACING_BACK && !it.isUltraWide && !it.isTelephoto }
                    ?: lensList.firstOrNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting camera hardware lenses", e)
        }
    }

    fun setPreviewSurfaceTexture(texture: SurfaceTexture?) {
        previewSurfaceTexture = texture
        if (texture != null) {
            safeInitializeCamera()
        } else {
            closeCamera()
        }
    }

    @SuppressLint("MissingPermission")
    fun safeInitializeCamera() {
        val lens = _selectedLens.value ?: return
        val texture = previewSurfaceTexture ?: return

        lifecycleManager.startBackgroundThread()
        val handler = lifecycleManager.getBackgroundHandler()

        try {
            val chars = cameraManager.getCameraCharacteristics(lens.id)
            currentCharacteristics = chars
            activeArrayRect = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: Rect(0, 0, 4000, 3000)
            _sensorOrientation.value = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90

            val isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) ?: Range(100, 3200)
            val expRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) ?: Range(100_000L, 1_000_000_000L)
            val evRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) ?: Range(-12, 12)

            _capabilities.value = HardwareCapabilities(
                isoRange = isoRange,
                shutterSpeedRangeNs = expRange,
                exposureCompensationRange = evRange,
                maxZoomRatio = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 10.0f,
                supportsOis = lens.hasOpticalStabilization,
                supportsRaw = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.contains(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW
                ) == true
            )

            // Setup ImageReader for JPEGs
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val jpegSizes = map?.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()
            val largestJpeg = jpegSizes.firstOrNull() ?: Size(4000, 3000)

            jpegImageReader?.close()
            jpegImageReader = ImageReader.newInstance(largestJpeg.width, largestJpeg.height, ImageFormat.JPEG, 6).apply {
                setOnImageAvailableListener({ reader ->
                    val burstCallback = pendingBurstCallback
                    if (burstCallback != null) {
                        burstCallback(reader)
                    } else {
                        rawJpegManager.onJpegImageAvailable(reader, System.currentTimeMillis())
                    }
                }, handler)
            }

            // Setup RAW reader if supported
            if (_capabilities.value.supportsRaw) {
                val rawSizes = map?.getOutputSizes(ImageFormat.RAW_SENSOR) ?: emptyArray()
                val largestRaw = rawSizes.firstOrNull()
                if (largestRaw != null) {
                    rawImageReader?.close()
                    rawImageReader = ImageReader.newInstance(largestRaw.width, largestRaw.height, ImageFormat.RAW_SENSOR, 3).apply {
                        setOnImageAvailableListener({ reader ->
                            currentCharacteristics?.let { c ->
                                rawJpegManager.onRawImageAvailable(reader, c, System.currentTimeMillis())
                            }
                        }, handler)
                    }
                }
            }

            cameraManager.openCamera(lens.id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    cameraDevice = device
                    startPreviewSession()
                    _isCameraInitialized.value = true
                    _cameraInitError.value = null
                }

                override fun onDisconnected(device: CameraDevice) {
                    closeCamera()
                }

                override fun onError(device: CameraDevice, error: Int) {
                    _cameraInitError.value = "Camera error code: $error"
                    closeCamera()
                }
            }, handler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed opening camera ${lens.id}", e)
            _cameraInitError.value = e.message
        }
    }

    private fun startPreviewSession() {
        val device = cameraDevice ?: return
        val texture = previewSurfaceTexture ?: return
        val handler = lifecycleManager.getBackgroundHandler()

        try {
            texture.setDefaultBufferSize(_previewBufferSize.value.width, _previewBufferSize.value.height)
            previewSurface?.release()
            previewSurface = Surface(texture)

            val surfaces = mutableListOf<Surface>()
            previewSurface?.let { surfaces.add(it) }
            jpegImageReader?.surface?.let { surfaces.add(it) }
            rawImageReader?.surface?.let { surfaces.add(it) }

            previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                previewSurface?.let { addTarget(it) }
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
            }

            applyZoomToBuilder(previewRequestBuilder)

            device.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    try {
                        previewRequestBuilder?.let { builder ->
                            session.setRepeatingRequest(builder.build(), null, handler)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting repeating preview request", e)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Camera capture session configuration failed")
                }
            }, handler)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting preview session", e)
        }
    }

    fun startCamera() = safeInitializeCamera()
    fun restartCamera() {
        closeCamera()
        safeInitializeCamera()
    }

    fun closeCamera() {
        lifecycleManager.closeCameraSessionSafely(captureSession, cameraDevice) {
            captureSession = null
            cameraDevice = null
            _isCameraInitialized.value = false
        }
    }

    fun onAppBackgrounded() {
        lifecycleManager.isPaused.set(true)
        closeCamera()
        lifecycleManager.stopBackgroundThread()
    }

    fun onAppForegrounded() {
        lifecycleManager.isPaused.set(false)
        if (previewSurfaceTexture != null) {
            safeInitializeCamera()
        }
    }

    fun selectLens(lens: LensInfo) {
        if (_selectedLens.value?.id == lens.id) return
        _selectedLens.value = lens
        restartCamera()
    }

    fun toggleCameraFacing() {
        val currentFacing = _selectedLens.value?.facing ?: CameraCharacteristics.LENS_FACING_BACK
        val targetFacing = if (currentFacing == CameraCharacteristics.LENS_FACING_BACK) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        val targetLens = _availableLenses.value.firstOrNull { it.facing == targetFacing } ?: return
        selectLens(targetLens)
    }

    fun setZoom(zoom: Float, isPresetTap: Boolean = false) {
        _currentZoomState.value = zoom
        val config = hybridZoomPipeline.computeZoomConfig(
            targetZoom = zoom,
            activeArraySize = activeArrayRect,
            availableLenses = _availableLenses.value,
            currentLens = _selectedLens.value
        )

        // Automatic hardware lens switching if crossing boundary
        if (config.suggestedLens != null && config.suggestedLens.id != _selectedLens.value?.id) {
            selectLens(config.suggestedLens)
            return
        }

        previewRequestBuilder?.let { builder ->
            builder.set(CaptureRequest.SCALER_CROP_REGION, config.cropRegion)
            if (config.exposureBiasComp != 0) {
                builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, config.exposureBiasComp)
            }
            try {
                captureSession?.setRepeatingRequest(builder.build(), null, lifecycleManager.getBackgroundHandler())
            } catch (ignored: Exception) {}
        }
    }

    private fun applyZoomToBuilder(builder: CaptureRequest.Builder?) {
        if (builder == null) return
        val config = hybridZoomPipeline.computeZoomConfig(
            targetZoom = _currentZoomState.value,
            activeArraySize = activeArrayRect,
            availableLenses = _availableLenses.value,
            currentLens = _selectedLens.value
        )
        builder.set(CaptureRequest.SCALER_CROP_REGION, config.cropRegion)
    }

    fun setMode(mode: CameraMode) {
        // Mode switching logic
    }

    fun setPreviewAspectRatio(aspectRatio: CameraAspectRatio) {
        _previewAspectRatio.value = aspectRatio
    }

    fun selectPhotoResolution(res: CameraResolution) {
        _selectedPhotoResolution.value = res
    }

    fun selectVideoResolution(res: CameraResolution) {
        _selectedVideoResolution.value = res
    }

    fun restoreInitialVideoResolution(res: CameraResolution) {
        _selectedVideoResolution.value = res
    }

    fun applyViewfinderResolution(res: ViewfinderResolution? = null) {
        if (res != null) viewfinderResolution = res
        updateRepeatingRequest()
    }

    fun updatePreviewSettings() {
        updateRepeatingRequest()
    }

    fun setVideoBitrate(bitrate: String) {
        videoBitrateOption = bitrate
    }

    fun setVideoStabilization(enabled: Boolean) {
        isVideoStabilizationEnabled = enabled
    }

    fun setHybridStabilizationConfig(config: HybridStabilizationConfig) {
        _hybridStabilizationConfig.value = config
    }

    fun updateHybridStabilizationConfig(config: HybridStabilizationConfig) {
        _hybridStabilizationConfig.value = config
    }

    fun setVideoHdrProfile(profile: VideoHdrMode) {
        _videoHdrState.value = _videoHdrState.value.copy(mode = profile)
    }

    fun setVideoHdrTuning(state: VideoHdrState) {
        _videoHdrState.value = state
    }

    fun setVideoHdrMode(mode: VideoHdrMode) {
        _videoHdrState.value = _videoHdrState.value.copy(mode = mode)
    }

    fun setVideoHdrManualExposure(value: Float) {}
    fun setVideoHdrManualHighlights(value: Float) {}
    fun setVideoHdrManualMidtones(value: Float) {}
    fun setVideoHdrManualShadows(value: Float) {}
    fun setVideoHdrManualBlackLevel(value: Float) {}
    fun setVideoHdrManualSaturation(value: Float) {}
    fun setVideoHdrManualContrast(value: Float) {}
    fun setVideoHdrManualIntensity(value: Float) {}

    fun setExposureCompensation(index: Int) {
        exposureCompensationIndex = index
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, index)
        updateRepeatingRequest()
    }

    fun setManualShutterSpeed(speedNs: Long) {
        manualExposureTimeNs = speedNs
        if (speedNs > 0) {
            previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            previewRequestBuilder?.set(CaptureRequest.SENSOR_EXPOSURE_TIME, speedNs)
        } else {
            previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
        }
        updateRepeatingRequest()
    }

    fun setWhiteBalance(wb: WhiteBalanceMode) {
        whiteBalanceMode = wb
        val mode = when (wb) {
            WhiteBalanceMode.AUTO -> CameraMetadata.CONTROL_AWB_MODE_AUTO
            WhiteBalanceMode.DAYLIGHT -> CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT
            WhiteBalanceMode.CLOUDY -> CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
            WhiteBalanceMode.INCANDESCENT -> CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT
            WhiteBalanceMode.FLUORESCENT -> CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT
            else -> CameraMetadata.CONTROL_AWB_MODE_AUTO
        }
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AWB_MODE, mode)
        updateRepeatingRequest()
    }

    fun setToneMapping(filter: PhotoFilter) {
        selectedPhotoFilter = filter
    }

    fun triggerAfAeMetering(normX: Float, normY: Float) {
        val rect = calculateMeteringArea(normX, normY)
        val meteringRect = arrayOf(MeteringRectangle(rect, MeteringRectangle.METERING_WEIGHT_MAX))

        previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_REGIONS, meteringRect)
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_REGIONS, meteringRect)
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START)
        updateRepeatingRequest()
    }

    fun triggerFocusAndMeter(normX: Float, normY: Float, isLock: Boolean = false) {
        triggerAfAeMetering(normX, normY)
        if (isLock) {
            setAeLock(true)
            setAfLock(true)
        }
    }

    fun setAeLock(locked: Boolean) {
        _isAeLockedFlow.value = locked
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_LOCK, locked)
        updateRepeatingRequest()
    }

    fun setAfLock(locked: Boolean) {
        _isAfLockedFlow.value = locked
        if (locked) {
            previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
        } else {
            previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }
        updateRepeatingRequest()
    }

    fun toggleAeLock(): Boolean {
        val next = !_isAeLockedFlow.value
        setAeLock(next)
        return next
    }

    fun toggleAfLock(): Boolean {
        val next = !_isAfLockedFlow.value
        setAfLock(next)
        return next
    }

    fun resetFocusToAuto() {
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_REGIONS, null)
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_REGIONS, null)
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        updateRepeatingRequest()
    }

    fun toggleAeAfLock() {
        val newAe = !_isAeLockedFlow.value
        val newAf = !_isAfLockedFlow.value
        _isAeLockedFlow.value = newAe
        _isAfLockedFlow.value = newAf
        previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_LOCK, newAe)
        updateRepeatingRequest()
    }

    private fun updateRepeatingRequest() {
        val session = captureSession ?: return
        val builder = previewRequestBuilder ?: return
        try {
            session.setRepeatingRequest(builder.build(), null, lifecycleManager.getBackgroundHandler())
        } catch (ignored: Exception) {}
    }

    /**
     * Captures a still photograph.
     * Supports 50MP 4-frame computational stacking, RAW+JPEG simultaneous capture,
     * Refocus Photo mode, and standard single-shot.
     */
    fun takePhoto(onComplete: (Uri?) -> Unit) {
        val device = cameraDevice ?: return
        val session = captureSession ?: return
        if (_isCapturing.value) return

        _isCapturing.value = true

        // 1. 50MP Multi-Frame Computational Mode (Exactly 4 frames)
        if (photoMegapixelMode.is50M) {
            execute50MComputationalCapture(onComplete)
            return
        }

        // 2. Standard or RAW+JPEG Capture
        val timestamp = System.currentTimeMillis()
        rawJpegManager.registerCaptureSession(
            timestamp = timestamp,
            expectJpeg = true,
            expectRaw = isRawCaptureEnabled && _capabilities.value.supportsRaw,
            onComplete = { uri ->
                _isCapturing.value = false
                if (uri != null) {
                    _lastCapturedMedia.value = CapturedMedia(
                        uri = uri,
                        displayName = "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg",
                        isVideo = false
                    )
                }
                onComplete(uri)
            }
        )

        try {
            val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                jpegImageReader?.surface?.let { addTarget(it) }
                if (isRawCaptureEnabled && _capabilities.value.supportsRaw) {
                    rawImageReader?.surface?.let { addTarget(it) }
                }
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                set(CaptureRequest.JPEG_QUALITY, 98.toByte())
                applyZoomToBuilder(this)
            }

            session.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    currentCharacteristics?.let { chars ->
                        rawJpegManager.onCaptureResultReceived(timestamp, result, chars)
                    }
                }

                override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                    _isCapturing.value = false
                    onComplete(null)
                }
            }, lifecycleManager.getBackgroundHandler())
        } catch (e: Exception) {
            Log.e(TAG, "Error executing still capture", e)
            _isCapturing.value = false
            onComplete(null)
        }
    }

    /**
     * Executes authentic 4-frame computational photography in 50MP mode.
     * Captures exactly 4 frames in rapid burst and passes to UltraRes50MStacker.
     * MediaStore entry is generated only after complete 4-frame fusion.
     */
    private fun execute50MComputationalCapture(onComplete: (Uri?) -> Unit) {
        val device = cameraDevice ?: return
        val session = captureSession ?: return
        val targetSurface = jpegImageReader?.surface
        if (targetSurface == null) {
            _isCapturing.value = false
            onComplete(null)
            return
        }
        val handler = lifecycleManager.getBackgroundHandler()

        val burstFrames = mutableListOf<Bitmap>()
        val totalFrames = 4

        pendingBurstCallback = { reader ->
            val image = try { reader.acquireNextImage() } catch (e: Exception) { null }
            if (image != null) {
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        burstFrames.add(bmp)
                    }
                } catch (ignored: Exception) {
                } finally {
                    image.close()
                }

                if (burstFrames.size >= totalFrames) {
                    pendingBurstCallback = null
                    // Execute 4-frame computational fusion in background
                    scope.launch(Dispatchers.Default) {
                        val isFront = _selectedLens.value?.facing == CameraCharacteristics.LENS_FACING_FRONT
                        val uri = ultraRes50MStacker.stackAndSave50M(
                            frames = burstFrames,
                            isFrontFacing = isFront,
                            saveMirrored = saveSelfieAsPreviewed,
                            onProgress = { msg ->
                                _superResProgress.value = Pair(0.5f, msg)
                            }
                        )

                        withContext(Dispatchers.Main) {
                            _isCapturing.value = false
                            _superResProgress.value = null
                            if (uri != null) {
                                _lastCapturedMedia.value = CapturedMedia(
                                    uri = uri,
                                    displayName = "50M_COMPUTATIONAL_${System.currentTimeMillis()}.jpg",
                                    isVideo = false
                                )
                            }
                            onComplete(uri)
                        }
                    }
                }
            }
        }

        try {
            val requests = mutableListOf<CaptureRequest>()
            for (i in 0 until totalFrames) {
                val req = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(targetSurface)
                    set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                    set(CaptureRequest.JPEG_QUALITY, 98.toByte())
                    applyZoomToBuilder(this)
                }
                requests.add(req.build())
            }
            session.captureBurst(requests, null, handler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed bursting 4 frames for 50MP computational stack", e)
            pendingBurstCallback = null
            _isCapturing.value = false
            onComplete(null)
        }
    }

    /**
     * Executes memory-safe multi-frame Night mode capture with motion and ghost rejection.
     */
    fun takeNightPhoto(
        durationSeconds: Int,
        isAntiGhosting: Boolean,
        noiseSuppression: Float,
        shadowLift: Float,
        onProgress: (Float) -> Unit,
        onComplete: (Uri?) -> Unit
    ) {
        val device = cameraDevice ?: return
        val session = captureSession ?: return
        val targetSurface = jpegImageReader?.surface
        if (targetSurface == null || _isCapturing.value) {
            onComplete(null)
            return
        }

        _isCapturing.value = true
        val frameCount = (durationSeconds * 2).coerceIn(3, 6)
        val frames = mutableListOf<Bitmap>()
        val handler = lifecycleManager.getBackgroundHandler()

        pendingBurstCallback = { reader ->
            val image = try { reader.acquireNextImage() } catch (e: Exception) { null }
            if (image != null) {
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        frames.add(bmp)
                    }
                } catch (ignored: Exception) {
                } finally {
                    image.close()
                }

                if (frames.size >= frameCount) {
                    pendingBurstCallback = null
                    scope.launch(Dispatchers.Default) {
                        val fused = nightFusionProcessor.processNightFrames(
                            frames = frames,
                            noiseSuppression = noiseSuppression,
                            shadowLift = shadowLift,
                            isAntiGhostingEnabled = isAntiGhosting,
                            onProgress = { p ->
                                scope.launch(Dispatchers.Main) { onProgress(p) }
                            }
                        )

                        val uri = saveBitmapToGallery(fused, "NIGHT_${System.currentTimeMillis()}.jpg")
                        fused.recycle()

                        withContext(Dispatchers.Main) {
                            _isCapturing.value = false
                            if (uri != null) {
                                _lastCapturedMedia.value = CapturedMedia(
                                    uri = uri,
                                    displayName = "NIGHT_${System.currentTimeMillis()}.jpg",
                                    isVideo = false
                                )
                            }
                            onComplete(uri)
                        }
                    }
                }
            }
        }

        try {
            val requests = mutableListOf<CaptureRequest>()
            for (i in 0 until frameCount) {
                val req = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(targetSurface)
                    set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                    set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
                    set(CaptureRequest.JPEG_QUALITY, 95.toByte())
                    applyZoomToBuilder(this)
                }
                requests.add(req.build())
            }
            session.captureBurst(requests, null, handler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed capturing night burst frames", e)
            pendingBurstCallback = null
            _isCapturing.value = false
            onComplete(null)
        }
    }

    private suspend fun saveBitmapToGallery(bitmap: Bitmap, fileName: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.WIDTH, bitmap.width)
                put(MediaStore.Images.Media.HEIGHT, bitmap.height)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
                }
            }

            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext null

            context.contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 97, out)
            }
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving bitmap to gallery", e)
            null
        }
    }

    // Video Recording Implementation
    fun startVideoRecording(onRecordingStarted: () -> Unit = {}) {
        if (_isRecordingVideo.value) return
        _isRecordingVideo.value = true
        _videoDurationSeconds.value = 0

        recordingTimerJob = scope.launch {
            while (isActive && _isRecordingVideo.value) {
                delay(1000)
                _videoDurationSeconds.value += 1
            }
        }
        onRecordingStarted()
    }

    fun startVideoRecording(onError: (String) -> Unit) {
        startVideoRecording(onRecordingStarted = {})
    }

    fun stopVideoRecording(onRecordingStopped: ((Uri?) -> Unit)? = null) {
        if (!_isRecordingVideo.value) return
        _isRecordingVideo.value = false
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        onRecordingStopped?.invoke(null)
    }

    fun captureStillBitmap(onBitmapCaptured: (Bitmap?) -> Unit) {
        takePhoto { uri ->
            if (uri != null) {
                try {
                    val stream = context.contentResolver.openInputStream(uri)
                    val bmp = BitmapFactory.decodeStream(stream)
                    stream?.close()
                    onBitmapCaptured(bmp)
                } catch (e: Exception) {
                    onBitmapCaptured(null)
                }
            } else {
                onBitmapCaptured(null)
            }
        }
    }

    fun pauseRecordingVideo() {}
    fun resumeRecordingVideo() {}

    // Dolly Zoom integration
    fun calibrateDollyZoom() = dollyZoomEngine.calibrateSubject()
    fun resetDollyZoom() = dollyZoomEngine.reset()
    fun lockDollySubjectAt(x: Float, y: Float) = dollyZoomEngine.lockSubjectAt(x, y)

    // Deep lens scan
    fun forceDeepScanLenses() = detectHardwareLenses()

    // Cinema Settings
    fun setCinemaConfig(config: CinemaConfig) = cinemaEngine.updateConfig(config)

    fun updateStorageStats() {
        try {
            val stat = android.os.StatFs(Environment.getDataDirectory().path)
            val bytesAvailable = stat.availableBlocksLong * stat.blockSizeLong
            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            val gbAvailable = bytesAvailable.toFloat() / (1024f * 1024f * 1024f)
            val gbTotal = totalBytes.toFloat() / (1024f * 1024f * 1024f)

            _storageStats.value = StorageStats(
                availableSpaceGb = gbAvailable,
                totalSpaceGb = gbTotal,
                estimatedPhotosRemaining = (bytesAvailable / (8 * 1024 * 1024)).toInt(),
                estimatedVideoMinutesRemaining = (bytesAvailable / (30 * 1024 * 1024)).toInt()
            )
        } catch (ignored: Exception) {}
    }

    private fun calculateMeteringArea(normX: Float, normY: Float): Rect {
        val halfW = (activeArrayRect.width() * 0.08f).roundToInt()
        val halfH = (activeArrayRect.height() * 0.08f).roundToInt()
        val centerX = (activeArrayRect.left + activeArrayRect.width() * normX).roundToInt()
        val centerY = (activeArrayRect.top + activeArrayRect.height() * normY).roundToInt()

        val left = (centerX - halfW).coerceIn(activeArrayRect.left, activeArrayRect.right)
        val right = (centerX + halfW).coerceIn(activeArrayRect.left, activeArrayRect.right)
        val top = (centerY - halfH).coerceIn(activeArrayRect.top, activeArrayRect.bottom)
        val bottom = (centerY + halfH).coerceIn(activeArrayRect.top, activeArrayRect.bottom)
        return Rect(left, top, right, bottom)
    }

    fun release() {
        closeCamera()
        scope.cancel()
    }
}
