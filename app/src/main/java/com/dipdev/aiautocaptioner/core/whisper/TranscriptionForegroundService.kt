package com.dipdev.aiautocaptioner.core.whisper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.dipdev.aiautocaptioner.ui.processing.ProcessingStep
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TranscriptionForegroundService : Service() {

    @Inject
    lateinit var transcriptionManager: TranscriptionManager

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var observeJob: Job? = null

    companion object {
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "transcription_channel"
        const val ACTION_CANCEL = "com.dipdev.aiautocaptioner.ACTION_CANCEL_TRANSCRIPTION"
        private const val TAG = "TranscriptionService"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            transcriptionManager.cancel()
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundServiceGracefully()

        observeJob?.cancel()
        observeJob = serviceScope.launch {
            transcriptionManager.step.collect { step ->
                updateNotificationForStep(step)
                if (step is ProcessingStep.Done || step is ProcessingStep.Error) {
                    stopSelf()
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundServiceGracefully() {
        val notification = buildNotification("Initializing transcription...")
        
        try {
            if (Build.VERSION.SDK_INT >= 35) { // VANILLA_ICE_CREAM
                ServiceCompat.startForeground(
                    this, 
                    NOTIFICATION_ID, 
                    notification, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
                )
            } else if (Build.VERSION.SDK_INT >= 34) { // UPSIDE_DOWN_CAKE
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
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AutoCaptioner::TranscriptionWakeLock").apply {
                acquire(30 * 60 * 1000L) // 30 mins timeout max just in case
            }
            Log.d(TAG, "WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Transcription",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress for AI transcription"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getCancelPendingIntent(): PendingIntent {
        val cancelIntent = Intent(this, TranscriptionForegroundService::class.java).apply {
            action = ACTION_CANCEL
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getService(this, 0, cancelIntent, flags)
    }

    private fun buildNotification(contentText: String, progress: Int? = null, isIndeterminate: Boolean = true): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.dipdev.aiautocaptioner.R.drawable.ic_notification)
            .setContentTitle("Generating Captions")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel, 
                "Cancel", 
                getCancelPendingIntent()
            )
            
        if (progress != null) {
            builder.setProgress(100, progress, false)
        } else if (isIndeterminate) {
            builder.setProgress(100, 0, true)
        } else {
            builder.setProgress(0, 0, false)
        }
            
        return builder.build()
    }

    private fun updateNotificationForStep(step: ProcessingStep) {
        var progressValue: Int? = null
        var isIndeterminate = true
        
        val text = when (step) {
            is ProcessingStep.DownloadingModel -> {
                if (step.progress != null) {
                    progressValue = step.progress
                    isIndeterminate = false
                    "Downloading model... ${step.progress}%"
                } else {
                    "Downloading ${step.modelName}..."
                }
            }
            is ProcessingStep.ExtractingAudio -> "Extracting audio..."
            is ProcessingStep.LoadingModel -> "Loading AI model..."
            is ProcessingStep.Transcribing -> {
                progressValue = (step.progress * 100).toInt()
                isIndeterminate = false
                "Transcribing video... $progressValue%"
            }
            is ProcessingStep.Saving -> "Saving transcription..."
            is ProcessingStep.Done -> {
                isIndeterminate = false
                "Transcription complete"
            }
            is ProcessingStep.Error -> {
                isIndeterminate = false
                "Error: ${step.message}"
            }
            is ProcessingStep.Idle -> "Initializing..."
            else -> "Processing..."
        }
        
        val notification = buildNotification(text, progressValue, isIndeterminate)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        serviceScope.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }
}
