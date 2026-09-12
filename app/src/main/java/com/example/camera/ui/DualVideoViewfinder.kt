package com.example.camera.ui

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Splitscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.camera.model.DualVideoConfig
import com.example.camera.model.DualVideoLayout

/**
 * Real Dual Video Viewfinder.
 * Provides split-screen (Side by Side, Top/Bottom) and Picture-in-Picture (PiP)
 * for true concurrent multi-camera capture when supported by hardware.
 */
@Composable
fun DualVideoViewfinder(
    config: DualVideoConfig,
    onPrimarySurfaceReady: (Surface) -> Unit,
    onSecondarySurfaceReady: (Surface) -> Unit,
    onLayoutChanged: (DualVideoLayout) -> Unit,
    onSwapCameras: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("dual_video_viewfinder")
    ) {
        if (!config.isConcurrentSupported) {
            // Graceful Hardware Limitation UI - Never fake sequential capture
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 28.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xEE1E1E26))
                    .border(1.dp, Color(0xFFFFB300).copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "HARDWARE CONCURRENT LIMITATION",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This device's camera hardware does not provide concurrent multi-camera HAL streaming. True concurrent recording requires dual hardware ISP support.",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        } else {
            // Concurrent Multi-Stream Viewfinder Layouts
            when (config.layout) {
                DualVideoLayout.SIDE_BY_SIDE -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .border(0.5.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            CameraStreamView(
                                label = "CAM ${config.primaryCameraId} (MAIN)",
                                onSurfaceAvailable = onPrimarySurfaceReady
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .border(0.5.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            CameraStreamView(
                                label = "CAM ${config.secondaryCameraId} (AUX)",
                                onSurfaceAvailable = onSecondarySurfaceReady
                            )
                        }
                    }
                }

                DualVideoLayout.TOP_BOTTOM -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .border(0.5.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            CameraStreamView(
                                label = "CAM ${config.primaryCameraId} (MAIN)",
                                onSurfaceAvailable = onPrimarySurfaceReady
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .border(0.5.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            CameraStreamView(
                                label = "CAM ${config.secondaryCameraId} (AUX)",
                                onSurfaceAvailable = onSecondarySurfaceReady
                            )
                        }
                    }
                }

                DualVideoLayout.PIP -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Fullscreen primary
                        CameraStreamView(
                            label = "MAIN CAM",
                            onSurfaceAvailable = onPrimarySurfaceReady
                        )

                        // Floating Inset PiP secondary window
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 90.dp, end = 16.dp)
                                .width(120.dp)
                                .height(170.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(2.dp, Color(0xFFFFD54F), RoundedCornerShape(16.dp))
                        ) {
                            CameraStreamView(
                                label = "PIP CAM",
                                onSurfaceAvailable = onSecondarySurfaceReady
                            )
                        }
                    }
                }
            }
        }

        // Floating Control Strip for Dual Video (Layout Switch & Camera Swap)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 190.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xDD18181E))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DualVideoLayout.entries.forEach { layout ->
                val isSelected = config.layout == layout
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) Color(0xFFFFD54F) else Color.Transparent)
                        .clickable { onLayoutChanged(layout) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = layout.label,
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF))
                    .clickable { onSwapCameras() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Cameraswitch,
                    contentDescription = "Swap",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun CameraStreamView(
    label: String,
    onSurfaceAvailable: (Surface) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            onSurfaceAvailable(Surface(st))
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Label Badge
        Text(
            text = label,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x99000000))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
