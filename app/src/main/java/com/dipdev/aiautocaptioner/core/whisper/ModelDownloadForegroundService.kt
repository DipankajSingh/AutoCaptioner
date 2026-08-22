package com.dipdev.aiautocaptioner.core.whisper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.data.repository.DownloadState
import com.dipdev.aiautocaptioner.data.repository.ModelRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

object ModelDownloadServiceManager {
    val downloadState = MutableStateFlow<DownloadState?>(null)
}

@AndroidEntryPoint
class ModelDownloadForegroundService : Service() {

    @Inject
    lateinit var modelRepository: ModelRepository

    @Inject
    lateinit var crashReporter: com.dipdev.aiautocaptioner.core.logging.CrashReporter

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var downloadJob: Job? = null

    companion object {
        const val ACTION_CANCEL_DOWNLOAD = "action_cancel_download"
        const val EXTRA_MODEL_ID = "extra_model_id"
        private const val NOTIFICATION_ID = 102
        private const val CHANNEL_ID = "model_download_channel"
        private const val TAG = "ModelDownloadService"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground MUST be called before any early returns or async work
        // to satisfy the system's 5-second window after startForegroundService().
        startForegroundServiceGracefully()

        if (intent?.action == ACTION_CANCEL_DOWNLOAD) {
            downloadJob?.cancel()
            ModelDownloadServiceManager.downloadState.value = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val modelId = intent?.getStringExtra(EXTRA_MODEL_ID)
        if (modelId.isNullOrEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        ModelDownloadServiceManager.downloadState.value = DownloadState.Starting

        downloadJob?.cancel()
        downloadJob = serviceScope.launch {
            modelRepository.downloadModel(modelId).collect { state ->
                ModelDownloadServiceManager.downloadState.value = state
                updateNotificationProgress(state)

                if (state is DownloadState.Complete || state is DownloadState.Error) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundServiceGracefully() {
        val notification = buildNotification(
            title = getString(R.string.app_name),
            contentText = "Setting up your caption engine…",
            progress = null
        )
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}")
            stopSelf()
        }
    }

    private fun updateNotificationProgress(state: DownloadState) {
        val notification = when (state) {
            is DownloadState.Starting -> buildNotification("Setting up your caption engine…", "Starting...", null)
            is DownloadState.Downloading -> buildNotification("Setting up your caption engine…", "Downloading model...", state.progress)
            is DownloadState.Complete -> buildNotification("Caption engine ready", "Download complete", 100, isFinished = true)
            is DownloadState.Error -> buildNotification("Download failed", state.message, null, isFinished = true)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, contentText: String, progress: Int?, isFinished: Boolean = false): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(contentText)
            .setOngoing(!isFinished)
            .setAutoCancel(isFinished)

        if (progress != null) {
            builder.setProgress(100, progress, false)
        } else if (!isFinished) {
            builder.setProgress(100, 0, true)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for downloading AI caption models"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadJob?.cancel()
        serviceScope.cancel()
    }
}
