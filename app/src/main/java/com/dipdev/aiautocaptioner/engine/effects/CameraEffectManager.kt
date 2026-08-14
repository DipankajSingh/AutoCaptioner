package com.dipdev.aiautocaptioner.engine.effects

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.CameraEffect
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import androidx.core.util.Consumer
import androidx.media3.common.ColorInfo
import androidx.media3.common.DebugViewProvider
import androidx.media3.common.Effect
import androidx.media3.common.Format
import androidx.media3.common.SurfaceInfo
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.VideoFrameProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.DefaultVideoFrameProcessor
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * Custom concrete subclass of [CameraEffect] encapsulating our hardware GPU shader processing pipeline.
 */
@OptIn(UnstableApi::class)
class StudioGpuCameraEffect(
    targets: Int,
    executor: Executor,
    processor: SurfaceProcessor,
    errorListener: Consumer<Throwable>
) : CameraEffect(targets, executor, processor, errorListener)

/**
 * Central Controller for managing real-time hardware GPU effects in the Smart Recorder studio.
 *
 * This controller manages our 2026 Frequency-Separated Skin Smoothing ([SkinSmoothGlEffect]) and
 * 10-bit HDR-safe Creator Color Grading ([LutGlEffect]), bundling them into an unified CameraX
 * [CameraEffect] utilizing a hardware [SurfaceProcessor]. It guarantees zero-copy frame routing directly
 * to both live Compose Viewfinders and hardware MP4 video encoders without CPU frame readbacks.
 */
@OptIn(UnstableApi::class)
class CameraEffectManager @Inject constructor() {

    private var smoothnessIntensity = 0.35f
    private var activeFilter = CreatorFilter.NATURAL

    private val activeSkinEffects = mutableListOf<SkinSmoothGlEffect>()
    private val activeLutEffects = mutableListOf<LutGlEffect>()

    private val activeEffects = mutableListOf<CameraEffect>()
    private val activeProcessors = mutableListOf<StudioEffectSurfaceProcessor>()
    private val effectExecutor: Executor = Executors.newSingleThreadExecutor()

    /**
     * Creates and bundles the unified GPU effect pipeline targeting preview and video capture simultaneously.
     *
     * @param context Application or activity context required for OpenGL shader creation.
     * @param targets CameraX surface targets (defaults to [CameraEffect.PREVIEW] and [CameraEffect.VIDEO_CAPTURE]).
     * @return A set of [CameraEffect] ready to attach to [androidx.camera.view.LifecycleCameraController.setEffects].
     */
    fun buildCameraEffects(
        context: Context,
        targets: Int = CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE
    ): Set<CameraEffect> {
        activeProcessors.forEach { try { it.release() } catch (_: Exception) {} }
        activeProcessors.clear()
        activeEffects.clear()
        activeSkinEffects.clear()
        activeLutEffects.clear()

        val result = mutableListOf<CameraEffect>()

        // Build dedicated GPU effect pipeline for Live Preview Viewfinder
        if ((targets and CameraEffect.PREVIEW) != 0) {
            val previewSkin = SkinSmoothGlEffect(smoothnessIntensity)
            val previewLut = LutGlEffect(activeFilter)
            activeSkinEffects.add(previewSkin)
            activeLutEffects.add(previewLut)

            val previewProcessor = StudioEffectSurfaceProcessor(
                context = context.applicationContext,
                skinSmoothEffect = previewSkin,
                lutEffect = previewLut,
                executor = effectExecutor
            )
            activeProcessors.add(previewProcessor)
            val previewEffect = StudioGpuCameraEffect(
                CameraEffect.PREVIEW,
                effectExecutor,
                previewProcessor
            ) { error ->
                Log.e(
                    TAG,
                    "Preview GPU effect processing exception observed: ${error.message}",
                    error
                )
            }
            result.add(previewEffect)
            activeEffects.add(previewEffect)
        }

        // Build dedicated GPU effect pipeline for Video Capture
        if ((targets and CameraEffect.VIDEO_CAPTURE) != 0) {
            val videoSkin = SkinSmoothGlEffect(smoothnessIntensity)
            val videoLut = LutGlEffect(activeFilter)
            activeSkinEffects.add(videoSkin)
            activeLutEffects.add(videoLut)

            val videoProcessor = StudioEffectSurfaceProcessor(
                context = context.applicationContext,
                skinSmoothEffect = videoSkin,
                lutEffect = videoLut,
                executor = effectExecutor
            )
            activeProcessors.add(videoProcessor)
            val videoEffect = StudioGpuCameraEffect(
                CameraEffect.VIDEO_CAPTURE,
                effectExecutor,
                videoProcessor
            ) { error ->
                Log.e(
                    TAG,
                    "Video Capture GPU effect processing exception observed: ${error.message}",
                    error
                )
            }
            result.add(videoEffect)
            activeEffects.add(videoEffect)
        }

        return result.toSet()
    }

    fun setSmoothnessIntensity(intensity: Float) {
        val clamped = intensity.coerceIn(0.0f, 1.0f)
        smoothnessIntensity = clamped
        activeSkinEffects.forEach { it.setSmoothness(clamped) }
    }

    fun getSmoothnessIntensity(): Float = smoothnessIntensity

    fun setActiveFilter(filter: CreatorFilter) {
        activeFilter = filter
        activeLutEffects.forEach { it.setActiveFilter(filter) }
    }

    fun getActiveFilter(): CreatorFilter = activeFilter

    /**
     * Releases all OpenGL ES program handles, EGL surfaces, and GPU memory associated with the active effects.
     * Must be invoked during Lifecycle ON_DESTROY or ViewModel clearing to guarantee zero resource leaks.
     */
    fun release() {
        try {
            activeProcessors.forEach { it.release() }
        } catch (e: Exception) {
            Log.w(TAG, "Exception silenced during clean SurfaceProcessor GPU resource release: ${e.message}")
        } finally {
            activeProcessors.clear()
            activeEffects.clear()
            activeSkinEffects.clear()
            activeLutEffects.clear()
        }
    }

    companion object {
        private const val TAG = "CameraEffectManager"
    }
}

/**
 * Custom hardware SurfaceProcessor bridging CameraX sensor streams with Media3 GPU effects.
 *
 * Mirrors the lifecycle of the official CameraX `Media3SurfaceProcessor` adapter: a Media3
 * [VideoFrameProcessor] is only created once BOTH an input surface (from CameraX) and an output
 * surface (from the downstream consumer) are available. Creating the processor with an input but
 * no output surface makes the camera keep feeding frames that can never be rendered, which fills
 * the processor queue and stalls the whole camera pipeline (frozen preview, failed recordings).
 */
@OptIn(UnstableApi::class)
internal class StudioEffectSurfaceProcessor(
    private val context: Context,
    private val skinSmoothEffect: SkinSmoothGlEffect,
    private val lutEffect: LutGlEffect,
    private val executor: Executor
) : SurfaceProcessor {

    // Pending surfaces that have not been connected to a processor yet.
    private var pendingInput: SurfaceRequest? = null
    private var pendingOutput: SurfaceOutput? = null

    // Surfaces that are currently wired into the active processor.
    private var connectedInput: SurfaceRequest? = null
    private var connectedOutput: SurfaceOutput? = null
    private var connectedProcessor: VideoFrameProcessor? = null

    // Processors that must stay alive until their surfaces are closed.
    private val activeProcessors = mutableSetOf<VideoFrameProcessor>()
    private var isReleased = false

    override fun onInputSurface(request: SurfaceRequest) {
        if (isReleased) {
            request.willNotProvideSurface()
            return
        }
        executor.execute {
            if (isReleased) {
                request.willNotProvideSurface()
                return@execute
            }
            pendingInput?.willNotProvideSurface()
            pendingInput = request
            disconnectProcessor(connectedProcessor)
            tryConnect()
        }
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        if (isReleased) return
        executor.execute {
            if (isReleased) return@execute
            connectedInput?.invalidate()
            pendingOutput = surfaceOutput
            disconnectProcessor(connectedProcessor)
            tryConnect()
        }
    }

    /**
     * Connects the pending input/output pair to a fresh processor once both are available.
     * A pre-existing connected output can be reused while we wait for a new input request.
     */
    private fun tryConnect() {
        val input = pendingInput
        val output = pendingOutput ?: connectedOutput
        if (input != null && output != null) {
            connectInputAndOutput(input, output)
        }
    }

    private fun disconnectProcessor(processor: VideoFrameProcessor?) {
        if (processor != null && activeProcessors.contains(processor)) {
            activeProcessors.remove(processor)
            try {
                processor.release()
            } catch (e: Exception) {
                Log.w("StudioEffectProcessor", "Error releasing VideoFrameProcessor: ${e.message}")
            }
        }
    }

    private fun connectInputAndOutput(input: SurfaceRequest, output: SurfaceOutput) {
        try {
            val identityMatrix = FloatArray(16).apply { android.opengl.Matrix.setIdentityM(this, 0) }
            output.updateTransformMatrix(identityMatrix, identityMatrix)

            val colorInfo = ColorInfo.SDR_BT709_LIMITED
            val processor = DefaultVideoFrameProcessor.Factory.Builder().build().create(
                context,
                DebugViewProvider.NONE,
                colorInfo,
                /* renderFramesAutomatically= */ true,
                executor,
                object : VideoFrameProcessor.Listener {
                    override fun onError(exception: VideoFrameProcessingException) {
                        Log.e("StudioEffectProcessor", "Frame processing exception: ${exception.message}", exception)
                    }
                    override fun onEnded() {
                        Log.i("StudioEffectProcessor", "Video frame processing ended cleanly.")
                    }
                }
            )

            val effects: List<Effect> = listOf(skinSmoothEffect, lutEffect)

            val resolution = input.resolution
            val format = Format.Builder()
                .setWidth(resolution.width)
                .setHeight(resolution.height)
                .setColorInfo(colorInfo)
                .build()

            processor.registerInputStream(
                VideoFrameProcessor.INPUT_TYPE_SURFACE_AUTOMATIC_FRAME_REGISTRATION,
                format,
                effects,
                0L
            )
            activeProcessors.add(processor)

            processor.setOnInputSurfaceReadyListener {
                val inputSurface = processor.inputSurface
                input.provideSurface(inputSurface, executor) {
                    disconnectProcessor(processor)
                    if (connectedInput == input) {
                        connectedInput = null
                    }
                }
            }
            connectedInput = input

            val outputSurface = output.getSurface(executor) {
                disconnectProcessor(processor)
                output.close()
                if (connectedOutput == output) {
                    connectedOutput = null
                }
            }
            processor.setOutputSurfaceInfo(
                SurfaceInfo(
                    outputSurface,
                    output.size.width,
                    output.size.height,
                    0 // orientationDegrees
                )
            )
            connectedOutput = output

            pendingInput = null
            pendingOutput = null
            connectedProcessor = processor
        } catch (e: Exception) {
            Log.e("StudioEffectProcessor", "Failed to initialize Media3 VideoFrameProcessor", e)
            pendingInput?.willNotProvideSurface()
            pendingInput = null
            pendingOutput = null
        }
    }

    fun release() {
        executor.execute {
            if (isReleased) return@execute
            isReleased = true
            pendingInput?.willNotProvideSurface()
            pendingInput = null
            pendingOutput = null
            disconnectProcessor(connectedProcessor)
            connectedInput = null
            connectedOutput = null
        }
    }
}
