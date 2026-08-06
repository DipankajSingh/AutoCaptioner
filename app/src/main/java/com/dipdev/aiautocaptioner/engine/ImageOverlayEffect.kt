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
    private val videoHeight: Int,
    private val rotationDegrees: Int = 0
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

        val isRotated = rotationDegrees == 90 || rotationDegrees == 270
        var actualX = positionX
        var actualY = positionY

        if (isRotated) {
            when (rotationDegrees) {
                90 -> {
                    actualX = positionY
                    actualY = 1f - positionX
                }
                270 -> {
                    actualX = 1f - positionY
                    actualY = positionX
                }
            }
        }

        val mappedX = actualX * 2f - 1f
        val mappedY = 1f - (actualY * 2f)

        // For aspect ratio scaling, use the DISPLAY width/height which is counter-rotated
        val displayW = if (isRotated) bgHeight else bgWidth
        val displayH = if (isRotated) bgWidth else bgHeight

        val imgAspect = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
        val vidAspect = displayW.toFloat() / displayH.toFloat().coerceAtLeast(1f)
        val fitScale = if (imgAspect > vidAspect) {
            displayW.toFloat() / bitmap.width.toFloat().coerceAtLeast(1f)
        } else {
            displayH.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
        }

        return StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(mappedX, mappedY)
            .setScale(scaleX * fitScale, scaleY * fitScale)
            .setRotationDegrees(if (isRotated) -rotationDegrees.toFloat() else 0f)
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
