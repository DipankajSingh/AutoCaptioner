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
    private val endTimeMs: Long
) : BitmapOverlay() {

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        return bitmap
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val timeMs = presentationTimeUs / 1000
        val isVisible = timeMs in startTimeMs..endTimeMs

        // Map [0.0, 1.0] to [-1.0, 1.0] for X, and [1.0, -1.0] for Y (since Media3 Y is bottom-up)
        val mappedX = positionX * 2f - 1f
        val mappedY = 1f - (positionY * 2f)

        return StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(mappedX, mappedY)
            .setScale(scaleX, scaleY)
            .setAlphaScale(if (isVisible) 1f else 0f)
            .build()
    }
}
