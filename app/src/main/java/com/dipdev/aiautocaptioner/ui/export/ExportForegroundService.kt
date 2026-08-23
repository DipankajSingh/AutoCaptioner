package com.dipdev.aiautocaptioner.ui.export

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
import android.graphics.ColorSpace
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.DefaultVideoFrameProcessor
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.core.logging.CrashReporter
import com.dipdev.aiautocaptioner.data.db.dao.ExportedFileDao
import com.dipdev.aiautocaptioner.data.db.entity.ExportedFileEntity
import com.dipdev.aiautocaptioner.data.db.entity.ProjectEntity
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

// ─────────────────────────────────────────────────────────────────────────────
// Shared state bus — observed by ExportViewModel to drive UI.
// ─────────────────────────────────────────────────────────────────────────────
object ExportServiceManager {
    val exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val progress    = MutableStateFlow(0f)
    val etaMs       = MutableStateFlow<Long?>(null)
    val outputPath  = MutableStateFlow<String?>(null)

    fun reset() {
        exportState.value = ExportState.Idle
        progress.value    = 0f
        etaMs.value       = null
        outputPath.value  = null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ExportForegroundService
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class)
@AndroidEntryPoint
class ExportForegroundService : Service() {

    // ── Injected dependencies ─────────────────────────────────────────────────
    @Inject lateinit var projectRepository: ProjectRepository
    @Inject lateinit var captionRepository: CaptionRepository
    @Inject lateinit var overlayRepository: OverlayRepository
    @Inject lateinit var exportedFileDao: ExportedFileDao
    @Inject lateinit var crashReporter: CrashReporter

    // ── Coroutine scope — cancelled in onDestroy() to prevent leaks ───────────
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Export state ──────────────────────────────────────────────────────────
    private var activeTransformer: Transformer? = null
    private var progressJob: Job? = null
    private var currentOutFile: File? = null

    /**
     * Accessed from both Dispatchers.IO (serviceScope) and Dispatchers.Main
     * (Transformer callbacks). @Volatile ensures visibility across threads
     * without the overhead of full synchronisation.
     */
    @Volatile private var isFinishing = false

    // ── Cached system resources (avoid repeated allocations on hot paths) ─────
    private val notificationManager: NotificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }
    /** Large icon bitmap — decoded once, reused for every notification update. */
    private val largeIconBitmap: Bitmap? by lazy {
        BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
    }
    /** Last progress percentage posted to the notification (throttle guard). */
    private var lastNotifiedProgress = -1

    // ── Constants ─────────────────────────────────────────────────────────────
    companion object {
        private const val TAG = "ExportService"
        const val NOTIFICATION_ID   = 102
        const val CHANNEL_ID        = "export_channel"
        const val ACTION_CANCEL     = "com.dipdev.aiautocaptioner.ACTION_CANCEL_EXPORT"
        const val EXTRA_PROJECT_ID  = "extra_project_id"
        const val EXTRA_TARGET_FPS  = "extra_target_fps"
        const val EXTRA_TARGET_HEIGHT  = "extra_target_height"
        const val EXTRA_TARGET_BITRATE = "extra_target_bitrate"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            Log.d(TAG, "Cancel received")
            cancelExport()
            return START_NOT_STICKY
        }

        val projectId = intent?.getStringExtra(EXTRA_PROJECT_ID)
        if (projectId == null) {
            Log.w(TAG, "Started with no project ID — stopping immediately")
            stopSelf()
            return START_NOT_STICKY
        }

        // Read optional extras — treat missing or -1 as "use original"
        fun Intent.intExtraOrNull(key: String) =
            if (hasExtra(key)) getIntExtra(key, -1).takeIf { it > 0 } else null

        val targetFps     = intent.intExtraOrNull(EXTRA_TARGET_FPS)
        val targetHeight  = intent.intExtraOrNull(EXTRA_TARGET_HEIGHT)
        val targetBitrate = intent.intExtraOrNull(EXTRA_TARGET_BITRATE)

        isFinishing          = false
        lastNotifiedProgress = -1

        startForegroundNotification()
        ExportServiceManager.exportState.value = ExportState.Running
        ExportServiceManager.progress.value    = 0f

        startExport(projectId, targetFps, targetHeight, targetBitrate)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        progressJob?.cancel()
        activeTransformer?.cancel()
        activeTransformer = null
        // Cancel the scope to stop any in-flight DB / IO work launched after a
        // destroy (e.g., if the OS kills the service mid-export).
        serviceScope.cancel()
        // Only remove the notification if we didn't already post a terminal one.
        if (!isFinishing) {
            notificationManager.cancel(NOTIFICATION_ID)
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Foreground service timeout — stopping")
        stopSelf(startId)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cancellation
    // ─────────────────────────────────────────────────────────────────────────

    private fun cancelExport() {
        isFinishing = true
        progressJob?.cancel()
        activeTransformer?.cancel()
        activeTransformer = null
        currentOutFile?.delete()
        ExportServiceManager.exportState.value = ExportState.Cancelled
        ExportServiceManager.progress.value    = 0f
        ExportServiceManager.etaMs.value       = null
        stopExportService()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.export_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = buildExportNotification(
            title       = getString(R.string.export_notif_title),
            contentText = getString(R.string.export_notif_content),
            isIndeterminate = true
        )

        try {
            if (Build.VERSION.SDK_INT >= 35) {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
                )
            } else {
                // API 24–34: plain foreground service (type not required/available)
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            crashReporter.recordException(e)
            Log.e(TAG, "Failed to start foreground: ${e.message}")
            stopSelf()
        }
    }

    private fun buildExportNotification(
        title: String,
        contentText: String,
        bigText: String? = null,
        progress: Int? = null,
        isIndeterminate: Boolean = false,
        isFinished: Boolean = false,
        isError: Boolean = false,
        showActions: Boolean = true
    ): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(largeIconBitmap)          // reuses cached Bitmap
            .setContentTitle(title)
            .setContentText(contentText)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(!isFinished)
            .setAutoCancel(isFinished)
            .setColor(
                when {
                    isError    -> 0xFFEF4444.toInt()
                    isFinished -> 0xFF22C55E.toInt()
                    else       -> 0xFFF59E0B.toInt()
                }
            )

        if (showActions) {
            builder.addAction(R.drawable.ic_logo_ui,
                getString(R.string.notif_action_open), getOpenAppPendingIntent())
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notif_action_cancel), getCancelPendingIntent())
        }

        if (bigText != null) {
            builder.setStyle(
                NotificationCompat.BigTextStyle().bigText(bigText).setSummaryText(title)
            )
        }

        when {
            progress != null   -> builder.setProgress(100, progress, false)
            isIndeterminate    -> builder.setProgress(100, 0, true)
            else               -> builder.setProgress(0, 0, false)
        }

        return builder.build()
    }

    /**
     * Posts a progress notification only when the integer percentage has
     * changed by at least 1 point — avoids hammering the notification
     * binder every 500 ms when progress is stalled.
     */
    private fun updateNotificationProgress(progressPct: Int, etaMs: Long?) {
        if (isFinishing || progressPct == lastNotifiedProgress) return
        lastNotifiedProgress = progressPct
        val etaText = etaMs?.let { ", ETA ${formatEta(it)}" } ?: ""
        val notification = buildExportNotification(
            title       = getString(R.string.export_notif_title),
            contentText = "Rendering… $progressPct%",
            bigText     = "Exporting your video with captions…\n$progressPct% complete$etaText",
            progress    = progressPct
        )
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun getOpenAppPendingIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        } ?: Intent()
        return PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getCancelPendingIntent(): PendingIntent {
        val cancelIntent = Intent(this, ExportForegroundService::class.java).apply {
            action = ACTION_CANCEL
        }
        return PendingIntent.getService(
            this, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core export pipeline
    // ─────────────────────────────────────────────────────────────────────────

    private fun startExport(
        projectId: String,
        targetFps: Int?,
        targetHeight: Int?,
        targetBitrate: Int?
    ) {
        serviceScope.launch {
            try {
                // ── 1. Load project ───────────────────────────────────────────
                val project = projectRepository.getProjectById(projectId)
                    ?: throw IllegalStateException("Project $projectId not found")

                // Validate the source file exists before we hand it to Transformer
                val sourceFile = File(project.workingVideoPath)
                if (!sourceFile.exists()) {
                    throw java.io.FileNotFoundException(
                        "Source video missing: ${project.workingVideoPath}"
                    )
                }

                val isPortrait   = project.videoRotation == 90 || project.videoRotation == 270
                val displayWidth  = if (isPortrait) project.videoHeight else project.videoWidth
                val displayHeight = if (isPortrait) project.videoWidth  else project.videoHeight

                // ── 2. Build overlay effects ──────────────────────────────────
                val textureOverlays = ImmutableList.builder<TextureOverlay>()

                // Caption overlay
                val activeStyle = project.activeStyleId
                    ?.let { captionRepository.getStyleById(it) }
                    ?: captionRepository.getFirstStyle()
                var captionOverlayEffect: CaptionOverlayEffect? = null
                val segments = captionRepository.getSegmentsOnce(projectId)
                if (activeStyle != null && segments.isNotEmpty()) {
                    val wordsMap = captionRepository
                        .getAllWordsForProject(projectId)
                        .groupBy { it.segmentId }
                    captionOverlayEffect = CaptionOverlayEffect(
                        context        = this@ExportForegroundService,
                        segments       = segments,
                        wordsMap       = wordsMap,
                        style          = activeStyle,
                        rotationDegrees = project.videoRotation
                    )
                    textureOverlays.add(captionOverlayEffect)
                }

                // Text overlays
                val textOverlayEffects = overlayRepository
                    .getTextOverlaysForProjectSync(projectId)
                    .mapNotNull { overlay ->
                        try {
                            TextOverlayEffect(
                                context         = this@ExportForegroundService,
                                overlay         = overlay,
                                videoWidth      = displayWidth,
                                videoHeight     = displayHeight,
                                rotationDegrees = project.videoRotation
                            )
                        } catch (e: Throwable) {
                            crashReporter.recordException(e)
                            Log.w(TAG, "Skipped text overlay: ${e.message}")
                            null
                        }
                    }
                textureOverlays.addAll(textOverlayEffects)

                // Image overlays — content-URI images are read once into a
                // ByteArray so we only open the stream once instead of twice.
                val imageOverlayEffects = overlayRepository
                    .getOverlaysOnce(projectId)
                    .mapNotNull { overlay ->
                        try {
                            val maxW = (displayWidth  * overlay.scaleX).toInt().coerceAtLeast(256)
                            val maxH = (displayHeight * overlay.scaleY).toInt().coerceAtLeast(256)

                            // Read source bytes once regardless of URI scheme
                            val bytes: ByteArray? = readImageBytes(overlay.imageUri)
                            if (bytes == null) {
                                Log.w(TAG, "Could not read image: ${overlay.imageUri}")
                                return@mapNotNull null
                            }

                            // Pass 1 — bounds only (no pixel allocation)
                            val boundsOpts = BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)

                            // Pass 2 — sub-sampled decode
                            val opts = BitmapFactory.Options().apply {
                                inSampleSize = calculateInSampleSize(boundsOpts, maxW, maxH)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
                                }
                            }
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                                ?: return@mapNotNull null

                            ImageOverlayEffect(
                                bitmap          = bitmap,
                                positionX       = overlay.positionX,
                                positionY       = overlay.positionY,
                                scaleX          = overlay.scaleX,
                                scaleY          = overlay.scaleY,
                                startTimeMs     = overlay.startTimeMs,
                                endTimeMs       = overlay.endTimeMs,
                                videoWidth      = displayWidth,
                                videoHeight     = displayHeight,
                                rotationDegrees = project.videoRotation,
                                opacity         = overlay.opacity,
                                filterName      = overlay.filterName,
                                isFlippedX      = overlay.isFlippedX
                            )
                        } catch (e: Throwable) {
                            crashReporter.recordException(e)
                            Log.w(TAG, "Skipped image overlay: ${e.message}")
                            null
                        }
                    }
                textureOverlays.addAll(imageOverlayEffects)

                // ── 3. Prepare output file ────────────────────────────────────
                val outDir  = File(filesDir, "exports").also { it.mkdirs() }
                val outFile = File(outDir, "export_${System.currentTimeMillis()}.mp4")
                currentOutFile = outFile
                ExportServiceManager.outputPath.value = outFile.absolutePath

                // ── 4. Build video effects chain ──────────────────────────────
                val videoEffectsBuilder = ImmutableList.builder<androidx.media3.common.Effect>()

                // Prevent upscaling to 4K when source is sub-4K
                val resolvedTargetHeight = when {
                    targetHeight == null || targetHeight <= 0 -> null
                    targetHeight >= 2160 &&
                        project.videoWidth < 3840 && project.videoHeight < 3840 -> null
                    else -> targetHeight
                }
                videoEffectsBuilder.add(OverlayEffect(textureOverlays.build()))
                if (resolvedTargetHeight != null) {
                    videoEffectsBuilder.add(Presentation.createForHeight(resolvedTargetHeight))
                }

                val effects = Effects(
                    emptyList(),
                    videoEffectsBuilder.build()
                )

                // ── 5. Build EditedMediaItem ──────────────────────────────────
                val editedMediaItem = EditedMediaItem.Builder(
                    MediaItem.fromUri(project.workingVideoPath)
                ).setEffects(effects)
                    .apply { if (targetFps != null) setFrameRate(targetFps) }
                    .build()

                // ── 6. Build encoder factory ──────────────────────────────────
                val encoderSettings = VideoEncoderSettings.Builder()
                    .apply { if (targetBitrate != null) setBitrate(targetBitrate) }
                    .build()

                val encoderFactory = DefaultEncoderFactory.Builder(this@ExportForegroundService)
                    .setRequestedVideoEncoderSettings(encoderSettings)
                    .build()

                // ── 7. Start Transformer on Main thread ───────────────────────
                withContext(Dispatchers.Main) {
                    val videoFrameProcessorFactory = DefaultVideoFrameProcessor.Factory.Builder()
                        .setSdrWorkingColorSpace(DefaultVideoFrameProcessor.WORKING_COLOR_SPACE_ORIGINAL)
                        .build()

                    val transformer = Transformer.Builder(this@ExportForegroundService)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setEncoderFactory(encoderFactory)
                        .setVideoFrameProcessorFactory(videoFrameProcessorFactory)
                        .experimentalSetMaxFramesInEncoder(4)
                        .addListener(buildTransformerListener(
                            outFile             = outFile,
                            outDir              = outDir,
                            projectId           = projectId,
                            targetBitrate       = targetBitrate,
                            captionOverlay      = captionOverlayEffect,
                            imageOverlays       = imageOverlayEffects,
                            textOverlays        = textOverlayEffects,
                            project             = project
                        ))
                        .build()

                    activeTransformer = transformer
                    transformer.start(editedMediaItem, outFile.absolutePath)
                }

                // ── 8. Progress polling loop (on Main to match Transformer) ───
                startProgressPolling()

            } catch (e: Throwable) {
                handleExportFailure(e)
            }
        }
    }

    /**
     * Builds the Transformer listener as a named function to keep [startExport]
     * from becoming an unreadable wall of nested lambdas.
     */
    private fun buildTransformerListener(
        outFile: File,
        outDir: File,
        projectId: String,
        targetBitrate: Int?,
        captionOverlay: CaptionOverlayEffect?,
        imageOverlays: List<ImageOverlayEffect>,
        textOverlays: List<TextOverlayEffect>,
        project: ProjectEntity
    ): Transformer.Listener = object : Transformer.Listener {

        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
            if (isFinishing) return
            Log.d(TAG, "Export completed — ${exportResult.videoFrameCount} frames")

            isFinishing = true
            progressJob?.cancel()
            activeTransformer = null

            releaseOverlays(captionOverlay, imageOverlays, textOverlays)

            ExportServiceManager.progress.value = 1f
            ExportServiceManager.etaMs.value    = null

            // Persist records FIRST, then signal success so the UI never
            // celebrates before the data is committed.
            serviceScope.launch {
                try {
                    val timestamp = System.currentTimeMillis()

                    projectRepository.updateProject(
                        project.copy(
                            status            = ProjectStatus.EXPORTED,
                            exportedVideoPath = outFile.absolutePath,
                            updatedAt         = timestamp
                        )
                    )

                    val srtFile = File(outDir, outFile.nameWithoutExtension + ".srt")
                    val srtContent = withContext(Dispatchers.IO) {
                        captionRepository.buildSrtContent(projectId)
                    }
                    srtFile.writeText(srtContent)

                    exportedFileDao.insertExportedFile(
                        ExportedFileEntity(
                            id            = UUID.randomUUID().toString(),
                            projectId     = project.id,
                            videoFilePath = outFile.absolutePath,
                            srtFilePath   = srtFile.absolutePath,
                            exportedAt    = timestamp,
                            quality       = targetBitrate?.let { "$it bps" }
                        )
                    )

                    // Only now tell the UI we succeeded
                    ExportServiceManager.exportState.value = ExportState.Success

                } catch (e: Throwable) {
                    // DB/SRT failure after a successful encode — record it but
                    // still show success (video is intact on disk).
                    crashReporter.recordException(e)
                    Log.e(TAG, "Post-export DB write failed: ${e.message}")
                    ExportServiceManager.exportState.value = ExportState.Success
                } finally {
                    withContext(Dispatchers.Main) { stopExportService() }
                }
            }
        }

        override fun onError(
            composition: Composition,
            exportResult: ExportResult,
            exportException: ExportException
        ) {
            if (isFinishing) return
            Log.e(TAG, "Transformer error [${exportException.errorCode}]: ${exportException.message}")

            isFinishing = true
            progressJob?.cancel()
            activeTransformer = null

            releaseOverlays(captionOverlay, imageOverlays, textOverlays)
            crashReporter.recordException(exportException)

            ExportServiceManager.etaMs.value    = null
            ExportServiceManager.exportState.value = ExportState.Error(
                friendlyErrorMessage(this@ExportForegroundService, exportException)
            )
            currentOutFile?.delete()

            serviceScope.launch {
                withContext(Dispatchers.Main) { stopExportService() }
            }
        }
    }

    /** Progress polling loop — runs on Main to safely call [Transformer.getProgress]. */
    private fun startProgressPolling() {
        progressJob = serviceScope.launch(Dispatchers.Main) {
            val holder        = ProgressHolder()
            val startMs       = System.currentTimeMillis()
            var timeAt98Ms    = 0L

            while (!isFinishing) {
                // Snapshot the reference once per iteration — avoids TOCTOU
                // if another thread nulls activeTransformer mid-call.
                val transformer = activeTransformer ?: break

                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val p   = holder.progress
                    val now = System.currentTimeMillis()

                    ExportServiceManager.progress.value = p / 100f

                    val eta = when {
                        // Transformer stalls at ~99% during muxing — count
                        // elapsed since we crossed 98% so ETA converges.
                        p >= 98 -> {
                            if (timeAt98Ms == 0L) timeAt98Ms = now
                            now - timeAt98Ms
                        }
                        // Extrapolate from overall rate once past the noisy start.
                        p >= 5 && now > startMs -> ((100L - p) * (now - startMs)) / p
                        else -> -1L
                    }

                    val etaValue = eta.takeIf { it >= 0L }
                    ExportServiceManager.etaMs.value = etaValue
                    updateNotificationProgress(p, etaValue)
                }

                delay(500.milliseconds)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Error handling
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun handleExportFailure(e: Throwable) {
        if (isFinishing) return
        Log.e(TAG, "Export pipeline error: ${e.message}", e)

        isFinishing = true
        progressJob?.cancel()
        activeTransformer = null

        crashReporter.recordException(e)
        ExportServiceManager.etaMs.value       = null
        ExportServiceManager.exportState.value = ExportState.Error(
            friendlyErrorMessage(this@ExportForegroundService, e)
        )
        currentOutFile?.delete()

        withContext(Dispatchers.Main) { stopExportService() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun stopExportService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseOverlays(
        captionOverlayEffect: CaptionOverlayEffect?,
        imageOverlayEffects: List<ImageOverlayEffect>,
        textOverlayEffects: List<TextOverlayEffect>
    ) {
        captionOverlayEffect?.release()
        imageOverlayEffects.forEach { it.release() }
        textOverlayEffects.forEach  { it.release() }
    }

    /**
     * Reads an image URI into a ByteArray so it can be decoded twice
     * (first for bounds, then for pixels) without opening two streams.
     * Returns null if the URI is unreadable.
     */
    private fun readImageBytes(imageUri: String): ByteArray? = try {
        if (imageUri.startsWith("content://")) {
            contentResolver.openInputStream(imageUri.toUri())?.use(InputStream::readBytes)
        } else {
            File(imageUri).takeIf { it.exists() }?.readBytes()
        }
    } catch (e: Throwable) {
        Log.w(TAG, "Could not read image bytes from $imageUri: ${e.message}")
        null
    }

    /**
     * Calculates the largest power-of-2 sub-sampling factor that keeps the
     * decoded bitmap at or above the required dimensions.
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val rawH = options.outHeight
        val rawW = options.outWidth
        var sampleSize = 1
        if (rawH > reqHeight || rawW > reqWidth) {
            val halfH = rawH / 2
            val halfW = rawW / 2
            while ((halfH / sampleSize) >= reqHeight && (halfW / sampleSize) >= reqWidth) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top-level utilities (also used by ExportScreen / ExportViewModel)
// ─────────────────────────────────────────────────────────────────────────────

fun formatEta(etaMs: Long?): String {
    if (etaMs == null) return ""
    val seconds = etaMs / 1000
    if (seconds <= 0) return "Finishing…"
    if (seconds < 60) return "~${seconds}s"
    val minutes = (seconds + 59) / 60
    if (minutes < 60) return "~$minutes min"
    return "~${minutes / 60}h ${minutes % 60}m"
}

/**
 * Maps raw Media3 / JVM throwables to friendly, creator-readable strings.
 *
 * Crashlytics always receives the original exception — this only controls
 * what the user sees on the Export error screen.
 */
@OptIn(UnstableApi::class)
fun friendlyErrorMessage(context: Context, error: Throwable): String {
    // ── Media3 ExportException — use the structured error code ────────────────
    if (error is ExportException) {
        return when (error.errorCode) {
            ExportException.ERROR_CODE_ENCODER_INIT_FAILED ->
                context.getString(R.string.export_error_encoder_init)
            ExportException.ERROR_CODE_ENCODING_FAILED ->
                context.getString(R.string.export_error_encoding_failed)
            ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED,
            ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                context.getString(R.string.export_error_format_unsupported)
            ExportException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED ->
                context.getString(R.string.export_error_encoding_failed)
            ExportException.ERROR_CODE_MUXING_FAILED,
            ExportException.ERROR_CODE_MUXING_TIMEOUT ->
                context.getString(R.string.export_error_muxing_failed)
            ExportException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                context.getString(R.string.export_error_file_not_found)
            ExportException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            ExportException.ERROR_CODE_IO_UNSPECIFIED ->
                context.getString(R.string.export_error_io)
            else -> context.getString(R.string.export_error_generic)
        }
    }

    // ── Source file missing ────────────────────────────────────────────────────
    if (error is java.io.FileNotFoundException) {
        return context.getString(R.string.export_error_file_not_found)
    }

    // ── JVM class-not-found (e.g. LogSessionId on pre-API-31) ─────────────────
    val raw = error.message?.lowercase() ?: ""
    if (error is ClassNotFoundException || error is NoClassDefFoundError ||
        raw.contains("logsessionid") || raw.contains("failed resolution of")
    ) {
        return context.getString(R.string.export_error_device_compat)
    }

    // ── Out of memory ──────────────────────────────────────────────────────────
    if (error is OutOfMemoryError || raw.contains("out of memory") || raw.contains("oom")) {
        return context.getString(R.string.export_error_oom)
    }

    // ── Hardware codec / encoder failures ─────────────────────────────────────
    if (raw.contains("codec") || raw.contains("mediacodec") || raw.contains("encoder")) {
        return context.getString(R.string.export_error_encoder_init)
    }

    // ── Generic fallback — never expose internal stack details to users ────────
    return context.getString(R.string.export_error_generic)
}
