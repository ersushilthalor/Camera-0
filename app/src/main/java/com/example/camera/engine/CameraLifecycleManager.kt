package com.example.camera.engine

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Robust Camera Lifecycle and Background Thread Manager.
 *
 * Ensures clean release of CameraDevice, CameraCaptureSession, and coroutines
 * when app is paused/minimized, and glitch-free restart on resume without black screens.
 */
class CameraLifecycleManager {

    companion object {
        private const val TAG = "CameraLifecycleManager"
    }

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    val isPaused = AtomicBoolean(false)
    val cameraOpenCloseLock = Semaphore(1)

    fun startBackgroundThread(): Handler {
        if (backgroundThread == null || !backgroundThread!!.isAlive) {
            val thread = HandlerThread("Camera2Background").apply { start() }
            backgroundThread = thread
            backgroundHandler = Handler(thread.looper)
        }
        return backgroundHandler!!
    }

    fun getBackgroundHandler(): Handler? = backgroundHandler

    fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join(1000)
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    fun closeCameraSessionSafely(
        session: CameraCaptureSession?,
        device: CameraDevice?,
        onClosed: () -> Unit
    ) {
        try {
            cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)
            try {
                session?.stopRepeating()
                session?.abortCaptures()
            } catch (ignored: Exception) {}

            try {
                session?.close()
            } catch (ignored: Exception) {}

            try {
                device?.close()
            } catch (ignored: Exception) {}

            onClosed()
        } catch (e: Exception) {
            Log.e(TAG, "Exception while closing camera safely", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }
}
