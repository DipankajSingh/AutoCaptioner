package com.dipdev.aiautocaptioner.engine

import android.graphics.Canvas
import android.graphics.RectF
import com.dipdev.aiautocaptioner.data.db.entity.*
import com.dipdev.aiautocaptioner.engine.CaptionAnimator.TimedWord
import com.dipdev.aiautocaptioner.engine.CaptionAnimator.WordTransform
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation

// ─────────────────────────────────────────────────────────────────────────────
// CaptionRenderer
//
// Thin Canvas draw orchestrator. All animation math lives in [CaptionAnimator];
// all Paint objects live in [CaptionPaints]. This file only does layout and
// drawing calls — nothing else.
// ─────────────────────────────────────────────────────────────────────────────
object CaptionRenderer {

    private val tempWordRect = RectF()
    private val tempLineRect = RectF()

    fun draw(
        canvas: Canvas,
        currentPositionMs: Long,
        videoWidth: Int,
        videoHeight: Int,
        style: CaptionStyleEntity,
        segments: List<CaptionSegmentEntity>,
        wordsMap: Map<String, List<CaptionWordEntity>>
    ) {
        val seg = segments.find { currentPositionMs in it.startTimeMs..it.endTimeMs } ?: return
        val rawWords = wordsMap[seg.id]
        val animMs = style.animationDurationMs.toLong().coerceAtLeast(50L)
        val baseScale = videoHeight / 1920f

        // ── Configure shared paints ───────────────────────────────────────────
        CaptionPaints.configure(style, baseScale)

        // ── Build TimedWord list ──────────────────────────────────────────────
        val allWords: List<TimedWord> = CaptionUtils.buildTimedWords(seg, rawWords, currentPositionMs)
        if (allWords.isEmpty()) return

        // ── Metrics & constants ───────────────────────────────────────────────
        val fm      = CaptionPaints.text.fontMetrics
        val lineH   = fm.bottom - fm.top
        val padX    = style.backgroundPaddingH * baseScale
        val padY    = style.backgroundPaddingV * baseScale
        val corner  = style.backgroundCornerRadius * baseScale
        val spaceW  = CaptionPaints.text.measureText(" ")

        val visible = CaptionAnimator.filterVisible(allWords, style.displayMode, currentPositionMs, animMs)
        val maxW    = if (style.maxWordsPerLine <= 0) 999 else style.maxWordsPerLine
        val maxL    = if (style.maxLines <= 0) 999 else style.maxLines
        val lines   = visible.chunked(maxW).take(maxL)

        canvas.save()
        canvas.clipRect(0f, 0f, videoWidth.toFloat(), videoHeight.toFloat())
        val totalH = lines.size * lineH + padY * 2f
        val startY = (videoHeight * style.positionY) - totalH / 2f

        val lineLayouts: List<Triple<List<TimedWord>, Float, Float>> =
            lines.map { words ->
                val lineW = words.sumOf { w ->
                    (CaptionPaints.text.measureText(CaptionUtils.sanitize(w.text, style)) + spaceW).toDouble()
                }.toFloat() - spaceW
                val x = when (style.alignment) {
                    TextAlignment.CENTER -> (videoWidth - lineW) / 2f
                    TextAlignment.START  -> videoWidth * 0.08f
                    TextAlignment.END    -> videoWidth * 0.92f - lineW
                }
                Triple(words, x, lineW)
            }


        // Pass 1: Background pill, Outlines, Shadows
        var lineYP1 = startY
        for ((lineWords, lineStartX, lineWidth) in lineLayouts) {
            var x = lineStartX
            val lineTop = lineYP1 + fm.top
            val lineBot = lineYP1 + fm.bottom
            drawLineBackground(canvas, style, x, lineWidth, lineTop, lineBot, padX, padY, corner, videoWidth.toFloat())

            for (w in lineWords) {
                val txt = CaptionUtils.sanitize(w.text, style)
                val wordW = CaptionPaints.text.measureText(txt)
                val xfm = CaptionAnimator.computeWordTransform(currentPositionMs, w, style, animMs)

                // Draw per-word background pill
                if (style.backgroundOpacity > 0f && style.backgroundType == BackgroundType.PER_WORD) {
                    tempWordRect.set(x - padX / 2f, lineTop - padY, x + wordW + padX / 2f, lineBot + padY)
                    canvas.withTranslation(xfm.translateX, xfm.translateY) {
                        CaptionPaints.bg.alpha = (CaptionPaints.bg.alpha * xfm.alpha).toInt()
                        drawRoundRect(tempWordRect, corner / 2f, corner / 2f, CaptionPaints.bg)
                        CaptionPaints.bg.alpha = (style.backgroundOpacity * 255).toInt()
                    }
                }

                drawWord(canvas, txt, x, lineYP1, lineTop, lineBot, wordW, xfm, style, currentPositionMs, w, baseScale, padX, padY, corner, isBgPass = true)
                x += wordW + spaceW
            }
            lineYP1 += lineH
        }

        // Disable shadows for text fills so they sit cleanly on top
        CaptionPaints.text.clearShadowLayer()
        CaptionPaints.outline.clearShadowLayer()

        // Pass 2: Text Fills and Overlays
        var lineYP2 = startY
        for ((lineWords, lineStartX, _) in lineLayouts) {
            var x = lineStartX
            val lineTop = lineYP2 + fm.top
            val lineBot = lineYP2 + fm.bottom
            for (w in lineWords) {
                val txt = CaptionUtils.sanitize(w.text, style)
                val wordW = CaptionPaints.text.measureText(txt)
                val xfm = CaptionAnimator.computeWordTransform(currentPositionMs, w, style, animMs)
                drawWord(canvas, txt, x, lineYP2, lineTop, lineBot, wordW, xfm, style, currentPositionMs, w, baseScale, padX, padY, corner, isBgPass = false)
                x += wordW + spaceW
            }
            lineYP2 += lineH
        }
        canvas.restore()
    }

    // ── Word draw ─────────────────────────────────────────────────────────────

    private fun drawWord(
        canvas: Canvas,
        txt: String,
        x: Float, y: Float, lineTop: Float, lineBot: Float,
        wordW: Float,
        xfm: WordTransform,
        style: CaptionStyleEntity,
        posMs: Long,
        w: TimedWord,
        baseScale: Float,
        padX: Float, padY: Float, corner: Float,
        isBgPass: Boolean
    ) {
        val cx = x + wordW / 2f
        val cy = y + (lineBot - lineTop) / 2f + lineTop

        // Determine fill color
        val fillColor: Int = when {
            xfm.colorOverride != null -> xfm.colorOverride
            w.isEmphasized            -> style.highlightColor.toInt()
            w.isActive && style.displayMode != DisplayMode.PHRASE -> style.highlightColor.toInt()
            else -> style.textColor.toInt()
        }

        // TYPEWRITER clip or standard draw
        val drawTxt = if (style.wordEnterAnimation == AnimationType.TYPEWRITER ||
                          style.displayMode == DisplayMode.TYPEWRITER) {
            val chars = kotlin.math.ceil(txt.length * xfm.clipFraction).toInt().coerceIn(0, txt.length)
            txt.take(chars)
        } else txt

        if (drawTxt.isEmpty() && xfm.alpha < 0.01f) return

        canvas.withTranslation(cx, cy) {
            // Pivot transforms on word center
            scale(xfm.scaleX, xfm.scaleY)
            translate(-cx, -cy)
            translate(xfm.translateX, xfm.translateY)

            val a = (255 * xfm.alpha).toInt()

            if (isBgPass) {
                if (style.outlineWidth > 0f) {
                    CaptionPaints.outline.alpha = a
                    drawText(drawTxt, x, y, CaptionPaints.outline)
                } else if (style.shadowRadius > 0f) {
                    CaptionPaints.text.color = fillColor
                    CaptionPaints.text.alpha = a
                    drawText(drawTxt, x, y, CaptionPaints.text)
                }
            } else {
                CaptionPaints.text.color = fillColor
                CaptionPaints.text.alpha = a
                drawText(drawTxt, x, y, CaptionPaints.text)
                CaptionPaints.text.alpha = 255
                CaptionPaints.outline.alpha = 255
            }

        }

        // ── Karaoke / highlight overlays (drawn without transform to stay aligned) ──
        if (!isBgPass && w.isActive) {
            when (style.karaokeHighlightMode) {
                KaraokeHighlightMode.FILL_LEFT_RIGHT,
                KaraokeHighlightMode.COLOR_CHANGE -> {
                    if (style.displayMode == DisplayMode.KARAOKE_FILL ||
                        style.karaokeHighlightMode == KaraokeHighlightMode.FILL_LEFT_RIGHT) {
                        val dur    = (w.endTimeMs - w.startTimeMs).coerceAtLeast(1L)
                        val fillP  = ((posMs - w.startTimeMs).toFloat() / dur).coerceIn(0f, 1f)
                        canvas.withClip(x, lineTop, x + wordW * fillP, lineBot) {
                            CaptionPaints.highlight.alpha = (255 * xfm.alpha).toInt()
                            drawText(txt, x, y, CaptionPaints.highlight)
                            CaptionPaints.highlight.alpha = 255
                        }
                    }
                }
                KaraokeHighlightMode.UNDERLINE -> {
                    val saved = CaptionPaints.bg.color
                    CaptionPaints.bg.color = style.highlightColor.toInt()
                    CaptionPaints.bg.alpha = (200 * xfm.alpha).toInt()
                    canvas.drawRect(x, lineBot + 2f * baseScale, x + wordW, lineBot + 5f * baseScale, CaptionPaints.bg)
                    CaptionPaints.bg.color = saved
                    CaptionPaints.bg.alpha = (style.backgroundOpacity * 255).toInt()
                }
                KaraokeHighlightMode.BACKGROUND_HIGHLIGHT -> {
                    tempWordRect.set(x - padX / 2f, lineTop - padY / 2f, x + wordW + padX / 2f, lineBot + padY / 2f)
                    val saved = CaptionPaints.bg.color
                    CaptionPaints.bg.color = style.highlightColor.toInt()
                    CaptionPaints.bg.alpha = 80
                    canvas.drawRoundRect(tempWordRect, corner / 2f, corner / 2f, CaptionPaints.bg)
                    CaptionPaints.bg.color = saved
                    CaptionPaints.bg.alpha = (style.backgroundOpacity * 255).toInt()
                }
                KaraokeHighlightMode.SCALE_UP -> { /* handled in WordTransform via CaptionAnimator */ }
            }
        }
    }

    // ── Line background ───────────────────────────────────────────────────────

    private fun drawLineBackground(
        canvas: Canvas, style: CaptionStyleEntity,
        x: Float, lineW: Float, lineTop: Float, lineBot: Float,
        padX: Float, padY: Float, corner: Float, vw: Float
    ) {
        if (style.backgroundOpacity <= 0f) return
        tempLineRect.set(x - padX, lineTop - padY, x + lineW + padX, lineBot + padY)
        when (style.backgroundType) {
            BackgroundType.BOX       -> canvas.drawRoundRect(tempLineRect, corner, corner, CaptionPaints.bg)
            BackgroundType.PILL      -> canvas.drawRoundRect(tempLineRect, tempLineRect.height() / 2f, tempLineRect.height() / 2f, CaptionPaints.bg)
            BackgroundType.FULL_LINE -> canvas.drawRect(0f, tempLineRect.top, vw, tempLineRect.bottom, CaptionPaints.bg)
            BackgroundType.PER_WORD,
            BackgroundType.NONE      -> {}
        }
    }

}

