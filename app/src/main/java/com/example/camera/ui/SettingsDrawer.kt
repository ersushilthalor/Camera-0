package com.example.camera.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.model.*

/**
 * Categorized Navigation Pages for the Redesigned Settings Sheet.
 */
enum class SettingsSubPage(val title: String, val subtitle: String, val icon: ImageVector) {
    PHOTO("Photo Settings", "50MP computational, RAW sensor, selfie mirror & grid", Icons.Outlined.CameraAlt),
    VIDEO("Video Settings", "Resolution, frame rates, bitrate & stereo audio", Icons.Outlined.Videocam),
    STABILIZATION("Hybrid Stabilization", "Coordinated physical OIS + electronic EIS", Icons.Outlined.HdrAuto),
    FOCUS_EXPOSURE("Focus & Exposure", "Tap to focus, AE/AF Lock & exposure reticle", Icons.Outlined.CenterFocusStrong),
    NIGHT_MODE("Night Mode", "Multi-frame burst fusion & exposure duration", Icons.Outlined.NightsStay),
    CINEMA_LOG("Cinema & 10-Bit Log", "10-bit HLG, zebra stripes, peaking & waveforms", Icons.Outlined.MovieCreation),
    UI_CUSTOMIZATION("Camera UI & Layout", "iPhone, Samsung, Vivo templates & visual layout editor", Icons.Outlined.DashboardCustomize),
    HARDWARE("Hardware & Diagnostics", "Aux lens discovery & Camera2 HAL diagnostics", Icons.Outlined.Memory)
}

/**
 * Modern, Minimalist & Categorized Settings Sheet.
 * Navigates into dedicated sub-pages for each category to keep the interface clean,
 * uncluttered, and premium.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDrawer(
    isOpen: Boolean,
    cameraMode: CameraMode,
    capabilities: HardwareCapabilities,
    availableLenses: List<LensInfo> = emptyList(),
    selectedLens: LensInfo? = null,
    selectedPhotoResolution: CameraResolution?,
    selectedVideoResolution: CameraResolution?,
    photoMegapixelMode: PhotoMegapixelMode = PhotoMegapixelMode.M12,
    videoFps: Int,
    videoBitrate: VideoBitrateOption,
    isVideoStabilizationEnabled: Boolean,
    isAudioEnabled: Boolean,
    isRawEnabled: Boolean,
    saveSelfieAsPreviewed: Boolean = true,
    gridType: GridType = GridType.NONE,
    cinemaConfig: CinemaConfig = CinemaConfig(),
    cinemaCapabilities: CinemaHardwareCapabilities = CinemaHardwareCapabilities(),
    viewfinderResolution: ViewfinderResolution = ViewfinderResolution.NORMAL,
    hybridStabilizationConfig: HybridStabilizationConfig = HybridStabilizationConfig(),
    nightConfig: NightConfig = NightConfig(),
    tapFocusConfig: TapFocusConfig = TapFocusConfig(),
    onLensSelected: (LensInfo) -> Unit = {},
    onForceDeepScan: () -> Unit = {},
    onPhotoResolutionSelected: (CameraResolution) -> Unit,
    onPhotoMegapixelModeSelected: (PhotoMegapixelMode) -> Unit = {},
    onVideoResolutionSelected: (CameraResolution) -> Unit,
    onViewfinderResolutionSelected: (ViewfinderResolution) -> Unit = {},
    onVideoFpsSelected: (Int) -> Unit,
    onVideoBitrateSelected: (VideoBitrateOption) -> Unit,
    onStabilizationToggle: (Boolean) -> Unit,
    onHybridStabilizationChange: (HybridStabilizationConfig) -> Unit = {},
    onNightConfigChange: (NightConfig) -> Unit = {},
    onTapFocusConfigChange: (TapFocusConfig) -> Unit = {},
    onAudioToggle: () -> Unit,
    onRawToggle: () -> Unit,
    onSaveSelfieAsPreviewedToggle: (Boolean) -> Unit = {},
    onGridTypeSelected: (GridType) -> Unit = {},
    onCinemaConfigChange: (CinemaConfig) -> Unit = {},
    uiCustomizationState: UiCustomizationState = UiCustomizationState(),
    onSelectTemplate: (UiTemplateType) -> Unit = {},
    onUpdateGlobalLayoutConfig: (ModeLayoutConfig) -> Unit = {},
    onUpdateModeLayoutConfig: (CameraMode, ModeLayoutConfig) -> Unit = { _, _ -> },
    onResetModeLayoutConfig: (CameraMode) -> Unit = {},
    onSaveCustomPreset: (String, ModeLayoutConfig) -> Unit = { _, _ -> },
    onLoadCustomPreset: (CustomUiPreset) -> Unit = {},
    onDeleteCustomPreset: (String) -> Unit = {},
    onResetAllToTemplate: (UiTemplateType) -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    var activeSubPage by remember { mutableStateOf<SettingsSubPage?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFFF8F9FA),
        contentColor = Color(0xFF1F2937),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color(0xFFD1D5DB))
        },
        modifier = modifier.testTag("settings_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (activeSubPage != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { activeSubPage = null },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE5E7EB))
                                .testTag("settings_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFF1F2937),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = activeSubPage?.title ?: "Settings",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111827)
                            )
                            Text(
                                text = "Settings Category",
                                fontSize = 11.5.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }
                } else {
                    Column {
                        Text(
                            text = "Settings",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827)
                        )
                        Text(
                            text = "Camera preferences & capture engines",
                            fontSize = 12.sp,
                            color = Color(0xFF6B7280)
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE5E7EB))
                        .testTag("settings_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF374151),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Animated Body: Directory vs Dedicated SubPage
            AnimatedContent(
                targetState = activeSubPage,
                transitionSpec = {
                    if (targetState != null) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                    }
                },
                label = "settingsPageTransition"
            ) { subPage ->
                if (subPage == null) {
                    // MAIN DIRECTORY LIST
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SettingsSubPage.entries.forEach { page ->
                            item(key = page.name) {
                                val summary = when (page) {
                                    SettingsSubPage.PHOTO -> if (photoMegapixelMode == PhotoMegapixelMode.M50) "50MP Computational" else "12MP Standard"
                                    SettingsSubPage.VIDEO -> "${selectedVideoResolution?.let { "${it.width}x${it.height}" } ?: "4K"} · ${videoFps} FPS"
                                    SettingsSubPage.STABILIZATION -> if (isVideoStabilizationEnabled && hybridStabilizationConfig.isHybridEnabled) "Coordinated OIS + EIS" else if (isVideoStabilizationEnabled) "Standard EIS" else "Off"
                                    SettingsSubPage.FOCUS_EXPOSURE -> if (tapFocusConfig.isTapToFocusEnabled) "Tap to Focus ON" else "Continuous AF"
                                    SettingsSubPage.NIGHT_MODE -> "${nightConfig.durationSeconds}s Duration · Multi-Frame"
                                    SettingsSubPage.CINEMA_LOG -> "${cinemaConfig.colorProfile.label} · ${cinemaConfig.logBitDepth.label}"
                                    SettingsSubPage.UI_CUSTOMIZATION -> "${uiCustomizationState.selectedTemplate.title} · ${if (uiCustomizationState.modeSpecificConfigs.isNotEmpty()) "${uiCustomizationState.modeSpecificConfigs.size} custom modes" else "Active Layout"}"
                                    SettingsSubPage.HARDWARE -> "${availableLenses.size} Lenses · Full HAL"
                                }

                                SettingsCategoryTile(
                                    page = page,
                                    summary = summary,
                                    onClick = { activeSubPage = page }
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(28.dp))
                        }
                    }
                } else {
                    // DEDICATED SUBPAGE
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        when (subPage) {
                            SettingsSubPage.PHOTO -> {
                                item {
                                    SettingsSectionCard(title = "Capture Quality & Resolution") {
                                        LightToggleRow(
                                            title = "50MP Computational Mode",
                                            subtitle = "Single-frame native capture with edge-aware detail synthesis",
                                            isChecked = photoMegapixelMode == PhotoMegapixelMode.M50,
                                            onToggle = {
                                                val next = if (photoMegapixelMode == PhotoMegapixelMode.M50) {
                                                    PhotoMegapixelMode.M12
                                                } else {
                                                    PhotoMegapixelMode.M50
                                                }
                                                onPhotoMegapixelModeSelected(next)
                                            }
                                        )

                                        HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                                        if (capabilities.supportsRaw) {
                                            LightToggleRow(
                                                title = "RAW (DNG) Sensor Capture",
                                                subtitle = "Saves uncompressed 16-bit linear sensor DNG alongside JPEG",
                                                isChecked = isRawEnabled,
                                                onToggle = { onRawToggle() }
                                            )
                                            HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)
                                        }

                                        LightToggleRow(
                                            title = "Save Selfie as Previewed",
                                            subtitle = "Saves front camera captures with exact preview orientation without inversion",
                                            isChecked = saveSelfieAsPreviewed,
                                            onToggle = { onSaveSelfieAsPreviewedToggle(!saveSelfieAsPreviewed) }
                                        )
                                    }
                                }

                                item {
                                    SettingsSectionCard(title = "Composition & Grid Assist") {
                                        Text(
                                            text = "Viewfinder Framing Grid",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1F2937),
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            GridType.entries.forEach { type ->
                                                val isSelected = gridType == type
                                                LightSelectPill(
                                                    label = type.title,
                                                    isSelected = isSelected,
                                                    onClick = { onGridTypeSelected(type) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            SettingsSubPage.VIDEO -> {
                                item {
                                    SettingsSectionCard(title = "Recording Format & Frame Rates") {
                                        Text(
                                            text = "Frame Rate (FPS)",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1F2937),
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            capabilities.supportedFpsRanges.forEach { fps ->
                                                val isSelected = videoFps == fps
                                                LightSelectPill(
                                                    label = "$fps FPS",
                                                    isSelected = isSelected,
                                                    onClick = { onVideoFpsSelected(fps) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }

                                        HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                                        Text(
                                            text = "Encoding Bitrate",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1F2937),
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            VideoBitrateOption.entries.forEach { option ->
                                                val isSelected = videoBitrate == option
                                                LightSelectPill(
                                                    label = option.title,
                                                    isSelected = isSelected,
                                                    onClick = { onVideoBitrateSelected(option) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }

                                        HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                                        LightToggleRow(
                                            title = "Record Audio",
                                            subtitle = "High-fidelity AAC 48kHz stereo microphone track",
                                            isChecked = isAudioEnabled,
                                            onToggle = { onAudioToggle() }
                                        )
                                    }
                                }
                            }

                            SettingsSubPage.STABILIZATION -> {
                                item {
                                    SettingsSectionCard(title = "Hybrid Stabilization Engine") {
                                        val hasStab = capabilities.supportsEis || capabilities.supportsOis
                                        LightToggleRow(
                                            title = "Video Stabilization Master",
                                            subtitle = if (hasStab) "Enables hardware and software shake compensation" else "Hardware not supported",
                                            isChecked = isVideoStabilizationEnabled && hasStab,
                                            enabled = hasStab,
                                            onToggle = { onStabilizationToggle(!isVideoStabilizationEnabled) }
                                        )

                                        HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                                        LightToggleRow(
                                            title = "Coordinated Hybrid Mode",
                                            subtitle = "Coordinates physical OIS gyro actuators with electronic EIS margins together",
                                            isChecked = hybridStabilizationConfig.isHybridEnabled,
                                            enabled = isVideoStabilizationEnabled,
                                            onToggle = {
                                                val next = hybridStabilizationConfig.copy(isHybridEnabled = !hybridStabilizationConfig.isHybridEnabled)
                                                onHybridStabilizationChange(next)
                                            }
                                        )

                                        HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                                        LightToggleRow(
                                            title = "Prefer Physical OIS",
                                            subtitle = "Utilizes optical voice-coil sensor shift for low-light stability",
                                            isChecked = hybridStabilizationConfig.isOisPreferred,
                                            enabled = capabilities.supportsOis && isVideoStabilizationEnabled,
                                            onToggle = {
                                                val next = hybridStabilizationConfig.copy(isOisPreferred = !hybridStabilizationConfig.isOisPreferred)
                                                onHybridStabilizationChange(next)
                                            }
                                        )

                                        HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                                        LightToggleRow(
                                            title = "Prefer Electronic EIS",
                                            subtitle = "Applies real-time frame warping and edge stabilization",
                                            isChecked = hybridStabilizationConfig.isEisPreferred,
                                            enabled = capabilities.supportsEis && isVideoStabilizationEnabled,
                                            onToggle = {
                                                val next = hybridStabilizationConfig.copy(isEisPreferred = !hybridStabilizationConfig.isEisPreferred)
                                                onHybridStabilizationChange(next)
                                            }
                                        )

                                        HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                                        LightToggleRow(
                                            title = "Ultra Stabilization",
                                            subtitle = "High-frequency physical gyro trajectory tracking with dynamic counter-steer reframing on top of EIS",
                                            isChecked = hybridStabilizationConfig.isUltraStabilizationEnabled,
                                            enabled = isVideoStabilizationEnabled,
                                            onToggle = {
                                                val next = hybridStabilizationConfig.copy(isUltraStabilizationEnabled = !hybridStabilizationConfig.isUltraStabilizationEnabled)
                                                onHybridStabilizationChange(next)
                                            }
                                        )

                                        HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                                        LightToggleRow(
                                            title = "Adaptive FPS & Resolution",
                                            subtitle = "Dynamically adjusts crop margin on 4K 60fps to eliminate edge stretching",
                                            isChecked = hybridStabilizationConfig.isAdaptiveFpsLens,
                                            onToggle = {
                                                val next = hybridStabilizationConfig.copy(isAdaptiveFpsLens = !hybridStabilizationConfig.isAdaptiveFpsLens)
                                                onHybridStabilizationChange(next)
                                            }
                                        )
                                    }
                                }

                                item {
                                    SettingsSectionCard(title = "Hardware Status") {
                                        LightSpecItem("Physical Optical Stabilization (OIS)", if (capabilities.supportsOis) "Supported & Active" else "Not Present")
                                        LightSpecItem("Electronic Video Stabilization (EIS)", if (capabilities.supportsEis) "Supported & Active" else "Not Present")
                                        LightSpecItem("Ultra Stabilization Engine", if (hybridStabilizationConfig.isUltraStabilizationEnabled) "Active (Gyro + EIS)" else "Standby")
                                    }
                                }
                            }

                            SettingsSubPage.FOCUS_EXPOSURE -> {
                                item {
                                    SettingsSectionCard(title = "Focus & Metering Controls") {
                                        LightToggleRow(
                                            title = "Tap to Focus & Meter",
                                            subtitle = "Centers 240px auto-focus and auto-exposure rectangle on touched area",
                                            isChecked = tapFocusConfig.isTapToFocusEnabled,
                                            onToggle = {
                                                val next = tapFocusConfig.copy(isTapToFocusEnabled = !tapFocusConfig.isTapToFocusEnabled)
                                                onTapFocusConfigChange(next)
                                            }
                                        )

                                        HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                                        LightToggleRow(
                                            title = "Long Press AE/AF Lock",
                                            subtitle = "Locks exposure and focus distance upon holding reticle for 500ms",
                                            isChecked = tapFocusConfig.isAeAfLockEnabled,
                                            onToggle = {
                                                val next = tapFocusConfig.copy(isAeAfLockEnabled = !tapFocusConfig.isAeAfLockEnabled)
                                                onTapFocusConfigChange(next)
                                            }
                                        )

                                        HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                                        LightToggleRow(
                                            title = "Reticle Sun Exposure Slider",
                                            subtitle = "Shows interactive exposure adjustment sun icon adjacent to focus reticle",
                                            isChecked = tapFocusConfig.isSunExposureSliderEnabled,
                                            onToggle = {
                                                val next = tapFocusConfig.copy(isSunExposureSliderEnabled = !tapFocusConfig.isSunExposureSliderEnabled)
                                                onTapFocusConfigChange(next)
                                            }
                                        )

                                        HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                                        LightToggleRow(
                                            title = "Auto-Dismiss Reticle",
                                            subtitle = "Automatically dismisses focus indicator after 2.4s if not locked",
                                            isChecked = tapFocusConfig.autoDismissReticle,
                                            onToggle = {
                                                val next = tapFocusConfig.copy(autoDismissReticle = !tapFocusConfig.autoDismissReticle)
                                                onTapFocusConfigChange(next)
                                            }
                                        )
                                    }
                                }
                            }

                            SettingsSubPage.NIGHT_MODE -> {
                                item {
                                    SettingsSectionCard(title = "Computational Night Pipeline") {
                                        Text(
                                            text = "Exposure Duration (Seconds)",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1F2937),
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(1, 2, 3, 4, 5).forEach { sec ->
                                                val isSelected = nightConfig.durationSeconds == sec
                                                LightSelectPill(
                                                    label = "${sec}s",
                                                    isSelected = isSelected,
                                                    onClick = { onNightConfigChange(nightConfig.copy(durationSeconds = sec)) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }

                                        HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                                        LightToggleRow(
                                            title = "Multi-Frame Burst Fusion",
                                            subtitle = "Captures 4-12 aligned frames and averages pixel noise",
                                            isChecked = nightConfig.multiFrameFusionEnabled,
                                            onToggle = {
                                                val next = nightConfig.copy(multiFrameFusionEnabled = !nightConfig.multiFrameFusionEnabled)
                                                onNightConfigChange(next)
                                            }
                                        )

                                        HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                                        LightToggleRow(
                                            title = "Hand-Shake Anti-Ghosting",
                                            subtitle = "Filters misaligned motion vectors and moving subjects",
                                            isChecked = nightConfig.antiGhostingEnabled,
                                            onToggle = {
                                                val next = nightConfig.copy(antiGhostingEnabled = !nightConfig.antiGhostingEnabled)
                                                onNightConfigChange(next)
                                            }
                                        )

                                        HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Spatial Noise Suppression",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF1F2937)
                                                )
                                                Text(
                                                    text = "${(nightConfig.noiseSuppression * 100).toInt()}%",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF1A73E8)
                                                )
                                            }
                                            Slider(
                                                value = nightConfig.noiseSuppression,
                                                onValueChange = { onNightConfigChange(nightConfig.copy(noiseSuppression = it)) },
                                                valueRange = 0.2f..1.0f,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = Color(0xFF1A73E8),
                                                    activeTrackColor = Color(0xFF1A73E8)
                                                )
                                            )
                                        }

                                        HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Shadow Detail Lift",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF1F2937)
                                                )
                                                Text(
                                                    text = "%.2fx".format(nightConfig.shadowLift),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF1A73E8)
                                                )
                                            }
                                            Slider(
                                                value = nightConfig.shadowLift,
                                                onValueChange = { onNightConfigChange(nightConfig.copy(shadowLift = it)) },
                                                valueRange = 1.0f..2.0f,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = Color(0xFF1A73E8),
                                                    activeTrackColor = Color(0xFF1A73E8)
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            SettingsSubPage.CINEMA_LOG -> {
                                item {
                                    SettingsSectionCard(title = "Professional Monitoring & Color") {
                                        val can10Bit = cinemaCapabilities.supports10BitRecording
                                        LightToggleRow(
                                            title = "10-Bit Log Color Depth",
                                            subtitle = if (can10Bit) "10-bit HLG profile active" else "8-bit standard (Device limited)",
                                            isChecked = cinemaConfig.logBitDepth == LogBitDepth.BIT_10 && can10Bit,
                                            enabled = can10Bit,
                                            onToggle = {
                                                val nextDepth = if (cinemaConfig.logBitDepth == LogBitDepth.BIT_10) {
                                                    LogBitDepth.BIT_8
                                                } else {
                                                    LogBitDepth.BIT_10
                                                }
                                                onCinemaConfigChange(cinemaConfig.copy(logBitDepth = nextDepth))
                                            }
                                        )

                                        HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                                        LightToggleRow(
                                            title = "Focus Peaking Assist",
                                            subtitle = "Highlights in-focus edges with high-contrast color outline",
                                            isChecked = cinemaConfig.isFocusPeakingEnabled,
                                            onToggle = {
                                                onCinemaConfigChange(cinemaConfig.copy(isFocusPeakingEnabled = !cinemaConfig.isFocusPeakingEnabled))
                                            }
                                        )

                                        HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                                        val isZebraOn = cinemaConfig.zebraThreshold != ZebraThreshold.OFF
                                        LightToggleRow(
                                            title = "Zebra Exposure Stripes",
                                            subtitle = "Overlays diagonal stripes on overexposed highlight zones",
                                            isChecked = isZebraOn,
                                            onToggle = {
                                                val next = if (isZebraOn) ZebraThreshold.OFF else ZebraThreshold.IRE_70
                                                onCinemaConfigChange(cinemaConfig.copy(zebraThreshold = next))
                                            }
                                        )

                                        HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                                        LightToggleRow(
                                            title = "Waveform Luminance Monitor",
                                            subtitle = "Real-time luminance IRE waveform monitor overlay",
                                            isChecked = cinemaConfig.isWaveformEnabled,
                                            onToggle = {
                                                onCinemaConfigChange(cinemaConfig.copy(isWaveformEnabled = !cinemaConfig.isWaveformEnabled))
                                            }
                                        )
                                    }
                                }

                                item {
                                    SettingsSectionCard(title = "Hardware Color Profile") {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            CinemaColorProfile.entries.forEach { profile ->
                                                val isSelected = cinemaConfig.colorProfile == profile
                                                LightOptionRow(
                                                    title = profile.label,
                                                    subtitle = profile.description,
                                                    isSelected = isSelected,
                                                    onClick = {
                                                        onCinemaConfigChange(cinemaConfig.copy(colorProfile = profile))
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            SettingsSubPage.UI_CUSTOMIZATION -> {
                                item {
                                    CameraUiCustomizationView(
                                        uiState = uiCustomizationState,
                                        currentCameraMode = cameraMode,
                                        onSelectTemplate = onSelectTemplate,
                                        onUpdateGlobalConfig = onUpdateGlobalLayoutConfig,
                                        onUpdateModeConfig = onUpdateModeLayoutConfig,
                                        onResetModeConfig = onResetModeLayoutConfig,
                                        onSavePreset = onSaveCustomPreset,
                                        onLoadPreset = onLoadCustomPreset,
                                        onDeletePreset = onDeleteCustomPreset,
                                        onResetAllToTemplate = onResetAllToTemplate
                                    )
                                }
                            }

                            SettingsSubPage.HARDWARE -> {
                                item {
                                    SettingsSectionCard(title = "Viewfinder Resolution & Performance") {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            ViewfinderResolution.entries.forEach { resOption ->
                                                val isSelected = viewfinderResolution == resOption
                                                LightSelectPill(
                                                    label = resOption.label,
                                                    isSelected = isSelected,
                                                    onClick = { onViewfinderResolutionSelected(resOption) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }

                                item {
                                    SettingsSectionCard(title = "Hardware Lens Discovery") {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Deep Scan Aux Lenses",
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF1F2937)
                                                )
                                                Text(
                                                    text = "Bypasses OEM package filter to uncover physical cameras",
                                                    fontSize = 11.5.sp,
                                                    color = Color(0xFF6B7280)
                                                )
                                            }
                                            Button(
                                                onClick = onForceDeepScan,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFF1A73E8),
                                                    contentColor = Color.White
                                                ),
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                            ) {
                                                Text("Scan", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                item {
                                    SettingsSectionCard(title = "Camera2 Hardware Specs") {
                                        LightSpecItem("Manual Sensor Control", if (capabilities.supportsManualSensor) "Supported" else "Limited")
                                        LightSpecItem("RAW Sensor Capture", if (capabilities.supportsRaw) "Supported (DNG)" else "Not Available")
                                        LightSpecItem("Optical Stabilization (OIS)", if (capabilities.supportsOis) "Supported" else "No")
                                        LightSpecItem("Electronic Stabilization (EIS)", if (capabilities.supportsEis) "Supported" else "No")
                                        LightSpecItem("Max Digital Zoom", "%.1fx".format(capabilities.maxZoom))
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(28.dp))
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// STYLED REUSABLE LIGHT-MODE COMPONENTS
// ============================================================================

@Composable
private fun SettingsCategoryTile(
    page: SettingsSubPage,
    summary: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE8F0FE)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = page.icon,
                        contentDescription = page.title,
                        tint = Color(0xFF1A73E8),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = page.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = page.subtitle,
                        fontSize = 11.5.sp,
                        color = Color(0xFF6B7280),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFF3F4F6)
                    ) {
                        Text(
                            text = summary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF4B5563),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = "Open",
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827)
            )
            content()
        }
    }
}

@Composable
private fun LightToggleRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    enabled: Boolean = true,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onToggle() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) Color(0xFF1F2937) else Color(0xFF9CA3AF)
            )
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                color = if (enabled) Color(0xFF6B7280) else Color(0xFFD1D5DB),
                lineHeight = 15.sp
            )
        }

        Switch(
            checked = isChecked,
            onCheckedChange = { onToggle() },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF1A73E8),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFD1D5DB)
            )
        )
    }
}

@Composable
private fun LightSelectPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) Color(0xFF1A73E8) else Color(0xFFF3F4F6),
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
        modifier = modifier
            .height(36.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else Color(0xFF374151)
            )
        }
    }
}

@Composable
private fun LightOptionRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) Color(0xFFE8F0FE) else Color(0xFFF9FAFB),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) Color(0xFF1A73E8) else Color(0xFFE5E7EB)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) Color(0xFF1A73E8) else Color(0xFF1F2937)
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280)
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Selected",
                    tint = Color(0xFF1A73E8),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun LightSpecItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            color = Color(0xFF4B5563)
        )
        Text(
            text = value,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF111827)
        )
    }
}
