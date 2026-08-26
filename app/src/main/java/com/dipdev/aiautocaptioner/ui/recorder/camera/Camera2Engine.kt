package com.dipdev.aiautocaptioner.ui.recorder.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
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
import java.io.File

class Camera2Engine(
    private val context: Context
) : CameraEngine {

    private val _state = MutableStateFlow<CameraState>(CameraState.Idle)
    override val state: StateFlow<CameraState> = _state

    override val textureView: TextureView by lazy { createTextureView() }

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

    private var surfaceAvailable = false
    private var openRequested = false
    private var openingInProgress = false
    private var sensorOrientation = 0
    private var currentCameraId: String? = null

    private fun createTextureView(): TextureView {
        return TextureView(context).apply {
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

    private fun getCameraId(): String {
        val facing = if (useFrontCamera) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        return cameraManager.cameraIdList.first { id ->
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == facing
        }
    }

    private fun getStreamConfigMap(cameraId: String): StreamConfigurationMap =
        cameraManager.getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!

    private fun chooseTextureViewSize(map: StreamConfigurationMap): Size {
        val targetLandscapeRatio = if (targetArHeight > targetArWidth) {
            targetArHeight.toFloat() / targetArWidth.toFloat()
        } else {
            targetArWidth.toFloat() / targetArHeight.toFloat()
        }
        return map.getOutputSizes(SurfaceTexture::class.java)
            .minByOrNull { size ->
                val sizeRatio = Math.max(size.width, size.height).toFloat() / Math.min(size.width, size.height).toFloat()
                kotlin.math.abs(sizeRatio - targetLandscapeRatio)
            }
            ?: map.getOutputSizes(SurfaceTexture::class.java).first()
    }

    private fun onSurfaceAvailable(st: SurfaceTexture) {
        val cameraId = getCameraId()
        val map = getStreamConfigMap(cameraId)
        val targetSize = chooseTextureViewSize(map)
        st.setDefaultBufferSize(targetSize.width, targetSize.height)
        previewSurface = Surface(st)
        surfaceAvailable = true
        if (openRequested) openCamera()
    }

    private fun onSurfaceDestroyed() {
        previewSurface = null
        surfaceAvailable = false
    }

    private fun openCamera() {
        if (cameraDevice != null || !surfaceAvailable || openingInProgress) return
        openingInProgress = true

        val cameraId = getCameraId()
        currentCameraId = cameraId
        sensorOrientation = cameraManager.getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

        val analysisSize = Size(640, 480)
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
            val best = ranges.maxByOrNull { it.upper - it.lower } ?: ranges.first()
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
        } catch (e: Exception) {
            Log.w(TAG, "Error closing camera: ${e.message}")
        }
        _state.update { CameraState.Idle }
    }

    private fun restartWithNewSettings() {
        val st = textureView.surfaceTexture ?: return
        val cameraId = currentCameraId ?: return
        val map = getStreamConfigMap(cameraId)
        val targetSize = chooseTextureViewSize(map)
        st.setDefaultBufferSize(targetSize.width, targetSize.height)
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
        useFrontCamera = !useFrontCamera
        cameraHandler.post {
            closeCamera()
            if (openRequested && surfaceAvailable) openCamera()
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

    override fun setAspectAndPreview(ar: Int, arWidth: Int, arHeight: Int) {
        targetArWidth = arWidth
        targetArHeight = arHeight
        cameraHandler.post {
            if (captureSession != null) restartWithNewSettings()
        }
    }

    override fun setFrameAnalyzer(analyzer: FrameAnalyzer?) {
        frameAnalyzer = analyzer
    }

    override fun startRecording(
        file: File,
        videoWidth: Int,
        videoHeight: Int,
        videoBitrate: Int,
        listener: RecordingListener
    ): ActiveRecording {
        cameraHandler.post {
            doStartRecording(file, videoWidth, videoHeight, videoBitrate, listener)
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
        listener: RecordingListener
    ) {
        val camera = cameraDevice ?: run {
            listener.onRecordingError(IllegalStateException("Camera not open"))
            return
        }
        val session = captureSession ?: run {
            listener.onRecordingError(IllegalStateException("Session not ready"))
            return
        }
        val surface = previewSurface ?: run {
            listener.onRecordingError(IllegalStateException("Preview surface not ready"))
            return
        }

        currentRecordingListener = listener

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
            setVideoFrameRate(30)
            val isPortraitRequest = videoHeight > videoWidth
            val encWidth = if (isPortraitRequest) videoHeight else videoWidth
            val encHeight = if (isPortraitRequest) videoWidth else videoHeight
            setVideoSize(encWidth, encHeight)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            
            val displayRotation = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                context.display?.rotation ?: android.view.Surface.ROTATION_0
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
                                listener.onRecordingError(RuntimeException("MediaRecorder error: $what/$extra"))
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
                        listener.onRecordingStarted()
                    } catch (e: Exception) {
                        listener.onRecordingError(e)
                    }
                }

                override fun onConfigureFailed(newSession: CameraCaptureSession) {
                    listener.onRecordingError(IllegalStateException("Failed to configure recording session"))
                }
            }
        )

        try {
            camera.createCaptureSession(sessionConfig)
        } catch (e: Exception) {
            listener.onRecordingError(e)
        }
    }

    private fun doStopRecording(file: File, listener: RecordingListener) {
        if (!isRecording) return
        isRecording = false
        try {
            mediaRecorder?.stop()
        } catch (e: RuntimeException) {
            file.delete()
            listener.onRecordingError(e)
            currentRecordingListener = null
            releaseMediaRecorder()
            switchToPreview()
            return
        }
        releaseMediaRecorder()

        if (file.exists() && file.length() > 0) {
            listener.onRecordingFinished(file)
        } else {
            listener.onRecordingError(RuntimeException("Recording file is empty"))
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
        }
        cameraThread.quitSafely()
        try { cameraThread.join(500) } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "Camera2Engine"
    }
}
