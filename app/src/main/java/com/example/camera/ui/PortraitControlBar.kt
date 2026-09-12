package com.example.camera.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.model.BokehStyle
import com.example.camera.model.PortraitConfig
import com.example.camera.model.PortraitProcessingState

@Composable
fun PortraitControlBar(
    config: PortraitConfig,
    processingState: PortraitProcessingState = PortraitProcessingState(),
    onBlurStrengthChanged: (Float) -> Unit,
    onApertureSelected: (String) -> Unit,
    onBokehStyleSelected: (BokehStyle) -> Unit,
    onToggleFaceEnhancement: () -> Unit = {},
    onToggleSkinTone: () -> Unit = {},
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val apertures = listOf("f/0.95", "f/1.2", "f/1.4", "f/1.8", "f/2.4", "f/2.8")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF161A29).copy(alpha = 0.65f),
                        Color(0xFF0C0E18).copy(alpha = 0.75f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.38f),
                        Color.White.copy(alpha = 0.10f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("portrait_control_bar")
    ) {
        Column {
            // Header: Aperture (f-stop) and Close (X) button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFD54F).copy(alpha = 0.20f))
                            .border(1.dp, Color(0xFFFFD54F).copy(alpha = 0.50f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "f",
                            color = Color(0xFFFFD54F),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            fontFamily = FontFamily.Serif
                        )
                    }
                    Text(
                        text = "Aperture & Depth",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .testTag("close_portrait_settings")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close aperture settings",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Row 1: Aperture Simulation Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                apertures.forEach { aperture ->
                    val isSelected = config.simulatedAperture == aperture
                    val bgColor by animateColorAsState(
                        if (isSelected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.08f),
                        label = "apertureBg"
                    )
                    val textColor by animateColorAsState(
                        if (isSelected) Color.Black else Color.White,
                        label = "apertureText"
                    )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Color(0xFF26210A) else Color.White.copy(alpha = 0.08f))
                    .border(
                        width = if (isSelected) 1.5.dp else 0.dp,
                        color = if (isSelected) Color(0xFFFFD54F) else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onApertureSelected(aperture) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("aperture_chip_$aperture"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = aperture,
                    color = if (isSelected) Color(0xFFFFD54F) else Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
            }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Depth Blur Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Blur: ${config.blurStrength.toInt()}%",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(80.dp)
                )
                Slider(
                    value = config.blurStrength,
                    onValueChange = onBlurStrengthChanged,
                    valueRange = 0f..100f,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("portrait_blur_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFFD54F),
                        activeTrackColor = Color(0xFFFFD54F),
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 3: Cinematic Bokeh Styles (Round, Elliptical, Polygonal, Light Source)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bokeh:",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                BokehStyle.values().forEach { style ->
                    val isSelected = config.bokehStyle == style
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0xFFFFD54F).copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onBokehStyleSelected(style) }
                            .testTag("bokeh_style_${style.name.lowercase()}")
                    ) {
                        Text(
                            text = style.label,
                            color = if (isSelected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}
