package com.dipdev.aiautocaptioner.engine.render.pass

import android.graphics.Canvas
import android.graphics.RectF
import com.dipdev.aiautocaptioner.data.db.entity.BackgroundType
import com.dipdev.aiautocaptioner.engine.CaptionPaints
import com.dipdev.aiautocaptioner.engine.render.FrameData
import com.dipdev.aiautocaptioner.engine.render.RenderPass

/**
 * Draws backgrounds: BOX, PILL, FULL_LINE, PER_WORD.
 *
 * This is the first pass (lowest zIndex) — backgrounds go behind everything.
 */
class BackgroundPass : RenderPass {
    override val zIndex = 0

    private val tempRect = RectF()

    override fun render(canvas: Canvas, frame: FrameData) {
        val style = frame.style
        if (style.backgroundOpacity <= 0f) return
        if (style.backgroundType == BackgroundType.NONE) return

        val baseScale = frame.baseScale
        val padX = style.backgroundPaddingH * baseScale
        val padY = style.backgroundPaddingV * baseScale
        val corner = style.backgroundCornerRadius * baseScale
        val fm = CaptionPaints.text.fontMetrics
        val bgAlpha = (style.backgroundOpacity * 255 * frame.pageAlpha).toInt()

        var lineY = frame.layout.startY

        for (line in frame.layout.lines) {
            val lineTop = lineY + fm.top
            val lineBot = lineY + fm.bottom

            CaptionPaints.bg.alpha = bgAlpha

            when (style.backgroundType) {
                BackgroundType.BOX -> {
                    tempRect.set(line.startX - padX, lineTop - padY, line.startX + line.lineWidth + padX, lineBot + padY)
                    canvas.drawRoundRect(tempRect, corner, corner, CaptionPaints.bg)
                }
                BackgroundType.PILL -> {
                    tempRect.set(line.startX - padX, lineTop - padY, line.startX + line.lineWidth + padX, lineBot + padY)
                    canvas.drawRoundRect(tempRect, tempRect.height() / 2f, tempRect.height() / 2f, CaptionPaints.bg)
                }
                BackgroundType.FULL_LINE -> {
                    tempRect.set(line.startX - padX, lineTop - padY, line.startX + line.lineWidth + padX, lineBot + padY)
                    canvas.drawRect(0f, tempRect.top, frame.videoWidth.toFloat(), tempRect.bottom, CaptionPaints.bg)
                }
                BackgroundType.PER_WORD -> {
                    // Per-word backgrounds drawn below in the word loop
                }
                BackgroundType.NONE -> {}
            }

            // Per-word backgrounds
            if (style.backgroundType == BackgroundType.PER_WORD) {
                var x = if (frame.isRtl) line.startX + line.lineWidth else line.startX
                val spaceW = CaptionPaints.text.measureText(" ")

                for (wl in line.words) {
                    val xfm = frame.transforms[wl.word] ?: continue

                    if (frame.isRtl) x -= wl.width

                    tempRect.set(
                        x - padX / 2f, lineTop - padY,
                        x + wl.width + padX / 2f, lineBot + padY
                    )
                    CaptionPaints.bg.alpha = (bgAlpha * xfm.alpha).toInt()
                    canvas.drawRoundRect(tempRect, corner / 2f, corner / 2f, CaptionPaints.bg)
                    CaptionPaints.bg.alpha = bgAlpha

                    if (frame.isRtl) x -= spaceW else x += wl.width + spaceW
                }
            }

            lineY += frame.layout.lineHeight
        }

        CaptionPaints.bg.alpha = (style.backgroundOpacity * 255).toInt()
    }
}
