package com.example.camera.superres

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.camera.model.PhotoMegapixelMode
import com.example.camera.model.SuperResBackend
import com.example.camera.model.SuperResMemoryLimit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Android Foreground Service executing Real-ESRGAN AI Super Resolution in the background.
 *
 * Key Capabilities:
 * - Viewfinder stays completely unblocked; camera remains immediately ready for more captures.
 * - Processing continues safely even if the user exits, minimizes, or swipes away the app.
 * - System notification with real-time progress bar and status updates.
 * - Multiple captured photos are queued and processed sequentially without memory pressure.
 * - Automatically saves finished high-res photos to MediaStore DCIM/Camera and notifies the UI.
 */
class SuperResForegroundService : Service() {

    data class SuperResTask(
        val id: Long,
        val tempFilePath: String,
        val megapixelMode: PhotoMegapixelMode,
        val backend: SuperResBackend,
        val memoryLimit: SuperResMemoryLimit
    )

    data class SuperResResult(
        val id: Long,
        val uri: Uri,
        val displayName: String
    )

    companion object {
        private const val TAG = "SuperResService"
        private const val CHANNEL_ID = "super_res_processing_channel"
        private const val CHANNEL_NAME = "AI Super Resolution"
        private const val NOTIFICATION_ID = 4096
        private const val COMPLETED_NOTIFICATION_ID = 4097

        const val EXTRA_TEMP_PATH = "extra_temp_path"
        const val EXTRA_MP_MODE = "extra_mp_mode"
        const val EXTRA_BACKEND = "extra_backend"
        const val EXTRA_MEMORY_LIMIT = "extra_memory_limit"

        private val _isProcessing = MutableStateFlow(false)
        val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

        private val _activeProgress = MutableStateFlow<Float?>(null)
        val activeProgress: StateFlow<Float?> = _activeProgress.asStateFlow()

        private val _activeStatus = MutableStateFlow<String?>(null)
        val activeStatus: StateFlow<String?> = _activeStatus.asStateFlow()

        private val _lastCompletedJob = MutableSharedFlow<SuperResResult>(extraBufferCapacity = 16)
        val lastCompletedJob: SharedFlow<SuperResResult> = _lastCompletedJob.asSharedFlow()

        fun startProcessing(
            context: Context,
            tempFilePath: String,
            megapixelMode: PhotoMegapixelMode,
            backend: SuperResBackend,
            memoryLimit: SuperResMemoryLimit
        ) {
            val intent = Intent(context, SuperResForegroundService::class.java).apply {
                putExtra(EXTRA_TEMP_PATH, tempFilePath)
                putExtra(EXTRA_MP_MODE, megapixelMode.name)
                putExtra(EXTRA_BACKEND, backend.name)
                putExtra(EXTRA_MEMORY_LIMIT, memoryLimit.name)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SuperResForegroundService", e)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val taskChannel = Channel<SuperResTask>(Channel.UNLIMITED)
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private val realEsrganEngine by lazy { RealEsrganEngine(applicationContext) }

    private var taskIdCounter = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Immediately promote to foreground to satisfy Android requirements within 5 seconds
        val initialNotification = buildOngoingNotification(0.02f, "Initializing AI Super Resolution...")
        startForegroundCompat(initialNotification)
        startWorkerLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val tempPath = intent?.getStringExtra(EXTRA_TEMP_PATH)
        if (!tempPath.isNullOrEmpty()) {
            val mpModeName = intent.getStringExtra(EXTRA_MP_MODE) ?: PhotoMegapixelMode.M24.name
            val backendName = intent.getStringExtra(EXTRA_BACKEND) ?: SuperResBackend.AUTO.name
            val memLimitName = intent.getStringExtra(EXTRA_MEMORY_LIMIT) ?: SuperResMemoryLimit.AUTO.name

            val mpMode = try { PhotoMegapixelMode.valueOf(mpModeName) } catch (e: Exception) { PhotoMegapixelMode.M24 }
            val backend = try { SuperResBackend.valueOf(backendName) } catch (e: Exception) { SuperResBackend.AUTO }
            val memLimit = try { SuperResMemoryLimit.valueOf(memLimitName) } catch (e: Exception) { SuperResMemoryLimit.AUTO }

            val task = SuperResTask(
                id = ++taskIdCounter,
                tempFilePath = tempPath,
                megapixelMode = mpMode,
                backend = backend,
                memoryLimit = memLimit
            )
            serviceScope.launch {
                taskChannel.send(task)
            }
        }

        return START_NOT_STICKY
    }

    private fun startWorkerLoop() {
        serviceScope.launch {
            for (task in taskChannel) {
                _isProcessing.value = true
                processTask(task)
            }
            _isProcessing.value = false
            _activeProgress.value = null
            _activeStatus.value = null
            stopForegroundCompat()
            stopSelf()
        }
    }

    private suspend fun processTask(task: SuperResTask) {
        val tempFile = File(task.tempFilePath)
        if (!tempFile.exists()) {
            Log.e(TAG, "Temp image file not found: ${task.tempFilePath}")
            return
        }

        val modeLabel = task.megapixelMode.label
        updateNotification(0.05f, "Preparing $modeLabel Super Resolution...")

        val savedUri = realEsrganEngine.processAndSaveSuperResFromFile(
            tempFile = tempFile,
            megapixelMode = task.megapixelMode,
            backend = task.backend,
            memoryLimit = task.memoryLimit,
            onProgress = { progress, status ->
                _activeProgress.value = progress
                _activeStatus.value = status
                updateNotification(progress, status)
            }
        )

        // Delete temporary cache file to free space
        try {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        } catch (ignored: Exception) {}

        if (savedUri != null) {
            val displayName = "${task.megapixelMode.label}_AI_SR.jpg"
            val result = SuperResResult(task.id, savedUri, displayName)
            _lastCompletedJob.emit(result)
            showCompletionNotification(savedUri, modeLabel)
            Log.i(TAG, "Successfully enhanced photo to $modeLabel in background: $savedUri")
        } else {
            Log.e(TAG, "Super-resolution failed for task ${task.id}")
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in startForegroundCompat", e)
        }
    }

    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error in stopForegroundCompat", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background AI Super Resolution processing progress"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildOngoingNotification(progress: Float, status: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val progressPercent = (progress * 100).toInt().coerceIn(0, 100)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI Super Resolution")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setProgress(100, progressPercent, progress <= 0.05f)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(progress: Float, status: String) {
        try {
            val notification = buildOngoingNotification(progress, status)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Could not update notification", e)
        }
    }

    private fun showCompletionNotification(savedUri: Uri, modeLabel: String) {
        try {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(savedUri, "image/jpeg")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                (System.currentTimeMillis() % 10000).toInt(),
                viewIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val completionNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Photo Enhanced ($modeLabel)")
                .setContentText("High-resolution photo saved to DCIM/Camera")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            notificationManager.notify(COMPLETED_NOTIFICATION_ID, completionNotification)
        } catch (e: Exception) {
            Log.w(TAG, "Could not post completion notification", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        taskChannel.close()
        serviceScope.cancel()
    }
}
