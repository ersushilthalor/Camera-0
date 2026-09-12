package com.example.camera.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Authentic Frosted Glass Container with Real Physical Properties:
 * - Natural depth drop shadow
 * - Translucent tinted glass substrate
 * - Specular directional light sheen gradient
 * - Refractive translucent glass border
 * - Top-edge specular highlight rim
 */
@Composable
fun FrostedGlassBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 18.dp,
    baseAlpha: Float = 0.72f,
    baseTint: Color = Color(0xFF141724),
    borderWidth: Dp = 1.dp,
    borderColor: Color? = null,
    showTopHighlightRim: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val borderBrush = if (borderColor != null) {
        SolidColor(borderColor)
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.42f),
                Color.White.copy(alpha = 0.14f),
                Color.White.copy(alpha = 0.05f)
            )
        )
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.40f),
                spotColor = Color.Black.copy(alpha = 0.70f)
            )
            .clip(shape)
            // Primary substrate: deep translucent tinted glass
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        baseTint.copy(alpha = baseAlpha),
                        Color(0xFF0A0C13).copy(alpha = (baseAlpha + 0.14f).coerceAtMost(0.96f))
                    )
                )
            )
            // Secondary layer: physical specular light refraction across surface
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.16f),
                        Color.White.copy(alpha = 0.03f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.22f)
                    )
                )
            )
            // Physical glass refractive border
            .border(
                width = borderWidth,
                brush = borderBrush,
                shape = shape
            )
    ) {
        // Frosted liquid blur background layer for floating window
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .blur(20.dp)
        )

        content()

        // Top specular highlight rim (hairline light reflection on cut glass edge)
        if (showTopHighlightRim) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.45f),
                                Color.White.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}

/**
 * Extension modifier to apply frosted glass styling directly to any component.
 */
fun Modifier.frostedGlass(
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 16.dp,
    baseAlpha: Float = 0.72f,
    baseTint: Color = Color(0xFF141724),
    borderWidth: Dp = 1.dp,
    borderColor: Color? = null
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = 0.40f),
        spotColor = Color.Black.copy(alpha = 0.70f)
    )
    .clip(shape)
    .background(
        Brush.verticalGradient(
            colors = listOf(
                baseTint.copy(alpha = baseAlpha),
                Color(0xFF0A0C13).copy(alpha = (baseAlpha + 0.14f).coerceAtMost(0.96f))
            )
        )
    )
    .background(
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.16f),
                Color.White.copy(alpha = 0.03f),
                Color.Transparent,
                Color.Black.copy(alpha = 0.22f)
            )
        )
    )
    .border(
        width = borderWidth,
        brush = if (borderColor != null) {
            SolidColor(borderColor)
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.42f),
                    Color.White.copy(alpha = 0.14f),
                    Color.White.copy(alpha = 0.05f)
                )
            )
        },
        shape = shape
    )
