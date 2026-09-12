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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
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
import com.example.camera.model.PhotoFilter
import com.example.camera.ui.components.FrostedGlassBox

@Composable
fun PhotoFilterSelectorBar(
    selectedFilter: PhotoFilter,
    onFilterSelected: (PhotoFilter) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    FrostedGlassBox(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .testTag("photo_filter_selector_bar"),
        shape = RoundedCornerShape(20.dp),
        borderWidth = 1.dp,
        borderColor = Color(0xFF64FFDA).copy(alpha = 0.35f)
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
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Photo Filters",
                        tint = Color(0xFF64FFDA),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PHOTO FILTERS",
                        color = Color(0xFF64FFDA),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ${selectedFilter.displayName}",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp).testTag("close_photo_filter_bar")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close filter bar",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Pills Horizontal Scroll
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PhotoFilter.entries.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    val accentColor = Color(0xFF64FFDA)

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) {
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            accentColor.copy(alpha = 0.95f),
                                            accentColor.copy(alpha = 0.80f)
                                        )
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF22242C).copy(alpha = 0.85f),
                                            Color(0xFF181A22).copy(alpha = 0.90f)
                                        )
                                    )
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onFilterSelected(filter) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                            .testTag("filter_option_${filter.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter.displayName,
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
