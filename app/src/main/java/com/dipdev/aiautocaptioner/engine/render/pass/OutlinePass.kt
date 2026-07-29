package com.dipdev.aiautocaptioner.engine.render.pass

import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.withTranslation
import com.dipdev.aiautocaptioner.data.db.entity.KaraokeHighlightMode
import com.dipdev.aiautocaptioner.engine.CaptionPaints
import com.dipdev.aiautocaptioner.engine.render.FrameData
import com.dipdev.aiautocaptioner.engine.render.RenderPass
import kotlin.math.roundToInt

/**
 * Draws text outlines and drop shadows.
 *
 * Second pass (zIndex = 10) — outlines go behind text fills but in front of backgrounds.
 * Shadow is set on the outline paint by CaptionPaints.configure().
 */
class OutlinePass : RenderPass {
    override val zIndex = 10

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun render(canvas: Canvas, frame: FrameData) {
        val style = frame.style
        val fm = CaptionPaints.text.fontMetrics
        val baseScale = frame.baseScale

        var lineY = frame.layout.startY

        for (line in frame.layout.lines) {
            val lineTop = lineY + fm.top
            val lineBot = lineY + fm.bottom
            var x = if (frame.isRtl) line.startX + line.lineWidth else line.startX
            val spaceW = CaptionPaints.text.measureText(" ")

            for (wl in line.words) {
                val xfm = frame.transforms[wl.word] ?: continue
                val a = (255 * xfm.alpha * frame.pageAlpha).roundToInt().coerceIn(0, 255)

                if (a < 3) continue

                if (frame.isRtl) x -= wl.width

                if (!(wl.word.isActive && style.karaokeHighlightMode == KaraokeHighlightMode.BACKGROUND_HIGHLIGHT)) {
                    val cx = x + wl.width / 2f
                    val cy = lineY + (lineBot - lineTop) / 2f + lineTop

                    canvas.withTranslation(cx, cy) {
                        scale(xfm.scaleX, xfm.scaleY)
                        translate(-cx, -cy)
                        translate(xfm.translateX, xfm.translateY)

                        // Glow layer
                        if (style.glowEnabled && style.glowRadius > 0f) {
                            glowPaint.textSize = CaptionPaints.text.textSize
                            glowPaint.typeface = CaptionPaints.text.typeface
                            glowPaint.color = style.glowColor.toInt()
                            glowPaint.alpha = (a * style.textOpacity * 0.7f).roundToInt().coerceIn(0, 255)
                            glowPaint.textAlign = Paint.Align.LEFT
                            glowPaint.letterSpacing = style.letterSpacing
                            glowPaint.setShadowLayer(style.glowRadius * baseScale, 0f, 0f, glowPaint.color)
                            drawText(wl.displayText, 0, wl.displayText.length, x, lineY, glowPaint)
                            glowPaint.clearShadowLayer()
                        }

                        // Outline pass (shadow is baked into CaptionPaints.outline)
                        if (style.outlineWidth > 0f) {
                            CaptionPaints.outline.alpha = (a * style.textOpacity).roundToInt().coerceIn(0, 255)
                            drawText(wl.displayText, 0, wl.displayText.length, x, lineY, CaptionPaints.outline)
                        }
                    }
                }

                if (frame.isRtl) x -= spaceW else x += wl.width + spaceW
            }
            lineY += frame.layout.lineHeight
        }

        CaptionPaints.outline.alpha = 255
    }
}
