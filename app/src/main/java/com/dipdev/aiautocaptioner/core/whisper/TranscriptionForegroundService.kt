package com.dipdev.aiautocaptioner.core.whisper

import android.graphics.BitmapFactory
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
                if (step is ProcessingStep.Done || step is ProcessingStep.Error || step is ProcessingStep.Cancelled || step is ProcessingStep.Cancelling) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                    releaseWakeLock()
                    stopSelf()
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundServiceGracefully() {
        val notification = buildNotification(title = "AutoCaptioner", contentText = "Initializing…")
        
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

    private fun getOpenAppPendingIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        } ?: Intent()
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(this, 1, intent, flags)
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

    private fun buildNotification(
        title: String,
        contentText: String,
        bigText: String? = null,
        progress: Int? = null,
        isIndeterminate: Boolean = true,
        isFinished: Boolean = false,
        isError: Boolean = false,
        showCancel: Boolean = true
    ): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.dipdev.aiautocaptioner.R.mipmap.ic_launcher)
            .setLargeIcon(BitmapFactory.decodeResource(resources, com.dipdev.aiautocaptioner.R.mipmap.ic_launcher))
            .setContentTitle(title)
            .setContentText(contentText)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(!isFinished)
            .setAutoCancel(isFinished)

        if (showCancel) {
            builder.addAction(
                com.dipdev.aiautocaptioner.R.drawable.ic_logo_ui,
                "Open App",
                getOpenAppPendingIntent()
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                getCancelPendingIntent()
            )
        }

        if (bigText != null) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(bigText)
                    .setSummaryText(title)
            )
        }

        if (isError) {
            builder.setColor(0xFFEF4444.toInt()) // red
        } else if (isFinished) {
            builder.setColor(0xFF22C55E.toInt()) // green
        } else {
            builder.setColor(0xFFF59E0B.toInt()) // amber
        }

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
        val notification: Notification = when (step) {
            is ProcessingStep.DownloadingModel -> {
                buildNotification(
                    title = "Downloading Model",
                    contentText = "Downloading ${step.modelName}… ${step.progress}%",
                    bigText = "Downloading ${step.modelName}\n${step.progress}% complete",
                    progress = step.progress,
                    isIndeterminate = step.progress <= 0
                )
            }
            is ProcessingStep.ExtractingAudio -> buildNotification(
                title = "Extracting Audio",
                contentText = "Separating audio from video…",
                bigText = "Analyzing video file and extracting audio track…"
            )
            is ProcessingStep.LoadingModel -> buildNotification(
                title = "Loading AI Model",
                contentText = "Warming up the AI engine…",
                bigText = "Loading whisper model into memory…"
            )
            is ProcessingStep.Transcribing -> {
                val pct = (step.progress * 100).toInt()
                val timeLeft = step.estimatedSecondsRemaining?.let { sec ->
                    if (sec >= 60) "~${sec / 60}m ${sec % 60}s left" else "~${sec}s left"
                } ?: ""
                buildNotification(
                    title = "Transcribing",
                    contentText = "$pct%${if (timeLeft.isNotEmpty()) " · $timeLeft" else ""}",
                    bigText = "AI is transcribing your video…\n$pct% complete${if (timeLeft.isNotEmpty()) "\nEstimated: $timeLeft" else ""}",
                    progress = pct,
                    isIndeterminate = false
                )
            }
            is ProcessingStep.Saving -> buildNotification(
                title = "Saving",
                contentText = "Saving transcription…",
                bigText = "Saving transcription results…"
            )
            is ProcessingStep.Done -> buildNotification(
                title = "Transcription Complete",
                contentText = "Your captions are ready",
                bigText = "Transcription finished successfully.\nTap to open your project.",
                isFinished = true,
                showCancel = false
            )
            is ProcessingStep.Error -> buildNotification(
                title = "Transcription Failed",
                contentText = step.message,
                bigText = "Error: ${step.message}",
                isError = true,
                isFinished = true,
                showCancel = false
            )
            is ProcessingStep.Idle -> buildNotification(
                title = "AutoCaptioner",
                contentText = "Initializing…"
            )
            is ProcessingStep.Cancelling -> buildNotification(
                title = "Cancelling",
                contentText = "Stopping transcription…"
            )
            is ProcessingStep.Cancelled -> buildNotification(
                title = "Cancelled",
                contentText = "Transcription was cancelled",
                isFinished = true,
                showCancel = false
            )
            else -> buildNotification(
                title = "AutoCaptioner",
                contentText = "Processing…"
            )
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        observeJob?.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
    }
}
