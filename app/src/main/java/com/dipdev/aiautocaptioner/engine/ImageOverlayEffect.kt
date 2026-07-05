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

    private val transparentBitmap by lazy {
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val timeMs = presentationTimeUs / 1000
        return if (timeMs in startTimeMs..endTimeMs) {
            bitmap
        } else {
            transparentBitmap
        }
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        // Map [0.0, 1.0] to [-1.0, 1.0]
        val mappedX = positionX * 2f - 1f
        val mappedY = positionY * 2f - 1f

        return StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(mappedX, mappedY)
            .setScale(scaleX, scaleY)
            .build()
    }
}
