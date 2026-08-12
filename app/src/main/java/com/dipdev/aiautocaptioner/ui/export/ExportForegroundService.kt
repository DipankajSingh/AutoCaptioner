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
import android.graphics.ColorSpace
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextureOverlay
import androidx.media3.effect.Presentation
import androidx.media3.effect.DefaultVideoFrameProcessor
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.dipdev.aiautocaptioner.core.logging.CrashReporter
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.data.db.dao.ExportedFileDao
import com.dipdev.aiautocaptioner.data.db.entity.ExportedFileEntity
import com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus
import com.dipdev.aiautocaptioner.data.repository.CaptionRepository
import com.dipdev.aiautocaptioner.data.repository.OverlayRepository
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import com.dipdev.aiautocaptioner.engine.CaptionOverlayEffect
import com.dipdev.aiautocaptioner.engine.ImageOverlayEffect
import com.dipdev.aiautocaptioner.engine.TextOverlayEffect
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
    val etaMs = MutableStateFlow<Long?>(null)
    val outputPath = MutableStateFlow<String?>(null)

    fun reset() {
        exportState.value = ExportState.Idle
        progress.value = 0f
        etaMs.value = null
        outputPath.value = null
    }
}

@OptIn(UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class)
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
            ExportServiceManager.etaMs.value = null
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
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 1, intent, flags)
    }

    private fun getCancelPendingIntent(): PendingIntent {
        val cancelIntent = Intent(this, ExportForegroundService::class.java).apply {
            action = ACTION_CANCEL
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getService(this, 0, cancelIntent, flags)
    }

    private fun startForegroundService() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.export_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = buildExportNotification(
            title = getString(R.string.export_notif_title),
            contentText = getString(R.string.export_notif_content),
            isIndeterminate = true
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING else 0
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

    private fun updateNotificationProgress(progress: Int, etaMs: Long?) {
        if (isFinishing) return
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val etaText = etaMs?.let { ", ETA ${formatEta(it)}" } ?: ""
        val notification = buildExportNotification(
            title = getString(R.string.export_notif_title),
            contentText = "Rendering… $progress%",
            bigText = "Exporting your video with captions…\n$progress% complete$etaText",
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

                val segments = captionRepository.getSegmentsOnce(projectId)
                val activeStyle = project.activeStyleId?.let { captionRepository.getStyleById(it) }
                    ?: if (project.creationMode == com.dipdev.aiautocaptioner.data.db.entity.CreationMode.QUICK_CAPTION) {
                        captionRepository.getFirstStyle()
                    } else null

                var captionOverlayEffect: CaptionOverlayEffect? = null
                if (activeStyle != null && segments.isNotEmpty()) {
                    val wordsList = captionRepository.getAllWordsForProject(projectId)
                    val wordsMap = wordsList.groupBy { it.segmentId }
                    captionOverlayEffect = CaptionOverlayEffect(
                        context = this@ExportForegroundService,
                        segments = segments,
                        wordsMap = wordsMap,
                        style = activeStyle,
                        videoWidth = project.videoWidth,
                        videoHeight = project.videoHeight,
                        rotationDegrees = project.videoRotation
                    )
                    textureOverlays.add(captionOverlayEffect)
                }

                val textOverlays = overlayRepository.getTextOverlaysForProjectSync(projectId)
                val textOverlayEffects = textOverlays.mapNotNull { overlay ->
                    try {
                        TextOverlayEffect(
                            context = this@ExportForegroundService,
                            overlay = overlay,
                            videoWidth = displayWidth,
                            videoHeight = displayHeight,
                            rotationDegrees = project.videoRotation
                        )
                    } catch (e: Throwable) {
                        crashReporter.recordException(e)
                        null
                    }
                }
                textureOverlays.addAll(textOverlayEffects)

                val overlays = overlayRepository.getOverlaysOnce(projectId)
                val imageOverlayEffects = overlays.mapNotNull { overlay ->
                    try {
                        val maxTargetWidth = (displayWidth * overlay.scaleX).toInt().coerceAtLeast(256)
                        val maxTargetHeight = (displayHeight * overlay.scaleY).toInt().coerceAtLeast(256)
                        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        if (overlay.imageUri.startsWith("content://")) {
                            contentResolver.openInputStream(overlay.imageUri.toUri())?.use { stream ->
                                BitmapFactory.decodeStream(stream, null, boundsOpts)
                            }
                        } else {
                            BitmapFactory.decodeFile(overlay.imageUri, boundsOpts)
                        }
                        val opts = BitmapFactory.Options().apply {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
                            }
                            inSampleSize = calculateInSampleSize(boundsOpts, maxTargetWidth, maxTargetHeight)
                        }
                        val bitmap = if (overlay.imageUri.startsWith("content://")) {
                            contentResolver.openInputStream(overlay.imageUri.toUri())?.use { stream ->
                                BitmapFactory.decodeStream(stream, null, opts)
                            }
                        } else {
                            BitmapFactory.decodeFile(overlay.imageUri, opts)
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
                                videoHeight = displayHeight,
                                rotationDegrees = project.videoRotation,
                                opacity = overlay.opacity,
                                filterName = overlay.filterName,
                                isFlippedX = overlay.isFlippedX
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
                var finalTargetHeight = targetHeight
                
                // 4K Condition Check
                if (targetHeight != null && targetHeight > 0) {
                    if (project.videoWidth >= 3840 || project.videoHeight >= 3840) {
                        // Skip upscaling logic if the original is already 4K or above,
                        // meaning if targetHeight is less than the original, we respect the downscale (e.g. 4K -> 1080p).
                        // If they chose 1080p output and original is 4K, let it scale down to 1080p.
                        // If they chose 4K output and original is 4K, let it pass through.
                        // So the presentation effect is fine.
                    } else if (targetHeight >= 2160) {
                        // Attempting to export 4K but source is less than 4K (e.g., 1080p).
                        // Skip the upscaling to save resources and file size.
                        finalTargetHeight = null
                    }
                }
                
                if (finalTargetHeight != null && finalTargetHeight > 0) {
                    videoEffectsBuilder.add(Presentation.createForHeight(finalTargetHeight))
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
                    val videoFrameProcessorFactory = DefaultVideoFrameProcessor.Factory.Builder()
                        .setSdrWorkingColorSpace(DefaultVideoFrameProcessor.WORKING_COLOR_SPACE_ORIGINAL)
                        .build()

                    val transformer = Transformer.Builder(this@ExportForegroundService)
                        .setVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H264)
                        .setEncoderFactory(encoderFactory)
                        .setVideoFrameProcessorFactory(videoFrameProcessorFactory)
                        .experimentalSetMaxFramesInEncoder(4)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(
                                composition: Composition,
                                exportResult: ExportResult
                            ) {
                                if (isFinishing) return
                                Log.d(TAG, "Export completed")
                                releaseOverlays(captionOverlayEffect, imageOverlayEffects, textOverlayEffects)
                                isFinishing = true
                                progressJob?.cancel()
                                activeTransformer = null
                                ExportServiceManager.etaMs.value = null
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
                                    val srtContent = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        captionRepository.buildSrtContent(projectId)
                                    }
                                    val srtFile = File(outDir, outFile.nameWithoutExtension + ".srt")
                                    srtFile.writeText(srtContent)
                                    exportedFileDao.insertExportedFile(
                                        ExportedFileEntity(
                                            id = UUID.randomUUID().toString(),
                                            projectId = project.id,
                                            videoFilePath = outFile.absolutePath,
                                            srtFilePath = srtFile.absolutePath,
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
                                releaseOverlays(captionOverlayEffect, imageOverlayEffects, textOverlayEffects)
                                isFinishing = true
                                progressJob?.cancel()
                                activeTransformer = null
                                crashReporter.recordException(exportException)
                                ExportServiceManager.etaMs.value = null
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
                    val exportStartMs = System.currentTimeMillis()
                    var timeAt98Ms = 0L
                    while (activeTransformer != null && !isFinishing) {
                        val progressState = activeTransformer?.getProgress(progressHolder)
                        if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                            val p = progressHolder.progress
                            ExportServiceManager.progress.value = p / 100f

                            val now = System.currentTimeMillis()
                            val eta = when {
                                // Transformer stalls at ~99% during the muxing phase.
                                // Count down from the moment we reach the end so the ETA
                                // converges instead of inflating.
                                p >= 98 -> {
                                    if (timeAt98Ms == 0L) timeAt98Ms = now
                                    now - timeAt98Ms
                                }
                                // Skip the noisy early phase, then extrapolate the
                                // remaining work from the overall progress rate.
                                p >= 5 && now > exportStartMs -> ((100L - p) * (now - exportStartMs)) / p
                                else -> -1L
                            }
                            val etaMs = eta.takeIf { it >= 0L }
                            ExportServiceManager.etaMs.value = etaMs
                            updateNotificationProgress(p, etaMs)
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
                ExportServiceManager.etaMs.value = null
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

    private fun releaseOverlays(
        captionOverlayEffect: CaptionOverlayEffect?,
        imageOverlayEffects: List<ImageOverlayEffect>,
        textOverlayEffects: List<TextOverlayEffect>
    ) {
        captionOverlayEffect?.release()
        imageOverlayEffects.forEach { it.release() }
        textOverlayEffects.forEach { it.release() }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun stopExportService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf(startId)
    }
}

fun formatEta(etaMs: Long?): String {
    if (etaMs == null) return ""
    val seconds = etaMs / 1000
    if (seconds <= 0) return "Finishing…"
    if (seconds < 60) return "~${seconds}s"
    val minutes = (seconds + 59) / 60
    if (minutes < 60) return "~$minutes min"
    return "~${minutes / 60}h ${minutes % 60}m"
}
