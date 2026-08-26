package com.dipdev.aiautocaptioner.ui.recorder.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import android.os.Looper
import java.io.File

/**
 * Camera2 implementation of [CameraEngine].
 *
 * This is a **process-scoped singleton** via Hilt `@Singleton` binding. All mutable internal state
 * (camera device, capture session, media recorder, surfaces) is exclusively accessed from
 * [cameraHandler] — the single dedicated [HandlerThread]. Public API methods post work to
 * [cameraHandler] and are safe to call from any thread.
 *
 * Lifecycle:
 * - [open] / [close] control the camera device. [close] can be called repeatedly (safe to call
 *   from ViewModel `onCleared`).
 * - [release] tears down the handler thread permanently and must only be called at process death
 *   (effectively unreachable after removing it from ViewModel lifecycle).
 *
 * Threading contract:
 * - Mutable fields annotated with `@Volatile` are read/written from multiple threads but always
 *   with single-writer discipline or atomic operations.
 * - Non-volatile mutable fields are only accessed from [cameraHandler] threads.
 */
class Camera2Engine(
    private val context: Context
) : CameraEngine {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<CameraState>(CameraState.Idle)
    override val state: StateFlow<CameraState> = _state

    override val textureView: TextureView by lazy { createTextureView() }

    override val maxZoomRatio: Float
        get() = maxZoomValue

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val cameraThread = HandlerThread("Camera2Thread").also { it.start() }
    private val cameraHandler = Handler(cameraThread.looper)

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewSurface: Surface? = null
    private var imageReader: ImageReader? = null
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentRecordingListener: RecordingListener? = null

    @Volatile private var targetArWidth = 9
    @Volatile private var targetArHeight = 16
    @Volatile private var useFrontCamera = true
    @Volatile private var torchEnabled = false
    @Volatile private var frameAnalyzer: FrameAnalyzer? = null
    @Volatile private var currentZoomRatio = 1f
    @Volatile private var previewTargetFps = 30
    private var maxZoomValue = 1f
    private var sensorActiveArray: android.graphics.Rect? = null
    private var bufferWidth = 0
    private var bufferHeight = 0

    @Volatile private var surfaceAvailable = false
    @Volatile private var openRequested = false
    @Volatile private var openingInProgress = false
    private var sensorOrientation = 0
    private var currentCameraId: String? = null

    private fun createTextureView(): TextureView {
        return TextureView(context).apply {
            addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                val w = right - left
                val h = bottom - top
                if (w > 0 && h > 0) {
                    mainHandler.post { applyTextureTransform(this@apply, w, h) }
                }
            }
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                    cameraHandler.post { onSurfaceAvailable(st) }
                }

                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}

                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                    cameraHandler.post { onSurfaceDestroyed() }
                    return true
                }

                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
            }
        }
    }

    private fun applyTextureTransform(tv: TextureView, viewWidth: Int, viewHeight: Int) {
        val bufW = bufferWidth
        val bufH = bufferHeight
        if (bufW == 0 || bufH == 0 || viewWidth == 0 || viewHeight == 0) return

        val viewRatio = viewWidth.toFloat() / viewHeight.toFloat()
        val bufferRatio = bufW.toFloat() / bufH.toFloat()

        val scale: Float
        if (bufferRatio > viewRatio) {
            scale = viewWidth.toFloat() / bufW.toFloat()
        } else {
            scale = viewHeight.toFloat() / bufH.toFloat()
        }

        val dx = (viewWidth - bufW * scale) / 2f
        val dy = (viewHeight - bufH * scale) / 2f

        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate(dx, dy)
        tv.setTransform(matrix)
    }

    private fun getCameraId(): String {
        val facing = if (useFrontCamera) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        return cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == facing
        } ?: throw IllegalStateException("No camera found for facing: $facing")
    }

    private fun getStreamConfigMap(cameraId: String): StreamConfigurationMap =
        cameraManager.getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: throw IllegalStateException("Camera $cameraId does not support stream configuration")

    private fun chooseTextureViewSize(map: StreamConfigurationMap): Size {
        val targetLandscapeRatio = if (targetArHeight > targetArWidth) {
            targetArHeight.toFloat() / targetArWidth.toFloat()
        } else {
            targetArWidth.toFloat() / targetArHeight.toFloat()
        }
        return map.getOutputSizes(SurfaceTexture::class.java)
            .minByOrNull { size ->
                val sizeRatio = size.width.coerceAtLeast(size.height).toFloat() / size.width.coerceAtMost(
                    size.height
                ).toFloat()
                kotlin.math.abs(sizeRatio - targetLandscapeRatio)
            }
            ?: map.getOutputSizes(SurfaceTexture::class.java).first()
    }

    private fun onSurfaceAvailable(st: SurfaceTexture) {
        try {
            val cameraId = getCameraId()
            val map = getStreamConfigMap(cameraId)
            val targetSize = chooseTextureViewSize(map)
            st.setDefaultBufferSize(targetSize.width, targetSize.height)
            bufferWidth = targetSize.width
            bufferHeight = targetSize.height
            previewSurface?.release()
            previewSurface = Surface(st)
            surfaceAvailable = true
            if (openRequested) openCamera()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure surface: ${e.message}")
            _state.update { CameraState.Error }
        }
    }

    private fun onSurfaceDestroyed() {
        if (isRecording) {
            surfaceAvailable = false
            return
        }
        closeCurrentSession()
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
        previewSurface = null
        surfaceAvailable = false
        _state.update { CameraState.Idle }
    }

    private fun openCamera() {
        if (cameraDevice != null || !surfaceAvailable || openingInProgress) return
        openingInProgress = true

        try {
            val cameraId = getCameraId()
            currentCameraId = cameraId
            sensorOrientation = cameraManager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            maxZoomValue = cameraManager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
            sensorActiveArray = cameraManager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            currentZoomRatio = 1f

            val analysisSize = Size(640, 480)
            imageReader?.close()
            imageReader = ImageReader.newInstance(
            analysisSize.width, analysisSize.height,
            ImageFormat.YUV_420_888, 2
        ).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    frameAnalyzer?.analyze(image, sensorOrientation)
                } catch (e: Exception) {
                    Log.w(TAG, "Frame analysis error: ${e.message}")
                } finally {
                    image.close()
                }
            }, cameraHandler)
        }

        @SuppressLint("MissingPermission")
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                openingInProgress = false
                cameraDevice = camera
                createPreviewSession(camera)
            }

            override fun onDisconnected(camera: CameraDevice) {
                openingInProgress = false
                camera.close()
                cameraDevice = null
                _state.update { CameraState.Idle }
            }

            override fun onError(camera: CameraDevice, error: Int) {
                openingInProgress = false
                camera.close()
                cameraDevice = null
                _state.update { CameraState.Error }
                Log.e(TAG, "Camera error: $error")
            }
        }, cameraHandler)
        } catch (e: Exception) {
            openingInProgress = false
            _state.update { CameraState.Error }
            Log.e(TAG, "Failed to open camera: ${e.message}")
        }
    }

    private fun createPreviewSession(camera: CameraDevice) {
        val surface = previewSurface ?: return
        val reader = imageReader ?: return

        val surfaces = listOf(surface, reader.surface)

        val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            addTarget(reader.surface)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            if (torchEnabled) {
                set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            }
            setBestFpsRange()
        }

        val sessionConfig = SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR,
            surfaces.map { OutputConfiguration(it) },
            { cameraHandler.post(it) },
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    session.setRepeatingRequest(builder.build(), null, cameraHandler)
                    _state.update { CameraState.Active }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Capture session configure failed")
                    _state.update { CameraState.Error }
                }
            }
        )
        camera.createCaptureSession(sessionConfig)
    }

    private fun CaptureRequest.Builder.setBestFpsRange() {
        val cameraId = currentCameraId ?: return
        val ranges = cameraManager.getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        if (ranges != null) {
            val best = ranges.maxByOrNull { range ->
                when {
                    range.lower == previewTargetFps && range.upper == previewTargetFps -> 3
                    range.lower == previewTargetFps -> 2
                    range.upper == previewTargetFps -> 1
                    else -> 0
                }
            }
                ?: ranges.maxByOrNull { it.upper - it.lower }
                ?: ranges.first()
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, best)
        }
    }

    private fun closeCamera() {
        openingInProgress = false
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
            previewSurface?.release()
            previewSurface = null
        } catch (e: Exception) {
            Log.w(TAG, "Error closing camera: ${e.message}")
        }
        _state.update { CameraState.Idle }
    }

    private fun closeCurrentSession() {
        try {
            captureSession?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing session: ${e.message}")
        }
        captureSession = null
    }

    private fun restartWithNewSettings() {
        val st = textureView.surfaceTexture ?: return
        val cameraId = currentCameraId ?: return
        val map = getStreamConfigMap(cameraId)
        val targetSize = chooseTextureViewSize(map)
        st.setDefaultBufferSize(targetSize.width, targetSize.height)
        bufferWidth = targetSize.width
        bufferHeight = targetSize.height
        closeCurrentSession()
        previewSurface?.release()
        previewSurface = Surface(st)
        cameraDevice?.let { createPreviewSession(it) }
    }

    private fun buildPreviewRequest(): CaptureRequest? {
        val camera = cameraDevice ?: return null
        val surface = previewSurface ?: return null
        val reader = imageReader ?: return null

        return camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            addTarget(reader.surface)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            if (torchEnabled) {
                set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            }
            setBestFpsRange()
            applyZoomToRequest(this)
        }.build()
    }

    override fun open() {
        openRequested = true
        cameraHandler.post {
            if (surfaceAvailable) openCamera()
        }
    }

    override fun close() {
        openRequested = false
        cameraHandler.post { closeCamera() }
    }

    override fun flipCamera() {
        if (isRecording) return
        useFrontCamera = !useFrontCamera
        currentZoomRatio = 1f
        cameraHandler.post {
            closeCamera()
            val st = textureView.surfaceTexture ?: return@post
            val cameraId = try { getCameraId() } catch (e: Exception) { return@post }
            currentCameraId = cameraId
            val map = try { getStreamConfigMap(cameraId) } catch (e: Exception) { return@post }
            val targetSize = chooseTextureViewSize(map)
            st.setDefaultBufferSize(targetSize.width, targetSize.height)
            bufferWidth = targetSize.width
            bufferHeight = targetSize.height
            previewSurface?.release()
            previewSurface = Surface(st)
            surfaceAvailable = true
            if (openRequested) openCamera()
        }
    }

    override fun setTorch(enabled: Boolean) {
        torchEnabled = enabled
        cameraHandler.post {
            val session = captureSession ?: return@post
            val request = buildPreviewRequest() ?: return@post
            try {
                session.setRepeatingRequest(request, null, cameraHandler)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set torch: ${e.message}")
            }
        }
    }

    override fun setAspectAndPreview(arWidth: Int, arHeight: Int) {
        targetArWidth = arWidth
        targetArHeight = arHeight
        cameraHandler.post {
            if (captureSession != null) restartWithNewSettings()
        }
    }

    override fun setFrameAnalyzer(analyzer: FrameAnalyzer?) {
        frameAnalyzer = analyzer
    }

    override fun setFocusPoint(x: Float, y: Float, viewWidth: Int, viewHeight: Int) {
        cameraHandler.post {
            val session = captureSession ?: return@post
            val camera = cameraDevice ?: return@post
            val activeArray = sensorActiveArray ?: return@post

            val sensorW = activeArray.width().toFloat()
            val sensorH = activeArray.height().toFloat()

            val sensorX = (x / viewWidth * sensorW).toInt().coerceIn(0, activeArray.width() - 1)
            val sensorY = (y / viewHeight * sensorH).toInt().coerceIn(0, activeArray.height() - 1)

            val halfW = (sensorW / 6).toInt()
            val halfH = (sensorH / 6).toInt()
            val focusRect = MeteringRectangle(
                (sensorX - halfW).coerceAtLeast(0),
                (sensorY - halfH).coerceAtLeast(0),
                halfW * 2,
                halfH * 2,
                MeteringRectangle.METERING_WEIGHT_MAX
            )

            val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewSurface!!)
                imageReader?.let { addTarget(it.surface) }
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(focusRect))
                set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(focusRect))
                set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
                set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)
                if (torchEnabled) {
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                }
                applyZoomToRequest(this)
            }

            session.setRepeatingRequest(requestBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    val afState = result.get(CaptureResult.CONTROL_AF_STATE)
                    if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                        afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                        switchBackToContinuousAf()
                    }
                }
            }, cameraHandler)
        }
    }

    private fun switchBackToContinuousAf() {
        val session = captureSession ?: return
        val request = buildPreviewRequest() ?: return
        try {
            session.setRepeatingRequest(request, null, cameraHandler)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore continuous AF: ${e.message}")
        }
    }

    override fun setZoomRatio(ratio: Float) {
        val clamped = ratio.coerceIn(1f, maxZoomValue)
        currentZoomRatio = clamped
        cameraHandler.post {
            val session = captureSession ?: return@post
            val request = buildPreviewRequest() ?: return@post
            try {
                session.setRepeatingRequest(request, null, cameraHandler)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set zoom: ${e.message}")
            }
        }
    }

    override fun setPreviewFps(fps: Int) {
        previewTargetFps = fps
        cameraHandler.post {
            val session = captureSession ?: return@post
            val request = buildPreviewRequest() ?: return@post
            try {
                session.setRepeatingRequest(request, null, cameraHandler)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set preview FPS: ${e.message}")
            }
        }
    }

    private fun applyZoomToRequest(builder: CaptureRequest.Builder) {
        val activeArray = sensorActiveArray ?: return
        if (currentZoomRatio <= 1f) return

        val zoomFactor = 1f / currentZoomRatio
        val cropW = (activeArray.width() * zoomFactor).toInt()
        val cropH = (activeArray.height() * zoomFactor).toInt()
        val cropX = (activeArray.width() - cropW) / 2
        val cropY = (activeArray.height() - cropH) / 2
        builder.set(CaptureRequest.SCALER_CROP_REGION, android.graphics.Rect(cropX, cropY, cropX + cropW, cropY + cropH))
    }

    override fun startRecording(
        file: File,
        videoWidth: Int,
        videoHeight: Int,
        videoBitrate: Int,
        videoFrameRate: Int,
        audioBitrate: Int,
        listener: RecordingListener
    ): ActiveRecording {
        cameraHandler.post {
            doStartRecording(file, videoWidth, videoHeight, videoBitrate, videoFrameRate, audioBitrate, listener)
        }
        return object : ActiveRecording {
            override fun pause() {
                cameraHandler.post {
                    try { mediaRecorder?.pause() } catch (_: Exception) {}
                }
            }

            override fun resume() {
                cameraHandler.post {
                    try { mediaRecorder?.resume() } catch (_: Exception) {}
                }
            }

            override fun stop() {
                cameraHandler.post { doStopRecording(file, listener) }
            }
        }
    }

    private fun doStartRecording(
        file: File,
        videoWidth: Int,
        videoHeight: Int,
        videoBitrate: Int,
        videoFrameRate: Int,
        audioBitrate: Int,
        listener: RecordingListener
    ) {
        val camera = cameraDevice ?: run {
            mainHandler.post { listener.onRecordingError(IllegalStateException("Camera not open")) }
            return
        }
        if (captureSession == null) {
            mainHandler.post { listener.onRecordingError(IllegalStateException("Session not ready")) }
            return
        }
        val surface = previewSurface ?: run {
            mainHandler.post { listener.onRecordingError(IllegalStateException("Preview surface not ready")) }
            return
        }

        currentRecordingListener = listener

        val mainListener = object : RecordingListener {
            override fun onRecordingStarted() { mainHandler.post { listener.onRecordingStarted() } }
            override fun onRecordingFinished(file: File) { mainHandler.post { listener.onRecordingFinished(file) } }
            override fun onRecordingError(error: Throwable) { mainHandler.post { listener.onRecordingError(error) } }
        }

        @Suppress("DEPRECATION")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(file.absolutePath)
            setVideoEncodingBitRate(videoBitrate)
            setVideoFrameRate(videoFrameRate)
            setAudioEncodingBitRate(audioBitrate)
            val isPortraitRequest = videoHeight > videoWidth
            val encWidth = if (isPortraitRequest) videoHeight else videoWidth
            val encHeight = if (isPortraitRequest) videoWidth else videoHeight
            setVideoSize(encWidth, encHeight)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            
            val displayRotation = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                context.display.rotation
            } else {
                @Suppress("DEPRECATION")
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                windowManager.defaultDisplay.rotation
            }
            val surfaceRotationDegrees = when (displayRotation) {
                android.view.Surface.ROTATION_0 -> 0
                android.view.Surface.ROTATION_90 -> 90
                android.view.Surface.ROTATION_180 -> 180
                android.view.Surface.ROTATION_270 -> 270
                else -> 0
            }
            val hint = (sensorOrientation - surfaceRotationDegrees + 360) % 360
            setOrientationHint(hint)
            
            prepare()
        }
        mediaRecorder = rec

        val recSurface = rec.surface

        val surfaces = mutableListOf(surface, recSurface)

        val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            addTarget(surface)
            addTarget(recSurface)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            if (torchEnabled) {
                set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            }
            setBestFpsRange()
        }

        val sessionConfig = SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR,
            surfaces.map { OutputConfiguration(it) },
            { cameraHandler.post(it) },
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(newSession: CameraCaptureSession) {
                    captureSession = newSession
                    try {
                        newSession.setRepeatingRequest(builder.build(), null, cameraHandler)
                        rec.setOnErrorListener { _, what, extra ->
                            cameraHandler.post {
                                isRecording = false
                                mainListener.onRecordingError(RuntimeException("MediaRecorder error: $what/$extra"))
                                currentRecordingListener = null
                            }
                        }
                        rec.setOnInfoListener { _, what, _ ->
                            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
                                what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                                cameraHandler.post { doStopRecording(file, listener) }
                            }
                        }
                        rec.start()
                        isRecording = true
                        mainListener.onRecordingStarted()
                    } catch (e: Exception) {
                        isRecording = false
                        currentRecordingListener = null
                        releaseMediaRecorder()
                        mainListener.onRecordingError(e)
                        switchToPreview()
                    }
                }

                override fun onConfigureFailed(newSession: CameraCaptureSession) {
                    isRecording = false
                    currentRecordingListener = null
                    releaseMediaRecorder()
                    mainListener.onRecordingError(IllegalStateException("Failed to configure recording session"))
                    switchToPreview()
                }
            }
        )

        try {
            closeCurrentSession()
            camera.createCaptureSession(sessionConfig)
        } catch (e: Exception) {
            isRecording = false
            currentRecordingListener = null
            releaseMediaRecorder()
            mainListener.onRecordingError(e)
            switchToPreview()
        }
    }

    private fun doStopRecording(file: File, listener: RecordingListener) {
        if (!isRecording) return
        isRecording = false
        val mainListener = object : RecordingListener {
            override fun onRecordingStarted() { mainHandler.post { listener.onRecordingStarted() } }
            override fun onRecordingFinished(file: File) { mainHandler.post { listener.onRecordingFinished(file) } }
            override fun onRecordingError(error: Throwable) { mainHandler.post { listener.onRecordingError(error) } }
        }
        try {
            mediaRecorder?.stop()
        } catch (e: RuntimeException) {
            file.delete()
            mainListener.onRecordingError(e)
            currentRecordingListener = null
            releaseMediaRecorder()
            switchToPreview()
            return
        }
        releaseMediaRecorder()

        if (file.exists() && file.length() > 0) {
            mainListener.onRecordingFinished(file)
        } else {
            mainListener.onRecordingError(RuntimeException("Recording file is empty"))
        }
        currentRecordingListener = null
        switchToPreview()
    }

    private fun releaseMediaRecorder() {
        mediaRecorder?.reset()
        mediaRecorder?.release()
        mediaRecorder = null
    }

    private fun switchToPreview() {
        cameraDevice?.let { createPreviewSession(it) }
    }

    override fun release() {
        openRequested = false
        cameraHandler.post {
            closeCamera()
            mediaRecorder?.let {
                try { it.reset(); it.release() } catch (_: Exception) {}
            }
            mediaRecorder = null
            cameraThread.quitSafely()
        }
        try { cameraThread.join(2000) } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "Camera2Engine"
    }
}
