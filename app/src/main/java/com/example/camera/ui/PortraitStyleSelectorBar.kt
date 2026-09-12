package com.example.camera.ui

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
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.model.PortraitStyle
import com.example.camera.ui.components.FrostedGlassBox

@Composable
fun PortraitStyleSelectorBar(
    selectedStyle: PortraitStyle,
    onStyleSelected: (PortraitStyle) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val portraitAccent = Color(0xFFFF8A65) // Warm amber/coral

    FrostedGlassBox(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .testTag("portrait_style_selector_bar"),
        shape = RoundedCornerShape(20.dp),
        borderWidth = 1.dp,
        borderColor = portraitAccent.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FaceRetouchingNatural,
                        contentDescription = "Portrait Style",
                        tint = portraitAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PORTRAIT STYLES",
                        color = portraitAccent,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ${selectedStyle.displayName}",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp).testTag("close_portrait_style_bar")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close style bar",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = selectedStyle.description,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 10.sp,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Style Pills Horizontal Scroll
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PortraitStyle.entries.forEach { style ->
                    val isSelected = selectedStyle == style

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) {
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            portraitAccent.copy(alpha = 0.95f),
                                            portraitAccent.copy(alpha = 0.80f)
                                        )
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF242020).copy(alpha = 0.85f),
                                            Color(0xFF1C1818).copy(alpha = 0.90f)
                                        )
                                    )
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onStyleSelected(style) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                            .testTag("style_option_${style.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = style.displayName,
                            color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.90f),
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
