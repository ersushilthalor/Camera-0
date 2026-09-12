package com.example.camera.engine

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.ColorSpaceTransform
import android.hardware.camera2.params.TonemapCurve
import android.os.Build
import android.util.Log
import android.util.Range
import android.util.Rational
import com.example.camera.model.VideoHdrMode
import com.example.camera.model.VideoHdrState
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Real-Time Video HDR & Adaptive Noise Reduction Engine for Camera2.
 *
 * Capabilities:
 * 1. Intelligently analyzes scene lighting, photometric EV, ISO, and motion per frame.
 * 2. Computes dynamic S-curve tonemapping: lifting deep shadows, preserving midtone contrast,
 *    and compressing specular highlights to prevent clipping.
 * 3. Applies aggressive live adaptive spatial + temporal noise reduction in dark / high-ISO scenes.
 * 4. Motion-aware processing prevents ghosting and smearing on moving subjects.
 * 5. Multi-frame exponential temporal smoothing prevents brightness pumping, flickering,
 *    or contrast fluttering.
 * 6. Directly applies hardware ISP controls (TonemapCurve, Gamma, Noise Reduction, Edge Mode,
 *    Scene Mode) to the Camera2 CaptureRequest, affecting BOTH the live viewfinder and recorded video.
 */
class VideoHdrEngine {

    companion object {
        private const val TAG = "VideoHdrEngine"
        private const val CURVE_POINTS = 64
        private const val TEMPORAL_SMOOTHING_ALPHA = 0.08f // Multi-frame EMA factor for anti-pumping
        private const val MOTION_THRESHOLD_DELTA_ISO = 250
        private const val MOTION_THRESHOLD_FOCUS_DELTA = 0.8f
    }

    // Configuration
    var mode: VideoHdrMode = VideoHdrMode.AUTO
        set(value) {
            field = value
            if (value == VideoHdrMode.OFF) {
                smoothedShadowLift = 0f
                smoothedHighlightProtect = 0f
                smoothedContrast = 1.0f
                smoothedNoiseReduction = 0f
                smoothedExposureBias = 0f
                smoothedSaturation = 1.0f
            } else if (value == VideoHdrMode.MANUAL) {
                applyManualParametersImmediately()
            }
            updateState()
        }

    var manualIntensity: Int = 50 // 0 to 100
        set(value) {
            field = value.coerceIn(0, 100)
            applyManualParametersImmediately()
            updateState()
        }

    var manualShadows: Int = 50 // 0 to 100
        set(value) {
            field = value.coerceIn(0, 100)
            applyManualParametersImmediately()
            updateState()
        }

    var manualHighlights: Int = 50 // 0 to 100
        set(value) {
            field = value.coerceIn(0, 100)
            applyManualParametersImmediately()
            updateState()
        }

    var manualContrast: Int = 50 // 0 to 100
        set(value) {
            field = value.coerceIn(0, 100)
            applyManualParametersImmediately()
            updateState()
        }

    var manualExposure: Int = 50 // 0 to 100
        set(value) {
            field = value.coerceIn(0, 100)
            applyManualParametersImmediately()
            updateState()
        }

    var manualBlackLevel: Int = 50 // 0 to 100
        set(value) {
            field = value.coerceIn(0, 100)
            applyManualParametersImmediately()
            updateState()
        }

    var manualMidtones: Int = 50 // 0 to 100
        set(value) {
            field = value.coerceIn(0, 100)
            applyManualParametersImmediately()
            updateState()
        }

    var manualSaturation: Int = 50 // 0 to 100
        set(value) {
            field = value.coerceIn(0, 100)
            applyManualParametersImmediately()
            updateState()
        }

    // Current smoothed parameters (for anti-flicker stability)
    private var smoothedShadowLift: Float = 0.35f
    private var smoothedHighlightProtect: Float = 0.40f
    private var smoothedContrast: Float = 1.10f
    private var smoothedExposureBias: Float = 0.0f
    private var smoothedBlackLevel: Float = 0.0f
    private var smoothedMidtones: Float = 0.0f
    private var smoothedSaturation: Float = 1.0f
    private var smoothedNoiseReduction: Float = 0.30f
    private var smoothedEv: Float = 10f
    private var smoothedIso: Float = 200f

    // Consecutive frame tracking for motion-aware temporal processing
    private var lastIso: Int = 200
    private var lastExposureTimeNs: Long = 10_000_000L
    private var lastFocusDistance: Float = 0f
    private var lastTimestampNs: Long = 0L
    private var isMotionDetected: Boolean = false

    // Hardware capability cache
    var aeCompensationRange: Range<Int> = Range(-12, 12)
        private set
    var aeCompensationStep: Float = 0.333f
        private set
    private var supportsColorCorrection: Boolean = false
    private var supportsContrastCurve: Boolean = false
    private var supportsGammaValue: Boolean = false
    private var supportsSceneHdr: Boolean = false
    private var supportsHighQualityNr: Boolean = false
    private var supportsHighQualityEdge: Boolean = false
    private var tonemapMaxPoints: Int = CURVE_POINTS

    // Pre-allocated curve buffers for zero garbage collection during video recording
    private val curveRed = FloatArray(CURVE_POINTS * 2)
    private val curveGreen = FloatArray(CURVE_POINTS * 2)
    private val curveBlue = FloatArray(CURVE_POINTS * 2)

    private fun applyManualParametersImmediately() {
        if (mode != VideoHdrMode.MANUAL) return
        val master = manualIntensity / 100f
        val shadowFactor = (manualShadows / 50f)
        val highlightFactor = (manualHighlights / 50f)
        val contrastDelta = (manualContrast - 50) / 50f
        val expDelta = (manualExposure - 50) / 50f
        val blackDelta = (manualBlackLevel - 50) / 50f
        val midDelta = (manualMidtones - 50) / 50f
        val satFactor = (manualSaturation / 50f)

        smoothedShadowLift = (master * 0.70f * shadowFactor).coerceIn(0f, 1.4f)
        smoothedHighlightProtect = (master * 0.60f * highlightFactor).coerceIn(0f, 1.4f)
        smoothedContrast = (1.0f + (master * 0.25f) + (contrastDelta * 0.35f)).coerceIn(0.6f, 1.8f)
        smoothedExposureBias = expDelta * 0.40f
        smoothedBlackLevel = blackDelta * 0.15f
        smoothedMidtones = midDelta * 0.30f
        smoothedSaturation = satFactor.coerceIn(0f, 2.2f)
        smoothedNoiseReduction = ((smoothedIso - 200f) / 3000f).coerceIn(0.1f, 1.0f)
    }

    // Public State for UI observation
    var currentState: VideoHdrState = VideoHdrState()
        private set

    var onStateChangedListener: ((VideoHdrState) -> Unit)? = null

    /**
     * Inspect CameraCharacteristics to determine supported hardware ISP features.
     */
    fun onCameraConfigured(chars: CameraCharacteristics) {
        val tonemapModes = chars.get(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES) ?: intArrayOf()
        supportsContrastCurve = tonemapModes.contains(CameraCharacteristics.TONEMAP_MODE_CONTRAST_CURVE)
        supportsGammaValue = tonemapModes.contains(CameraCharacteristics.TONEMAP_MODE_GAMMA_VALUE)
        tonemapMaxPoints = chars.get(CameraCharacteristics.TONEMAP_MAX_CURVE_POINTS) ?: CURVE_POINTS

        val sceneModes = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES) ?: intArrayOf()
        supportsSceneHdr = sceneModes.contains(CameraCharacteristics.CONTROL_SCENE_MODE_HDR)

        val nrModes = chars.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES) ?: intArrayOf()
        supportsHighQualityNr = nrModes.contains(CameraCharacteristics.NOISE_REDUCTION_MODE_HIGH_QUALITY)

        val edgeModes = chars.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES) ?: intArrayOf()
        supportsHighQualityEdge = edgeModes.contains(CameraCharacteristics.EDGE_MODE_HIGH_QUALITY)

        aeCompensationRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) ?: Range(-12, 12)
        val step = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat() ?: 0.333f
        aeCompensationStep = if (step > 0f) step else 0.333f

        val colorModes = chars.get(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_MODES) ?: intArrayOf()
        supportsColorCorrection = colorModes.contains(CameraCharacteristics.COLOR_CORRECTION_MODE_FAST) ||
                colorModes.contains(CameraCharacteristics.COLOR_CORRECTION_MODE_HIGH_QUALITY)

        Log.d(TAG, "Configured: contrastCurve=$supportsContrastCurve, gamma=$supportsGammaValue, " +
                "sceneHdr=$supportsSceneHdr, hqNr=$supportsHighQualityNr, hqEdge=$supportsHighQualityEdge, " +
                "aeRange=$aeCompensationRange, aeStep=$aeCompensationStep, colorCorr=$supportsColorCorrection")
        if (mode == VideoHdrMode.MANUAL) {
            applyManualParametersImmediately()
        }
        updateState()
    }

    /**
     * Process TotalCaptureResult from onCaptureCompleted for real-time scene analysis.
     * Returns true if request settings should be updated.
     */
    fun onFrameCaptured(result: TotalCaptureResult): Boolean {
        if (mode == VideoHdrMode.OFF) {
            return false
        }

        val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: 200
        val exposureNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 10_000_000L
        val aperture = result.get(CaptureResult.LENS_APERTURE) ?: 1.8f
        val focusDist = result.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: 0f
        val timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: System.nanoTime()

        return processFrameValues(iso, exposureNs, aperture, focusDist, timestamp)
    }

    /**
     * Process raw sensor values for frame-to-frame analysis and testing.
     */
    fun processFrameValues(
        iso: Int,
        exposureNs: Long,
        aperture: Float,
        focusDist: Float,
        timestamp: Long = System.nanoTime()
    ): Boolean {
        if (mode == VideoHdrMode.OFF) {
            updateState()
            return false
        }

        // 1. Motion-Aware Analysis across consecutive frames
        val deltaIso = abs(iso - lastIso)
        val deltaFocus = abs(focusDist - lastFocusDistance)
        val deltaExposure = abs(exposureNs - lastExposureTimeNs)

        // If sudden large changes occur between consecutive frames, scene or camera is in rapid motion
        isMotionDetected = (deltaIso > MOTION_THRESHOLD_DELTA_ISO) ||
                (deltaFocus > MOTION_THRESHOLD_FOCUS_DELTA) ||
                (deltaExposure > (lastExposureTimeNs / 2))

        lastIso = iso
        lastExposureTimeNs = exposureNs
        lastFocusDistance = focusDist
        lastTimestampNs = timestamp

        // 2. Photometric EV Calculation:
        // EV = log2(N^2 / t) - log2(ISO / 100)
        val exposureSeconds = (exposureNs / 1_000_000_000.0).coerceAtLeast(0.00001)
        val log2 = { v: Double -> ln(v) / ln(2.0) }
        val ev = (log2((aperture * aperture) / exposureSeconds) - log2((iso / 100.0).coerceAtLeast(0.1))).toFloat()

        // 3. Temporal Smoothing (Anti-pumping & Anti-flicker EMA filter)
        // Adapt alpha slightly if rapid scene change is detected, but keep low to prevent flicker
        val alpha = if (isMotionDetected) 0.14f else TEMPORAL_SMOOTHING_ALPHA
        smoothedEv = smoothedEv * (1f - alpha) + ev * alpha
        smoothedIso = smoothedIso * (1f - alpha) + iso.toFloat() * alpha

        // 4. Scene-Adaptive Dynamic Range & Noise Reduction targets
        val targetShadowLift: Float
        val targetHighlightProtect: Float
        val targetContrast: Float
        val targetExposureBias: Float
        val targetBlackLevel: Float
        val targetMidtones: Float
        val targetSaturation: Float
        val targetNoiseReduction: Float

        when (mode) {
            VideoHdrMode.AUTO -> {
                targetExposureBias = 0.0f
                targetBlackLevel = 0.0f
                targetMidtones = 0.0f
                targetSaturation = 1.0f
                // Determine parameters according to lighting condition and ISO
                when {
                    // Dark / Low-Light / High-ISO (EV < 4 or ISO > 800)
                    smoothedIso > 800 || smoothedEv < 4f -> {
                        val lowLightFactor = ((smoothedIso - 400f) / 2800f).coerceIn(0f, 1f)
                        targetShadowLift = 0.45f + (lowLightFactor * 0.35f) // 0.45 .. 0.80
                        targetHighlightProtect = 0.30f
                        targetContrast = 1.05f + (lowLightFactor * 0.10f)
                        // Aggressive live adaptive noise reduction in dark/high-ISO scenes
                        targetNoiseReduction = 0.50f + (lowLightFactor * 0.50f) // up to 1.0f
                    }
                    // Bright daylight / High Dynamic Range scenes (EV > 11)
                    smoothedEv > 11f -> {
                        targetShadowLift = 0.40f
                        targetHighlightProtect = 0.60f // strong highlight compression for skies & sunlight
                        targetContrast = 1.18f
                        targetNoiseReduction = 0.15f // keep sharp, noise is very low
                    }
                    // Balanced indoor / overcast (EV 4 .. 11)
                    else -> {
                        targetShadowLift = 0.35f
                        targetHighlightProtect = 0.40f
                        targetContrast = 1.10f
                        targetNoiseReduction = 0.25f
                    }
                }
            }
            VideoHdrMode.MANUAL -> {
                // Master intensity modifier (0 to 100)
                val master = manualIntensity / 100f
                // Individual controls normalized from 0..100 (50 = baseline neutral)
                val shadowFactor = (manualShadows / 50f) // 0.0 .. 1.0 (at 50) .. 2.0 (at 100)
                val highlightFactor = (manualHighlights / 50f) // 0.0 .. 1.0 .. 2.0
                val contrastDelta = (manualContrast - 50) / 50f // -1.0 .. 0.0 .. +1.0
                val expDelta = (manualExposure - 50) / 50f // -1.0 .. 0.0 .. +1.0
                val blackDelta = (manualBlackLevel - 50) / 50f // -1.0 .. 0.0 .. +1.0
                val midDelta = (manualMidtones - 50) / 50f // -1.0 .. 0.0 .. +1.0
                val satFactor = (manualSaturation / 50f) // 0.0 (monochrome) .. 1.0 (normal) .. 2.0 (vibrant)

                targetShadowLift = (master * 0.70f * shadowFactor).coerceIn(0f, 1.2f)
                targetHighlightProtect = (master * 0.60f * highlightFactor).coerceIn(0f, 1.2f)
                targetContrast = (1.0f + (master * 0.25f) + (contrastDelta * 0.35f)).coerceIn(0.7f, 1.6f)
                targetExposureBias = expDelta * 0.30f // -0.30 .. +0.30
                targetBlackLevel = blackDelta * 0.12f // -0.12 (lift black) .. +0.12 (deep black)
                targetMidtones = midDelta * 0.25f // -0.25 .. +0.25
                targetSaturation = satFactor.coerceIn(0f, 2.0f)

                // Noise reduction remains independently adaptive to actual ISO levels
                val isoNoiseBase = ((smoothedIso - 200f) / 3000f).coerceIn(0.1f, 1.0f)
                targetNoiseReduction = isoNoiseBase
            }
            VideoHdrMode.OFF -> {
                targetShadowLift = 0f
                targetHighlightProtect = 0f
                targetContrast = 1.0f
                targetExposureBias = 0f
                targetBlackLevel = 0f
                targetMidtones = 0f
                targetSaturation = 1.0f
                targetNoiseReduction = 0f
            }
        }

        // Apply smooth temporal blending to parameters
        smoothedShadowLift = smoothedShadowLift * (1f - alpha) + targetShadowLift * alpha
        smoothedHighlightProtect = smoothedHighlightProtect * (1f - alpha) + targetHighlightProtect * alpha
        smoothedContrast = smoothedContrast * (1f - alpha) + targetContrast * alpha
        smoothedExposureBias = smoothedExposureBias * (1f - alpha) + targetExposureBias * alpha
        smoothedBlackLevel = smoothedBlackLevel * (1f - alpha) + targetBlackLevel * alpha
        smoothedMidtones = smoothedMidtones * (1f - alpha) + targetMidtones * alpha
        smoothedSaturation = smoothedSaturation * (1f - alpha) + targetSaturation * alpha
        smoothedNoiseReduction = smoothedNoiseReduction * (1f - alpha) + targetNoiseReduction * alpha

        updateState()
        return true
    }

    private fun updateState() {
        val desc = when (mode) {
            VideoHdrMode.OFF -> "HDR: OFF"
            VideoHdrMode.AUTO -> {
                val condition = when {
                    smoothedIso > 800 || smoothedEv < 4f -> "Night/Low-Light Boost"
                    smoothedEv > 11f -> "High Dynamic Range"
                    else -> "Balanced Tone"
                }
                "HDR Auto · $condition (Shadow +${(smoothedShadowLift * 100).toInt()}%)"
            }
            VideoHdrMode.MANUAL -> "HDR Manual ($manualIntensity%) · Shd:${manualShadows}% Hlt:${manualHighlights}% Ctr:${manualContrast}% Sat:${manualSaturation}%"
        }

        currentState = VideoHdrState(
            mode = mode,
            manualIntensity = manualIntensity,
            manualShadows = manualShadows,
            manualHighlights = manualHighlights,
            manualContrast = manualContrast,
            manualExposure = manualExposure,
            manualBlackLevel = manualBlackLevel,
            manualMidtones = manualMidtones,
            manualSaturation = manualSaturation,
            isHdrActive = mode != VideoHdrMode.OFF,
            currentStrength = if (mode == VideoHdrMode.OFF) 0f else if (mode == VideoHdrMode.MANUAL) manualIntensity / 100f else smoothedShadowLift,
            shadowLift = if (mode == VideoHdrMode.OFF) 0f else smoothedShadowLift,
            highlightProtection = if (mode == VideoHdrMode.OFF) 0f else smoothedHighlightProtect,
            contrastFactor = if (mode == VideoHdrMode.OFF) 1.0f else smoothedContrast,
            noiseReductionStrength = if (mode == VideoHdrMode.OFF) 0f else smoothedNoiseReduction,
            currentIso = smoothedIso.toInt(),
            estimatedEv = smoothedEv,
            isMotionDetected = isMotionDetected,
            statusDescription = desc
        )
        onStateChangedListener?.invoke(currentState)
    }

    /**
     * Apply real-time HDR and Noise Reduction controls to Camera2 CaptureRequest.Builder.
     * These settings are processed directly by the camera ISP on ALL target surfaces
     * (viewfinder SurfaceTexture + MediaRecorder surface).
     */
    fun applyToCaptureRequest(builder: CaptureRequest.Builder) {
        if (mode == VideoHdrMode.OFF) {
            // Restore default linear tonemapping and fast noise reduction
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_FAST)
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_FAST)
            builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_FAST)
            if (supportsSceneHdr) {
                builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
            }
            return
        }

        // 1. Scene Mode / AE Compensation Handling
        if (mode == VideoHdrMode.AUTO) {
            if (supportsSceneHdr) {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE)
                builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_HDR)
            } else {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            }
        } else {
            // MANUAL mode: Keep CONTROL_MODE_AUTO so manual curves, exposure compensation, and color transforms apply directly!
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            if (supportsSceneHdr) {
                builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
            }

            // Real Hardware AE Exposure Compensation (Physically brightens/darkens real camera sensor!)
            val targetEv = (smoothedExposureBias * 3.5f) + (smoothedShadowLift * 0.35f)
            val compSteps = (targetEv / aeCompensationStep).roundToInt()
                .coerceIn(aeCompensationRange.lower, aeCompensationRange.upper)
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, compSteps)

            // Real Color Correction Saturation Matrix (Visually saturates/desaturates in real time!)
            if (supportsColorCorrection) {
                val sat = smoothedSaturation.coerceIn(0f, 2.2f)
                val rW = 0.299f
                val gW = 0.587f
                val bW = 0.114f

                val m00 = ((rW + (1f - rW) * sat) * 256).roundToInt().coerceIn(-1000, 1000)
                val m01 = ((gW * (1f - sat)) * 256).roundToInt().coerceIn(-1000, 1000)
                val m02 = ((bW * (1f - sat)) * 256).roundToInt().coerceIn(-1000, 1000)

                val m10 = ((rW * (1f - sat)) * 256).roundToInt().coerceIn(-1000, 1000)
                val m11 = ((gW + (1f - gW) * sat) * 256).roundToInt().coerceIn(-1000, 1000)
                val m12 = ((bW * (1f - sat)) * 256).roundToInt().coerceIn(-1000, 1000)

                val m20 = ((rW * (1f - sat)) * 256).roundToInt().coerceIn(-1000, 1000)
                val m21 = ((gW * (1f - sat)) * 256).roundToInt().coerceIn(-1000, 1000)
                val m22 = ((bW + (1f - bW) * sat) * 256).roundToInt().coerceIn(-1000, 1000)

                val transform = ColorSpaceTransform(
                    intArrayOf(
                        m00, 256, m01, 256, m02, 256,
                        m10, 256, m11, 256, m12, 256,
                        m20, 256, m21, 256, m22, 256
                    )
                )
                builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_FAST)
                builder.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, transform)
            }
        }

        // 2. Dynamic HDR Tonemap S-Curve Synthesis
        if (supportsContrastCurve) {
            val tonemapCurve = generateHdrTonemapCurve(
                shadowLift = smoothedShadowLift,
                highlightProtect = smoothedHighlightProtect,
                contrast = smoothedContrast,
                exposureBias = smoothedExposureBias,
                blackLevel = smoothedBlackLevel,
                midtones = smoothedMidtones,
                saturation = smoothedSaturation
            )
            builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE)
            builder.set(CaptureRequest.TONEMAP_CURVE, tonemapCurve)
        } else if (supportsGammaValue) {
            // Adaptive gamma curve fallback: lifting shadows naturally without washing out blacks
            val adaptiveGamma = (2.2f - (smoothedShadowLift * 0.6f) - (smoothedMidtones * 0.4f)).coerceIn(1.4f, 2.6f)
            builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_GAMMA_VALUE)
            builder.set(CaptureRequest.TONEMAP_GAMMA, adaptiveGamma)
        } else {
            builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_HIGH_QUALITY)
        }

        // 3. Aggressive Live Adaptive Noise Reduction (Spatial + Temporal)
        // When ISO > 600 or noise reduction target is high, switch to High-Quality spatial + temporal ISP filter
        if (smoothedNoiseReduction > 0.35f && supportsHighQualityNr) {
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
            builder.set(CaptureRequest.HOT_PIXEL_MODE, CaptureRequest.HOT_PIXEL_MODE_HIGH_QUALITY)
            builder.set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_HIGH_QUALITY)

            // Motion-aware edge processing:
            // If rapid motion is occurring, use FAST edge mode to prevent ghosting or smearing artifacts.
            // If camera/subject is stable, use HIGH_QUALITY edge mode for crisp detail retention.
            if (isMotionDetected) {
                builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_FAST)
            } else if (supportsHighQualityEdge) {
                builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
            }
        } else {
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_FAST)
            if (supportsHighQualityEdge) {
                builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
            }
        }
    }

    /**
     * Synthesizes a high-precision 64-point S-curve tonemapping curve for Camera2.
     * Monotonically smooth:
     * - Deep shadows lifted naturally
     * - Highlights softly compressed to eliminate blown-out clipping
     * - Midtones, black level, exposure bias, and color channel scaling applied cleanly
     */
    private fun generateHdrTonemapCurve(
        shadowLift: Float,
        highlightProtect: Float,
        contrast: Float,
        exposureBias: Float = 0f,
        blackLevel: Float = 0f,
        midtones: Float = 0f,
        saturation: Float = 1f
    ): TonemapCurve {
        val points = CURVE_POINTS
        var prevOut = 0f

        for (i in 0 until points) {
            val inVal = i.toFloat() / (points - 1).toFloat()

            // 1. Black level & Exposure offset
            val shiftedIn = (inVal * (1f + exposureBias) - (blackLevel * 0.15f)).coerceIn(0f, 1f)

            // 2. Shadow lifting function (power-bezier taper)
            val shadowBoost = shadowLift * shiftedIn * (1f - shiftedIn).pow(2f) * 2.0f
            val baseVal = (shiftedIn + shadowBoost).coerceIn(0f, 1f)

            // 3. Midtone adjustment + Contrast response
            // Midtone positive = lifts midtones; negative = darkens midtones
            val midShift = midtones * 4f * baseVal * (1f - baseVal)
            val midAdjusted = (baseVal + midShift).coerceIn(0f, 1f)

            val p = contrast.coerceIn(0.7f, 1.8f)
            val vPow = midAdjusted.pow(p)
            val contrastVal = if (midAdjusted <= 0f) 0f else vPow / (vPow + (1f - midAdjusted).pow(p))

            // 4. Highlight compression shoulder (protect specular highlights from clipping)
            val shoulder = 1f + (highlightProtect * 0.75f)
            val finalVal = (1f - (1f - contrastVal).pow(shoulder)).coerceIn(0f, 1f)

            // Ensure strictly monotonic non-decreasing output
            val monotonicOut = max(prevOut, finalVal).coerceIn(0f, 1f)
            prevOut = monotonicOut

            // Saturation modulation via color-channel divergence around luminance
            // Red and Blue slightly diverge from Green when saturation is boosted,
            // or converge to Green when desaturated.
            val satMod = (saturation - 1f) * 0.08f
            val rOut = (monotonicOut + satMod * (monotonicOut - 0.5f)).coerceIn(0f, 1f)
            val gOut = monotonicOut
            val bOut = (monotonicOut - (satMod * 0.5f) * (monotonicOut - 0.5f)).coerceIn(0f, 1f)

            val idx = i * 2
            curveRed[idx] = inVal
            curveRed[idx + 1] = rOut

            curveGreen[idx] = inVal
            curveGreen[idx + 1] = gOut

            curveBlue[idx] = inVal
            curveBlue[idx + 1] = bOut
        }

        return TonemapCurve(curveRed, curveGreen, curveBlue)
    }
}
