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
import com.dipdev.aiautocaptioner.R
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
        val notification = buildNotification(
            title = getString(R.string.app_name),
            contentText = getString(R.string.notif_title_initializing)
        )
        
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
            stopSelf()
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
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
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
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .setContentTitle(title)
            .setContentText(contentText)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(!isFinished)
            .setAutoCancel(isFinished)

        if (showCancel) {
            builder.addAction(
                R.drawable.ic_logo_ui,
                getString(R.string.notif_action_open),
                getOpenAppPendingIntent()
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notif_action_cancel),
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
                    title = getString(R.string.notif_title_downloading),
                    contentText = getString(R.string.notif_download_progress, step.modelName, step.progress),
                    bigText = getString(R.string.notif_download_big, step.modelName, step.progress),
                    progress = step.progress,
                    isIndeterminate = step.progress <= 0
                )
            }
            is ProcessingStep.ExtractingAudio -> buildNotification(
                title = getString(R.string.notif_title_extracting),
                contentText = getString(R.string.notif_extracting_content),
                bigText = getString(R.string.notif_extracting_big)
            )
            is ProcessingStep.LoadingModel -> buildNotification(
                title = getString(R.string.notif_title_loading_model),
                contentText = getString(R.string.notif_loading_content),
                bigText = getString(R.string.notif_loading_big)
            )
            is ProcessingStep.Transcribing -> {
                val pct = (step.progress * 100).toInt()
                val timeLeft = step.estimatedSecondsRemaining?.let { sec ->
                    if (sec >= 60) getString(R.string.notif_time_minutes_format, sec / 60, sec % 60) else getString(R.string.notif_time_seconds_format, sec)
                } ?: ""
                val contentText = if (timeLeft.isNotEmpty()) getString(R.string.notif_transcribing_content_with_time, pct, timeLeft) else getString(R.string.notif_transcribing_content, pct)
                val bigText = if (timeLeft.isNotEmpty()) getString(R.string.notif_transcribing_big_with_time, pct, timeLeft) else getString(R.string.notif_transcribing_big, pct)
                buildNotification(
                    title = getString(R.string.notif_title_transcribing),
                    contentText = contentText,
                    bigText = bigText,
                    progress = pct,
                    isIndeterminate = false
                )
            }
            is ProcessingStep.Saving -> buildNotification(
                title = getString(R.string.notif_title_saving),
                contentText = getString(R.string.notif_saving_content),
                bigText = getString(R.string.notif_saving_big)
            )
            is ProcessingStep.Done -> buildNotification(
                title = getString(R.string.notif_title_complete),
                contentText = getString(R.string.notif_complete_content),
                bigText = getString(R.string.notif_complete_big),
                isFinished = true,
                showCancel = false
            )
            is ProcessingStep.Error -> buildNotification(
                title = getString(R.string.notif_title_failed),
                contentText = step.message,
                bigText = getString(R.string.notif_error_big_format, step.message),
                isError = true,
                isFinished = true,
                showCancel = false
            )
            is ProcessingStep.Idle -> buildNotification(
                title = getString(R.string.app_name),
                contentText = getString(R.string.notif_title_initializing)
            )
            is ProcessingStep.Cancelling -> buildNotification(
                title = getString(R.string.notif_title_cancelling),
                contentText = getString(R.string.notif_cancelling_content)
            )
            is ProcessingStep.Cancelled -> buildNotification(
                title = getString(R.string.notif_title_cancelled),
                contentText = getString(R.string.notif_cancelled_content),
                isFinished = true,
                showCancel = false
            )
            else -> buildNotification(
                title = getString(R.string.app_name),
                contentText = getString(R.string.notif_fallback_processing)
            )
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        observeJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
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
