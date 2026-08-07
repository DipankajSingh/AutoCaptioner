package com.dipdev.aiautocaptioner.engine.effects

import android.content.Context
import android.util.Log
import android.view.Surface
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
        activeProcessors.forEach { try { it.release() } catch (e: Exception) {} }
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
                previewProcessor,
                Consumer { error -> Log.e(TAG, "Preview GPU effect processing exception observed: ${error.message}", error) }
            )
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
                videoProcessor,
                Consumer { error -> Log.e(TAG, "Video Capture GPU effect processing exception observed: ${error.message}", error) }
            )
            result.add(videoEffect)
            activeEffects.add(videoEffect)
        }

        return result.toSet()
    }

    fun setSmoothnessIntensity(intensity: Float) {
        smoothnessIntensity = intensity
        activeSkinEffects.forEach { it.setSmoothness(intensity) }
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
 */
@OptIn(UnstableApi::class)
internal class StudioEffectSurfaceProcessor(
    private val context: Context,
    private val skinSmoothEffect: SkinSmoothGlEffect,
    private val lutEffect: LutGlEffect,
    private val executor: Executor
) : SurfaceProcessor {

    private var videoFrameProcessor: VideoFrameProcessor? = null
    private var pendingSurfaceInfo: SurfaceInfo? = null

    override fun onInputSurface(request: SurfaceRequest) {
        Log.w("CameraEffectFix", "onInputSurface called for resolution: ${request.resolution}")
        executor.execute {
            try {
                // CameraX may call onInputSurface multiple times on the same processor across
                // the pipeline's lifetime (PreviewView re-attach, mode switches, recording
                // start/stop). Tear down any leftover processor from the previous cycle so the
                // surface request can always be satisfied.
                videoFrameProcessor?.release()
                videoFrameProcessor = null
                pendingSurfaceInfo = null

                val effects: List<Effect> = listOf(skinSmoothEffect, lutEffect)
                val colorInfo = ColorInfo.SDR_BT709_LIMITED
                
                val factory = DefaultVideoFrameProcessor.Factory.Builder().build()
                val processor = factory.create(
                    context,
                    DebugViewProvider.NONE,
                    colorInfo,
                    true,
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
                
                this.videoFrameProcessor = processor
                pendingSurfaceInfo?.let {
                    processor.setOutputSurfaceInfo(it)
                    pendingSurfaceInfo = null
                }
                
                // Register input stream with valid Media3 Format
                val resolution = request.resolution
                val format = Format.Builder()
                    .setWidth(resolution.width)
                    .setHeight(resolution.height)
                    .setColorInfo(colorInfo)
                    .build()

                processor.setOnInputSurfaceReadyListener {
                    val inputSurface = processor.inputSurface
                    request.provideSurface(inputSurface, executor) { result ->
                        Log.d("StudioEffectProcessor", "Input surface released: ${result.resultCode}")
                        release()
                    }
                }

                processor.registerInputStream(
                    VideoFrameProcessor.INPUT_TYPE_SURFACE_AUTOMATIC_FRAME_REGISTRATION,
                    format,
                    effects,
                    0L
                )
            } catch (e: Exception) {
                Log.e("StudioEffectProcessor", "Failed to initialize Media3 VideoFrameProcessor", e)
                request.willNotProvideSurface()
            }
        }
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        Log.w("CameraEffectFix", "onOutputSurface called for size: ${surfaceOutput.size}")
        executor.execute {
            val processor = videoFrameProcessor
            val resolution = surfaceOutput.size

            val surface = surfaceOutput.getSurface(executor) { event ->
                Log.d("StudioEffectProcessor", "Output surface close event observed: $event")
                videoFrameProcessor?.setOutputSurfaceInfo(null)
            }
            
            val identityMatrix = FloatArray(16).apply { android.opengl.Matrix.setIdentityM(this, 0) }
            surfaceOutput.updateTransformMatrix(identityMatrix, identityMatrix)

            val surfaceInfo = SurfaceInfo(
                surface,
                resolution.width,
                resolution.height,
                0 // orientationDegrees
            )

            if (processor != null) {
                processor.setOutputSurfaceInfo(surfaceInfo)
            } else {
                pendingSurfaceInfo = surfaceInfo
            }
        }
    }

    fun release() {
        executor.execute {
            try {
                videoFrameProcessor?.release()
            } catch (e: Exception) {
                Log.w("StudioEffectProcessor", "Error releasing VideoFrameProcessor: ${e.message}")
            } finally {
                videoFrameProcessor = null
                pendingSurfaceInfo = null
            }
        }
    }
}
