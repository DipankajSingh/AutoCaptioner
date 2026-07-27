package com.dipdev.aiautocaptioner.engine.render.pass

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import com.dipdev.aiautocaptioner.data.db.entity.DisplayMode
import com.dipdev.aiautocaptioner.data.db.entity.GradientDirection
import com.dipdev.aiautocaptioner.data.db.entity.KaraokeHighlightMode
import com.dipdev.aiautocaptioner.data.db.entity.AnimationType
import com.dipdev.aiautocaptioner.engine.CaptionPaints
import com.dipdev.aiautocaptioner.engine.render.FrameData
import com.dipdev.aiautocaptioner.engine.render.RenderPass

/**
 * Draws text fills (the main colored text) and handles karaoke highlight overlays.
 *
 * Third pass (zIndex = 20) — text fills go on top of outlines.
 * Also handles: gradient text, karaoke fill sweep, underline highlight,
 * background highlight, and COLOR_CHANGE (via fillColor logic).
 */
class TextFillPass : RenderPass {
    override val zIndex = 20

    private val tempRect = RectF()

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
                val a = (255 * xfm.alpha * frame.pageAlpha).toInt()

                if (a < 3) {
                    if (frame.isRtl) x -= wl.width else x += wl.width + spaceW
                    continue
                }

                if (frame.isRtl) x -= wl.width

                val cx = x + wl.width / 2f
                val cy = lineY + (lineBot - lineTop) / 2f + lineTop

                // Resolve fill color
                val karaokeCol = if (style.displayMode == DisplayMode.KARAOKE_FILL) style.karaokeFillColor.toInt() else style.highlightColor.toInt()
                val fillColor = when {
                    xfm.colorOverride != null -> xfm.colorOverride
                    wl.word.isEmphasized -> style.highlightColor.toInt()
                    // Active word with BACKGROUND_HIGHLIGHT uses activeWordTextColor for high-contrast contrast against pill
                    wl.word.isActive && style.karaokeHighlightMode == KaraokeHighlightMode.BACKGROUND_HIGHLIGHT -> style.activeWordTextColor.toInt()
                    // KARAOKE_FILL + FILL_LEFT_RIGHT: active word uses base color (overlay provides sweep)
                    wl.word.isActive && style.displayMode == DisplayMode.KARAOKE_FILL &&
                        style.karaokeHighlightMode == KaraokeHighlightMode.FILL_LEFT_RIGHT -> style.textColor.toInt()
                    // KARAOKE_FILL: active word uses highlight (COLOR_CHANGE, UNDERLINE, etc.)
                    wl.word.isActive && style.displayMode == DisplayMode.KARAOKE_FILL -> karaokeCol
                    // KARAOKE_FILL: past words stay highlighted
                    style.displayMode == DisplayMode.KARAOKE_FILL &&
                        frame.timing.activeWordIndex >= 0 &&
                        wl.word.index <= frame.timing.activeWordIndex -> karaokeCol
                    // Other modes: active word is highlighted
                    wl.word.isActive && style.displayMode != DisplayMode.PHRASE -> style.highlightColor.toInt()
                    else -> style.textColor.toInt()
                }

                // Characters to draw (TYPEWRITER clips reveal)
                val charsToDraw = if (style.wordEnterAnimation == AnimationType.TYPEWRITER ||
                    style.displayMode == DisplayMode.TYPEWRITER
                ) {
                    kotlin.math.ceil(wl.displayText.length * xfm.clipFraction).toInt().coerceIn(0, wl.displayText.length)
                } else wl.displayText.length

                canvas.withTranslation(cx, cy) {
                    scale(xfm.scaleX, xfm.scaleY)
                    translate(-cx, -cy)
                    translate(xfm.translateX, xfm.translateY)

                    // Draw solid active background pill before drawing text glyphs
                    if (wl.word.isActive && style.karaokeHighlightMode == KaraokeHighlightMode.BACKGROUND_HIGHLIGHT) {
                        val padX = 12f * baseScale
                        val padY = 6f * baseScale
                        tempRect.set(x - padX, lineY + fm.ascent - padY, x + wl.width + padX, lineY + fm.descent + padY)
                        val radius = (style.activeWordCornerRadius * baseScale).coerceIn(4f * baseScale, tempRect.height() / 2f)
                        CaptionPaints.activeBg.alpha = (255 * xfm.alpha * frame.pageAlpha).toInt().coerceIn(0, 255)
                        canvas.drawRoundRect(tempRect, radius, radius, CaptionPaints.activeBg)
                        CaptionPaints.activeBg.alpha = 255
                    }

                    // Gradient text
                    if (style.gradientDirection != GradientDirection.NONE) {
                        val shader = when (style.gradientDirection) {
                            GradientDirection.LEFT_RIGHT -> LinearGradient(
                                x, 0f, x + wl.width, 0f,
                                style.textColor.toInt(), style.secondaryColor.toInt(),
                                Shader.TileMode.CLAMP
                            )
                            GradientDirection.TOP_BOTTOM -> LinearGradient(
                                0f, lineTop, 0f, lineBot,
                                style.textColor.toInt(), style.secondaryColor.toInt(),
                                Shader.TileMode.CLAMP
                            )
                            GradientDirection.DIAGONAL -> LinearGradient(
                                x, lineTop, x + wl.width, lineBot,
                                style.textColor.toInt(), style.secondaryColor.toInt(),
                                Shader.TileMode.CLAMP
                            )
                            GradientDirection.NONE -> null
                        }
                        CaptionPaints.text.shader = shader
                    } else {
                        CaptionPaints.text.shader = null
                    }

                    val fillAlpha = if (style.outlineOnly) 0 else (a * style.textOpacity).toInt()
                    CaptionPaints.text.color = fillColor
                    CaptionPaints.text.alpha = fillAlpha
                    drawText(wl.displayText, 0, charsToDraw, x, lineY, CaptionPaints.text)
                    CaptionPaints.text.shader = null
                    CaptionPaints.text.alpha = 255

                    // Karaoke / highlight overlays — inside transform block for alignment
                    val shouldShowOverlay = wl.word.isActive ||
                        (style.displayMode == DisplayMode.KARAOKE_FILL &&
                            frame.timing.activeWordIndex >= 0 &&
                            wl.word.index <= frame.timing.activeWordIndex)
                    if (shouldShowOverlay) {
                        renderKaraokeHighlight(canvas, wl, x, lineY, lineTop, lineBot, style, frame)
                    }
                }

                if (frame.isRtl) x -= spaceW else x += wl.width + spaceW
            }
            lineY += frame.layout.lineHeight
        }

        CaptionPaints.outline.alpha = 255
    }

    private fun renderKaraokeHighlight(
        canvas: Canvas,
        wl: com.dipdev.aiautocaptioner.engine.layout.WordLayout,
        x: Float, y: Float, lineTop: Float, lineBot: Float,
        style: com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity,
        frame: FrameData
    ) {
        val xfm = frame.transforms[wl.word] ?: return
        val baseScale = frame.baseScale
        val padX = style.backgroundPaddingH * baseScale
        val padY = style.backgroundPaddingV * baseScale
        val corner = style.backgroundCornerRadius * baseScale

        when (style.karaokeHighlightMode) {
            KaraokeHighlightMode.FILL_LEFT_RIGHT -> {
                if (style.displayMode == DisplayMode.KARAOKE_FILL) {
                    val posMs = frame.currentPositionMs
                    val dur = (wl.word.endTimeMs - wl.word.startTimeMs).coerceAtLeast(1L)
                    val fillP = if (wl.word.isActive) {
                        ((posMs - wl.word.startTimeMs).toFloat() / dur).coerceIn(0f, 1f)
                    } else {
                        1f // Past word: fully filled
                    }

                    if (frame.isRtl) {
                        canvas.withClip(x + wl.width * (1f - fillP), lineTop, x + wl.width, lineBot) {
                            CaptionPaints.highlight.alpha = (255 * xfm.alpha * frame.pageAlpha).toInt()
                            drawText(wl.displayText, 0, wl.displayText.length, x, y, CaptionPaints.highlight)
                            CaptionPaints.highlight.alpha = 255
                        }
                    } else {
                        canvas.withClip(x, lineTop, x + wl.width * fillP, lineBot) {
                            CaptionPaints.highlight.alpha = (255 * xfm.alpha * frame.pageAlpha).toInt()
                            drawText(wl.displayText, 0, wl.displayText.length, x, y, CaptionPaints.highlight)
                            CaptionPaints.highlight.alpha = 255
                        }
                    }
                }
            }
            KaraokeHighlightMode.UNDERLINE -> {
                val saved = CaptionPaints.bg.color
                CaptionPaints.bg.color = style.highlightColor.toInt()
                CaptionPaints.bg.alpha = (200 * xfm.alpha * frame.pageAlpha).toInt()
                canvas.drawRect(x, lineBot + 2f * baseScale, x + wl.width, lineBot + 5f * baseScale, CaptionPaints.bg)
                CaptionPaints.bg.color = saved
                CaptionPaints.bg.alpha = (style.backgroundOpacity * 255).toInt()
            }
            KaraokeHighlightMode.BACKGROUND_HIGHLIGHT -> {
                // Handled before text glyph rendering above as solid substrate
            }
            KaraokeHighlightMode.SCALE_UP -> { /* handled in AnimationEngine via scale boost */ }
            KaraokeHighlightMode.COLOR_CHANGE -> { /* handled via fillColor resolution above */ }
        }
    }
}
