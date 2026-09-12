package com.example.camera.tracking.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.camera.tracking.model.*
import com.example.camera.tracking.viewmodel.CameraTrackingUiState

/**
 * FIXED UI Overlay for AI Subject Tracking.
 * Stays strictly stationary while the camera image and crop window move beneath.
 */
@Composable
fun CameraControlsOverlay(
    uiState: CameraTrackingUiState,
    onBackToMainCamera: () -> Unit,
    onFlipCamera: () -> Unit,
    onCaptureModeChanged: (CameraCaptureMode) -> Unit,
    onShutterClick: () -> Unit,
    onUnlockTracking: () -> Unit,
    onOpenMediaReview: (CapturedMediaItem) -> Unit,
    onOpenSettings: () -> Unit,
    onToggleAspectRatio: () -> Unit,
    onToggleGimbal: (Boolean) -> Unit,
    onStartCinematicPan: (Float) -> Unit,
    onStopCinematicPan: () -> Unit,
    onCameraLensChanged: (TrackingCameraLens) -> Unit = {},
    onFpsOptionChanged: (TrackingFpsOption) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("camera_controls_overlay"),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP FIXED BAR
        TopFixedControlsBar(
            uiState = uiState,
            onBackToMainCamera = onBackToMainCamera,
            onFlipCamera = onFlipCamera,
            onOpenSettings = onOpenSettings,
            onToggleAspectRatio = onToggleAspectRatio,
            onToggleGimbal = onToggleGimbal,
            onStartCinematicPan = onStartCinematicPan,
            onStopCinematicPan = onStopCinematicPan,
            onCameraLensChanged = onCameraLensChanged,
            onFpsOptionChanged = onFpsOptionChanged
        )

        // CENTER FIXED STATUS HUD
        CenterTrackingHud(
            uiState = uiState,
            onUnlockTracking = onUnlockTracking,
            onStopCinematicPan = onStopCinematicPan
        )

        // BOTTOM FIXED CAMERA BAR
        BottomFixedControlsBar(
            uiState = uiState,
            onCaptureModeChanged = onCaptureModeChanged,
            onShutterClick = onShutterClick,
            onOpenMediaReview = onOpenMediaReview
        )
    }
}

@Composable
private fun TopFixedControlsBar(
    uiState: CameraTrackingUiState,
    onBackToMainCamera: () -> Unit,
    onFlipCamera: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleAspectRatio: () -> Unit,
    onToggleGimbal: (Boolean) -> Unit,
    onStartCinematicPan: (Float) -> Unit,
    onStopCinematicPan: () -> Unit,
    onCameraLensChanged: (TrackingCameraLens) -> Unit = {},
    onFpsOptionChanged: (TrackingFpsOption) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                )
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Upper row: Back, Ultra AI Status Indicator, FPS, Settings & Camera Flip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Back Button to exit AI tracking mode back to regular camera
                IconButton(
                    onClick = onBackToMainCamera,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x44334155))
                        .testTag("back_to_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Exit AI Tracking",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Ultra AI Tracker Status Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                        .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("ultra_ai_indicator")
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (uiState.trackingStatus == TrackingStatus.TRACKING_LOCKED) Color(0xFF00E5FF) else Color(0xFF10B981))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AI TRACKING",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF00E5FF).copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = "3× CROP",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Top action buttons: FPS, Settings Gear, Camera Flip
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E293B),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                    modifier = Modifier
                        .clickable {
                            val options = TrackingFpsOption.values()
                            val nextIdx = (options.indexOf(uiState.selectedFpsOption) + 1) % options.size
                            onFpsOptionChanged(options[nextIdx])
                        }
                        .testTag("fps_toggle_button")
                ) {
                    Text(
                        text = "${uiState.selectedFpsOption.label}",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Settings Gear Button
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x44334155))
                        .testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Settings",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onFlipCamera,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x44334155))
                        .testTag("flip_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Quick feature bar: Aspect Ratio, Gimbal, 5s Pan, Tracking Speed
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Quick Aspect Ratio button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0369A1))
                    .clickable { onToggleAspectRatio() }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
                    .testTag("quick_aspect_btn")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = uiState.aspectRatio.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Quick Digital Gimbal toggle
            val isGimbalOn = uiState.isGimbalEnabled
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isGimbalOn) Color(0xFF059669) else Color(0x44334155))
                .border(
                    1.dp,
                    if (isGimbalOn) Color(0xFF34D399) else Color.Transparent,
                    RoundedCornerShape(12.dp)
                )
                    .clickable { onToggleGimbal(!isGimbalOn) }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
                    .testTag("quick_gimbal_btn")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = if (isGimbalOn) Color.White else Color(0xFF94A3B8),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isGimbalOn) "GIMBAL EIS ON" else "GIMBAL EIS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isGimbalOn) Color.White else Color(0xFF94A3B8)
                    )
                }
            }

            // Quick 5s Cinematic Pan button
            val isPanning = uiState.isCinematicPanActive
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isPanning) Color(0xFF4F46E5) else Color(0x44334155))
                    .clickable {
                        if (isPanning) onStopCinematicPan() else onStartCinematicPan(5.0f)
                    }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
                    .testTag("quick_pan_btn")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = if (isPanning) Color(0xFFA5B4FC) else Color(0xFFCBD5E1),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isPanning) "PANNING ${(uiState.cinematicPanProgress * 100).toInt()}%" else "5s PAN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Tracking Speed badge (opens settings)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x44334155))
                    .clickable { onOpenSettings() }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${String.format("%.1f", uiState.trackingIntensity)}× SPD",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE2E8F0)
                    )
                }
            }

            // Quick Lens Selector: 0.5x, 1x, Front
            TrackingCameraLens.values().forEach { lens ->
                val isLensSelected = uiState.selectedLens == lens
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isLensSelected) Color(0xFF7C3AED) else Color(0x44334155))
                        .border(
                            1.dp,
                            if (isLensSelected) Color(0xFFA78BFA) else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onCameraLensChanged(lens) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("quick_lens_${lens.name}")
                ) {
                    Text(
                        text = lens.zoomLabel,
                        fontSize = 11.sp,
                        fontWeight = if (isLensSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isLensSelected) Color.White else Color(0xFFCBD5E1)
                    )
                }
            }

            // Adaptive Learned Subjects Badge
            if (uiState.learnedSubjectsCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F766E).copy(alpha = 0.8f))
                        .border(1.dp, Color(0xFF2DD4BF), RoundedCornerShape(12.dp))
                        .clickable { onOpenSettings() }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "🧠 ${uiState.learnedSubjectsCount} LEARNED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFCCFBF1)
                    )
                }
            }

            // Quick Lens Selector: 0.5x, 1x, Front
            TrackingCameraLens.values().forEach { lens ->
                val isLensSelected = uiState.selectedLens == lens
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isLensSelected) Color(0xFF7C3AED) else Color(0x44334155))
                        .border(
                            1.dp,
                            if (isLensSelected) Color(0xFFA78BFA) else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onCameraLensChanged(lens) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("quick_lens_${lens.name}")
                ) {
                    Text(
                        text = lens.displayName,
                        fontSize = 11.sp,
                        fontWeight = if (isLensSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isLensSelected) Color.White else Color(0xFFCBD5E1)
                    )
                }
            }

            // Adaptive Learned Subjects Badge
            if (uiState.learnedSubjectsCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F766E).copy(alpha = 0.8f))
                        .border(1.dp, Color(0xFF2DD4BF), RoundedCornerShape(12.dp))
                        .clickable { onOpenSettings() }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "🧠 ${uiState.learnedSubjectsCount} LEARNED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFCCFBF1)
                    )
                }
            }

            // Quick Lens Selector: 0.5x, 1x, Front
            TrackingCameraLens.values().forEach { lens ->
                val isLensSelected = uiState.selectedLens == lens
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isLensSelected) Color(0xFF7C3AED) else Color(0x44334155))
                        .border(
                            1.dp,
                            if (isLensSelected) Color(0xFFA78BFA) else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onCameraLensChanged(lens) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("quick_lens_${lens.name}")
                ) {
                    Text(
                        text = lens.zoomLabel,
                        fontSize = 11.sp,
                        fontWeight = if (isLensSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isLensSelected) Color.White else Color(0xFFCBD5E1)
                    )
                }
            }

            // Adaptive Learned Subjects Badge
            if (uiState.learnedSubjectsCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F766E).copy(alpha = 0.8f))
                        .border(1.dp, Color(0xFF2DD4BF), RoundedCornerShape(12.dp))
                        .clickable { onOpenSettings() }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "🧠 ${uiState.learnedSubjectsCount} LEARNED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFCCFBF1)
                    )
                }
            }
        }
    }
}

@Composable
private fun CenterTrackingHud(
    uiState: CameraTrackingUiState,
    onUnlockTracking: () -> Unit,
    onStopCinematicPan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Cinematic Pan Banner
        if (uiState.isCinematicPanActive) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xEE312E81),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA5B4FC)),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CINEMATIC PAN (${uiState.cinematicPanDurationSec.toInt()}s) • ${(uiState.cinematicPanProgress * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0x55EF4444))
                            .clickable { onStopCinematicPan() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Stop pan",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        // Notification Pill
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = when (uiState.trackingStatus) {
                    TrackingStatus.TRACKING_LOCKED -> Color(0xEE0B192C)
                    TrackingStatus.OCCLUDED_PREDICTING -> Color(0xEE2A1B00)
                    TrackingStatus.LOST -> Color(0xEE2D0C0C)
                    else -> Color(0xCC0F172A)
                },
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = when (uiState.trackingStatus) {
                        TrackingStatus.TRACKING_LOCKED -> Color(0xFF00E5FF)
                        TrackingStatus.OCCLUDED_PREDICTING -> Color(0xFFFFB300)
                        TrackingStatus.LOST -> Color(0xFFFF5252)
                        else -> Color(0x3394A3B8)
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusText = when (uiState.trackingStatus) {
                        TrackingStatus.TRACKING_LOCKED -> {
                            val zoomStr = String.format("%.1f", uiState.currentZoom)
                            "AI LOCKED ${zoomStr}× • ${uiState.activeSubject?.label ?: "Subject"} #${uiState.activeSubject?.trackingId ?: ""}"
                        }
                        TrackingStatus.OCCLUDED_PREDICTING -> "OCCLUDED • PREDICTING TRAJECTORY"
                        TrackingStatus.LOST -> "SEARCHING • AUTO REACQUIRING..."
                        else -> "DETECTING SUBJECTS • TAP TO LOCK"
                    }
                    Text(
                        text = statusText,
                        color = when (uiState.trackingStatus) {
                            TrackingStatus.TRACKING_LOCKED -> Color(0xFF00E5FF)
                            TrackingStatus.OCCLUDED_PREDICTING -> Color(0xFFFFD54F)
                            TrackingStatus.LOST -> Color(0xFFFF8A80)
                            else -> Color(0xFFE2E8F0)
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (uiState.trackingStatus != TrackingStatus.IDLE) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FFFFFF))
                                .clickable { onUnlockTracking() }
                                .testTag("unlock_tracking_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Unlock tracking",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // Gimbal Active Badge
        if (uiState.isGimbalEnabled) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0x99064E3B)
            ) {
                Text(
                    text = "✓ EIS GIMBAL STABILIZED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6EE7B7),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        // Recording indicator if active
        if (uiState.isRecording) {
            Spacer(modifier = Modifier.height(8.dp))
            val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "rec_alpha"
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Red.copy(alpha = 0.9f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = pulseAlpha))
                )
                Spacer(modifier = Modifier.width(6.dp))
                val minutes = uiState.recordingDurationSec / 60
                val seconds = uiState.recordingDurationSec % 60
                Text(
                    text = String.format("REC %02d:%02d (3× CROP)", minutes, seconds),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun BottomFixedControlsBar(
    uiState: CameraTrackingUiState,
    onCaptureModeChanged: (CameraCaptureMode) -> Unit,
    onShutterClick: () -> Unit,
    onOpenMediaReview: (CapturedMediaItem) -> Unit,
    onCameraLensChanged: (TrackingCameraLens) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.90f))
                )
            )
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Lens Quick Switcher: 0.5× Ultra-Wide, 1.0× Wide, Front Selfie
        Row(
            modifier = Modifier.padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            uiState.availableLenses.forEach { lens ->
                val isSelected = uiState.selectedLens == lens
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.25f) else Color(0x33000000),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF)) else null,
                    modifier = Modifier
                        .clickable { onCameraLensChanged(lens) }
                        .testTag("lens_quick_${lens.name}")
                ) {
                    Text(
                        text = lens.zoomLabel,
                        color = if (isSelected) Color(0xFF00E5FF) else Color(0xFFE2E8F0),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Mode Selector: PHOTO / VIDEO
        Row(
            modifier = Modifier.padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PHOTO",
                fontSize = 14.sp,
                fontWeight = if (uiState.captureMode == CameraCaptureMode.PHOTO) FontWeight.Bold else FontWeight.Normal,
                color = if (uiState.captureMode == CameraCaptureMode.PHOTO) Color(0xFF00E5FF) else Color(0x88FFFFFF),
                modifier = Modifier
                    .clickable { onCaptureModeChanged(CameraCaptureMode.PHOTO) }
                    .testTag("mode_photo_button")
            )
            Text(
                text = "VIDEO",
                fontSize = 14.sp,
                fontWeight = if (uiState.captureMode == CameraCaptureMode.VIDEO) FontWeight.Bold else FontWeight.Normal,
                color = if (uiState.captureMode == CameraCaptureMode.VIDEO) Color(0xFF00E5FF) else Color(0x88FFFFFF),
                modifier = Modifier
                    .clickable { onCaptureModeChanged(CameraCaptureMode.VIDEO) }
                    .testTag("mode_video_button")
            )
        }

        // Action Row: Media Review Gallery, Shutter Button, Quick Info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Media Review Thumbnail Button
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E293B))
                    .border(1.5.dp, Color(0x5500E5FF), RoundedCornerShape(12.dp))
                    .clickable {
                        uiState.lastCapturedMedia?.let { onOpenMediaReview(it) }
                    }
                    .testTag("media_gallery_button"),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.lastCapturedMedia != null) {
                    AsyncImage(
                        model = uiState.lastCapturedMedia.uri,
                        contentDescription = "Last captured media",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Inspect Output",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Center Shutter Button
            ShutterButton(
                captureMode = uiState.captureMode,
                isRecording = uiState.isRecording,
                onClick = onShutterClick
            )

            // Right side: Active Zoom Level readout
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(54.dp)
            ) {
                Text(
                    text = String.format("%.1f×", uiState.currentZoom),
                    color = if (uiState.currentZoom > 1.2f) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (uiState.currentZoom > 1.2f) "3× CROP" else "WIDE",
                    color = Color(0x66FFFFFF),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ShutterButton(
    captureMode: CameraCaptureMode,
    isRecording: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(6.dp)
            .clip(CircleShape)
            .background(Color.Transparent)
            .clickable { onClick() }
            .testTag("shutter_button"),
        contentAlignment = Alignment.Center
    ) {
        if (captureMode == CameraCaptureMode.PHOTO) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color.White)
            )
        } else {
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Red)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.Red)
                )
            }
        }
    }
}
