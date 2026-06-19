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
//
// RTL support
// ───────────
// When a segment's text is detected as RTL (Arabic, Hebrew, etc.) the renderer:
//   1. Flips the alignment defaults — START aligns to the right edge, END to
//      the left edge (matching natural reading-start for RTL scripts).
//   2. Advances x right-to-left within each line, so the first-spoken word
//      lands at the rightmost position (correct for RTL reading order).
//   3. Reverses the karaoke fill-clip direction, so it progresses right→left.
//
// Individual glyphs are shaped and rendered correctly by Android's HarfBuzz
// text stack regardless of our positioning, so no additional work is needed
// at the character level.
// ─────────────────────────────────────────────────────────────────────────────
object CaptionRenderer {

    private val tempWordRect = RectF()
    private val tempLineRect = RectF()

    private var cachedSegmentId: String? = null
    private var cachedStyleId: String? = null
    private var cachedWords: List<TimedWord> = emptyList()
    private var cachedVisibleWords: List<TimedWord> = emptyList()
    private var cachedLayouts: List<Triple<List<TimedWord>, Float, Float>> = emptyList()
    /** Whether the current segment's text reads right-to-left. */
    private var cachedIsRtl: Boolean = false

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
        if (cachedSegmentId != seg.id || cachedStyleId != style.id) {
            cachedSegmentId = seg.id
            cachedStyleId = style.id
            cachedWords = CaptionUtils.buildTimedWords(seg, rawWords)
            cachedIsRtl = CaptionUtils.isRtl(seg.text)
            cachedVisibleWords = emptyList() // force rebuild
        }

        if (cachedWords.isEmpty()) return

        for (w in cachedWords) {
            w.isActive = currentPositionMs in w.startTimeMs..w.endTimeMs
            w.isPast = currentPositionMs > w.endTimeMs
        }

        // ── Metrics & constants ───────────────────────────────────────────────
        val fm      = CaptionPaints.text.fontMetrics
        val lineH   = fm.bottom - fm.top
        val padX    = style.backgroundPaddingH * baseScale
        val padY    = style.backgroundPaddingV * baseScale
        val corner  = style.backgroundCornerRadius * baseScale
        // For RTL, the inter-word space is measured the same way; spaces in
        // Arabic/Hebrew still need a visual gap between word tokens.
        val spaceW  = CaptionPaints.text.measureText(" ")
        val isRtl   = cachedIsRtl

        val visible = CaptionAnimator.filterVisible(cachedWords, style.displayMode, currentPositionMs, animMs)

        if (visible != cachedVisibleWords) {
            cachedVisibleWords = visible
            val maxW    = if (style.maxWordsPerLine <= 0) 999 else style.maxWordsPerLine
            val maxL    = if (style.maxLines <= 0) 999 else style.maxLines
            val lines   = visible.chunked(maxW).take(maxL)

            cachedLayouts = lines.map { words ->
                val lineW = words.sumOf { w ->
                    (CaptionPaints.text.measureText(CaptionUtils.sanitize(w.text, style)) + spaceW).toDouble()
                }.toFloat() - spaceW

                // For RTL scripts, START (reading-start) is the right edge and
                // END (reading-end) is the left edge — the opposite of LTR.
                val x = when (style.alignment) {
                    TextAlignment.CENTER -> (videoWidth - lineW) / 2f
                    TextAlignment.START  -> if (isRtl) videoWidth * 0.92f - lineW else videoWidth * 0.08f
                    TextAlignment.END    -> if (isRtl) videoWidth * 0.08f           else videoWidth * 0.92f - lineW
                }
                Triple(words, x, lineW)
            }
        }

        val lineLayouts = cachedLayouts

        canvas.save()
        canvas.clipRect(0f, 0f, videoWidth.toFloat(), videoHeight.toFloat())
        val totalH = lineLayouts.size * lineH + padY * 2f
        val startY = (videoHeight * style.positionY) - totalH / 2f


        // Pass 1: Background pills, Outlines, Shadows
        var lineYP1 = startY
        for ((lineWords, lineStartX, lineWidth) in lineLayouts) {
            val lineTop = lineYP1 + fm.top
            val lineBot = lineYP1 + fm.bottom
            drawLineBackground(canvas, style, lineStartX, lineWidth, lineTop, lineBot, padX, padY, corner, videoWidth.toFloat())

            // RTL: start x at the right edge and advance left.
            // LTR: start x at the left edge and advance right.
            var x = if (isRtl) lineStartX + lineWidth else lineStartX

            for (w in lineWords) {
                val txt   = CaptionUtils.sanitize(w.text, style)
                val wordW = CaptionPaints.text.measureText(txt)
                val xfm   = CaptionAnimator.computeWordTransform(currentPositionMs, w, style, animMs)

                // For RTL, move x left by wordW before drawing so that x is
                // always the LEFT edge of the current word (Paint.Align.LEFT).
                if (isRtl) x -= wordW

                // Draw per-word background pill
                if (style.backgroundOpacity > 0f && style.backgroundType == BackgroundType.PER_WORD) {
                    tempWordRect.set(x - padX / 2f, lineTop - padY, x + wordW + padX / 2f, lineBot + padY)
                    canvas.withTranslation(xfm.translateX, xfm.translateY) {
                        CaptionPaints.bg.alpha = (CaptionPaints.bg.alpha * xfm.alpha).toInt()
                        drawRoundRect(tempWordRect, corner / 2f, corner / 2f, CaptionPaints.bg)
                        CaptionPaints.bg.alpha = (style.backgroundOpacity * 255).toInt()
                    }
                }

                drawWord(canvas, txt, x, lineYP1, lineTop, lineBot, wordW, xfm, style, currentPositionMs, w, baseScale, padX, padY, corner, isBgPass = true, isRtl = isRtl)

                // Advance: LTR adds word+space; RTL already moved left by wordW,
                // so only subtract the inter-word space.
                if (isRtl) x -= spaceW else x += wordW + spaceW
            }
            lineYP1 += lineH
        }

        // Disable shadows for text fills so they sit cleanly on top
        CaptionPaints.text.clearShadowLayer()
        CaptionPaints.outline.clearShadowLayer()

        // Pass 2: Text Fills and Overlays
        var lineYP2 = startY
        for ((lineWords, lineStartX, lineWidth) in lineLayouts) {
            val lineTop = lineYP2 + fm.top
            val lineBot = lineYP2 + fm.bottom
            var x = if (isRtl) lineStartX + lineWidth else lineStartX

            for (w in lineWords) {
                val txt   = CaptionUtils.sanitize(w.text, style)
                val wordW = CaptionPaints.text.measureText(txt)
                val xfm   = CaptionAnimator.computeWordTransform(currentPositionMs, w, style, animMs)

                if (isRtl) x -= wordW

                drawWord(canvas, txt, x, lineYP2, lineTop, lineBot, wordW, xfm, style, currentPositionMs, w, baseScale, padX, padY, corner, isBgPass = false, isRtl = isRtl)

                if (isRtl) x -= spaceW else x += wordW + spaceW
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
        isBgPass: Boolean,
        isRtl: Boolean = false
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
                        val dur   = (w.endTimeMs - w.startTimeMs).coerceAtLeast(1L)
                        val fillP = ((posMs - w.startTimeMs).toFloat() / dur).coerceIn(0f, 1f)
                        // RTL: fill progresses right→left (natural reading direction).
                        // LTR: fill progresses left→right.
                        if (isRtl) {
                            canvas.withClip(x + wordW * (1f - fillP), lineTop, x + wordW, lineBot) {
                                CaptionPaints.highlight.alpha = (255 * xfm.alpha).toInt()
                                drawText(txt, x, y, CaptionPaints.highlight)
                                CaptionPaints.highlight.alpha = 255
                            }
                        } else {
                            canvas.withClip(x, lineTop, x + wordW * fillP, lineBot) {
                                CaptionPaints.highlight.alpha = (255 * xfm.alpha).toInt()
                                drawText(txt, x, y, CaptionPaints.highlight)
                                CaptionPaints.highlight.alpha = 255
                            }
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
