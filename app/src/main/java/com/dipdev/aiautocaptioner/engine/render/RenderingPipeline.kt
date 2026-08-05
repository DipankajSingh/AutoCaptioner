package com.dipdev.aiautocaptioner.engine.render

import android.graphics.Canvas
import androidx.core.graphics.withClip
import com.dipdev.aiautocaptioner.engine.render.pass.BackgroundPass
import com.dipdev.aiautocaptioner.engine.render.pass.OutlinePass
import com.dipdev.aiautocaptioner.engine.render.pass.TextFillPass

/**
 * Composable rendering pipeline that executes [RenderPass]es in order.
 *
 * Default pipeline:
 *   1. BackgroundPass  (z=0)  — boxes, pills, full-line backgrounds
 *   2. OutlinePass     (z=10) — text outlines, shadows, glow
 *   3. TextFillPass    (z=20) — text fill, gradients, karaoke highlights
 *
 * To add a new visual effect:
 *   1. Implement RenderPass
 *   2. Add it to the pipeline (either here or via CaptionEngine configuration)
 *
 * No existing passes are modified.
 */
class RenderingPipeline(
    passes: List<RenderPass> = defaultPasses()
) {
    private val sortedPasses = passes.sortedBy { it.zIndex }

    /**
     * Execute all passes in zIndex order.
     * Called once per frame by CaptionEngine.
     */
    fun renderFrame(canvas: Canvas, frame: FrameData) {
        canvas.withClip(0f, 0f, frame.videoWidth.toFloat(), frame.videoHeight.toFloat()) {
            for (pass in sortedPasses) {
                pass.render(canvas, frame)
            }
        }
    }

    companion object {
        fun defaultPasses(): List<RenderPass> = listOf(
            BackgroundPass(),
            OutlinePass(),
            TextFillPass()
        )
    }
}
