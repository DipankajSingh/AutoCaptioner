package com.dipdev.aiautocaptioner.engine

import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionWordEntity

@UnstableApi
class CaptionOverlayEffect @OptIn(UnstableApi::class) constructor
    (
    private val context: Context,
    private val segments: List<CaptionSegmentEntity>,
    private val wordsMap: Map<String, List<CaptionWordEntity>>,
    private val style: CaptionStyleEntity,
    videoWidth: Int,
    videoHeight: Int,
    private val rotationDegrees: Int = 0
) : CanvasOverlay(/* useInputFrameSize = */ true) {

    private val captionEngine = CaptionEngine()

    private var released = false

    // Canvas (with useInputFrameSize = true) is in the raw input frame space.
    // For rotated videos we must draw in the un-rotated input space, so the
    // display (post-rotation) dimensions are the input dims with w/h swapped.
    private val isRotated = rotationDegrees == 90 || rotationDegrees == 270
    private val displayWidth = if (isRotated) videoHeight else videoWidth
    private val displayHeight = if (isRotated) videoWidth else videoHeight

    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        if (released) return

        // Media3 reuses the overlay texture across frames — clear it each frame
        // or captions from the previous frame persist (ghosting).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            canvas.drawColor(Color.TRANSPARENT, BlendMode.CLEAR)
        } else {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        }

        val currentPositionMs = presentationTimeUs / 1000

        if (isRotated) {
            // Map display-space drawing onto the input-space canvas so that after
            // Transformer rotates the whole frame, captions land at the intended
            // position. Rotation 90: (dx,dy) -> (dy, displayW - dx).
            // Rotation 270: (dx,dy) -> (displayH - dy, dx).
            canvas.save()
            when (rotationDegrees) {
                90 -> {
                    canvas.rotate(-90f)
                    canvas.translate(0f, displayWidth.toFloat())
                }
                270 -> {
                    canvas.rotate(90f)
                    canvas.translate(displayHeight.toFloat(), 0f)
                }
            }
            captionEngine.draw(
                context = context,
                canvas = canvas,
                currentPositionMs = currentPositionMs,
                videoWidth = displayWidth,
                videoHeight = displayHeight,
                style = style,
                segments = segments,
                wordsMap = wordsMap
            )
            canvas.restore()
        } else {
            captionEngine.draw(
                context = context,
                canvas = canvas,
                currentPositionMs = currentPositionMs,
                videoWidth = displayWidth,
                videoHeight = displayHeight,
                style = style,
                segments = segments,
                wordsMap = wordsMap
            )
        }
    }

    override fun release() {
        released = true
        super.release()
    }
}
