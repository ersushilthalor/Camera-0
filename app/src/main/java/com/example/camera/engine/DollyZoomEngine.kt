package com.example.camera.engine

import android.graphics.Rect
import android.graphics.RectF
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.params.Face
import com.example.camera.model.DollyDirection
import com.example.camera.model.DollyZoomState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Real Dolly Zoom (Vertigo Effect) Computation Engine.
 *
 * Synchronizes physical camera movement with optical/digital zoom so that the
 * foreground subject framing remains constant while the background perspective
 * expands (push-in) or compresses (pull-out).
 *
 * Incorporates:
 * - User tap-to-lock on subject (face or arbitrary focal region)
 * - Real-time apparent size calculation and bounding reticle
 * - Predictive zoom compensation anticipating motion delta
 * - Multi-stage Low-Pass Filtering to eliminate sensor noise & focus jumps
 * - Strict Slew-Rate Limiting for buttery-smooth performance
 */
class DollyZoomEngine {

    companion object {
        private const val MAX_ZOOM_SLEW_PER_FRAME = 0.045f // Smooth ~1.35x change per second at 30fps
        private const val DISTANCE_FILTER_ALPHA = 0.12f
        private const val SCALE_FILTER_ALPHA = 0.16f
        private const val PREDICTIVE_LOOKAHEAD_FRAMES = 2.0f // Anticipate 2 frames ahead (~66ms)
    }

    private val _dollyState = MutableStateFlow(DollyZoomState())
    val dollyState: StateFlow<DollyZoomState> = _dollyState.asStateFlow()

    // Tracking state
    private var isFaceLocked: Boolean = false
    private var lockedFaceId: Int = -1
    private var lockedNormX: Float = 0.5f
    private var lockedNormY: Float = 0.5f

    // Scale & Distance Baselines
    private var referenceSubjectScale: Float = 0.25f
    private var filteredSubjectScale: Float = 0.25f
    private var prevFilteredScale: Float = 0.25f
    private var scaleVelocity: Float = 0f

    private var referenceDistanceMeters: Float = 1.2f
    private var filteredDistanceMeters: Float = 1.2f
    private var prevFilteredDistance: Float = 1.2f
    private var distanceVelocity: Float = 0f

    private var referenceZoom: Float = 1.0f
    private var currentSmoothedZoom: Float = 1.0f
    private var targetZoomClamped: Float = 1.0f
    private var smoothedTrackingRatio: Float = 1.0f

    // Missed frame bridge
    private var missedFrames: Int = 0

    /**
     * Locks on subject at the specified normalized viewfinder coordinates (normX, normY: 0..1).
     * If a face intersects or is near the tap point, it locks to that face.
     * Otherwise, locks to the region centered at (normX, normY).
     */
    fun lockSubject(
        normX: Float,
        normY: Float,
        currentZoom: Float,
        faces: Array<Face>?,
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

        lockedNormX = normX.coerceIn(0.05f, 0.95f)
        lockedNormY = normY.coerceIn(0.05f, 0.95f)

        val rawDist = if (lensFocusDiopters > 0.01f) {
            (1.0f / lensFocusDiopters).coerceIn(0.25f, 12.0f)
        } else {
            1.2f
        }
        referenceDistanceMeters = rawDist
        filteredDistanceMeters = rawDist
        prevFilteredDistance = rawDist
        distanceVelocity = 0f

        // Check if any detected face is close to the tap coordinates
        var matchedFace: Face? = null
        if (sensorRect != null && sensorRect.width() > 0 && faces != null && faces.isNotEmpty()) {
            val sensorW = sensorRect.width().toFloat()
            val sensorH = sensorRect.height().toFloat()

            for (face in faces) {
                // Approximate face bounds in normalized 0..1 coordinates
                val faceNormCenterX = (face.bounds.centerX() - sensorRect.left) / sensorW
                val faceNormCenterY = (face.bounds.centerY() - sensorRect.top) / sensorH
                val dist = abs(faceNormCenterX - lockedNormX) + abs(faceNormCenterY - lockedNormY)
                if (dist < 0.35f) {
                    matchedFace = face
                    break
                }
            }
            if (matchedFace == null && faces.isNotEmpty()) {
                // If user tapped in general area, pick nearest or primary face
                matchedFace = faces.first()
            }
        }

        var bounds: RectF
        if (matchedFace != null && sensorRect != null && sensorRect.width() > 0) {
            isFaceLocked = true
            lockedFaceId = matchedFace.id
            val initialScale = matchedFace.bounds.width().toFloat() / sensorRect.width().toFloat()
            referenceSubjectScale = initialScale.coerceIn(0.04f, 0.90f)
            filteredSubjectScale = referenceSubjectScale
            prevFilteredScale = referenceSubjectScale

            val sW = sensorRect.width().toFloat()
            val sH = sensorRect.height().toFloat()
            bounds = RectF(
                (matchedFace.bounds.left - sensorRect.left) / sW,
                (matchedFace.bounds.top - sensorRect.top) / sH,
                (matchedFace.bounds.right - sensorRect.left) / sW,
                (matchedFace.bounds.bottom - sensorRect.top) / sH
            )
        } else {
            isFaceLocked = false
            lockedFaceId = -1
            referenceSubjectScale = 0.25f
            filteredSubjectScale = 0.25f
            prevFilteredScale = 0.25f

            val halfW = 0.14f
            val halfH = 0.14f
            bounds = RectF(
                (lockedNormX - halfW).coerceAtLeast(0f),
                (lockedNormY - halfH).coerceAtLeast(0f),
                (lockedNormX + halfW).coerceAtMost(1f),
                (lockedNormY + halfH).coerceAtMost(1f)
            )
        }

        scaleVelocity = 0f
        missedFrames = 0

        _dollyState.value = _dollyState.value.copy(
            isCalibrated = true,
            isTracking = true,
            isSubjectLocked = true,
            subjectBounds = bounds,
            trackingConfidence = if (isFaceLocked) 0.96f else 0.88f,
            targetDistanceMeters = referenceDistanceMeters,
            initialZoom = referenceZoom,
            currentDistanceMeters = referenceDistanceMeters,
            targetZoom = referenceZoom,
            smoothedZoom = currentSmoothedZoom,
            statusPrompt = if (isFaceLocked) "Subject Locked (Face) · Walk smoothly" else "Target Locked · Walk smoothly"
        )
    }

    /**
     * Backward-compatible calibration using center or detected face.
     */
    fun calibrate(
        currentZoom: Float,
        currentFace: Face?,
        lensFocusDiopters: Float,
        sensorRect: Rect?,
        minZoom: Float = 1.0f,
        maxZoom: Float = 8.0f
    ) {
        val facesArray = if (currentFace != null) arrayOf(currentFace) else emptyArray()
        lockSubject(
            normX = 0.5f,
            normY = 0.5f,
            currentZoom = currentZoom,
            faces = facesArray,
            lensFocusDiopters = lensFocusDiopters,
            sensorRect = sensorRect,
            minZoom = minZoom,
            maxZoom = maxZoom
        )
    }

    /**
     * Resets calibration to initial state.
     */
    fun reset() {
        isFaceLocked = false
        lockedFaceId = -1
        referenceSubjectScale = 0.25f
        filteredSubjectScale = 0.25f
        prevFilteredScale = 0.25f
        scaleVelocity = 0f

        referenceDistanceMeters = 1.2f
        filteredDistanceMeters = 1.2f
        prevFilteredDistance = 1.2f
        distanceVelocity = 0f

        referenceZoom = 1.0f
        currentSmoothedZoom = 1.0f
        targetZoomClamped = 1.0f
        smoothedTrackingRatio = 1.0f
        missedFrames = 0
        _dollyState.value = DollyZoomState()
    }

    fun setDirection(direction: DollyDirection) {
        _dollyState.value = _dollyState.value.copy(direction = direction)
    }

    /**
     * Called on each Camera2 TotalCaptureResult frame.
     * Computes real-time apparent size, predictive zoom compensation, and smooth output.
     */
    fun processFrame(
        result: CaptureResult,
        sensorRect: Rect?,
        minAvailableZoom: Float = 1.0f,
        maxAvailableZoom: Float = 8.0f
    ): Float? {
        val state = _dollyState.value
        if (!state.isCalibrated || !state.isTracking) return null

        if (maxAvailableZoom <= minAvailableZoom + 0.05f) {
            _dollyState.value = state.copy(statusPrompt = "Continuous zoom not supported by hardware")
            return null
        }

        // 1. Filter optical lens focus distance
        val diopters = result.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: 0f
        if (diopters > 0.01f) {
            val rawDist = (1.0f / diopters).coerceIn(0.25f, 15.0f)
            val newDist = filteredDistanceMeters * (1f - DISTANCE_FILTER_ALPHA) + (rawDist * DISTANCE_FILTER_ALPHA)
            distanceVelocity = newDist - filteredDistanceMeters
            prevFilteredDistance = filteredDistanceMeters
            filteredDistanceMeters = newDist
        }

        // 2. Real-time apparent size calculation & tracking
        val faces = result.get(CaptureResult.STATISTICS_FACES)
        var matchedFace: Face? = null
        if (isFaceLocked && faces != null && faces.isNotEmpty()) {
            matchedFace = faces.firstOrNull { it.id == lockedFaceId } ?: faces.firstOrNull()
        }

        var trackingConfidence = 0.85f
        var currentBounds = state.subjectBounds

        if (isFaceLocked && matchedFace != null && sensorRect != null && sensorRect.width() > 0) {
            missedFrames = 0
            val instantaneousScale = matchedFace.bounds.width().toFloat() / sensorRect.width().toFloat()
            val newScale = filteredSubjectScale * (1f - SCALE_FILTER_ALPHA) + (instantaneousScale * SCALE_FILTER_ALPHA)
            scaleVelocity = newScale - filteredSubjectScale
            prevFilteredScale = filteredSubjectScale
            filteredSubjectScale = newScale

            // Predictive apparent size: anticipate movement lookahead to eliminate lag
            val predictedScale = (filteredSubjectScale + scaleVelocity * PREDICTIVE_LOOKAHEAD_FRAMES).coerceAtLeast(0.02f)

            // Vertigo formula: To maintain constant subject apparent size:
            // Target Ratio = Reference Scale / Predicted Apparent Scale
            val faceRatio = referenceSubjectScale / predictedScale
            smoothedTrackingRatio = smoothedTrackingRatio * (1f - SCALE_FILTER_ALPHA) + (faceRatio * SCALE_FILTER_ALPHA)
            trackingConfidence = 0.95f

            // Update bounding reticle in real-time
            val sW = sensorRect.width().toFloat()
            val sH = sensorRect.height().toFloat()
            currentBounds = RectF(
                (matchedFace.bounds.left - sensorRect.left) / sW,
                (matchedFace.bounds.top - sensorRect.top) / sH,
                (matchedFace.bounds.right - sensorRect.left) / sW,
                (matchedFace.bounds.bottom - sensorRect.top) / sH
            )
        } else {
            // Distance-based predictive zoom compensation
            missedFrames++
            val predictedDist = (filteredDistanceMeters + distanceVelocity * PREDICTIVE_LOOKAHEAD_FRAMES).coerceAtLeast(0.15f)
            val distRatio = predictedDist / max(referenceDistanceMeters, 0.15f)
            val blendFactor = if (isFaceLocked && missedFrames < 30) 0.08f else DISTANCE_FILTER_ALPHA
            smoothedTrackingRatio = smoothedTrackingRatio * (1f - blendFactor) + (distRatio * blendFactor)
            trackingConfidence = if (missedFrames < 30) 0.80f else 0.70f
        }

        // 3. Target zoom calculation
        val rawTargetZoom = referenceZoom * smoothedTrackingRatio
        val boundedTarget = rawTargetZoom.coerceIn(minAvailableZoom, maxAvailableZoom)
        targetZoomClamped = boundedTarget

        // 4. Stable Slew-Rate Limiting for buttery-smooth transition
        val delta = boundedTarget - currentSmoothedZoom
        val adaptiveStep = delta * 0.18f
        val step = adaptiveStep.coerceIn(-MAX_ZOOM_SLEW_PER_FRAME, MAX_ZOOM_SLEW_PER_FRAME)
        currentSmoothedZoom += step

        // 5. Contextual status prompt with boundary awareness
        val atMaxLimit = currentSmoothedZoom >= maxAvailableZoom - 0.05f
        val atMinLimit = currentSmoothedZoom <= minAvailableZoom + 0.05f

        val prompt = when {
            atMaxLimit -> "Max Zoom Limit Reached · Hold Distance"
            atMinLimit -> "Min Zoom Limit Reached · Hold Distance"
            filteredDistanceMeters < referenceDistanceMeters * 0.88f -> "Pushing In · Foreground locked, background expanding"
            filteredDistanceMeters > referenceDistanceMeters * 1.12f -> "Pulling Out · Foreground locked, background compressing"
            else -> "Subject Locked · Move smoothly forwards or backwards"
        }

        _dollyState.value = state.copy(
            currentDistanceMeters = filteredDistanceMeters,
            targetZoom = boundedTarget,
            smoothedZoom = currentSmoothedZoom,
            trackingConfidence = trackingConfidence,
            subjectBounds = currentBounds,
            statusPrompt = prompt
        )

        return currentSmoothedZoom
    }
}
