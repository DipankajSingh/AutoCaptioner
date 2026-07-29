package com.dipdev.aiautocaptioner.engine

import android.content.Context
import android.graphics.Canvas
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
    private val videoWidth: Int,
    private val videoHeight: Int
) : CanvasOverlay(/* useInputFrameSize = */ true) {

    private val captionEngine = CaptionEngine()

    private var released = false

    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        if (released) return

        val currentPositionMs = presentationTimeUs / 1000

        captionEngine.draw(
            context = context,
            canvas = canvas,
            currentPositionMs = currentPositionMs,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
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
