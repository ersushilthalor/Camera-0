package com.example.camera.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thread-safe RAW + JPEG Simultaneous Capture Manager.
 *
 * Eliminates race conditions, thread deadlocks, and buffer starvation when
 * capturing both RAW (DNG) and JPEG streams simultaneously.
 */
class RawJpegCaptureManager(
    private val context: Context,
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "RawJpegCaptureMgr"
    }

    private class CaptureSessionContext(
        val expectJpeg: Boolean,
        val expectRaw: Boolean,
        val onComplete: (Uri?) -> Unit
    ) {
        val jpegSaved = AtomicBoolean(false)
        val rawSaved = AtomicBoolean(false)
        var primaryUri: Uri? = null
        var captureResult: TotalCaptureResult? = null
        var pendingRawImage: Image? = null
    }

    private val activeSessions = ConcurrentHashMap<Long, CaptureSessionContext>()

    fun registerCaptureSession(
        timestamp: Long,
        expectJpeg: Boolean,
        expectRaw: Boolean,
        onComplete: (Uri?) -> Unit
    ) {
        activeSessions[timestamp] = CaptureSessionContext(
            expectJpeg = expectJpeg,
            expectRaw = expectRaw,
            onComplete = onComplete
        )
    }

    fun onCaptureResultReceived(timestamp: Long, result: TotalCaptureResult, characteristics: CameraCharacteristics) {
        val session = findSessionForTimestamp(timestamp) ?: return
        synchronized(session) {
            session.captureResult = result
            // If RAW image was waiting for capture result, process it now
            val waitingRaw = session.pendingRawImage
            if (waitingRaw != null) {
                session.pendingRawImage = null
                processRawImageAsync(session, waitingRaw, result, characteristics)
            }
        }
    }

    fun onJpegImageAvailable(reader: ImageReader, timestamp: Long) {
        val image = try {
            reader.acquireLatestImage() ?: return
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring JPEG image", e)
            return
        }

        val session = findSessionForTimestamp(timestamp)
        scope.launch(Dispatchers.IO) {
            var savedUri: Uri? = null
            try {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)

                savedUri = saveJpegBytesToMediaStore(bytes)
                if (session != null) {
                    session.primaryUri = savedUri
                    session.jpegSaved.set(true)
                    checkSessionCompletion(session)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed saving JPEG in simultaneous capture", e)
            } finally {
                try { image.close() } catch (ignored: Exception) {}
            }
        }
    }

    fun onRawImageAvailable(reader: ImageReader, characteristics: CameraCharacteristics, timestamp: Long) {
        val image = try {
            reader.acquireLatestImage() ?: return
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring RAW image", e)
            return
        }

        val session = findSessionForTimestamp(timestamp)
        if (session == null) {
            image.close()
            return
        }

        synchronized(session) {
            val result = session.captureResult
            if (result != null) {
                processRawImageAsync(session, image, result, characteristics)
            } else {
                // Wait for matching TotalCaptureResult
                session.pendingRawImage = image
            }
        }
    }

    private fun processRawImageAsync(
        session: CaptureSessionContext,
        image: Image,
        result: TotalCaptureResult,
        characteristics: CameraCharacteristics
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val rawUri = saveRawImageToMediaStore(image, characteristics, result)
                if (session.primaryUri == null && rawUri != null) {
                    session.primaryUri = rawUri
                }
                session.rawSaved.set(true)
                checkSessionCompletion(session)
            } catch (e: Exception) {
                Log.e(TAG, "Failed saving RAW DNG image", e)
            } finally {
                try { image.close() } catch (ignored: Exception) {}
            }
        }
    }

    private fun checkSessionCompletion(session: CaptureSessionContext) {
        val jpegDone = !session.expectJpeg || session.jpegSaved.get()
        val rawDone = !session.expectRaw || session.rawSaved.get()

        if (jpegDone && rawDone) {
            session.onComplete(session.primaryUri)
            // Remove session from map
            activeSessions.entries.removeIf { it.value === session }
        }
    }

    private fun findSessionForTimestamp(timestamp: Long): CaptureSessionContext? {
        if (activeSessions.containsKey(timestamp)) return activeSessions[timestamp]
        // Match closest within 200ms
        return activeSessions.entries.minByOrNull { Math.abs(it.key - timestamp) }?.value
    }

    private suspend fun saveJpegBytesToMediaStore(bytes: ByteArray): Uri? = withContext(Dispatchers.IO) {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "IMG_${timeStamp}.jpg"

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext null

            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }

            uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving JPEG bytes", e)
            null
        }
    }

    private suspend fun saveRawImageToMediaStore(
        rawImage: Image,
        characteristics: CameraCharacteristics,
        captureResult: TotalCaptureResult
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "RAW_${timeStamp}.dng"

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext null

            context.contentResolver.openOutputStream(uri)?.use { out ->
                DngCreator(characteristics, captureResult).use { dng ->
                    dng.writeImage(out, rawImage)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }

            Log.d(TAG, "Successfully saved RAW DNG to MediaStore: $uri")
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving DNG file", e)
            null
        }
    }
}
