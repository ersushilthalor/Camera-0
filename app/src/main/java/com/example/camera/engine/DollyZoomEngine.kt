package com.example.camera.engine

import android.graphics.Rect
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.params.Face
import com.example.camera.model.DollyDirection
import com.example.camera.model.DollyZoomState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max

/**
 * Real Dolly Zoom (Vertigo Effect) Computation Engine.
 *
 * Synchronizes physical camera movement with optical/digital zoom so that the
 * foreground subject framing remains constant while the background perspective
 * expands (push-in) or compresses (pull-out).
 *
 * Incorporates:
 * - Robust dual-source tracking (Subject Face Geometry + Optical Lens Distance)
 * - Multi-stage Low-Pass Filtering to eliminate sensor noise & focus jumps
 * - Strict Slew-Rate Limiting to guarantee continuous, silky-smooth zoom without sudden jumps
 * - Continuous boundary handling so it never freezes, stops, or locks up at zoom limits
 */
class DollyZoomEngine {

    companion object {
        private const val MAX_ZOOM_SLEW_PER_FRAME = 0.04f // Silky smooth ~1.2x change per second at 30fps
        private const val DISTANCE_FILTER_ALPHA = 0.10f
        private const val SCALE_FILTER_ALPHA = 0.14f
    }

    private val _dollyState = MutableStateFlow(DollyZoomState())
    val dollyState: StateFlow<DollyZoomState> = _dollyState.asStateFlow()

    // Calibration references
    private var isFaceLocked: Boolean = false
    private var referenceSubjectScale: Float = 0f
    private var filteredSubjectScale: Float = 0f
    private var referenceDistanceMeters: Float = 1.2f
    private var filteredDistanceMeters: Float = 1.2f
    private var referenceZoom: Float = 1.0f
    private var currentSmoothedZoom: Float = 1.0f
    private var targetZoomClamped: Float = 1.0f
    private var smoothedTrackingRatio: Float = 1.0f

    // Missed frame bridge for face tracking
    private var missedFaceFrames: Int = 0

    /**
     * Locks and calibrates on current subject scale and distance as baseline.
     */
    fun calibrate(
        currentZoom: Float,
        currentFace: Face?,
        lensFocusDiopters: Float,
        sensorRect: Rect?,
        minZoom: Float = 1.0f,
        maxZoom: Float = 8.0f
    ) {
        val clampedZoom = currentZoom.coerceIn(minZoom, maxZoom)
        referenceZoom = clampedZoom
        currentSmoothedZoom = clampedZoom
        targetZoomClamped = clampedZoom
        smoothedTrackingRatio = 1.0f

        val rawDist = if (lensFocusDiopters > 0.01f) {
            (1.0f / lensFocusDiopters).coerceIn(0.25f, 12.0f)
        } else {
            1.2f
        }
        referenceDistanceMeters = rawDist
        filteredDistanceMeters = rawDist

        if (currentFace != null && sensorRect != null && sensorRect.width() > 0) {
            isFaceLocked = true
            val initialScale = currentFace.bounds.width().toFloat() / sensorRect.width().toFloat()
            referenceSubjectScale = initialScale.coerceIn(0.04f, 0.90f)
            filteredSubjectScale = referenceSubjectScale
        } else {
            isFaceLocked = false
            referenceSubjectScale = 0.25f
            filteredSubjectScale = 0.25f
        }

        missedFaceFrames = 0

        _dollyState.value = _dollyState.value.copy(
            isCalibrated = true,
            isTracking = true,
            targetDistanceMeters = referenceDistanceMeters,
            initialZoom = referenceZoom,
            currentDistanceMeters = referenceDistanceMeters,
            targetZoom = referenceZoom,
            smoothedZoom = currentSmoothedZoom,
            statusPrompt = if (isFaceLocked) "Subject Locked (Face) · Walk smoothly" else "Center Subject Locked · Walk smoothly"
        )
    }

    /**
     * Resets calibration to initial state.
     */
    fun reset() {
        isFaceLocked = false
        referenceSubjectScale = 0f
        filteredSubjectScale = 0f
        referenceDistanceMeters = 1.2f
        filteredDistanceMeters = 1.2f
        referenceZoom = 1.0f
        currentSmoothedZoom = 1.0f
        targetZoomClamped = 1.0f
        smoothedTrackingRatio = 1.0f
        missedFaceFrames = 0
        _dollyState.value = DollyZoomState()
    }

    fun setDirection(direction: DollyDirection) {
        _dollyState.value = _dollyState.value.copy(direction = direction)
    }

    /**
     * Called on each Camera2 TotalCaptureResult frame.
     * Computes continuous, smooth, slew-rate-limited zoom without sudden jumps, flicker, or freezing.
     */
    fun processFrame(
        result: CaptureResult,
        sensorRect: Rect?,
        minAvailableZoom: Float = 1.0f,
        maxAvailableZoom: Float = 8.0f
    ): Float? {
        val state = _dollyState.value
        if (!state.isCalibrated || !state.isTracking) return null

        // Graceful fallback if camera hardware does not support zoom range
        if (maxAvailableZoom <= minAvailableZoom + 0.05f) {
            _dollyState.value = state.copy(statusPrompt = "Camera does not support continuous zoom range")
            return null
        }

        // 1. Filter optical lens focus distance
        val diopters = result.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: 0f
        if (diopters > 0.01f) {
            val rawDist = (1.0f / diopters).coerceIn(0.25f, 15.0f)
            filteredDistanceMeters = filteredDistanceMeters * (1f - DISTANCE_FILTER_ALPHA) + (rawDist * DISTANCE_FILTER_ALPHA)
        }

        // 2. Track subject scale
        val faces = result.get(CaptureResult.STATISTICS_FACES)
        val primaryFace = faces?.firstOrNull()

        if (isFaceLocked && primaryFace != null && sensorRect != null && sensorRect.width() > 0) {
            missedFaceFrames = 0
            val instantaneousScale = primaryFace.bounds.width().toFloat() / sensorRect.width().toFloat()
            filteredSubjectScale = filteredSubjectScale * (1f - SCALE_FILTER_ALPHA) + (instantaneousScale * SCALE_FILTER_ALPHA)

            // Vertigo formula: To maintain constant subject scale:
            // Target Ratio = Reference Scale / Current Scale
            val faceRatio = referenceSubjectScale / max(filteredSubjectScale, 0.02f)
            smoothedTrackingRatio = smoothedTrackingRatio * (1f - SCALE_FILTER_ALPHA) + (faceRatio * SCALE_FILTER_ALPHA)
        } else {
            // Distance-based dolly ratio with smooth temporal continuity
            missedFaceFrames++
            val distRatio = filteredDistanceMeters / max(referenceDistanceMeters, 0.15f)
            val blendFactor = if (isFaceLocked && missedFaceFrames < 30) 0.08f else DISTANCE_FILTER_ALPHA
            smoothedTrackingRatio = smoothedTrackingRatio * (1f - blendFactor) + (distRatio * blendFactor)
        }

        val rawTargetZoom = referenceZoom * smoothedTrackingRatio

        // 3. Clamp target to available camera range
        val boundedTarget = rawTargetZoom.coerceIn(minAvailableZoom, maxAvailableZoom)
        targetZoomClamped = boundedTarget

        // 4. Stable Critically-Damped Slew-Rate Limiter (guarantees continuous, buttery-smooth transition, never jumping)
        val delta = boundedTarget - currentSmoothedZoom
        val adaptiveStep = delta * 0.16f
        val step = adaptiveStep.coerceIn(-MAX_ZOOM_SLEW_PER_FRAME, MAX_ZOOM_SLEW_PER_FRAME)
        currentSmoothedZoom += step

        // 5. Contextual status prompt with boundary awareness
        val atMaxLimit = currentSmoothedZoom >= maxAvailableZoom - 0.05f
        val atMinLimit = currentSmoothedZoom <= minAvailableZoom + 0.05f

        val prompt = when {
            atMaxLimit -> "Max Zoom Reached · Maintain Distance"
            atMinLimit -> "Min Zoom Reached · Maintain Distance"
            filteredDistanceMeters < referenceDistanceMeters * 0.88f -> "Pushing In · Background expanding"
            filteredDistanceMeters > referenceDistanceMeters * 1.12f -> "Pulling Out · Background compressing"
            else -> "Tracking Subject · Keep moving smoothly"
        }

        _dollyState.value = state.copy(
            currentDistanceMeters = filteredDistanceMeters,
            targetZoom = boundedTarget,
            smoothedZoom = currentSmoothedZoom,
            statusPrompt = prompt
        )

        return currentSmoothedZoom
    }
}
