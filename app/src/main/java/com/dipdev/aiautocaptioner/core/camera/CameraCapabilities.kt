package com.dipdev.aiautocaptioner.core.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap

/** Which physical camera lens to use. Maps 1:1 to CameraCharacteristics.LENS_FACING. */
enum class CameraFacing { BACK, FRONT }

/**
 * Static, queryable facts about a camera lens: which id it is, whether it has a flash unit,
 * and — critically for the quality selector — which [RecordingQuality] tiers it can actually
 * satisfy. This is the single place that guards against offering a quality tier the sensor
 * can't deliver; everything upstream (ViewModel, UI) treats [supportedQualities] as ground
 * truth rather than re-deriving it.
 */
class CameraCapabilities(context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    /** Resolves the camera id for a given facing, or null if the device has no such lens. */
    fun findCameraId(facing: CameraFacing): String? {
        val targetFacing = when (facing) {
            CameraFacing.BACK -> CameraCharacteristics.LENS_FACING_BACK
            CameraFacing.FRONT -> CameraCharacteristics.LENS_FACING_FRONT
        }
        return cameraManager.cameraIdList.firstOrNull { id ->
            characteristics(id).get(CameraCharacteristics.LENS_FACING) == targetFacing
        }
    }

    fun hasFlash(cameraId: String): Boolean =
        characteristics(cameraId).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true

    fun sensorOrientation(cameraId: String): Int =
        characteristics(cameraId).get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90

    fun characteristics(cameraId: String): CameraCharacteristics =
        cameraManager.getCameraCharacteristics(cameraId)

    /**
     * The largest pixel area this lens can stream to a SurfaceTexture (our GL input surface).
     * We deliberately query SurfaceTexture-targeted sizes here — not MediaRecorder- or
     * ImageReader-oriented sizes — because a SurfaceTexture (feeding our own GL pipeline) is
     * the only kind of output surface this feature ever hands to Camera2.
     */
    private fun maxSurfaceTextureArea(cameraId: String): Long {
        val map = characteristics(cameraId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: return 0L
        val sizes = map.getOutputSizes(SurfaceTexture::class.java) ?: return 0L
        return sizes.maxOfOrNull { it.width.toLong() * it.height.toLong() } ?: 0L
    }

    /**
     * Filters [RecordingQuality.ALL] down to the tiers this lens can actually satisfy for the
     * given [aspectRatio]. A tier is supported if the sensor can stream at least as many
     * pixels as the resolved output size requires. Always returns at least one entry (falls
     * back to [RecordingQuality.SD_480]) so the UI never ends up with an empty quality list.
     */
    fun supportedQualities(cameraId: String, aspectRatio: RecordingAspectRatio): List<RecordingQuality> {
        val maxArea = maxSurfaceTextureArea(cameraId)
        val supported = RecordingQuality.ALL.filter { quality ->
            val size = aspectRatio.resolve(quality)
            (size.width.toLong() * size.height.toLong()) <= maxArea
        }
        return supported.ifEmpty { listOf(RecordingQuality.SD_480) }
    }

    fun streamConfigMap(cameraId: String): StreamConfigurationMap? =
        characteristics(cameraId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

    /**
     * Picks a real, sensor-supported [SurfaceTexture] output size for the given [quality] /
     * [aspectRatio] target.
     *
     * IMPORTANT: this is NOT the same thing as [RecordingAspectRatio.resolve] — that method
     * returns the desired *final, portrait, already-cropped* output size (e.g. 720x1280),
     * which is meaningless as a camera stream size. Camera2's [StreamConfigurationMap]
     * always reports SurfaceTexture sizes in the sensor's native landscape-style layout
     * (width >= height) regardless of how the phone is held — asking the sensor to natively
     * stream a portrait-shaped buffer is not a valid request. This function bridges the two:
     * it picks the smallest supported *landscape* size whose pixel area comfortably covers
     * the desired output area (so the later GL crop loses as little resolution as possible
     * without over-fetching), falling back to the largest available size if none is big enough.
     */
    fun bestCameraStreamSize(
        cameraId: String,
        quality: RecordingQuality,
        aspectRatio: RecordingAspectRatio
    ): android.util.Size {
        val supportedSizes = streamConfigMap(cameraId)
            ?.getOutputSizes(SurfaceTexture::class.java)
            ?.toList()
            .orEmpty()

        if (supportedSizes.isEmpty()) {
            // Should not happen on any real device with a working camera, but keep this total.
            return android.util.Size(1280, 720)
        }

        val target = aspectRatio.resolve(quality)
        val desiredArea = target.width.toLong() * target.height.toLong()

        return supportedSizes
            .sortedBy { it.width.toLong() * it.height.toLong() }
            .firstOrNull { it.width.toLong() * it.height.toLong() >= desiredArea }
            ?: supportedSizes.maxByOrNull { it.width.toLong() * it.height.toLong() }!!
    }
}
