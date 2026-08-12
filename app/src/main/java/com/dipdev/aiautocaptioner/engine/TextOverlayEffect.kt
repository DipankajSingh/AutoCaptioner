package com.dipdev.aiautocaptioner.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import com.dipdev.aiautocaptioner.data.db.entity.TextOverlayEntity

private const val TEXT_BASE_WIDTH = 1080f
private const val QUALITY_SCALE = 2f

@UnstableApi
class TextOverlayEffect(
    private val context: Context,
    private val overlay: TextOverlayEntity,
    private val videoWidth: Int,
    private val videoHeight: Int,
    private val rotationDegrees: Int = 0
) : BitmapOverlay() {

    private var released = false
    private var bgWidth = videoWidth
    private var bgHeight = videoHeight

    private val cachedBitmap: Bitmap by lazy {
        createTextBitmap()
    }

    private fun createTextBitmap(): Bitmap {
        val fontPx = overlay.fontSize * videoWidth / TEXT_BASE_WIDTH
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = overlay.textColorArgb
            textSize = fontPx * QUALITY_SCALE
            try {
                if (overlay.fontAssetPath.isNotEmpty()) {
                    typeface = Typeface.createFromAsset(context.assets, overlay.fontAssetPath)
                } else {
                    typeface = Typeface.DEFAULT
                }
            } catch (e: Exception) {
                typeface = Typeface.DEFAULT
            }
        }

        // Keep the same box model as TextOverlayContent: a fixed total text
        // box width, with font-relative padding on every side.
        val horizontalPadding = fontPx * 0.75f * QUALITY_SCALE
        val verticalPadding = fontPx * 0.5f * QUALITY_SCALE
        val boxWidth = if (overlay.textWidth != null) {
            ((overlay.textWidth * videoWidth) * QUALITY_SCALE).toInt().coerceAtLeast(10)
        } else {
            (StaticLayout.getDesiredWidth(overlay.text, textPaint) + horizontalPadding * 2f)
                .toInt().coerceAtLeast(10)
        }
        val textWidth = (boxWidth - horizontalPadding * 2f).toInt().coerceAtLeast(1)

        val alignment = when (overlay.textAlignment.uppercase()) {
            "START", "LEFT" -> Layout.Alignment.ALIGN_NORMAL
            "END", "RIGHT" -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_CENTER
        }

        val staticLayout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(overlay.text, 0, overlay.text.length, textPaint, textWidth)
                .setAlignment(alignment)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                overlay.text, textPaint, textWidth,
                alignment, 1.0f, 0.0f, false
            )
        }

        val bitmapWidth = boxWidth
        val bitmapHeight = (staticLayout.height + verticalPadding * 2f).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (overlay.backgroundStyle != "NONE" && overlay.backgroundOpacity > 0f) {
            val bgPaint = Paint().apply {
                color = overlay.backgroundColorArgb
                alpha = (overlay.backgroundOpacity.coerceIn(0f, 1f) * 255).toInt().coerceIn(0, 255)
                style = Paint.Style.FILL
            }
            val cornerRadius = fontPx * 0.5f * QUALITY_SCALE
            canvas.drawRoundRect(0f, 0f, bitmapWidth.toFloat(), bitmapHeight.toFloat(), cornerRadius, cornerRadius, bgPaint)
        }

        canvas.save()
        canvas.translate(horizontalPadding, verticalPadding)
        staticLayout.draw(canvas)
        canvas.restore()
        return bitmap
    }

    override fun configure(size: androidx.media3.common.util.Size) {
        super.configure(size)
        bgWidth = size.width
        bgHeight = size.height
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        return cachedBitmap
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val timeMs = presentationTimeUs / 1000
        val isVisible = timeMs in overlay.startTimeMs..overlay.endTimeMs

        val isRotated = rotationDegrees == 90 || rotationDegrees == 270
        var actualX = overlay.positionX
        var actualY = overlay.positionY
        var overlayRotation = overlay.rotation

        if (isRotated) {
            when (rotationDegrees) {
                90 -> {
                    actualX = overlay.positionY
                    actualY = 1f - overlay.positionX
                    overlayRotation += 90f
                }
                270 -> {
                    actualX = 1f - overlay.positionY
                    actualY = overlay.positionX
                    overlayRotation -= 90f
                }
            }
        }

        val mappedX = actualX * 2f - 1f
        val mappedY = 1f - (actualY * 2f)

        val finalScale = 1f / QUALITY_SCALE

        return StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(mappedX, mappedY)
            .setScale(overlay.scaleX * finalScale, overlay.scaleY * finalScale)
            .setRotationDegrees(overlayRotation)
            .setAlphaScale(if (isVisible) 1f else 0f)
            .build()
    }

    override fun release() {
        if (!released) {
            released = true
            // cachedBitmap.recycle() // TextureOverlay handles bitmap lifecycle often, wait no BitmapOverlay recycles it? Actually, we can recycle it if we want, but let's let GC or Media3 handle it.
            super.release()
        }
    }
}
