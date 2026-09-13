package com.example.camera.data

import android.content.Context
import android.content.SharedPreferences
import com.example.camera.model.*

/**
 * Persists user camera and video settings across sessions.
 */
class CameraPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pro_camera_user_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CAMERA_MODE = "pref_camera_mode"
        private const val KEY_FLASH_MODE = "pref_flash_mode"
        private const val KEY_TIMER_MODE = "pref_timer_mode"
        private const val KEY_GRID_TYPE = "pref_grid_type"
        private const val KEY_RAW_ENABLED = "pref_raw_enabled"
        private const val KEY_VIDEO_QUALITY = "pref_video_quality" // e.g. "4K 30", "1080p 30", "1080p 60", "720p 30"
        private const val KEY_VIDEO_WIDTH = "pref_video_width"
        private const val KEY_VIDEO_HEIGHT = "pref_video_height"
        private const val KEY_VIDEO_FPS = "pref_video_fps"
        private const val KEY_VIDEO_BITRATE = "pref_video_bitrate"
        private const val KEY_VIDEO_STABILIZATION = "pref_video_stabilization"
        private const val KEY_AUDIO_ENABLED = "pref_audio_enabled"
        private const val KEY_COLOR_PROFILE = "pref_color_profile"
        private const val KEY_WHITE_BALANCE = "pref_white_balance"
        private const val KEY_FOCUS_MODE = "pref_focus_mode"
        private const val KEY_LAST_FACING = "pref_last_facing"
        private const val KEY_PORTRAIT_BLUR = "pref_portrait_blur"
        private const val KEY_PORTRAIT_APERTURE = "pref_portrait_aperture"
        private const val KEY_SAVE_SELFIE_AS_PREVIEWED = "pref_save_selfie_as_previewed"
        private const val KEY_VIDEO_HDR_MODE = "pref_video_hdr_mode"
        private const val KEY_VIDEO_HDR_INTENSITY = "pref_video_hdr_intensity"
        private const val KEY_VIDEO_HDR_SHADOWS = "pref_video_hdr_shadows"
        private const val KEY_VIDEO_HDR_HIGHLIGHTS = "pref_video_hdr_highlights"
        private const val KEY_VIDEO_HDR_CONTRAST = "pref_video_hdr_contrast"
        private const val KEY_VIDEO_HDR_EXPOSURE = "pref_video_hdr_exposure"
        private const val KEY_VIDEO_HDR_BLACK_LEVEL = "pref_video_hdr_black_level"
        private const val KEY_VIDEO_HDR_MIDTONES = "pref_video_hdr_midtones"
        private const val KEY_VIDEO_HDR_SATURATION = "pref_video_hdr_saturation"
        private const val KEY_VIEWFINDER_RESOLUTION = "pref_viewfinder_resolution"
        private const val KEY_REFOCUS_PHOTO_ENABLED = "pref_refocus_photo_enabled"
        private const val KEY_AI_ZOOM_ENABLED = "pref_ai_zoom_enabled"
        private const val KEY_AI_ZOOM_QUALITY = "pref_ai_zoom_quality"
    }

    var isAiZoomEnabled: Boolean
        get() = prefs.getBoolean(KEY_AI_ZOOM_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AI_ZOOM_ENABLED, value).apply()

    var aiZoomQuality: com.example.camera.dbsr.AiZoomQuality
        get() {
            val name = prefs.getString(KEY_AI_ZOOM_QUALITY, com.example.camera.dbsr.AiZoomQuality.AUTO.name)
                ?: com.example.camera.dbsr.AiZoomQuality.AUTO.name
            return try { com.example.camera.dbsr.AiZoomQuality.valueOf(name) } catch (e: Exception) { com.example.camera.dbsr.AiZoomQuality.AUTO }
        }
        set(value) = prefs.edit().putString(KEY_AI_ZOOM_QUALITY, value.name).apply()

    var isRefocusPhotoEnabled: Boolean
        get() = prefs.getBoolean(KEY_REFOCUS_PHOTO_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_REFOCUS_PHOTO_ENABLED, value).apply()

    var cameraMode: CameraMode
        get() {
            val name = prefs.getString(KEY_CAMERA_MODE, CameraMode.PHOTO.name) ?: CameraMode.PHOTO.name
            return try { CameraMode.valueOf(name) } catch (e: Exception) { CameraMode.PHOTO }
        }
        set(value) = prefs.edit().putString(KEY_CAMERA_MODE, value.name).apply()

    var flashMode: FlashMode
        get() {
            val name = prefs.getString(KEY_FLASH_MODE, FlashMode.OFF.name) ?: FlashMode.OFF.name
            return try { FlashMode.valueOf(name) } catch (e: Exception) { FlashMode.OFF }
        }
        set(value) = prefs.edit().putString(KEY_FLASH_MODE, value.name).apply()

    var timerMode: TimerMode
        get() {
            val name = prefs.getString(KEY_TIMER_MODE, TimerMode.OFF.name) ?: TimerMode.OFF.name
            return try { TimerMode.valueOf(name) } catch (e: Exception) { TimerMode.OFF }
        }
        set(value) = prefs.edit().putString(KEY_TIMER_MODE, value.name).apply()

    var gridType: GridType
        get() {
            val name = prefs.getString(KEY_GRID_TYPE, GridType.NONE.name) ?: GridType.NONE.name
            return try { GridType.valueOf(name) } catch (e: Exception) { GridType.NONE }
        }
        set(value) = prefs.edit().putString(KEY_GRID_TYPE, value.name).apply()

    var isRawEnabled: Boolean
        get() = prefs.getBoolean(KEY_RAW_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_RAW_ENABLED, value).apply()

    var videoWidth: Int
        get() = prefs.getInt(KEY_VIDEO_WIDTH, 3840) // Default 4K if supported, will gracefully adapt
        set(value) = prefs.edit().putInt(KEY_VIDEO_WIDTH, value).apply()

    var videoHeight: Int
        get() = prefs.getInt(KEY_VIDEO_HEIGHT, 2160)
        set(value) = prefs.edit().putInt(KEY_VIDEO_HEIGHT, value).apply()

    var videoFps: Int
        get() = prefs.getInt(KEY_VIDEO_FPS, 30)
        set(value) = prefs.edit().putInt(KEY_VIDEO_FPS, value).apply()

    var videoBitrate: VideoBitrateOption
        get() {
            val name = prefs.getString(KEY_VIDEO_BITRATE, VideoBitrateOption.AUTO.name) ?: VideoBitrateOption.AUTO.name
            return try { VideoBitrateOption.valueOf(name) } catch (e: Exception) { VideoBitrateOption.AUTO }
        }
        set(value) = prefs.edit().putString(KEY_VIDEO_BITRATE, value.name).apply()

    var isVideoStabilizationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIDEO_STABILIZATION, true)
        set(value) = prefs.edit().putBoolean(KEY_VIDEO_STABILIZATION, value).apply()

    var isAudioEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUDIO_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUDIO_ENABLED, value).apply()

    var colorProfile: ColorProfile
        get() {
            val name = prefs.getString(KEY_COLOR_PROFILE, ColorProfile.STANDARD.name) ?: ColorProfile.STANDARD.name
            return try { ColorProfile.valueOf(name) } catch (e: Exception) { ColorProfile.STANDARD }
        }
        set(value) = prefs.edit().putString(KEY_COLOR_PROFILE, value.name).apply()

    var whiteBalance: WhiteBalanceMode
        get() {
            val name = prefs.getString(KEY_WHITE_BALANCE, WhiteBalanceMode.AUTO.name) ?: WhiteBalanceMode.AUTO.name
            return try { WhiteBalanceMode.valueOf(name) } catch (e: Exception) { WhiteBalanceMode.AUTO }
        }
        set(value) = prefs.edit().putString(KEY_WHITE_BALANCE, value.name).apply()

    var focusMode: FocusMode
        get() {
            val name = prefs.getString(KEY_FOCUS_MODE, FocusMode.CONTINUOUS.name) ?: FocusMode.CONTINUOUS.name
            return try { FocusMode.valueOf(name) } catch (e: Exception) { FocusMode.CONTINUOUS }
        }
        set(value) = prefs.edit().putString(KEY_FOCUS_MODE, value.name).apply()

    var lastFacing: Int
        get() = prefs.getInt(KEY_LAST_FACING, android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK)
        set(value) = prefs.edit().putInt(KEY_LAST_FACING, value).apply()

    var portraitBlurStrength: Float
        get() = prefs.getFloat(KEY_PORTRAIT_BLUR, 60f)
        set(value) = prefs.edit().putFloat(KEY_PORTRAIT_BLUR, value).apply()

    var portraitAperture: String
        get() = prefs.getString(KEY_PORTRAIT_APERTURE, "f/1.4") ?: "f/1.4"
        set(value) = prefs.edit().putString(KEY_PORTRAIT_APERTURE, value).apply()

    var saveSelfieAsPreviewed: Boolean
        get() = prefs.getBoolean(KEY_SAVE_SELFIE_AS_PREVIEWED, true)
        set(value) = prefs.edit().putBoolean(KEY_SAVE_SELFIE_AS_PREVIEWED, value).apply()

    var videoHdrMode: com.example.camera.model.VideoHdrMode
        get() {
            val name = prefs.getString(KEY_VIDEO_HDR_MODE, com.example.camera.model.VideoHdrMode.AUTO.name)
                ?: com.example.camera.model.VideoHdrMode.AUTO.name
            return try { com.example.camera.model.VideoHdrMode.valueOf(name) } catch (e: Exception) { com.example.camera.model.VideoHdrMode.AUTO }
        }
        set(value) = prefs.edit().putString(KEY_VIDEO_HDR_MODE, value.name).apply()

    var videoHdrManualIntensity: Int
        get() = prefs.getInt(KEY_VIDEO_HDR_INTENSITY, 50)
        set(value) = prefs.edit().putInt(KEY_VIDEO_HDR_INTENSITY, value.coerceIn(0, 100)).apply()

    var videoHdrManualShadows: Int
        get() = prefs.getInt(KEY_VIDEO_HDR_SHADOWS, 50)
        set(value) = prefs.edit().putInt(KEY_VIDEO_HDR_SHADOWS, value.coerceIn(0, 100)).apply()

    var videoHdrManualHighlights: Int
        get() = prefs.getInt(KEY_VIDEO_HDR_HIGHLIGHTS, 50)
        set(value) = prefs.edit().putInt(KEY_VIDEO_HDR_HIGHLIGHTS, value.coerceIn(0, 100)).apply()

    var videoHdrManualContrast: Int
        get() = prefs.getInt(KEY_VIDEO_HDR_CONTRAST, 50)
        set(value) = prefs.edit().putInt(KEY_VIDEO_HDR_CONTRAST, value.coerceIn(0, 100)).apply()

    var videoHdrManualExposure: Int
        get() = prefs.getInt(KEY_VIDEO_HDR_EXPOSURE, 50)
        set(value) = prefs.edit().putInt(KEY_VIDEO_HDR_EXPOSURE, value.coerceIn(0, 100)).apply()

    var videoHdrManualBlackLevel: Int
        get() = prefs.getInt(KEY_VIDEO_HDR_BLACK_LEVEL, 50)
        set(value) = prefs.edit().putInt(KEY_VIDEO_HDR_BLACK_LEVEL, value.coerceIn(0, 100)).apply()

    var videoHdrManualMidtones: Int
        get() = prefs.getInt(KEY_VIDEO_HDR_MIDTONES, 50)
        set(value) = prefs.edit().putInt(KEY_VIDEO_HDR_MIDTONES, value.coerceIn(0, 100)).apply()

    var videoHdrManualSaturation: Int
        get() = prefs.getInt(KEY_VIDEO_HDR_SATURATION, 50)
        set(value) = prefs.edit().putInt(KEY_VIDEO_HDR_SATURATION, value.coerceIn(0, 100)).apply()

    var viewfinderResolution: com.example.camera.model.ViewfinderResolution
        get() {
            val name = prefs.getString(KEY_VIEWFINDER_RESOLUTION, com.example.camera.model.ViewfinderResolution.NORMAL.name)
                ?: com.example.camera.model.ViewfinderResolution.NORMAL.name
            return try { com.example.camera.model.ViewfinderResolution.valueOf(name) } catch (e: Exception) { com.example.camera.model.ViewfinderResolution.NORMAL }
        }
        set(value) = prefs.edit().putString(KEY_VIEWFINDER_RESOLUTION, value.name).apply()

    // Photo Megapixel Mode (12M vs 50M)
    var photoMegapixelMode: com.example.camera.model.PhotoMegapixelMode
        get() {
            val name = prefs.getString("pref_photo_mp_mode", com.example.camera.model.PhotoMegapixelMode.M12.name)
                ?: com.example.camera.model.PhotoMegapixelMode.M12.name
            return try { com.example.camera.model.PhotoMegapixelMode.valueOf(name) } catch (e: Exception) { com.example.camera.model.PhotoMegapixelMode.M12 }
        }
        set(value) = prefs.edit().putString("pref_photo_mp_mode", value.name).apply()

    // Cinema Mode Preferences - Full Persistence across sessions
    var cinemaFps: Int
        get() = prefs.getInt("pref_cinema_fps", 24)
        set(value) = prefs.edit().putInt("pref_cinema_fps", value).apply()

    var cinemaWidth: Int
        get() = prefs.getInt("pref_cinema_width", 3840)
        set(value) = prefs.edit().putInt("pref_cinema_width", value).apply()

    var cinemaHeight: Int
        get() = prefs.getInt("pref_cinema_height", 2160)
        set(value) = prefs.edit().putInt("pref_cinema_height", value).apply()

    var cinemaLogBitDepth: com.example.camera.model.LogBitDepth
        get() {
            val name = prefs.getString("pref_cinema_bit_depth", com.example.camera.model.LogBitDepth.BIT_10.name)
                ?: com.example.camera.model.LogBitDepth.BIT_10.name
            return try { com.example.camera.model.LogBitDepth.valueOf(name) } catch (e: Exception) { com.example.camera.model.LogBitDepth.BIT_10 }
        }
        set(value) = prefs.edit().putString("pref_cinema_bit_depth", value.name).apply()

    var cinemaColorProfile: com.example.camera.model.CinemaColorProfile
        get() {
            val name = prefs.getString("pref_cinema_color_profile", com.example.camera.model.CinemaColorProfile.FLAT_LOG.name)
                ?: com.example.camera.model.CinemaColorProfile.FLAT_LOG.name
            return try { com.example.camera.model.CinemaColorProfile.valueOf(name) } catch (e: Exception) { com.example.camera.model.CinemaColorProfile.FLAT_LOG }
        }
        set(value) = prefs.edit().putString("pref_cinema_color_profile", value.name).apply()

    var cinemaColorSpace: com.example.camera.model.CinemaColorSpace
        get() {
            val name = prefs.getString("pref_cinema_color_space", com.example.camera.model.CinemaColorSpace.REC_709.name)
                ?: com.example.camera.model.CinemaColorSpace.REC_709.name
            return try { com.example.camera.model.CinemaColorSpace.valueOf(name) } catch (e: Exception) { com.example.camera.model.CinemaColorSpace.REC_709 }
        }
        set(value) = prefs.edit().putString("pref_cinema_color_space", value.name).apply()

    var cinemaIsRawSensorLogPipeline: Boolean
        get() = prefs.getBoolean("pref_cinema_raw_pipeline", true)
        set(value) = prefs.edit().putBoolean("pref_cinema_raw_pipeline", value).apply()

    var cinemaIsFocusPeakingEnabled: Boolean
        get() = prefs.getBoolean("pref_cinema_focus_peaking", false)
        set(value) = prefs.edit().putBoolean("pref_cinema_focus_peaking", value).apply()

    var cinemaIsWaveformEnabled: Boolean
        get() = prefs.getBoolean("pref_cinema_waveform", false)
        set(value) = prefs.edit().putBoolean("pref_cinema_waveform", value).apply()

    var cinemaZebraThreshold: com.example.camera.model.ZebraThreshold
        get() {
            val name = prefs.getString("pref_cinema_zebra_threshold", com.example.camera.model.ZebraThreshold.IRE_70.name)
                ?: com.example.camera.model.ZebraThreshold.IRE_70.name
            return try { com.example.camera.model.ZebraThreshold.valueOf(name) } catch (e: Exception) { com.example.camera.model.ZebraThreshold.IRE_70 }
        }
        set(value) = prefs.edit().putString("pref_cinema_zebra_threshold", value.name).apply()

    var cinemaShadows: Float
        get() = prefs.getFloat("pref_cinema_shadows", 0.0f)
        set(value) = prefs.edit().putFloat("pref_cinema_shadows", value).apply()

    var cinemaHighlights: Float
        get() = prefs.getFloat("pref_cinema_highlights", 0.0f)
        set(value) = prefs.edit().putFloat("pref_cinema_highlights", value).apply()

    var cinemaContrast: Float
        get() = prefs.getFloat("pref_cinema_contrast", 0.0f)
        set(value) = prefs.edit().putFloat("pref_cinema_contrast", value).apply()

    var cinemaSaturation: Float
        get() = prefs.getFloat("pref_cinema_saturation", 1.0f)
        set(value) = prefs.edit().putFloat("pref_cinema_saturation", value).apply()

    var cinemaSharpness: com.example.camera.model.CinemaSharpness
        get() {
            val name = prefs.getString("pref_cinema_sharpness", com.example.camera.model.CinemaSharpness.NATURAL.name)
                ?: com.example.camera.model.CinemaSharpness.NATURAL.name
            return try { com.example.camera.model.CinemaSharpness.valueOf(name) } catch (e: Exception) { com.example.camera.model.CinemaSharpness.NATURAL }
        }
        set(value) = prefs.edit().putString("pref_cinema_sharpness", value.name).apply()

    var cinemaExposureCompensation: Int
        get() = prefs.getInt("pref_cinema_ev", 0)
        set(value) = prefs.edit().putInt("pref_cinema_ev", value).apply()

    var cinemaWhiteBalance: com.example.camera.model.WhiteBalanceMode
        get() {
            val name = prefs.getString("pref_cinema_wb", com.example.camera.model.WhiteBalanceMode.AUTO.name)
                ?: com.example.camera.model.WhiteBalanceMode.AUTO.name
            return try { com.example.camera.model.WhiteBalanceMode.valueOf(name) } catch (e: Exception) { com.example.camera.model.WhiteBalanceMode.AUTO }
        }
        set(value) = prefs.edit().putString("pref_cinema_wb", value.name).apply()

    var cinemaManualIso: Int?
        get() {
            val iso = prefs.getInt("pref_cinema_iso", -1)
            return if (iso > 0) iso else null
        }
        set(value) = prefs.edit().putInt("pref_cinema_iso", value ?: -1).apply()

    var cinemaManualShutterSpeedNs: Long?
        get() {
            val ns = prefs.getLong("pref_cinema_shutter", -1L)
            return if (ns > 0) ns else null
        }
        set(value) = prefs.edit().putLong("pref_cinema_shutter", value ?: -1L).apply()

    fun getCinemaConfig(): com.example.camera.model.CinemaConfig {
        return com.example.camera.model.CinemaConfig(
            videoFps = cinemaFps,
            selectedResolution = com.example.camera.model.CameraResolution(cinemaWidth, cinemaHeight),
            logBitDepth = cinemaLogBitDepth,
            colorProfile = cinemaColorProfile,
            colorSpace = cinemaColorSpace,
            isRawSensorLogPipeline = cinemaIsRawSensorLogPipeline,
            isFocusPeakingEnabled = cinemaIsFocusPeakingEnabled,
            isWaveformEnabled = cinemaIsWaveformEnabled,
            zebraThreshold = cinemaZebraThreshold,
            shadows = cinemaShadows,
            highlights = cinemaHighlights,
            contrast = cinemaContrast,
            saturation = cinemaSaturation,
            sharpness = cinemaSharpness,
            exposureCompensation = cinemaExposureCompensation,
            whiteBalance = cinemaWhiteBalance,
            manualIso = cinemaManualIso,
            manualShutterSpeedNs = cinemaManualShutterSpeedNs
        )
    }

    fun saveCinemaConfig(config: com.example.camera.model.CinemaConfig) {
        cinemaFps = config.videoFps
        config.selectedResolution?.let {
            cinemaWidth = it.width
            cinemaHeight = it.height
        }
        cinemaLogBitDepth = config.logBitDepth
        cinemaColorProfile = config.colorProfile
        cinemaColorSpace = config.colorSpace
        cinemaIsRawSensorLogPipeline = config.isRawSensorLogPipeline
        cinemaIsFocusPeakingEnabled = config.isFocusPeakingEnabled
        cinemaIsWaveformEnabled = config.isWaveformEnabled
        cinemaZebraThreshold = config.zebraThreshold
        cinemaShadows = config.shadows
        cinemaHighlights = config.highlights
        cinemaContrast = config.contrast
        cinemaSaturation = config.saturation
        cinemaSharpness = config.sharpness
        cinemaExposureCompensation = config.exposureCompensation
        cinemaWhiteBalance = config.whiteBalance
        cinemaManualIso = config.manualIso
        cinemaManualShutterSpeedNs = config.manualShutterSpeedNs
    }

    // Upgraded Night Mode Preferences
    var nightDurationSeconds: Int
        get() = prefs.getInt("pref_night_duration", 2).coerceIn(1, 5)
        set(value) = prefs.edit().putInt("pref_night_duration", value.coerceIn(1, 5)).apply()

    var nightIsMultiFrameFusion: Boolean
        get() = prefs.getBoolean("pref_night_fusion", true)
        set(value) = prefs.edit().putBoolean("pref_night_fusion", value).apply()

    var nightIsAntiGhostingEnabled: Boolean
        get() = prefs.getBoolean("pref_night_anti_ghosting", true)
        set(value) = prefs.edit().putBoolean("pref_night_anti_ghosting", value).apply()

    var nightNoiseSuppression: Float
        get() = prefs.getFloat("pref_night_noise_suppression", 0.85f)
        set(value) = prefs.edit().putFloat("pref_night_noise_suppression", value).apply()

    var nightShadowLift: Float
        get() = prefs.getFloat("pref_night_shadow_lift", 1.25f)
        set(value) = prefs.edit().putFloat("pref_night_shadow_lift", value).apply()

    // Hybrid Stabilization Preferences
    var isHybridStabilizationEnabled: Boolean
        get() = prefs.getBoolean("pref_hybrid_stabilization", true)
        set(value) = prefs.edit().putBoolean("pref_hybrid_stabilization", value).apply()

    var isOisPreferred: Boolean
        get() = prefs.getBoolean("pref_ois_preferred", true)
        set(value) = prefs.edit().putBoolean("pref_ois_preferred", value).apply()

    var isEisPreferred: Boolean
        get() = prefs.getBoolean("pref_eis_preferred", true)
        set(value) = prefs.edit().putBoolean("pref_eis_preferred", value).apply()

    var isAdaptiveFpsLensStabilization: Boolean
        get() = prefs.getBoolean("pref_adaptive_stabilization", true)
        set(value) = prefs.edit().putBoolean("pref_adaptive_stabilization", value).apply()

    var isUltraStabilizationEnabled: Boolean
        get() = prefs.getBoolean("pref_ultra_stabilization", false)
        set(value) = prefs.edit().putBoolean("pref_ultra_stabilization", value).apply()

    // Tap to Focus & Exposure Preferences
    var isTapToFocusExposureEnabled: Boolean
        get() = prefs.getBoolean("pref_tap_focus_exposure", true)
        set(value) = prefs.edit().putBoolean("pref_tap_focus_exposure", value).apply()

    var isAeAfLockEnabled: Boolean
        get() = prefs.getBoolean("pref_ae_af_lock", true)
        set(value) = prefs.edit().putBoolean("pref_ae_af_lock", value).apply()

    var isSunExposureSliderEnabled: Boolean
        get() = prefs.getBoolean("pref_sun_slider", true)
        set(value) = prefs.edit().putBoolean("pref_sun_slider", value).apply()

    // Dolly Zoom Preferences
    var dollyDirection: com.example.camera.model.DollyDirection
        get() {
            val name = prefs.getString("pref_dolly_direction", com.example.camera.model.DollyDirection.AUTO.name)
                ?: com.example.camera.model.DollyDirection.AUTO.name
            return try { com.example.camera.model.DollyDirection.valueOf(name) } catch (e: Exception) { com.example.camera.model.DollyDirection.AUTO }
        }
        set(value) = prefs.edit().putString("pref_dolly_direction", value.name).apply()

    var nightConfig: NightConfig
        get() = NightConfig(
            durationSeconds = nightDurationSeconds,
            multiFrameFusionEnabled = nightIsMultiFrameFusion,
            antiGhostingEnabled = nightIsAntiGhostingEnabled,
            noiseSuppression = nightNoiseSuppression,
            shadowLift = nightShadowLift,
            isMultiFrameFusion = nightIsMultiFrameFusion,
            isAntiGhostingEnabled = nightIsAntiGhostingEnabled,
            noiseSuppressionStrength = nightNoiseSuppression,
            shadowLiftFactor = nightShadowLift
        )
        set(value) {
            nightDurationSeconds = value.durationSeconds
            nightIsMultiFrameFusion = value.multiFrameFusionEnabled
            nightIsAntiGhostingEnabled = value.antiGhostingEnabled
            nightNoiseSuppression = value.noiseSuppression
            nightShadowLift = value.shadowLift
        }

    var tapFocusConfig: TapFocusConfig
        get() = TapFocusConfig(
            isTapToFocusExposureEnabled = isTapToFocusExposureEnabled,
            isTapToFocusEnabled = isTapToFocusExposureEnabled,
            isAeAfLockEnabled = isAeAfLockEnabled,
            isSunExposureSliderEnabled = isSunExposureSliderEnabled,
            autoDismissReticle = true
        )
        set(value) {
            isTapToFocusExposureEnabled = value.isTapToFocusEnabled
            isAeAfLockEnabled = value.isAeAfLockEnabled
            isSunExposureSliderEnabled = value.isSunExposureSliderEnabled
        }

    var hybridStabilizationConfig: HybridStabilizationConfig
        get() = HybridStabilizationConfig(
            isHybridEnabled = isHybridStabilizationEnabled,
            isOisPreferred = isOisPreferred,
            isEisPreferred = isEisPreferred,
            isAdaptiveFpsLens = isAdaptiveFpsLensStabilization,
            isUltraStabilizationEnabled = isUltraStabilizationEnabled
        )
        set(value) {
            isHybridStabilizationEnabled = value.isHybridEnabled
            isOisPreferred = value.isOisPreferred
            isEisPreferred = value.isEisPreferred
            isAdaptiveFpsLensStabilization = value.isAdaptiveFpsLens
            isUltraStabilizationEnabled = value.isUltraStabilizationEnabled
        }

    var uiCustomizationState: UiCustomizationState
        get() {
            val jsonStr = prefs.getString("pref_ui_customization_state", "") ?: ""
            return if (jsonStr.isNotBlank()) {
                UiCustomizationState.fromJson(jsonStr)
            } else {
                UiCustomizationState()
            }
        }
        set(value) {
            prefs.edit().putString("pref_ui_customization_state", value.toJson()).apply()
        }

    // Extended Camera Controls & System Preferences
    var videoCodec: String
        get() = prefs.getString("pref_video_codec", "HEVC") ?: "HEVC"
        set(value) = prefs.edit().putString("pref_video_codec", value).apply()

    var jpegQuality: Int
        get() = prefs.getInt("pref_jpeg_quality", 100)
        set(value) = prefs.edit().putInt("pref_jpeg_quality", value).apply()

    var volumeKeyAction: String
        get() = prefs.getString("pref_volume_key_action", "SHUTTER") ?: "SHUTTER"
        set(value) = prefs.edit().putString("pref_volume_key_action", value).apply()

    var doubleTapAction: String
        get() = prefs.getString("pref_double_tap_action", "FLIP_CAMERA") ?: "FLIP_CAMERA"
        set(value) = prefs.edit().putString("pref_double_tap_action", value).apply()

    var showHorizonLevel: Boolean
        get() = prefs.getBoolean("pref_show_horizon_level", true)
        set(value) = prefs.edit().putBoolean("pref_show_horizon_level", value).apply()

    var antiBanding: String
        get() = prefs.getString("pref_anti_banding", "AUTO") ?: "AUTO"
        set(value) = prefs.edit().putString("pref_anti_banding", value).apply()

    var zoomSpeed: String
        get() = prefs.getString("pref_zoom_speed", "SMOOTH") ?: "SMOOTH"
        set(value) = prefs.edit().putString("pref_zoom_speed", value).apply()

    var audioSource: String
        get() = prefs.getString("pref_audio_source", "STEREO") ?: "STEREO"
        set(value) = prefs.edit().putString("pref_audio_source", value).apply()

    var previewQuality: String
        get() = prefs.getString("pref_preview_quality", "HIGH_60FPS") ?: "HIGH_60FPS"
        set(value) = prefs.edit().putString("pref_preview_quality", value).apply()

    var shutterFeedback: String
        get() = prefs.getString("pref_shutter_feedback", "SOUND_HAPTIC") ?: "SOUND_HAPTIC"
        set(value) = prefs.edit().putString("pref_shutter_feedback", value).apply()

    var autoHdrEnabled: Boolean
        get() = prefs.getBoolean("pref_auto_hdr_enabled", true)
        set(value) = prefs.edit().putBoolean("pref_auto_hdr_enabled", value).apply()

    var autoFramingEnabled: Boolean
        get() = prefs.getBoolean("pref_auto_framing_enabled", true)
        set(value) = prefs.edit().putBoolean("pref_auto_framing_enabled", value).apply()

    fun resetAllSettingsToDefaults() {
        prefs.edit().clear().apply()
    }
}
