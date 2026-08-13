package com.dipdev.aiautocaptioner.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.common.OverlaySettings
import androidx.media3.effect.StaticOverlaySettings
import androidx.core.graphics.createBitmap

@UnstableApi
class ImageOverlayEffect(
    private val bitmap: Bitmap,
    private val positionX: Float,
    private val positionY: Float,
    private val scaleX: Float,
    private val scaleY: Float,
    private val startTimeMs: Long,
    private val endTimeMs: Long,
    videoWidth: Int,
    videoHeight: Int,
    private val rotationDegrees: Int = 0
    ,private val opacity: Float = 1f
    ,private val filterName: String? = null
    ,private val isFlippedX: Boolean = false
) : BitmapOverlay() {

    private var released = false

    // The actual background frame size at this overlay's point in the effect
    // chain (post-Presentation scaling). Falls back to the project dims until
    // configure() is invoked, which media3 does before rendering begins.
    private var bgWidth = videoWidth
    private var bgHeight = videoHeight

    /** Compose preview uses these exact color matrices. Apply them once to the
     * export bitmap so preview and rendered video use the same visual setting. */
    private val renderedBitmap: Bitmap by lazy {
        val matrix = when (filterName) {
            "Grayscale" -> ColorMatrix().apply { setSaturation(0f) }
            "Sepia" -> ColorMatrix(floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            "Invert" -> ColorMatrix(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            ))
            else -> null
        } ?: return@lazy bitmap

        createBitmap(bitmap.width, bitmap.height).also { filtered ->
            Canvas(filtered).drawBitmap(bitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(matrix)
            })
        }
    }

    override fun configure(size: androidx.media3.common.util.Size) {
        super.configure(size)
        bgWidth = size.width
        bgHeight = size.height
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        return renderedBitmap
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

        val imgAspect = renderedBitmap.width.toFloat() / renderedBitmap.height.toFloat().coerceAtLeast(1f)
        val vidAspect = displayW.toFloat() / displayH.toFloat().coerceAtLeast(1f)
        val fitScale = if (imgAspect > vidAspect) {
            displayW.toFloat() / renderedBitmap.width.toFloat().coerceAtLeast(1f)
        } else {
            displayH.toFloat() / renderedBitmap.height.toFloat().coerceAtLeast(1f)
        }

        return StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(mappedX, mappedY)
            .setScale((if (isFlippedX) -scaleX else scaleX) * fitScale, scaleY * fitScale)
            .setRotationDegrees(if (isRotated) -rotationDegrees.toFloat() else 0f)
            .setAlphaScale(if (isVisible) opacity.coerceIn(0f, 1f) else 0f)
            .build()
    }

    override fun release() {
        if (!released) {
            released = true
            if (renderedBitmap !== bitmap && !renderedBitmap.isRecycled) renderedBitmap.recycle()
            super.release()
        }
    }
}
