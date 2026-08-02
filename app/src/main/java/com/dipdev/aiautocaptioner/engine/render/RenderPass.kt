package com.dipdev.aiautocaptioner.engine.render

import android.graphics.Canvas
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.engine.layout.CaptionLayout
import com.dipdev.aiautocaptioner.engine.layout.WordLayout
import com.dipdev.aiautocaptioner.engine.timing.TimingResult
import com.dipdev.aiautocaptioner.engine.animation.WordTransform

/**
 * Context passed to each [RenderPass], containing all data needed for a single frame.
 */
data class FrameData(
    val timing: TimingResult,
    val layout: CaptionLayout,
    val transforms: Map<Int, WordTransform>,
    val style: CaptionStyleEntity,
    val videoWidth: Int,
    val videoHeight: Int,
    val baseScale: Float,
    val currentPositionMs: Long,
    val isRtl: Boolean,
    val pageAlpha: Float = 1f
) {
    /**
     * Live check for the currently-spoken word, resolved against the current
     * frame's timing. Layout words carry a frozen WordState snapshot from the
     * frame the layout was built in — never read lifecycle off that snapshot.
     */
    fun isActiveWord(index: Int): Boolean = timing.activeWord?.index == index
}

/**
 * Interface for a composable rendering pass.
 *
 * Each pass draws one layer of the caption:
 *  - BackgroundPass: boxes, pills, full-line backgrounds
 *  - OutlinePass: text outlines + drop shadows
 *  - TextFillPass: text fill color (with optional gradient)
 *  - GlowPass: neon glow effect
 *  - KaraokeFillPass: left-to-right fill sweep
 *  - HighlightPass: per-word color change / underline / background highlight
 *
 * Passes are ordered by [zIndex] — lower values draw first (behind).
 * To add a new visual effect: implement this interface and add to the pipeline.
 */
interface RenderPass {
    val zIndex: Int

    fun render(canvas: Canvas, frame: FrameData)
}
