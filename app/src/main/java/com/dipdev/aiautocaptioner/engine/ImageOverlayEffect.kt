package com.dipdev.aiautocaptioner.engine

import android.graphics.Bitmap
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.common.OverlaySettings
import androidx.media3.effect.StaticOverlaySettings

@UnstableApi
class ImageOverlayEffect(
    private val bitmap: Bitmap,
    private val positionX: Float,
    private val positionY: Float,
    private val scaleX: Float,
    private val scaleY: Float,
    private val startTimeMs: Long,
    private val endTimeMs: Long,
    private val videoWidth: Int,
    private val videoHeight: Int
) : BitmapOverlay() {

    private var released = false

    // The actual background frame size at this overlay's point in the effect
    // chain (post-Presentation scaling). Falls back to the project dims until
    // configure() is invoked, which media3 does before rendering begins.
    private var bgWidth = videoWidth
    private var bgHeight = videoHeight

    override fun configure(size: androidx.media3.common.util.Size) {
        super.configure(size)
        bgWidth = size.width
        bgHeight = size.height
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        return bitmap
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val timeMs = presentationTimeUs / 1000
        val isVisible = timeMs in startTimeMs..endTimeMs

        val mappedX = positionX * 2f - 1f
        val mappedY = 1f - (positionY * 2f)

        val imgAspect = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
        val vidAspect = bgWidth.toFloat() / bgHeight.toFloat().coerceAtLeast(1f)
        val fitScale = if (imgAspect > vidAspect) {
            bgWidth.toFloat() / bitmap.width.toFloat().coerceAtLeast(1f)
        } else {
            bgHeight.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
        }

        return StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(mappedX, mappedY)
            .setScale(scaleX * fitScale, scaleY * fitScale)
            .setAlphaScale(if (isVisible) 1f else 0f)
            .build()
    }

    override fun release() {
        if (!released) {
            released = true
            super.release()
        }
    }
}
