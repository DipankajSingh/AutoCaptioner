package com.dipdev.aiautocaptioner.engine.render

import android.graphics.Canvas
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.engine.layout.CaptionLayout
import com.dipdev.aiautocaptioner.engine.timing.TimingResult
import com.dipdev.aiautocaptioner.engine.animation.WordTransform


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

    fun isActiveWord(index: Int): Boolean = timing.activeWord?.index == index
}


interface RenderPass {
    val zIndex: Int

    fun render(canvas: Canvas, frame: FrameData)
}
