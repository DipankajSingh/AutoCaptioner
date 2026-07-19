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

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        return bitmap
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val timeMs = presentationTimeUs / 1000
        val isVisible = timeMs in startTimeMs..endTimeMs

        val mappedX = positionX * 2f - 1f
        val mappedY = 1f - (positionY * 2f)

        val imgAspect = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
        val vidAspect = videoWidth.toFloat() / videoHeight.toFloat().coerceAtLeast(1f)
        val (cx, cy) = if (imgAspect > vidAspect) {
            1f to (vidAspect / imgAspect)
        } else {
            (imgAspect / vidAspect) to 1f
        }

        return StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(mappedX, mappedY)
            .setScale(scaleX * cx, scaleY * cy)
            .setAlphaScale(if (isVisible) 1f else 0f)
            .build()
    }
}
