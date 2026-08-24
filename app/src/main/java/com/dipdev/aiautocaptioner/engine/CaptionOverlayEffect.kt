package com.dipdev.aiautocaptioner.engine

import android.content.Context
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import androidx.annotation.OptIn
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
    rotationDegrees: Int = 0
) : CanvasOverlay(/* useInputFrameSize = */ true) {

    private val captionEngine = CaptionEngine()

    private var released = false

    // Media3 1.10.x hands CanvasOverlay an already-rotated, display-oriented
    // canvas (upright for portrait videos), so drawing uses canvas dimensions
    // directly with no manual rotation compensation.
    // Read from the canvas at draw time so the caption baseScale always matches
    // the real frame resolution at this point in the effect chain.
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

        // TODO(diagnostic): remove after rotation verification
        if (com.dipdev.aiautocaptioner.BuildConfig.DEBUG) {
            android.util.Log.w(
                "RotationDebug",
                "caption onDraw canvas=${canvas.width}x${canvas.height} t=${currentPositionMs}ms"
            )
        }

        captionEngine.draw(
            context = context,
            canvas = canvas,
            currentPositionMs = currentPositionMs,
            videoWidth = canvas.width,
            videoHeight = canvas.height,
            style = style,
            segments = segments,
            wordsMap = wordsMap
        )
    }

    override fun release() {
        released = true
        super.release()
    }
}
