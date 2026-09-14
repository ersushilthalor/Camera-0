package com.example.camera.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FilterTiltShift
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.data.db.RefocusPhotoEntity
import com.example.camera.engine.RefocusEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Interactive Gallery Viewer for Refocus Photos.
 *
 * Provides:
 * 1. Tap-to-Refocus: tapping any depth region shifts optical focus to that depth plane.
 * 2. Smooth Continuous Focus Slider: seamlessly shifts focus across near, mid, and far planes.
 * 3. Natural 3D / Parallax Effect: device tilt and touch drag steer depth layers for holographic depth pop.
 * 4. Memory-safe: decodes screen-adapted bitmaps and recycles them safely.
 */
@Composable
fun InteractiveRefocusViewer(
    refocusEntity: RefocusPhotoEntity,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val refocusEngine = remember { RefocusEngine(context) }

    // Bitmaps and depth map data
    var nearBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var midBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var farBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var depthMapData by remember { mutableStateOf<RefocusEngine.DepthMapData?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Focus state (0.0 = Near, 0.5 = Subject/Mid, 1.0 = Far/Deep)
    val focusAnimatable = remember { Animatable(0.5f) }
    var targetFocusValue by remember { mutableFloatStateOf(0.5f) }

    // 3D Parallax state
    var is3DParallaxActive by remember { mutableStateOf(false) }
    var sensorTiltX by remember { mutableFloatStateOf(0f) }
    var sensorTiltY by remember { mutableFloatStateOf(0f) }
    var touchParallaxX by remember { mutableFloatStateOf(0f) }
    var touchParallaxY by remember { mutableFloatStateOf(0f) }

    // Tap indicator
    var tapLocation by remember { mutableStateOf<Offset?>(null) }
    var showTapReticle by remember { mutableStateOf(false) }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Load planes and depth map sequentially in background
    LaunchedEffect(refocusEntity.bundleDir) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val near = decodeScaledBitmap(refocusEntity.nearPlanePath, 1440)
                val mid = decodeScaledBitmap(refocusEntity.midPlanePath, 1440)
                val far = decodeScaledBitmap(refocusEntity.farPlanePath, 1440)
                val depth = refocusEngine.loadDepthMap(refocusEntity.depthMapPath)

                withContext(Dispatchers.Main) {
                    nearBitmap = near
                    midBitmap = mid
                    farBitmap = far
                    depthMapData = depth
                    isLoading = false
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    // Safely clear references on disposal without forcing canvas draw errors
    DisposableEffect(Unit) {
        onDispose {
            nearBitmap = null
            midBitmap = null
            farBitmap = null
        }
    }

    // Hardware tilt sensor for 3D Parallax
    DisposableEffect(is3DParallaxActive) {
        if (!is3DParallaxActive) return@DisposableEffect onDispose {}

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    // orientation[2] is roll (X tilt), orientation[1] is pitch (Y tilt)
                    val targetX = (orientation[2] * 1.5f).coerceIn(-1f, 1f)
                    val targetY = (orientation[1] * 1.5f).coerceIn(-1f, 1f)
                    sensorTiltX = sensorTiltX * 0.85f + targetX * 0.15f
                    sensorTiltY = sensorTiltY * 0.85f + targetY * 0.15f
                } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val ax = (event.values[0] / 9.8f).coerceIn(-1f, 1f)
                    val ay = (event.values[1] / 9.8f).coerceIn(-1f, 1f)
                    sensorTiltX = sensorTiltX * 0.85f - ax * 0.15f
                    sensorTiltY = sensorTiltY * 0.85f + ay * 0.15f
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { containerSize = it.size }
            .testTag("interactive_refocus_viewer")
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color(0xFFFFD54F),
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            val currentFocus = focusAnimatable.value

            // Compute layer alphas for continuous optical focus blending
            val nearAlpha: Float
            val midAlpha: Float
            val farAlpha: Float

            if (currentFocus <= 0.5f) {
                val t = currentFocus / 0.5f
                nearAlpha = 1f - t
                midAlpha = t
                farAlpha = 0f
            } else {
                val t = (currentFocus - 0.5f) / 0.5f
                nearAlpha = 0f
                midAlpha = 1f - t
                farAlpha = t
            }

            // Compute 3D Parallax differential shifts
            val totalParallaxX = (sensorTiltX * 24f + touchParallaxX).coerceIn(-36f, 36f)
            val totalParallaxY = (sensorTiltY * 24f + touchParallaxY).coerceIn(-36f, 36f)

            val nearShiftX = if (is3DParallaxActive) (totalParallaxX * 1.5f).roundToInt() else 0
            val nearShiftY = if (is3DParallaxActive) (totalParallaxY * 1.5f).roundToInt() else 0

            val midShiftX = if (is3DParallaxActive) (totalParallaxX * 0.2f).roundToInt() else 0
            val midShiftY = if (is3DParallaxActive) (totalParallaxY * 0.2f).roundToInt() else 0

            val farShiftX = if (is3DParallaxActive) (-totalParallaxX * 1.2f).roundToInt() else 0
            val farShiftY = if (is3DParallaxActive) (-totalParallaxY * 1.2f).roundToInt() else 0

            val scaleBoost = if (is3DParallaxActive) 1.06f else 1.0f

            // Photo Rendering Container with Touch Gestures
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scaleBoost)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            tapLocation = offset
                            showTapReticle = true

                            // Compute normalized position
                            val cw = containerSize.width.toFloat().coerceAtLeast(1f)
                            val ch = containerSize.height.toFloat().coerceAtLeast(1f)
                            val normX = (offset.x / cw).coerceIn(0f, 1f)
                            val normY = (offset.y / ch).coerceIn(0f, 1f)

                            // Read depth at tapped location
                            val tappedDepth = depthMapData?.getDepthAt(normX, normY) ?: 0.5f
                            targetFocusValue = tappedDepth

                            coroutineScope.launch {
                                focusAnimatable.animateTo(
                                    targetValue = tappedDepth,
                                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                                )
                            }
                        }
                    }
                    .pointerInput(is3DParallaxActive) {
                        if (is3DParallaxActive) {
                            detectDragGestures(
                                onDragEnd = {
                                    touchParallaxX = 0f
                                    touchParallaxY = 0f
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                touchParallaxX = (touchParallaxX + dragAmount.x / 4f).coerceIn(-30f, 30f)
                                touchParallaxY = (touchParallaxY + dragAmount.y / 4f).coerceIn(-30f, 30f)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Far / Deep Plane Layer
                farBitmap?.let { bmp ->
                    if (farAlpha > 0.01f || is3DParallaxActive) {
                        androidx.compose.foundation.Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Far Focus Plane",
                            alpha = if (is3DParallaxActive) 0.85f else farAlpha,
                            modifier = Modifier
                                .fillMaxSize()
                                .offset { IntOffset(farShiftX, farShiftY) }
                        )
                    }
                }

                // Mid / Subject Plane Layer
                midBitmap?.let { bmp ->
                    if (midAlpha > 0.01f || is3DParallaxActive) {
                        androidx.compose.foundation.Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Mid Focus Plane",
                            alpha = if (is3DParallaxActive) 0.90f else midAlpha,
                            modifier = Modifier
                                .fillMaxSize()
                                .offset { IntOffset(midShiftX, midShiftY) }
                        )
                    }
                }

                // Near Plane Layer
                nearBitmap?.let { bmp ->
                    if (nearAlpha > 0.01f || is3DParallaxActive) {
                        androidx.compose.foundation.Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Near Focus Plane",
                            alpha = if (is3DParallaxActive) 0.95f else nearAlpha,
                            modifier = Modifier
                                .fillMaxSize()
                                .offset { IntOffset(nearShiftX, nearShiftY) }
                        )
                    }
                }

                // Tap-to-Refocus Animated Ring Reticle
                if (showTapReticle && tapLocation != null) {
                    val reticleScale by animateFloatAsState(
                        targetValue = 1.0f,
                        animationSpec = tween(300),
                        finishedListener = { showTapReticle = false },
                        label = "reticleScale"
                    )
                    val reticleOffset = tapLocation!!
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = Color(0xFFFFD54F),
                            radius = 38.dp.toPx() * reticleScale,
                            center = reticleOffset,
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                        drawCircle(
                            color = Color(0xFFFFD54F),
                            radius = 4.dp.toPx(),
                            center = reticleOffset
                        )
                    }
                }
            }

            // Bottom Refocus & 3D Interactive Control Card
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Interactive Status Pill Bar
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterTiltShift,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (is3DParallaxActive) "3D PARALLAX DEPTH" else "INTERACTIVE REFOCUS",
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // 3D Toggle Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (is3DParallaxActive) Color(0xFFFFD54F) else Color(0x33FFFFFF),
                        modifier = Modifier
                            .clickable { is3DParallaxActive = !is3DParallaxActive }
                            .testTag("refocus_3d_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ViewInAr,
                                contentDescription = "3D Parallax",
                                tint = if (is3DParallaxActive) Color.Black else Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "3D Mode",
                                color = if (is3DParallaxActive) Color.Black else Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Slider Card for Smooth Focus Plane Selection
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Focus Plane: ${
                                    when {
                                        currentFocus < 0.33f -> "Near (Foreground)"
                                        currentFocus > 0.67f -> "Far (Background)"
                                        else -> "Mid (Subject)"
                                    }
                                }",
                                color = Color(0xFFFFD54F),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Tap photo or drag slider",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        }

                        // Smooth continuous focus slider
                        Slider(
                            value = currentFocus,
                            onValueChange = { newVal ->
                                targetFocusValue = newVal
                                coroutineScope.launch {
                                    focusAnimatable.snapTo(newVal)
                                }
                            },
                            valueRange = 0.0f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFFD54F),
                                activeTrackColor = Color(0xFFFFD54F),
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("refocus_plane_slider")
                        )

                        // Plane markers row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "◀ Near",
                                color = if (currentFocus < 0.33f) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                fontWeight = if (currentFocus < 0.33f) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.clickable {
                                    coroutineScope.launch {
                                        focusAnimatable.animateTo(0.1f, tween(300))
                                    }
                                }
                            )
                            Text(
                                text = "● Mid (Subject)",
                                color = if (currentFocus in 0.33f..0.67f) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                fontWeight = if (currentFocus in 0.33f..0.67f) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.clickable {
                                    coroutineScope.launch {
                                        focusAnimatable.animateTo(0.5f, tween(300))
                                    }
                                }
                            )
                            Text(
                                text = "Far ▶",
                                color = if (currentFocus > 0.67f) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                fontWeight = if (currentFocus > 0.67f) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.clickable {
                                    coroutineScope.launch {
                                        focusAnimatable.animateTo(0.9f, tween(300))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Memory-safe bitmap decoder that scales images down to max dimension.
 */
private fun decodeScaledBitmap(path: String, maxDimension: Int): Bitmap? {
    val file = File(path)
    if (!file.exists()) return null
    return try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val w = options.outWidth
        val h = options.outHeight
        if (w <= 0 || h <= 0) return null

        var sampleSize = 1
        val maxSide = max(w, h)
        while (maxSide / (sampleSize * 2) >= maxDimension) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
    } catch (e: Throwable) {
        null
    }
}
