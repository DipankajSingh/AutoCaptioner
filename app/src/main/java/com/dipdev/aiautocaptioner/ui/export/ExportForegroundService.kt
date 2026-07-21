package com.dipdev.aiautocaptioner.ui.export

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextureOverlay
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.dipdev.aiautocaptioner.core.logging.CrashReporter
import com.dipdev.aiautocaptioner.data.db.dao.ExportedFileDao
import com.dipdev.aiautocaptioner.data.db.entity.ExportedFileEntity
import com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus
import com.dipdev.aiautocaptioner.data.repository.CaptionRepository
import com.dipdev.aiautocaptioner.data.repository.OverlayRepository
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import com.dipdev.aiautocaptioner.engine.CaptionOverlayEffect
import com.dipdev.aiautocaptioner.engine.ImageOverlayEffect
import com.google.common.collect.ImmutableList
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

object ExportServiceManager {
    val exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val progress = MutableStateFlow<Float>(0f)
    val outputPath = MutableStateFlow<String?>(null)

    fun reset() {
        exportState.value = ExportState.Idle
        progress.value = 0f
        outputPath.value = null
    }
}

@UnstableApi
@AndroidEntryPoint
class ExportForegroundService : Service() {

    @Inject lateinit var projectRepository: ProjectRepository
    @Inject lateinit var captionRepository: CaptionRepository
    @Inject lateinit var overlayRepository: OverlayRepository
    @Inject lateinit var exportedFileDao: ExportedFileDao
    @Inject lateinit var crashReporter: CrashReporter

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeTransformer: Transformer? = null
    private var progressJob: Job? = null
    private var currentOutFile: File? = null
    private var isFinishing = false

    companion object {
        private const val TAG = "ExportService"
        const val NOTIFICATION_ID = 102
        const val CHANNEL_ID = "export_channel"
        const val ACTION_CANCEL = "com.dipdev.aiautocaptioner.ACTION_CANCEL_EXPORT"

        const val EXTRA_PROJECT_ID = "extra_project_id"
        const val EXTRA_TARGET_FPS = "extra_target_fps"
        const val EXTRA_TARGET_HEIGHT = "extra_target_height"
        const val EXTRA_TARGET_BITRATE = "extra_target_bitrate"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            Log.d(TAG, "Cancel received")
            isFinishing = true
            progressJob?.cancel()
            activeTransformer?.cancel()
            activeTransformer = null
            currentOutFile?.delete()
            ExportServiceManager.exportState.value = ExportState.Cancelled
            ExportServiceManager.progress.value = 0f
            stopExportService()
            return START_NOT_STICKY
        }

        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val projectId = intent.getStringExtra(EXTRA_PROJECT_ID)
        if (projectId == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val targetFps = if (intent.hasExtra(EXTRA_TARGET_FPS)) intent.getIntExtra(EXTRA_TARGET_FPS, -1) else null
        val targetHeight = if (intent.hasExtra(EXTRA_TARGET_HEIGHT)) intent.getIntExtra(EXTRA_TARGET_HEIGHT, -1) else null
        val targetBitrate = if (intent.hasExtra(EXTRA_TARGET_BITRATE)) intent.getIntExtra(EXTRA_TARGET_BITRATE, -1) else null

        isFinishing = false
        startForegroundService()

        ExportServiceManager.exportState.value = ExportState.Running
        ExportServiceManager.progress.value = 0f

        startExport(
            projectId = projectId,
            targetFps = if (targetFps != -1) targetFps else null,
            targetHeight = if (targetHeight != -1) targetHeight else null,
            targetBitrate = if (targetBitrate != -1) targetBitrate else null
        )

        return START_NOT_STICKY
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
        val cancelIntent = Intent(this, ExportForegroundService::class.java).apply {
            action = ACTION_CANCEL
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getService(this, 0, cancelIntent, flags)
    }

    private fun startForegroundService() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Video Export",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = buildExportNotification(
            title = "Exporting Video",
            contentText = "Preparing your video…",
            isIndeterminate = true
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildExportNotification(
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

        when {
            isError -> builder.setColor(0xFFEF4444.toInt())
            isFinished -> builder.setColor(0xFF22C55E.toInt())
            else -> builder.setColor(0xFFF59E0B.toInt())
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

    private fun updateNotificationProgress(progress: Int) {
        if (isFinishing) return
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = buildExportNotification(
            title = "Exporting Video",
            contentText = "Rendering… $progress%",
            bigText = "Exporting your video with captions…\n$progress% complete",
            progress = progress,
            isIndeterminate = false
        )
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun startExport(
        projectId: String,
        targetFps: Int?,
        targetHeight: Int?,
        targetBitrate: Int?
    ) {
        serviceScope.launch {
            try {
                val project = projectRepository.getProjectById(projectId)
                    ?: throw Exception("Project not found")

                val textureOverlays = ImmutableList.builder<TextureOverlay>()

                val isPortrait = project.videoRotation == 90 || project.videoRotation == 270
                val displayWidth  = if (isPortrait) project.videoHeight else project.videoWidth
                val displayHeight = if (isPortrait) project.videoWidth  else project.videoHeight

                val styleId = project.activeStyleId
                if (styleId != null) {
                    val activeStyle = captionRepository.getStyleById(styleId)
                    val segments = captionRepository.getSegmentsOnce(projectId)
                    if (activeStyle != null && segments.isNotEmpty()) {
                        val wordsList = captionRepository.getAllWordsForProject(projectId)
                        val wordsMap = wordsList.groupBy { it.segmentId }
                        val captionOverlayEffect = CaptionOverlayEffect(
                            context = this@ExportForegroundService,
                            segments = segments,
                            wordsMap = wordsMap,
                            style = activeStyle,
                            videoWidth = displayWidth,
                            videoHeight = displayHeight
                        )
                        textureOverlays.add(captionOverlayEffect)
                    }
                }

                val overlays = overlayRepository.getOverlaysOnce(projectId)
                val imageOverlayEffects = overlays.mapNotNull { overlay ->
                    try {
                        val bitmap = if (overlay.imageUri.startsWith("content://")) {
                            val inputStream = contentResolver.openInputStream(Uri.parse(overlay.imageUri))
                            val bmp = BitmapFactory.decodeStream(inputStream)
                            inputStream?.close()
                            bmp
                        } else {
                            BitmapFactory.decodeFile(overlay.imageUri)
                        }
                        if (bitmap != null) {
                            ImageOverlayEffect(
                                bitmap = bitmap,
                                positionX = overlay.positionX,
                                positionY = overlay.positionY,
                                scaleX = overlay.scaleX,
                                scaleY = overlay.scaleY,
                                startTimeMs = overlay.startTimeMs,
                                endTimeMs = overlay.endTimeMs,
                                videoWidth = displayWidth,
                                videoHeight = displayHeight
                            )
                        } else null
                    } catch (e: Throwable) {
                        crashReporter.recordException(e)
                        null
                    }
                }
                textureOverlays.addAll(imageOverlayEffects)

                val outDir = File(filesDir, "exports")
                if (!outDir.exists()) outDir.mkdirs()
                val outFile = File(outDir, "export_${System.currentTimeMillis()}.mp4")
                currentOutFile = outFile
                ExportServiceManager.outputPath.value = outFile.absolutePath

                val videoEffectsBuilder = ImmutableList.builder<androidx.media3.common.Effect>()
                if (targetHeight != null && targetHeight > 0) {
                    videoEffectsBuilder.add(Presentation.createForHeight(targetHeight))
                }
                videoEffectsBuilder.add(OverlayEffect(textureOverlays.build()))

                val videoEffects: List<androidx.media3.common.Effect> = videoEffectsBuilder.build()
                val audioProcessors: List<androidx.media3.common.audio.AudioProcessor> = emptyList()
                val effects = androidx.media3.transformer.Effects(audioProcessors, videoEffects)

                val editedMediaItemBuilder = EditedMediaItem.Builder(
                    MediaItem.fromUri(project.workingVideoPath)
                ).setEffects(effects)
                if (targetFps != null && targetFps > 0) {
                    editedMediaItemBuilder.setFrameRate(targetFps)
                }
                val editedMediaItem = editedMediaItemBuilder.build()

                val encoderSettingsBuilder = androidx.media3.transformer.VideoEncoderSettings.Builder()
                if (targetBitrate != null && targetBitrate > 0) encoderSettingsBuilder.setBitrate(targetBitrate)
                val encoderSettings = encoderSettingsBuilder.build()

                val encoderFactory = androidx.media3.transformer.DefaultEncoderFactory.Builder(this@ExportForegroundService)
                    .setRequestedVideoEncoderSettings(encoderSettings)
                    .build()

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    val transformer = Transformer.Builder(this@ExportForegroundService)
                        .setVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H264)
                        .setEncoderFactory(encoderFactory)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(
                                composition: Composition,
                                exportResult: ExportResult
                            ) {
                                if (isFinishing) return
                                Log.d(TAG, "Export completed")
                                isFinishing = true
                                progressJob?.cancel()
                                activeTransformer = null
                                ExportServiceManager.exportState.value = ExportState.Success
                                serviceScope.launch {
                                    val timestamp = System.currentTimeMillis()
                                    projectRepository.updateProject(
                                        project.copy(
                                            status = ProjectStatus.EXPORTED,
                                            exportedVideoPath = outFile.absolutePath,
                                            updatedAt = timestamp
                                        )
                                    )
                                    exportedFileDao.insertExportedFile(
                                        ExportedFileEntity(
                                            id = UUID.randomUUID().toString(),
                                            projectId = project.id,
                                            videoFilePath = outFile.absolutePath,
                                            srtFilePath = null,
                                            exportedAt = timestamp,
                                            quality = targetBitrate?.let { "$it bps" }
                                        )
                                    )
                                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                                        stopExportService()
                                    }
                                }
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException
                            ) {
                                if (isFinishing) return
                                Log.e(TAG, "Export failed: ${exportException.message}")
                                isFinishing = true
                                progressJob?.cancel()
                                activeTransformer = null
                                crashReporter.recordException(exportException)
                                ExportServiceManager.exportState.value = ExportState.Error(exportException.message ?: "Unknown Export Error")
                                currentOutFile?.delete()
                                serviceScope.launch {
                                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                                        stopExportService()
                                    }
                                }
                            }
                        })
                        .build()

                    activeTransformer = transformer
                    transformer.start(editedMediaItem, outFile.absolutePath)
                }

                progressJob = serviceScope.launch(Dispatchers.Main) {
                    val progressHolder = ProgressHolder()
                    while (activeTransformer != null && !isFinishing) {
                        val progressState = activeTransformer?.getProgress(progressHolder)
                        if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                            val p = progressHolder.progress
                            ExportServiceManager.progress.value = p / 100f
                            updateNotificationProgress(p)
                        }
                        delay(500.milliseconds)
                    }
                }

            } catch (e: Throwable) {
                if (isFinishing) return@launch
                Log.e(TAG, "Export error: ${e.message}")
                isFinishing = true
                progressJob?.cancel()
                activeTransformer = null
                crashReporter.recordException(e)
                ExportServiceManager.exportState.value = ExportState.Error(e.message ?: "Unknown error")
                currentOutFile?.delete()
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    stopExportService()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        progressJob?.cancel()
        activeTransformer?.cancel()
        activeTransformer = null
        if (!isFinishing) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID)
        }
    }

    private fun stopExportService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }
}
