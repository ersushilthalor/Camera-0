package com.example.camera.dbsr

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.media.Image
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance execution engine for Deep Burst Super-Resolution (AI Zoom).
 *
 * Handles:
 * - Unpacking Camera2 RAW_SENSOR Bayer frames (or high-quality YUV sensor frames) to 4-channel tensors [R, G1, G2, B]
 * - Crop ROI optimization for the active digital zoom ratio
 * - Multi-threaded asynchronous execution of DBSR pipeline
 * - Orientation correction, color calibration and storage into MediaStore
 */
class DbsrEngine(private val context: Context) {

    companion object {
        private const val TAG = "DbsrEngine"
    }

    data class RawBurstFrame(
        val tensor: Tensor,
        val cropRect: Rect,
        val isRawSensor: Boolean
    )

    /**
     * Extracts a 4-channel low-resolution Bayer tensor [R, G1, G2, B] from a RAW sensor image,
     * cropped to the designated digital zoom region of interest.
     */
    fun extractBayerTensorFromRaw(
        image: Image,
        cropRect: Rect,
        targetDim: Int = 160
    ): Tensor {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        val cropW = cropRect.width().coerceAtLeast(16)
        val cropH = cropRect.height().coerceAtLeast(16)

        // The Bayer quad is 2x2. Each quad yields 1 pixel in the 4-channel tensor [R, G1, G2, B]
        val quadW = cropW / 2
        val quadH = cropH / 2

        // Scale down to targetDim to ensure real-time mobile speed while preserving sub-pixel details
        val stepX = max(1, quadW / targetDim)
        val stepY = max(1, quadH / targetDim)

        val outW = min(targetDim, quadW / stepX)
        val outH = min(targetDim, quadH / stepY)

        val tensor = Tensor(4, outH, outW)

        // Read 16-bit or 10-bit raw values
        val isShort = buffer.remaining() >= rowStride * image.height && pixelStride >= 2
        val shortBuffer = if (isShort) buffer.asShortBuffer() else null

        val startX = (cropRect.left / 2) * 2
        val startY = (cropRect.top / 2) * 2

        for (ty in 0 until outH) {
            val qy = startY + (ty * stepY * 2)
            if (qy + 1 >= image.height) break

            for (tx in 0 until outW) {
                val qx = startX + (tx * stepX * 2)
                if (qx + 1 >= image.width) break

                val v00: Float
                val v01: Float
                val v10: Float
                val v11: Float

                if (shortBuffer != null) {
                    val row0 = (qy * rowStride) / 2
                    val row1 = ((qy + 1) * rowStride) / 2
                    v00 = (shortBuffer.get(row0 + qx).toInt() and 0xFFFF) / 1023f
                    v01 = (shortBuffer.get(row0 + qx + 1).toInt() and 0xFFFF) / 1023f
                    v10 = (shortBuffer.get(row1 + qx).toInt() and 0xFFFF) / 1023f
                    v11 = (shortBuffer.get(row1 + qx + 1).toInt() and 0xFFFF) / 1023f
                } else {
                    val row0 = qy * rowStride
                    val row1 = (qy + 1) * rowStride
                    v00 = (buffer.get(row0 + qx * pixelStride).toInt() and 0xFF) / 255f
                    v01 = (buffer.get(row0 + (qx + 1) * pixelStride).toInt() and 0xFF) / 255f
                    v10 = (buffer.get(row1 + qx * pixelStride).toInt() and 0xFF) / 255f
                    v11 = (buffer.get(row1 + (qx + 1) * pixelStride).toInt() and 0xFF) / 255f
                }

                // Channel 0: R, Channel 1: G1, Channel 2: G2, Channel 3: B
                tensor.set(0, ty, tx, v00.coerceIn(0f, 1f))
                tensor.set(1, ty, tx, v01.coerceIn(0f, 1f))
                tensor.set(2, ty, tx, v10.coerceIn(0f, 1f))
                tensor.set(3, ty, tx, v11.coerceIn(0f, 1f))
            }
        }

        return tensor
    }

    /**
     * Fallback path: Converts high-quality YUV_420_888 or JPEG sensor image to a 4-channel tensor.
     */
    fun extractTensorFromBitmap(bitmap: Bitmap, cropRect: Rect, targetDim: Int = 160): Tensor {
        val safeLeft = cropRect.left.coerceIn(0, bitmap.width - 1)
        val safeTop = cropRect.top.coerceIn(0, bitmap.height - 1)
        val safeW = cropRect.width().coerceIn(16, bitmap.width - safeLeft)
        val safeH = cropRect.height().coerceIn(16, bitmap.height - safeTop)

        val cropped = Bitmap.createBitmap(bitmap, safeLeft, safeTop, safeW, safeH)
        val scaled = Bitmap.createScaledBitmap(cropped, targetDim, targetDim, true)
        if (cropped != bitmap) cropped.recycle()

        val tensor = Tensor(4, targetDim, targetDim)
        val pixels = IntArray(targetDim * targetDim)
        scaled.getPixels(pixels, 0, targetDim, 0, 0, targetDim, targetDim)
        scaled.recycle()

        for (y in 0 until targetDim) {
            for (x in 0 until targetDim) {
                val p = pixels[y * targetDim + x]
                val r = ((p shr 16) and 0xFF) / 255f
                val g = ((p shr 8) and 0xFF) / 255f
                val b = (p and 0xFF) / 255f

                // In pseudo-sensor format: [R, G, G, B]
                tensor.set(0, y, x, r)
                tensor.set(1, y, x, g)
                tensor.set(2, y, x, g)
                tensor.set(3, y, x, b)
            }
        }

        return tensor
    }

    /**
     * Executes the complete Deep Burst Super-Resolution process on a burst of input frames.
     */
    suspend fun processBurst(
        burstFrames: List<Tensor>,
        baseRawTensor: Tensor,
        rotationDegrees: Int,
        quality: AiZoomQuality,
        onProgress: (Float) -> Unit
    ): Bitmap = withContext(Dispatchers.Default) {
        val model = DbsrModel.getInstance(context)

        onProgress(0.15f)
        val refFrame = burstFrames[0]
        val numFrames = burstFrames.size

        // 1. PWC-Net Optical Flow Estimation for each frame relative to reference frame
        val flows = mutableListOf<FlowField>()
        for (i in 0 until numFrames) {
            if (i == 0) {
                flows.add(FlowField(refFrame.h, refFrame.w)) // Zero flow for base frame
            } else {
                val flow = model.estimateOpticalFlow(burstFrames[i], refFrame, quality)
                flows.add(flow)
            }
            onProgress(0.15f + (0.25f * (i + 1) / numFrames))
        }

        // 2. Feature Encoding
        val encodedFeatures = mutableListOf<Tensor>()
        for (i in 0 until numFrames) {
            val feat = model.encodeRawFrame(burstFrames[i])
            encodedFeatures.add(feat)
            onProgress(0.40f + (0.20f * (i + 1) / numFrames))
        }

        // 3. Feature Warping via Optical Flow
        val warpedFeatures = mutableListOf<Tensor>()
        for (i in 0 until numFrames) {
            if (i == 0) {
                warpedFeatures.add(encodedFeatures[0])
            } else {
                val warped = model.warpFeatures(encodedFeatures[i], flows[i])
                warpedFeatures.add(warped)
            }
        }
        onProgress(0.65f)

        // 4. Adaptive Attention-Weighted Burst Merging
        val mergedFeature = model.mergeBurstFeatures(warpedFeatures, encodedFeatures[0])
        onProgress(0.80f)

        // 5. 4x Super-Resolution Decoder
        val decodedBitmap = model.decodeSuperResolution(mergedFeature, baseRawTensor)
        onProgress(0.92f)

        // 6. Apply JPEG orientation if needed
        val finalBitmap = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val rotated = Bitmap.createBitmap(decodedBitmap, 0, 0, decodedBitmap.width, decodedBitmap.height, matrix, true)
            if (rotated != decodedBitmap) decodedBitmap.recycle()
            rotated
        } else {
            decodedBitmap
        }

        onProgress(1.0f)
        finalBitmap
    }

    /**
     * Saves the final AI Zoom enhanced image to Android MediaStore.
     */
    suspend fun saveAiZoomImageToMediaStore(bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val fileName = "AIZOOM_DBSR_$timeStamp.jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Camera")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null

        try {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 98, out)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            Log.d(TAG, "Saved AI Zoom DBSR photo to MediaStore: $uri")
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write AI Zoom photo", e)
            resolver.delete(uri, null, null)
            null
        }
    }
}
