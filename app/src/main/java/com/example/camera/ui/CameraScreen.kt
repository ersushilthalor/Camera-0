package com.example.camera.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.camera.model.*
import com.example.camera.ui.components.FrostedGlassBox
import com.example.camera.viewmodel.CameraViewModel

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Permission handling
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasAudioPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            viewModel.safeInitializeCamera()
        }
    }

    if (!hasCameraPermission) {
        CameraPermissionPrompt(
            onRequestPermission = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                    )
                )
            },
            modifier = modifier
        )
        return
    }

    // Engine & VM States
    val cameraMode by viewModel.cameraMode.collectAsStateWithLifecycle()
    val capabilities by viewModel.engine.capabilities.collectAsStateWithLifecycle()
    val selectedPhotoResolution by viewModel.engine.selectedPhotoResolution.collectAsStateWithLifecycle()
    val selectedVideoResolution by viewModel.engine.selectedVideoResolution.collectAsStateWithLifecycle()
    val previewAspectRatio by viewModel.engine.previewAspectRatio.collectAsStateWithLifecycle()
    val previewBufferSize by viewModel.engine.previewBufferSize.collectAsStateWithLifecycle()
    val sensorOrientation by viewModel.engine.sensorOrientation.collectAsStateWithLifecycle()
    val storageStats by viewModel.engine.storageStats.collectAsStateWithLifecycle()
    val isRecordingVideo by viewModel.engine.isRecordingVideo.collectAsStateWithLifecycle()
    val videoDurationSeconds by viewModel.engine.videoDurationSeconds.collectAsStateWithLifecycle()
    val isCapturing by viewModel.engine.isCapturing.collectAsStateWithLifecycle()
    val lastCapturedMedia by viewModel.engine.lastCapturedMedia.collectAsStateWithLifecycle()

    val portraitConfig by viewModel.portraitConfig.collectAsStateWithLifecycle()
    val portraitProcessingState by viewModel.portraitProcessingState.collectAsStateWithLifecycle()
    val isPortraitSettingsOpen by viewModel.isPortraitSettingsOpen.collectAsStateWithLifecycle()
    val saveSelfieAsPreviewed by viewModel.saveSelfieAsPreviewed.collectAsStateWithLifecycle()
    val photoMegapixelMode by viewModel.photoMegapixelMode.collectAsStateWithLifecycle()
    val isVideoSettingsPanelOpen by viewModel.isVideoSettingsPanelOpen.collectAsStateWithLifecycle()

    val cinemaConfig by viewModel.cinemaConfig.collectAsStateWithLifecycle()
    val cinemaCapabilities by viewModel.cinemaCapabilities.collectAsStateWithLifecycle()
    val isCinemaSettingsOpen by viewModel.isCinemaSettingsOpen.collectAsStateWithLifecycle()
    val isMoreModesOpen by viewModel.isMoreModesOpen.collectAsStateWithLifecycle()

    val flashMode by viewModel.flashMode.collectAsStateWithLifecycle()
    val timerMode by viewModel.timerMode.collectAsStateWithLifecycle()
    val activeTimerCountdown by viewModel.activeTimerCountdown.collectAsStateWithLifecycle()
    val gridType by viewModel.gridType.collectAsStateWithLifecycle()
    val isManualProOpen by viewModel.isManualProOpen.collectAsStateWithLifecycle()
    val activeProTab by viewModel.activeProTab.collectAsStateWithLifecycle()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsStateWithLifecycle()
    val isMediaViewerOpen by viewModel.isMediaViewerOpen.collectAsStateWithLifecycle()
    val focusRingPoint by viewModel.focusRingPoint.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    val exposureCompensation by viewModel.exposureCompensation.collectAsStateWithLifecycle()
    val manualIso by viewModel.manualIso.collectAsStateWithLifecycle()
    val manualShutterSpeedNs by viewModel.manualShutterSpeedNs.collectAsStateWithLifecycle()
    val whiteBalance by viewModel.whiteBalance.collectAsStateWithLifecycle()
    val focusMode by viewModel.focusMode.collectAsStateWithLifecycle()
    val manualFocusDistance by viewModel.manualFocusDistance.collectAsStateWithLifecycle()
    val isAeLocked by viewModel.isAeLocked.collectAsStateWithLifecycle()
    val isAfLocked by viewModel.isAfLocked.collectAsStateWithLifecycle()
    val isRawEnabled by viewModel.isRawCaptureEnabled.collectAsStateWithLifecycle()
    val isVideoStabilizationEnabled by viewModel.isVideoStabilizationEnabled.collectAsStateWithLifecycle()
    val videoBitrate by viewModel.videoBitrateOption.collectAsStateWithLifecycle()
    val videoFps by viewModel.videoFps.collectAsStateWithLifecycle()
    val colorProfile by viewModel.colorProfile.collectAsStateWithLifecycle()
    val isAudioEnabled by viewModel.isAudioEnabled.collectAsStateWithLifecycle()
    val currentVideoQuality by viewModel.currentVideoQuality.collectAsStateWithLifecycle()
    val viewfinderResolution by viewModel.viewfinderResolution.collectAsStateWithLifecycle()
    val currentZoom by viewModel.currentZoom.collectAsStateWithLifecycle()
    val displayedLenses by viewModel.displayedLenses.collectAsStateWithLifecycle()
    val selectedLens by viewModel.selectedLens.collectAsStateWithLifecycle()

    val dollyZoomState by viewModel.dollyZoomState.collectAsStateWithLifecycle()
    val dualVideoConfig by viewModel.dualVideoConfig.collectAsStateWithLifecycle()
    val nightConfig by viewModel.nightConfig.collectAsStateWithLifecycle()
    val nightProgress by viewModel.nightProgress.collectAsStateWithLifecycle()
    val hybridStabilizationConfig by viewModel.hybridStabilizationConfig.collectAsStateWithLifecycle()
    val tapFocusConfig by viewModel.tapFocusConfig.collectAsStateWithLifecycle()
    val uiCustomizationState by viewModel.uiCustomizationState.collectAsStateWithLifecycle()
    val activeLayoutConfig = remember(uiCustomizationState, cameraMode) {
        uiCustomizationState.getConfigForMode(cameraMode)
    }

    val selectedPhotoFilter by viewModel.selectedPhotoFilter.collectAsStateWithLifecycle()
    val isPhotoFilterBarOpen by viewModel.isPhotoFilterBarOpen.collectAsStateWithLifecycle()
    val isPortraitStyleBarOpen by viewModel.isPortraitStyleBarOpen.collectAsStateWithLifecycle()

    if (cameraMode == CameraMode.AI_SUBJECT_TRACKING) {
        com.example.camera.tracking.ui.AiSubjectTrackingScreen(
            onBack = {
                viewModel.setCameraMode(CameraMode.PHOTO)
            }
        )
        return
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    viewModel.engine.onAppBackgrounded()
                }
                androidx.lifecycle.Lifecycle.Event.ON_START -> {
                    if (hasCameraPermission) {
                        viewModel.engine.onAppForegrounded()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isAnyWindowOpen = isPhotoFilterBarOpen || isPortraitStyleBarOpen ||
            isCinemaSettingsOpen || isManualProOpen || isMoreModesOpen ||
            (cameraMode == CameraMode.PORTRAIT && isPortraitSettingsOpen) ||
            isVideoSettingsPanelOpen

    val viewfinderBlurRadius by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isAnyWindowOpen || isSettingsOpen) 22.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 260),
        label = "viewfinderGlassBlur"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("camera_main_screen")
    ) {
        // 1. Viewfinder layer preserving exact aspect ratio without distortion
        Viewfinder(
            aspectRatio = previewAspectRatio,
            gridType = gridType,
            focusRingPoint = focusRingPoint,
            isAeLocked = isAeLocked,
            isAfLocked = isAfLocked,
            isFrontCamera = selectedLens?.facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT,
            cameraMode = cameraMode,
            previewBufferSize = previewBufferSize,
            sensorOrientation = sensorOrientation,
            onSurfaceTextureAvailable = { texture ->
                viewModel.engine.setPreviewSurfaceTexture(texture)
            },
            onTapToFocus = { point, normX, normY ->
                viewModel.onTapToFocus(point, normX, normY)
            },
            onZoomChange = { zoom ->
                viewModel.setZoom(zoom, isPresetTap = false)
            },
            onExposureCompensationChange = { ev ->
                viewModel.setExposureCompensation(ev)
            },
            onToggleLock = {
                viewModel.toggleAeAfLock()
            },
            currentExposureCompensation = exposureCompensation,
            modifier = Modifier
                .fillMaxSize()
                .then(if (viewfinderBlurRadius > 0.dp) Modifier.blur(viewfinderBlurRadius) else Modifier)
        )

        // 1b. Cinema Viewfinder Assist Overlays (Waveform, Peaking, Zebras)
        if (cameraMode == CameraMode.CINEMA) {
            CinemaAssistOverlays(
                cinemaConfig = cinemaConfig,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 1c. Dolly Zoom Real-Time Tracking & Alignment Reticle Overlay
        if (cameraMode == CameraMode.DOLLY_ZOOM) {
            DollyZoomOverlay(
                dollyState = dollyZoomState,
                onCalibrateSubject = { viewModel.calibrateDollyZoom() },
                onResetDolly = { viewModel.resetDollyZoom() },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 1d. Real Dual Video Multi-Camera Viewfinder & Split Pipeline
        if (cameraMode == CameraMode.DUAL_VIDEO) {
            DualVideoViewfinder(
                config = dualVideoConfig,
                onPrimarySurfaceReady = { surface ->
                    viewModel.dualCameraManager.setPrimarySurface(surface)
                },
                onSecondarySurfaceReady = { surface ->
                    viewModel.dualCameraManager.setSecondarySurface(surface)
                },
                onLayoutChanged = { layout ->
                    viewModel.updateDualVideoConfig(dualVideoConfig.copy(layout = layout))
                },
                onSwapCameras = {
                    val p = dualVideoConfig.primaryCameraId
                    val s = dualVideoConfig.secondaryCameraId
                    viewModel.updateDualVideoConfig(dualVideoConfig.copy(primaryCameraId = s, secondaryCameraId = p))
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 1e. Computational Night Mode Long-Exposure HUD
        if (cameraMode == CameraMode.NIGHT) {
            NightModeOverlay(
                config = nightConfig,
                captureProgress = nightProgress,
                onDurationChange = { dur ->
                    viewModel.setNightConfig(nightConfig.copy(durationSeconds = dur))
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Top Controls
        TopControlBar(
            cameraMode = cameraMode,
            flashMode = flashMode,
            timerMode = timerMode,
            gridType = gridType,
            isRawEnabled = isRawEnabled,
            supportsRaw = capabilities.supportsRaw,
            storageStats = storageStats,
            videoQuality = currentVideoQuality,
            videoResolution = selectedVideoResolution,
            videoFps = videoFps,
            photoMegapixelMode = photoMegapixelMode,
            cinemaConfig = cinemaConfig,
            onPhotoFilterClick = { viewModel.togglePhotoFilterBar() },
            activePhotoFilter = selectedPhotoFilter,
            selectedPortraitStyle = portraitConfig.selectedStyle,
            onPortraitStyleClick = { viewModel.togglePortraitStyleBar() },
            onCinemaSettingsClick = { viewModel.toggleCinemaSettings() },
            onCinemaEvChange = { ev ->
                viewModel.updateCinemaConfig(cinemaConfig.copy(exposureCompensation = ev))
            },
            onVideoQualityClick = { viewModel.cycleVideoQuality() },
            onVideoSettingsClick = { viewModel.toggleVideoSettingsPanel() },
            onToggleMegapixelMode = { viewModel.togglePhotoMegapixelMode() },
            onDollyZoomClick = {
                if (cameraMode == CameraMode.DOLLY_ZOOM) {
                    viewModel.setCameraMode(CameraMode.VIDEO)
                } else {
                    viewModel.setCameraMode(CameraMode.DOLLY_ZOOM)
                }
            },
            onDualVideoClick = {
                if (cameraMode == CameraMode.DUAL_VIDEO) {
                    viewModel.setCameraMode(CameraMode.VIDEO)
                } else {
                    viewModel.setCameraMode(CameraMode.DUAL_VIDEO)
                }
            },
            onFlashClick = { viewModel.cycleFlashMode() },
            onTimerClick = { viewModel.cycleTimerMode() },
            onGridClick = { viewModel.cycleGridType() },
            onRawClick = { viewModel.toggleRawCapture() },
            onSettingsClick = { viewModel.setSettingsOpen(true) },
            layoutConfig = activeLayoutConfig,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 2b. Floating Frosted Video Settings Panel (Resolution & Frame Rate)
        if (cameraMode == CameraMode.VIDEO) {
            FloatingVideoSettingsPanel(
                isOpen = isVideoSettingsPanelOpen,
                currentResolution = selectedVideoResolution,
                currentFps = videoFps,
                isUltraStabilizationEnabled = hybridStabilizationConfig.isUltraStabilizationEnabled,
                onResolutionSelected = { res ->
                    viewModel.selectVideoResolution(res)
                },
                onFpsSelected = { fps ->
                    viewModel.setVideoFps(fps)
                },
                onUltraStabilizationToggle = {
                    viewModel.toggleUltraStabilization()
                },
                onDismiss = { viewModel.setVideoSettingsPanelOpen(false) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp)
            )
        }

        // 3. Manual Pro Control Bar (Slide-up above bottom controls in Photo/Video modes)
        if (cameraMode != CameraMode.PORTRAIT) {
            ManualProControlBar(
                isOpen = isManualProOpen,
                activeTab = activeProTab,
                capabilities = capabilities,
                exposureCompensation = exposureCompensation,
                manualIso = manualIso,
                manualShutterSpeedNs = manualShutterSpeedNs,
                whiteBalance = whiteBalance,
                focusMode = focusMode,
                manualFocusDistance = manualFocusDistance,
                colorProfile = colorProfile,
                isAeLocked = isAeLocked,
                isAfLocked = isAfLocked,
                onTabSelected = { viewModel.setActiveProTab(it) },
                onExposureChange = { viewModel.setExposureCompensation(it) },
                onIsoChange = { viewModel.setManualIso(it) },
                onShutterChange = { viewModel.setManualShutterSpeedNs(it) },
                onWbChange = { viewModel.setWhiteBalance(it) },
                onFocusModeChange = { viewModel.setFocusMode(it) },
                onFocusDistanceChange = { viewModel.setManualFocusDistance(it) },
                onColorProfileChange = { viewModel.setColorProfile(it) },
                onToggleAeLock = { viewModel.toggleAeLock() },
                onToggleAfLock = { viewModel.toggleAfLock() },
                onClose = { viewModel.setManualProOpen(false) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 215.dp)
            )
        }

        // 3b. Dedicated Portrait Mode AI Controls Panel
        AnimatedVisibility(
            visible = cameraMode == CameraMode.PORTRAIT && isPortraitSettingsOpen,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 215.dp)
        ) {
            PortraitControlBar(
                config = portraitConfig,
                processingState = portraitProcessingState,
                onBlurStrengthChanged = { viewModel.setPortraitBlurStrength(it) },
                onApertureSelected = { viewModel.setPortraitAperture(it) },
                onBokehStyleSelected = { viewModel.setPortraitBokehStyle(it) },
                onToggleFaceEnhancement = { viewModel.togglePortraitFaceEnhancement() },
                onToggleSkinTone = { viewModel.togglePortraitSkinTone() },
                onClose = { viewModel.setPortraitSettingsOpen(false) },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        // 3d. Dedicated Cinema Mode Settings Window (matching reference image)
        AnimatedVisibility(
            visible = cameraMode == CameraMode.CINEMA && isCinemaSettingsOpen,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 215.dp)
        ) {
            CinemaSettingsWindow(
                config = cinemaConfig,
                capabilities = cinemaCapabilities,
                onConfigChange = { updatedConfig ->
                    viewModel.updateCinemaConfig(updatedConfig)
                },
                onDismissRequest = { viewModel.setCinemaSettingsOpen(false) },
                modifier = Modifier.padding(horizontal = 14.dp)
            )
        }

        // 3d2. Photo Mode Filter Selector Bar
        AnimatedVisibility(
            visible = cameraMode == CameraMode.PHOTO && isPhotoFilterBarOpen,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 215.dp)
        ) {
            PhotoFilterSelectorBar(
                selectedFilter = selectedPhotoFilter,
                onFilterSelected = { viewModel.setSelectedPhotoFilter(it) },
                onClose = { viewModel.setPhotoFilterBarOpen(false) }
            )
        }

        // 3d3. Portrait Mode Style Selector Bar
        AnimatedVisibility(
            visible = cameraMode == CameraMode.PORTRAIT && isPortraitStyleBarOpen,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 215.dp)
        ) {
            PortraitStyleSelectorBar(
                selectedStyle = portraitConfig.selectedStyle,
                onStyleSelected = { viewModel.setSelectedPortraitStyle(it) },
                onClose = { viewModel.setPortraitStyleBarOpen(false) }
            )
        }

        // 3e. Dedicated More Modes Drawer
        MoreModesDrawer(
            isOpen = isMoreModesOpen,
            onDismissRequest = { viewModel.setMoreModesOpen(false) },
            onSelectProManual = {
                viewModel.setMoreModesOpen(false)
                viewModel.setCameraMode(CameraMode.PHOTO)
                viewModel.setManualProOpen(true)
            },
            onSelectCinemaLog = {
                viewModel.setMoreModesOpen(false)
                viewModel.setCameraMode(CameraMode.CINEMA)
            },
            onSelectMacro = {
                viewModel.setMoreModesOpen(false)
                viewModel.setCameraMode(CameraMode.PHOTO)
                viewModel.showToast("Macro Mode Active (Close Focus)")
            },
            onSelectNight = {
                viewModel.setMoreModesOpen(false)
                viewModel.setCameraMode(CameraMode.NIGHT)
            },
            onSelectDollyZoom = {
                viewModel.setMoreModesOpen(false)
                viewModel.setCameraMode(CameraMode.DOLLY_ZOOM)
            },
            onSelectDualVideo = {
                viewModel.setMoreModesOpen(false)
                viewModel.setCameraMode(CameraMode.DUAL_VIDEO)
            },
            onSelectAiSubjectTracking = {
                viewModel.setMoreModesOpen(false)
                viewModel.setCameraMode(CameraMode.AI_SUBJECT_TRACKING)
            },
            onOpenSettings = {
                viewModel.setMoreModesOpen(false)
                viewModel.setSettingsOpen(true)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 140.dp)
        )

        // 4. Toast Notification Overlay
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 110.dp)
        ) {
            toastMessage?.let { msg ->
                FrostedGlassBox(
                    shape = RoundedCornerShape(20.dp),
                    elevation = 16.dp,
                    baseAlpha = 0.78f,
                    modifier = Modifier.testTag("camera_toast")
                ) {
                    Text(
                        text = msg,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // 5. Bottom Controls
        BottomControlBar(
            cameraMode = cameraMode,
            currentZoom = currentZoom,
            displayedLenses = displayedLenses,
            selectedLens = selectedLens,
            capabilities = capabilities,
            onShowToast = { msg -> viewModel.showToast(msg) },
            onLensSelected = { lens -> viewModel.selectLens(lens) },
            onZoomChange = { zoom -> viewModel.setZoom(zoom, isPresetTap = false) },
            onZoomPresetTap = { preset -> viewModel.setZoom(preset, isPresetTap = true) },
            isRecordingVideo = isRecordingVideo,
            videoDurationSeconds = videoDurationSeconds,
            isCapturing = isCapturing,
            lastCapturedMedia = lastCapturedMedia,
            activeTimerCountdown = activeTimerCountdown,
            onModeSelected = { viewModel.setCameraMode(it) },
            onShutterClick = { viewModel.onMainActionButtonClick() },
            onFlipCameraClick = { viewModel.toggleCameraFacing() },
            onGalleryClick = {
                if (lastCapturedMedia != null) {
                    viewModel.setMediaViewerOpen(true)
                } else {
                    viewModel.showToast("No recent photos yet")
                }
            },
            onCinemaModeClick = { viewModel.toggleCinemaSettings() },
            layoutConfig = activeLayoutConfig.copy(
                showZoomCapsule = activeLayoutConfig.showZoomCapsule && !isAnyWindowOpen
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // 6. Settings Bottom Sheet (Light Mode, Categorized)
        SettingsDrawer(
            isOpen = isSettingsOpen,
            cameraMode = cameraMode,
            capabilities = capabilities,
            availableLenses = displayedLenses,
            selectedLens = selectedLens,
            selectedPhotoResolution = selectedPhotoResolution,
            selectedVideoResolution = selectedVideoResolution,
            photoMegapixelMode = photoMegapixelMode,
            videoFps = videoFps,
            videoBitrate = videoBitrate,
            isVideoStabilizationEnabled = isVideoStabilizationEnabled,
            isAudioEnabled = isAudioEnabled,
            isRawEnabled = isRawEnabled,
            saveSelfieAsPreviewed = saveSelfieAsPreviewed,
            gridType = gridType,
            cinemaConfig = cinemaConfig,
            cinemaCapabilities = cinemaCapabilities,
            viewfinderResolution = viewfinderResolution,
            hybridStabilizationConfig = hybridStabilizationConfig,
            nightConfig = nightConfig,
            tapFocusConfig = tapFocusConfig,
            uiCustomizationState = uiCustomizationState,
            onSelectTemplate = { viewModel.selectUiTemplate(it) },
            onUpdateGlobalLayoutConfig = { viewModel.updateGlobalLayoutConfig(it) },
            onUpdateModeLayoutConfig = { mode, config -> viewModel.updateModeLayoutConfig(mode, config) },
            onResetModeLayoutConfig = { mode -> viewModel.resetModeLayoutToGlobal(mode) },
            onSaveCustomPreset = { name, config -> viewModel.saveCustomPreset(name, config) },
            onLoadCustomPreset = { viewModel.loadCustomPreset(it) },
            onDeleteCustomPreset = { viewModel.deleteCustomPreset(it) },
            onResetAllToTemplate = { viewModel.resetLayoutToTemplate(it) },
            onLensSelected = { viewModel.selectLens(it) },
            onForceDeepScan = { viewModel.forceDeepScanLenses() },
            onPhotoResolutionSelected = { viewModel.selectPhotoResolution(it) },
            onPhotoMegapixelModeSelected = { viewModel.setPhotoMegapixelMode(it) },
            onVideoResolutionSelected = { viewModel.selectVideoResolution(it) },
            onViewfinderResolutionSelected = { viewModel.setViewfinderResolution(it) },
            onVideoFpsSelected = { viewModel.setVideoFps(it) },
            onVideoBitrateSelected = { viewModel.setVideoBitrate(it) },
            onStabilizationToggle = { viewModel.setVideoStabilization(it) },
            onHybridStabilizationChange = { viewModel.setHybridStabilizationConfig(it) },
            onNightConfigChange = { viewModel.setNightConfig(it) },
            onTapFocusConfigChange = { viewModel.setTapFocusConfig(it) },
            onAudioToggle = { viewModel.toggleAudio() },
            onRawToggle = { viewModel.toggleRawCapture() },
            onSaveSelfieAsPreviewedToggle = { viewModel.setSaveSelfieAsPreviewed(it) },
            onGridTypeSelected = { viewModel.setGridType(it) },
            onCinemaConfigChange = { viewModel.updateCinemaConfig(it) },
            onDismiss = { viewModel.setSettingsOpen(false) }
        )

        // 7. Full-Screen Media Viewer Dialog
        if (isMediaViewerOpen) {
            MediaViewerDialog(
                media = lastCapturedMedia,
                onDismiss = { viewModel.setMediaViewerOpen(false) }
            )
        }
    }
}

@Composable
fun CameraPermissionPrompt(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF101012))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFD54F).copy(alpha = 0.15f))
                    .border(2.dp, Color(0xFFFFD54F), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Color(0xFFFFD54F),
                    modifier = Modifier.size(44.dp)
                )
            }

            Text(
                text = "Camera & Audio Access",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "To capture high-resolution photos and record crisp video with genuine Camera2 manual controls, grant camera and microphone permissions.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD54F),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("grant_permissions_button")
            ) {
                Text(
                    text = "Grant Permissions",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
