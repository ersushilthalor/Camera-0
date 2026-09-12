package com.example.camera.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.model.DollyZoomState

/**
 * Dolly Zoom Real-Time HUD Overlay.
 * Displays dynamic subject tracking reticle, auto-zoom compensation metric,
 * and calibration actions.
 */
@Composable
fun DollyZoomOverlay(
    dollyState: DollyZoomState,
    onCalibrateSubject: () -> Unit,
    onResetDolly: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .testTag("dolly_zoom_overlay")
    ) {
        // 1. Center Reticle for Subject Lock
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(160.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bracketLen = 28.dp.toPx()
                val strokeW = 2.5.dp.toPx()
                val goldColor = if (dollyState.isCalibrated) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.7f)

                // Top-Left corner
                drawLine(goldColor, Offset(0f, 0f), Offset(bracketLen, 0f), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(goldColor, Offset(0f, 0f), Offset(0f, bracketLen), strokeWidth = strokeW, cap = StrokeCap.Round)

                // Top-Right corner
                drawLine(goldColor, Offset(size.width, 0f), Offset(size.width - bracketLen, 0f), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(goldColor, Offset(size.width, 0f), Offset(size.width, bracketLen), strokeWidth = strokeW, cap = StrokeCap.Round)

                // Bottom-Left corner
                drawLine(goldColor, Offset(0f, size.height), Offset(bracketLen, size.height), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(goldColor, Offset(0f, size.height), Offset(0f, size.height - bracketLen), strokeWidth = strokeW, cap = StrokeCap.Round)

                // Bottom-Right corner
                drawLine(goldColor, Offset(size.width, size.height), Offset(size.width - bracketLen, size.height), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(goldColor, Offset(size.width, size.height), Offset(size.width, size.height - bracketLen), strokeWidth = strokeW, cap = StrokeCap.Round)

                // Center crosshair tick
                val cX = size.width / 2
                val cY = size.height / 2
                val tickLen = 6.dp.toPx()
                drawLine(goldColor.copy(alpha = 0.5f), Offset(cX - tickLen, cY), Offset(cX + tickLen, cY), strokeWidth = 1.5.dp.toPx())
                drawLine(goldColor.copy(alpha = 0.5f), Offset(cX, cY - tickLen), Offset(cX, cY + tickLen), strokeWidth = 1.5.dp.toPx())
            }
        }

        // 2. Status & Metric Badge (Upper Third)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 90.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xCC121216))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (dollyState.isCalibrated) Color(0xFF4CAF50) else Color(0xFFFFD54F))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (dollyState.isCalibrated) {
                        "ZOOM: %.1fx · DIST: %.2fm".format(dollyState.smoothedZoom, dollyState.currentDistanceMeters)
                    } else {
                        "DOLLY READY · FRAME SUBJECT"
                    },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = dollyState.statusPrompt,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }

        // 3. Action Controls (Lower Third, just above control bar)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 190.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xDD18181E))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Calibrate / Lock Subject
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (dollyState.isCalibrated) Color(0x33FFD54F) else Color(0xFFFFD54F))
                    .clickable { onCalibrateSubject() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("dolly_calibrate_button"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.CenterFocusStrong,
                    contentDescription = null,
                    tint = if (dollyState.isCalibrated) Color(0xFFFFD54F) else Color.Black,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (dollyState.isCalibrated) "RE-LOCK SUBJECT" else "LOCK SUBJECT",
                    color = if (dollyState.isCalibrated) Color(0xFFFFD54F) else Color.Black,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }

            if (dollyState.isCalibrated) {
                // Reset
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0x33FFFFFF))
                        .clickable { onResetDolly() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("dolly_reset_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "RESET",
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
