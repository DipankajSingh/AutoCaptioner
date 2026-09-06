package com.dipdev.aiautocaptioner.core.gl

import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.dipdev.aiautocaptioner.core.camera.RecordingAspectRatio

/**
 * Owns the single EGLContext this feature uses, and drives the camera → preview (and, from
 * stage 2 onward, camera → encoder) draw loop. All GL work happens on this thread's own
 * Looper — nothing here is ever touched from the main/Compose thread directly except through
 * the methods below, which post to [handler] and return immediately.
 *
 * Lifecycle: [start] must be called before anything else; [release] tears everything down.
 * [onCameraSurfaceReady] fires (on this thread) once the camera-input Surface exists — that
 * Surface is what should be handed to [com.dipdev.aiautocaptioner.core.camera.Camera2Session]
 * as the repeating-request target.
 */
class RendererThread(
    private val onCameraSurfaceReady: (Surface) -> Unit
) {
    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler

    private lateinit var eglCore: EglCore
    private lateinit var offscreenSurface: android.opengl.EGLSurface
    private lateinit var frameRenderer: GlFrameRenderer
    private lateinit var cameraTexture: SurfaceTexture
    private lateinit var cameraSurface: Surface

    private var previewWindowSurface: WindowSurface? = null
    private var previewViewportWidth = 0
    private var previewViewportHeight = 0

    // Encoder surface — non-null only while recording.
    private var encoderWindowSurface: WindowSurface? = null
    private var encoderViewportWidth = 0
    private var encoderViewportHeight = 0
    @Volatile private var isRecordingActive = false
    @Volatile private var pausedDurationNanos = 0L

    // Mutated via setters below (posted to this thread's handler), read only from onFrameAvailable.
    private var sensorOrientation = 90
    private var mirrorPreview = false
    private var targetAspect = RecordingAspectRatio.DEFAULT.widthRatio.toFloat() /
        RecordingAspectRatio.DEFAULT.heightRatio.toFloat()
    private var cameraFrameWidth = 0
    private var cameraFrameHeight = 0

    private val texMatrix = FloatArray(16)
    private var cachedModelMatrix: FloatArray = FrameTransform.IDENTITY
    private var cachedModelMatrixKey: String = ""

    fun start() {
        thread = HandlerThread("RecorderRendererThread").apply { start() }
        handler = Handler(thread.looper)
        handler.post {
            eglCore = EglCore()
            // EGL requires a current surface before any GL calls (including glGenTextures)
            // can be made, and the real preview surface may not exist yet — this 1x1 pbuffer
            // exists purely to give the context somewhere to be current until a real one shows up.
            offscreenSurface = eglCore.createOffscreenSurface(1, 1)
            eglCore.makeCurrent(offscreenSurface)

            frameRenderer = GlFrameRenderer()
            cameraTexture = SurfaceTexture(frameRenderer.textureId).apply {
                setOnFrameAvailableListener({ onFrameAvailable() }, handler)
            }
            cameraSurface = Surface(cameraTexture)
            onCameraSurfaceReady(cameraSurface)
        }
    }

    /** Must be called once the target output size is known (before/soon after camera opens). */
    fun setCameraFrameSize(width: Int, height: Int) {
        handler.post {
            cameraFrameWidth = width
            cameraFrameHeight = height
            cameraTexture.setDefaultBufferSize(width, height)
        }
    }

    /** Reflects the active lens's sensor orientation and whether the preview should mirror (front camera). */
    fun setTransformParams(sensorOrientationDeg: Int, mirrorPreviewForFrontCamera: Boolean) {
        handler.post {
            sensorOrientation = sensorOrientationDeg
            mirrorPreview = mirrorPreviewForFrontCamera
        }
    }

    fun setAspectRatio(ratio: RecordingAspectRatio) {
        handler.post {
            targetAspect = ratio.widthRatio.toFloat() / ratio.heightRatio.toFloat()
        }
    }

    /** Called by [com.dipdev.aiautocaptioner.ui.recorder.components.CameraPreviewView] as its TextureView surface changes. */
    fun setPreviewSurface(surface: Surface?) {
        handler.post {
            previewWindowSurface?.release()
            previewWindowSurface = surface?.let { WindowSurface(eglCore, it, ownsSurface = true) }
            if (surface == null) {
                // Nothing to draw to right now (e.g. view torn down) — fall back to the
                // offscreen surface so the GL context stays valid and frame draining doesn't stall.
                eglCore.makeCurrent(offscreenSurface)
            }
        }
    }

    fun setPreviewViewportSize(width: Int, height: Int) {
        handler.post {
            previewViewportWidth = width
            previewViewportHeight = height
        }
    }

    // -----------------------------------------------------------------------------------------
    // Stage 2 — Encoder surface control (called by RecordingSession)
    // -----------------------------------------------------------------------------------------

    /**
     * Attaches or detaches the encoder's input [Surface].
     *
     * Pass a non-null [surface] + dimensions at recording start, and `null` (with 0 dimensions)
     * at recording stop. The [WindowSurface] wraps the MediaCodec encoder's input surface;
     * [ownsSurface] is false because MediaCodec releases it during [MediaCodec.release].
     */
    fun setEncoderSurface(surface: Surface?, width: Int, height: Int) {
        handler.post {
            encoderWindowSurface?.release()
            encoderWindowSurface = surface?.let {
                WindowSurface(eglCore, it, ownsSurface = false)
            }
            encoderViewportWidth = width
            encoderViewportHeight = height
        }
    }

    /**
     * Enables or disables the encoder draw pass. When false, camera frames are still rendered
     * to the preview surface but skipped for the encoder (used during pause).
     */
    fun setRecordingActive(active: Boolean) {
        isRecordingActive = active
    }

    /**
     * Updates the total paused nanoseconds used for PTS correction on the encoder surface.
     * Called by [com.dipdev.aiautocaptioner.core.media.RecordingSession] each time recording resumes.
     */
    fun setPausedDurationNanos(nanos: Long) {
        pausedDurationNanos = nanos
    }

    // -----------------------------------------------------------------------------------------
    // Frame loop
    // -----------------------------------------------------------------------------------------

    private fun onFrameAvailable() {
        cameraTexture.updateTexImage()
        cameraTexture.getTransformMatrix(texMatrix)

        val preview = previewWindowSurface
        if (preview == null) {
            eglCore.makeCurrent(offscreenSurface)
            return
        }

        val previewMatrix = modelMatrixFor(cameraFrameWidth, cameraFrameHeight, mirror = mirrorPreview)

        // Draw to preview surface.
        preview.makeCurrent()
        frameRenderer.drawFrame(texMatrix, previewMatrix, previewViewportWidth, previewViewportHeight)
        preview.swapBuffers()

        // Draw to encoder surface (mirror = false — saved file must never be mirrored).
        val enc = encoderWindowSurface
        if (isRecordingActive && enc != null && encoderViewportWidth > 0 && encoderViewportHeight > 0) {
            val encMatrix = modelMatrixFor(cameraFrameWidth, cameraFrameHeight, mirror = false)
            // Adjust PTS by subtracting total paused duration to keep timestamps monotonic.
            val adjustedPtsNanos = (cameraTexture.timestamp - pausedDurationNanos).coerceAtLeast(0L)
            enc.makeCurrent()
            frameRenderer.drawFrame(texMatrix, encMatrix, encoderViewportWidth, encoderViewportHeight)
            enc.setPresentationTime(adjustedPtsNanos)
            enc.swapBuffers()
        }
    }

    private fun modelMatrixFor(srcWidth: Int, srcHeight: Int, mirror: Boolean): FloatArray {
        if (srcWidth == 0 || srcHeight == 0) return FrameTransform.IDENTITY
        val key = "$sensorOrientation:$srcWidth:$srcHeight:$targetAspect:$mirror"
        if (key == cachedModelMatrixKey) return cachedModelMatrix
        val matrix = FrameTransform.compute(
            sensorOrientation = sensorOrientation,
            sourceWidth = srcWidth,
            sourceHeight = srcHeight,
            targetAspect = targetAspect,
            mirrorHorizontally = mirror
        )
        cachedModelMatrix = matrix
        cachedModelMatrixKey = key
        return matrix
    }

    fun release() {
        handler.post {
            encoderWindowSurface?.release()
            previewWindowSurface?.release()
            frameRenderer.release()
            cameraSurface.release()
            cameraTexture.release()
            eglCore.releaseSurface(offscreenSurface)
            eglCore.release()
        }
        thread.quitSafely()
    }
}
