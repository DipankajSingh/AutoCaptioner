package com.dipdev.aiautocaptioner.core.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.MeteringRectangle
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.view.Surface
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Owns the Camera2 [CameraDevice] + [CameraCaptureSession] lifecycle end to end, on its own
 * background thread (Camera2 callbacks require a Handler; we never touch the main thread
 * here). Exposes zoom, torch, and tap-to-focus as plain calls — nothing above this class
 * (ViewModel, UI) ever sees a CaptureRequest.Builder directly.
 *
 * One [Camera2Session] instance = one open lens, targeting exactly one output [Surface]
 * (the SurfaceTexture-backed surface owned by [com.dipdev.aiautocaptioner.core.gl.RendererThread]).
 * Switching cameras means closing this instance and opening a fresh one — that matches the
 * Camera2 API's own model, where a CameraDevice is inherently single-lens, rather than
 * trying to reconfigure one instance in place.
 *
 * Permission note: the caller must have already confirmed CAMERA permission is granted
 * before calling [open] — this class does not request permissions itself.
 */
class Camera2Session(
    context: Context,
    private val cameraId: String,
    private val outputSurface: Surface
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val bgThread = HandlerThread("Camera2SessionThread").apply { start() }
    private val bgHandler = Handler(bgThread.looper)

    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var requestBuilder: CaptureRequest.Builder? = null

    val sensorOrientation: Int by lazy {
        cameraManager.getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
    }

    private val maxZoom: Float by lazy {
        cameraManager.getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.upper ?: 1f
    }

    @SuppressLint("MissingPermission")
    suspend fun open(): Unit = suspendCancellableCoroutine { cont ->
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                device = camera
                createSession(camera, cont)
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                device = null
                if (cont.isActive) cont.resumeWithException(IllegalStateException("Camera disconnected"))
            }

            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                device = null
                if (cont.isActive) cont.resumeWithException(IllegalStateException("Camera error code: $error"))
            }
        }, bgHandler)
    }

    private fun createSession(camera: CameraDevice, cont: CancellableContinuation<Unit>) {
        camera.createCaptureSession(
            listOf(outputSurface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(configuredSession: CameraCaptureSession) {
                    session = configuredSession
                    requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                        addTarget(outputSurface)
                        set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                        set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(30, 30))
                    }
                    applyRepeatingRequest()
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onConfigureFailed(configuredSession: CameraCaptureSession) {
                    if (cont.isActive) {
                        cont.resumeWithException(IllegalStateException("Capture session configuration failed"))
                    }
                }
            },
            bgHandler
        )
    }

    private fun applyRepeatingRequest() {
        val builder = requestBuilder ?: return
        val activeSession = session ?: return
        activeSession.setRepeatingRequest(builder.build(), null, bgHandler)
    }

    /** [ratio] is a raw zoom multiplier (1f = no zoom); clamped to what the lens supports. */
    fun setZoom(ratio: Float) {
        val builder = requestBuilder ?: return
        builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, ratio.coerceIn(1f, maxZoom))
        applyRepeatingRequest()
    }

    fun setTorch(enabled: Boolean) {
        val builder = requestBuilder ?: return
        builder.set(
            CaptureRequest.FLASH_MODE,
            if (enabled) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF
        )
        applyRepeatingRequest()
    }

    /**
     * Tap-to-focus. [normalizedX]/[normalizedY] are in [0f, 1f] view-space coordinates
     * (0,0 = top-left of the preview as the user sees it); this maps them into the sensor's
     * active-array coordinate space required by [CaptureRequest.CONTROL_AF_REGIONS].
     *
     * NOTE: because the preview is already rotated/cropped by the GL layer before the user
     * ever sees it, the caller (UI layer) is responsible for passing coordinates relative to
     * the *displayed* (post-transform) frame — this function does not undo that transform.
     * That mapping is straightforward for the common case (no rotation swap needed once GL
     * has already made the preview upright) but should be verified visually once tap-to-focus
     * is wired up in stage 3.
     */
    fun setFocusPoint(normalizedX: Float, normalizedY: Float) {
        val builder = requestBuilder ?: return
        val activeArray = cameraManager.getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return

        val regionHalfSize = (activeArray.width() * 0.05f).toInt().coerceAtLeast(1)
        val centerX = (normalizedX * activeArray.width()).toInt().coerceIn(0, activeArray.width())
        val centerY = (normalizedY * activeArray.height()).toInt().coerceIn(0, activeArray.height())
        val rect = Rect(
            (centerX - regionHalfSize).coerceAtLeast(0),
            (centerY - regionHalfSize).coerceAtLeast(0),
            (centerX + regionHalfSize).coerceAtMost(activeArray.width()),
            (centerY + regionHalfSize).coerceAtMost(activeArray.height())
        )
        val meteringRegion = arrayOf(MeteringRectangle(rect, MeteringRectangle.METERING_WEIGHT_MAX))

        builder.set(CaptureRequest.CONTROL_AF_REGIONS, meteringRegion)
        builder.set(CaptureRequest.CONTROL_AE_REGIONS, meteringRegion)
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
        applyRepeatingRequest()

        // Reset the trigger for subsequent requests so autofocus can be re-triggered by a future tap.
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
    }

    fun close() {
        session?.close()
        device?.close()
        session = null
        device = null
        requestBuilder = null
        bgThread.quitSafely()
    }
}
