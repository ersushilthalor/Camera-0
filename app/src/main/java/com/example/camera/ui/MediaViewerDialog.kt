package com.example.camera.ui

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.camera.data.RefocusRepository
import com.example.camera.data.db.RefocusPhotoEntity
import com.example.camera.model.CapturedMedia
import com.example.camera.ui.components.FrostedGlassBox

private const val TAG = "MediaViewerDialog"

@Composable
fun MediaViewerDialog(
    media: CapturedMedia?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (media == null) return
    val context = LocalContext.current
    var refocusEntity by remember(media.uri) { mutableStateOf<RefocusPhotoEntity?>(null) }
    var isRefocusLoading by remember(media.uri) { mutableStateOf(false) }

    LaunchedEffect(media.uri) {
        if (!media.isVideo) {
            isRefocusLoading = true
            try {
                val repo = RefocusRepository(context)
                refocusEntity = repo.getRefocusPhoto(media.uri.toString())
            } catch (e: Throwable) {
                Log.e(TAG, "Error fetching refocus photo entity", e)
                refocusEntity = null
            } finally {
                isRefocusLoading = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("media_viewer_dialog")
        ) {
            // Media Preview, Interactive Refocus Viewer, or In-App Video Playback
            if (media.isVideo) {
                val videoAspectRatio = remember(media.uri) {
                    var retriever: MediaMetadataRetriever? = null
                    try {
                        retriever = MediaMetadataRetriever()
                        retriever.setDataSource(context, media.uri)
                        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                        val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1080
                        val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1920
                        val isRotated = (rotation == 90 || rotation == 270)
                        val dispW = if (isRotated) rawH else rawW
                        val dispH = if (isRotated) rawW else rawH
                        (dispW.toFloat() / dispH.toFloat()).coerceIn(0.2f, 5.0f)
                    } catch (e: Throwable) {
                        Log.w(TAG, "Could not extract video metadata aspect ratio", e)
                        9f / 16f
                    } finally {
                        try {
                            retriever?.release()
                        } catch (ignored: Throwable) {}
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            android.widget.VideoView(ctx).apply {
                                setOnErrorListener { mp, what, extra ->
                                    Log.w(TAG, "VideoView playback error what=$what extra=$extra")
                                    true // Consume error to prevent system crash dialog
                                }
                                try {
                                    setVideoURI(media.uri)
                                    setOnPreparedListener { mp ->
                                        try {
                                            mp.isLooping = true
                                            start()
                                        } catch (e: Throwable) {
                                            Log.e(TAG, "Error starting video playback", e)
                                        }
                                    }
                                } catch (e: Throwable) {
                                    Log.e(TAG, "Failed setting video URI", e)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(videoAspectRatio, matchHeightConstraintsFirst = true)
                    )
                }
            } else if (refocusEntity != null) {
                InteractiveRefocusViewer(
                    refocusEntity = refocusEntity!!,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = media.uri,
                    contentDescription = media.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Top frosted bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }

                FrostedGlassBox(
                    shape = RoundedCornerShape(20.dp),
                    elevation = 8.dp,
                    baseAlpha = 0.55f,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = media.displayName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                IconButton(
                    onClick = {
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = if (media.isVideo) "video/*" else "image/*"
                                putExtra(Intent.EXTRA_STREAM, media.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Media"))
                        } catch (e: Throwable) {
                            Log.e(TAG, "Failed launching share intent", e)
                        }
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White
                    )
                }
            }

            // Bottom info bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                FrostedGlassBox(
                    shape = RoundedCornerShape(24.dp),
                    elevation = 12.dp,
                    baseAlpha = 0.65f
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (media.isVideo) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Video (${media.durationSeconds}s)",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else if (refocusEntity != null) {
                            Text(
                                text = "Refocus Photo (Tap to change focus)",
                                color = Color(0xFF34D399),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                text = "Photo",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
