package com.dipdev.aiautocaptioner.engine

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
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
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var cachedSegmentId: String? = null
    private var cachedStyleId: String? = null
    private var cachedLayoutFingerprint: Long = 0L
    private var cachedWords: List<TimedWord> = emptyList()
    private var cachedWindowWords: List<TimedWord> = emptyList()

    // Tracks the current page so we can detect page transitions
    private var cachedPageIndex: Int = -1

    data class WordLayout(val word: TimedWord, val txt: String, val wordW: Float)
    data class LineLayout(val words: List<WordLayout>, val lineStartX: Float, val lineWidth: Float)

    private var cachedLayouts: List<LineLayout> = emptyList()
    /** Whether the current segment's text reads right-to-left. */
    private var cachedIsRtl: Boolean = false

    /**
     * Fingerprint of all style properties that affect text measurement,
     * line layout, OR the display window strategy. When ANY of these change
     * the cached layouts must be rebuilt.
     */
    private fun layoutFingerprint(style: CaptionStyleEntity): Long {
        var h = 17L
        h = 31 * h + java.lang.Float.floatToIntBits(style.fontSize)
        h = 31 * h + style.fontFamily.hashCode()
        h = 31 * h + style.fontWeight
        h = 31 * h + if (style.isItalic) 1 else 0
        h = 31 * h + java.lang.Float.floatToIntBits(style.letterSpacing)
        h = 31 * h + style.maxWordsPerLine
        h = 31 * h + style.maxLines
        h = 31 * h + java.lang.Float.floatToIntBits(style.lineHeight)
        h = 31 * h + style.alignment.ordinal
        h = 31 * h + java.lang.Float.floatToIntBits(style.outlineWidth)
        h = 31 * h + if (style.outlineOnly) 1 else 0
        h = 31 * h + if (style.removePunctuation) 1 else 0
        h = 31 * h + style.textTransform.ordinal
        h = 31 * h + java.lang.Float.floatToIntBits(style.backgroundPaddingH)
        h = 31 * h + java.lang.Float.floatToIntBits(style.backgroundPaddingV)
        // Display mode and karaoke mode affect the window strategy — must bust cache
        h = 31 * h + style.displayMode.ordinal
        h = 31 * h + style.karaokeHighlightMode.ordinal
        return h
    }

    fun draw(
        context: Context,
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
        CaptionPaints.configure(context, style, baseScale)

        // ── Build TimedWord list ──────────────────────────────────────────────
        val fp = layoutFingerprint(style)
        if (cachedSegmentId != seg.id || cachedStyleId != style.id) {
            cachedSegmentId = seg.id
            cachedStyleId = style.id
            cachedWords = CaptionUtils.buildTimedWords(seg, rawWords)
            cachedIsRtl = CaptionUtils.isRtl(seg.text)
            cachedWindowWords = emptyList() // force layout rebuild below
            cachedPageIndex = -1
        }

        // Force layout rebuild when any layout-affecting property changed
        if (fp != cachedLayoutFingerprint) {
            cachedLayoutFingerprint = fp
            cachedWindowWords = emptyList()
            cachedPageIndex = -1
        }

        if (cachedWords.isEmpty()) return

        for (w in cachedWords) {
            w.isActive = currentPositionMs in w.startTimeMs..w.endTimeMs
            w.isPast = currentPositionMs > w.endTimeMs
        }

        // ── Compute display window ────────────────────────────────────────────
        val window = CaptionAnimator.computeDisplayWindow(
            words          = cachedWords,
            displayMode    = style.displayMode,
            posMs          = currentPositionMs,
            animMs         = animMs,
            maxWordsPerLine = if (style.maxWordsPerLine <= 0) 999 else style.maxWordsPerLine,
            maxLines       = if (style.maxLines <= 0) 999 else style.maxLines,
            previousPageIndex = cachedPageIndex
        )

        // Update cached page index after window is computed
        cachedPageIndex = window.pageIndex

        // Page transition alpha: fade the new page in over animMs when page flips
        val pageAlpha: Float = if (window.isNewPage) {
            // Find the newest word on this page (the one that just became active)
            val newestWordStart = window.words.firstOrNull { it.isActive }?.startTimeMs
                ?: window.words.firstOrNull()?.startTimeMs
                ?: currentPositionMs
            ((currentPositionMs - newestWordStart).toFloat() / animMs).coerceIn(0f, 1f)
        } else {
            1f
        }

        // ── Layout ───────────────────────────────────────────────────────────
        val fm      = CaptionPaints.text.fontMetrics
        val lineH   = (fm.bottom - fm.top) * style.lineHeight
        val padX    = style.backgroundPaddingH * baseScale
        val padY    = style.backgroundPaddingV * baseScale
        val corner  = style.backgroundCornerRadius * baseScale
        val spaceW  = CaptionPaints.text.measureText(" ")
        val isRtl   = cachedIsRtl

        val visible = window.words

        if (visible != cachedWindowWords) {
            cachedWindowWords = visible
            val maxW    = if (style.maxWordsPerLine <= 0) 999 else style.maxWordsPerLine
            val maxL    = if (style.maxLines <= 0) 999 else style.maxLines
            val lines   = visible.chunked(maxW).take(maxL)

            val currentSpaceW = spaceW
            val currentIsRtl = isRtl
            val currentAlign = style.alignment

            cachedLayouts = lines.map { words ->
                val wordLayouts = words.map { w ->
                    val txt = CaptionUtils.sanitize(w.text, style)
                    val wordW = CaptionPaints.text.measureText(txt)
                    WordLayout(w, txt, wordW)
                }
                val lineW = wordLayouts.sumOf { (it.wordW + currentSpaceW).toDouble() }.toFloat() - currentSpaceW

                val x = when (currentAlign) {
                    TextAlignment.CENTER -> (videoWidth - lineW) / 2f
                    TextAlignment.START  -> if (currentIsRtl) videoWidth * 0.92f - lineW else videoWidth * 0.08f
                    TextAlignment.END    -> if (currentIsRtl) videoWidth * 0.08f           else videoWidth * 0.92f - lineW
                }
                LineLayout(wordLayouts, x, lineW)
            }
        }

        val lineLayouts = cachedLayouts

        // Pre-compute word transforms to avoid redundant math
        val transforms = mutableMapOf<TimedWord, WordTransform>()
        for (line in lineLayouts) {
            for (wl in line.words) {
                transforms[wl.word] = CaptionAnimator.computeWordTransform(currentPositionMs, wl.word, style, animMs, baseScale)
            }
        }

        canvas.save()
        canvas.clipRect(0f, 0f, videoWidth.toFloat(), videoHeight.toFloat())
        val totalH = lineLayouts.size * lineH + padY * 2f
        val rawY = (videoHeight * style.positionY) - totalH / 2f
        val maxStart = (videoHeight - totalH).coerceAtLeast(0f)
        val startY = rawY.coerceIn(0f, maxStart)


        // Pass 1: Background pills, Outlines, Shadows
        // Shadows are applied here (in configure()) and NOT cleared before Pass 2.
        // The outline paint already has its shadow set from CaptionPaints.configure().
        var lineYP1 = startY
        for (line in lineLayouts) {
            val lineTop = lineYP1 + fm.top
            val lineBot = lineYP1 + fm.bottom
            drawLineBackground(canvas, style, line.lineStartX, line.lineWidth, lineTop, lineBot, padX, padY, corner, videoWidth.toFloat(), pageAlpha)

            var x = if (isRtl) line.lineStartX + line.lineWidth else line.lineStartX

            for (wl in line.words) {
                val xfm = transforms[wl.word] ?: continue

                if (isRtl) x -= wl.wordW

                // Draw per-word background pill
                if (style.backgroundOpacity > 0f && style.backgroundType == BackgroundType.PER_WORD) {
                    tempWordRect.set(x - padX / 2f, lineTop - padY, x + wl.wordW + padX / 2f, lineBot + padY)
                    canvas.withTranslation(xfm.translateX, xfm.translateY) {
                        CaptionPaints.bg.alpha = (CaptionPaints.bg.alpha * xfm.alpha * pageAlpha).toInt()
                        drawRoundRect(tempWordRect, corner / 2f, corner / 2f, CaptionPaints.bg)
                        CaptionPaints.bg.alpha = (style.backgroundOpacity * 255).toInt()
                    }
                }

                drawWord(canvas, wl.txt, x, lineYP1, lineTop, lineBot, wl.wordW, xfm, style, currentPositionMs, wl.word, baseScale, padX, padY, corner, isBgPass = true, isRtl = isRtl, pageAlpha = pageAlpha)

                if (isRtl) x -= spaceW else x += wl.wordW + spaceW
            }
            lineYP1 += lineH
        }

        // Pass 2: Text fills and karaoke overlays
        // Shadows are already on the outline paint from Pass 1 — no need to clear or re-set.
        var lineYP2 = startY
        for (line in lineLayouts) {
            val lineTop = lineYP2 + fm.top
            val lineBot = lineYP2 + fm.bottom
            var x = if (isRtl) line.lineStartX + line.lineWidth else line.lineStartX

            for (wl in line.words) {
                val xfm = transforms[wl.word] ?: continue

                if (isRtl) x -= wl.wordW

                drawWord(canvas, wl.txt, x, lineYP2, lineTop, lineBot, wl.wordW, xfm, style, currentPositionMs, wl.word, baseScale, padX, padY, corner, isBgPass = false, isRtl = isRtl, pageAlpha = pageAlpha)

                if (isRtl) x -= spaceW else x += wl.wordW + spaceW
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
        isRtl: Boolean = false,
        pageAlpha: Float = 1f
    ) {
        val cx = x + wordW / 2f
        val cy = y + (lineBot - lineTop) / 2f + lineTop

        val fillColor: Int = when {
            xfm.colorOverride != null                                          -> xfm.colorOverride
            w.isEmphasized                                                     -> style.highlightColor.toInt()
            w.isActive && style.displayMode != DisplayMode.PHRASE              -> style.highlightColor.toInt()
            else                                                               -> style.textColor.toInt()
        }

        val charsToDraw = if (style.wordEnterAnimation == AnimationType.TYPEWRITER ||
                          style.displayMode == DisplayMode.TYPEWRITER) {
            kotlin.math.ceil(txt.length * xfm.clipFraction).toInt().coerceIn(0, txt.length)
        } else txt.length

        if (charsToDraw == 0 && xfm.alpha < 0.01f) return

        canvas.withTranslation(cx, cy) {
            scale(xfm.scaleX, xfm.scaleY)
            translate(-cx, -cy)
            translate(xfm.translateX, xfm.translateY)

            val a = (255 * xfm.alpha * pageAlpha).toInt()

            if (isBgPass) {
                // Glow layer (drawn first, behind everything)
                if (style.glowEnabled && style.glowRadius > 0f) {
                    glowPaint.textSize = CaptionPaints.text.textSize
                    glowPaint.typeface = CaptionPaints.text.typeface
                    glowPaint.color = style.glowColor.toInt()
                    glowPaint.alpha = (a * style.textOpacity * 0.7f).toInt()
                    glowPaint.textAlign = Paint.Align.LEFT
                    glowPaint.letterSpacing = style.letterSpacing
                    glowPaint.setShadowLayer(
                        style.glowRadius * baseScale, 0f, 0f, glowPaint.color
                    )
                    drawText(txt, 0, charsToDraw, x, y, glowPaint)
                    glowPaint.clearShadowLayer()
                }

                // Outline pass (shadow is already baked into CaptionPaints.outline by configure())
                if (style.outlineWidth > 0f) {
                    CaptionPaints.outline.alpha = (a * style.textOpacity).toInt()
                    drawText(txt, 0, charsToDraw, x, y, CaptionPaints.outline)
                } else if (style.shadowRadius > 0f) {
                    // No outline — shadow is on the text paint itself (set in configure())
                    CaptionPaints.text.color = fillColor
                    CaptionPaints.text.alpha = (a * style.textOpacity).toInt()
                    drawText(txt, 0, charsToDraw, x, y, CaptionPaints.text)
                }
            } else {
                if (style.gradientDirection != GradientDirection.NONE) {
                    val shader = when (style.gradientDirection) {
                        GradientDirection.LEFT_RIGHT -> LinearGradient(
                            x, 0f, x + wordW, 0f,
                            style.textColor.toInt(), style.secondaryColor.toInt(),
                            Shader.TileMode.CLAMP
                        )
                        GradientDirection.TOP_BOTTOM -> LinearGradient(
                            0f, lineTop, 0f, lineBot,
                            style.textColor.toInt(), style.secondaryColor.toInt(),
                            Shader.TileMode.CLAMP
                        )
                        GradientDirection.DIAGONAL -> LinearGradient(
                            x, lineTop, x + wordW, lineBot,
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
                drawText(txt, 0, charsToDraw, x, y, CaptionPaints.text)
                CaptionPaints.text.alpha = 255
                CaptionPaints.outline.alpha = 255
            }
        }

        // ── Karaoke / highlight overlays ──────────────────────────────────────
        if (!isBgPass && w.isActive) {
            when (style.karaokeHighlightMode) {
                KaraokeHighlightMode.FILL_LEFT_RIGHT,
                KaraokeHighlightMode.COLOR_CHANGE -> {
                    // FILL_LEFT_RIGHT sweep: only when mode is KARAOKE_FILL AND highlight mode is FILL_LEFT_RIGHT
                    if (style.displayMode == DisplayMode.KARAOKE_FILL &&
                        style.karaokeHighlightMode == KaraokeHighlightMode.FILL_LEFT_RIGHT) {
                        val dur   = (w.endTimeMs - w.startTimeMs).coerceAtLeast(1L)
                        val fillP = ((posMs - w.startTimeMs).toFloat() / dur).coerceIn(0f, 1f)
                        if (isRtl) {
                            canvas.withClip(x + wordW * (1f - fillP), lineTop, x + wordW, lineBot) {
                                CaptionPaints.highlight.alpha = (255 * xfm.alpha * pageAlpha).toInt()
                                drawText(txt, 0, charsToDraw, x, y, CaptionPaints.highlight)
                                CaptionPaints.highlight.alpha = 255
                            }
                        } else {
                            canvas.withClip(x, lineTop, x + wordW * fillP, lineBot) {
                                CaptionPaints.highlight.alpha = (255 * xfm.alpha * pageAlpha).toInt()
                                drawText(txt, 0, charsToDraw, x, y, CaptionPaints.highlight)
                                CaptionPaints.highlight.alpha = 255
                            }
                        }
                    }
                    // COLOR_CHANGE: active word already uses highlightColor via fillColor above — no extra draw needed
                }
                KaraokeHighlightMode.UNDERLINE -> {
                    val saved = CaptionPaints.bg.color
                    CaptionPaints.bg.color = style.highlightColor.toInt()
                    CaptionPaints.bg.alpha = (200 * xfm.alpha * pageAlpha).toInt()
                    canvas.drawRect(x, lineBot + 2f * baseScale, x + wordW, lineBot + 5f * baseScale, CaptionPaints.bg)
                    CaptionPaints.bg.color = saved
                    CaptionPaints.bg.alpha = (style.backgroundOpacity * 255).toInt()
                }
                KaraokeHighlightMode.BACKGROUND_HIGHLIGHT -> {
                    tempWordRect.set(x - padX / 2f, lineTop - padY / 2f, x + wordW + padX / 2f, lineBot + padY / 2f)
                    val saved = CaptionPaints.bg.color
                    CaptionPaints.bg.color = style.highlightColor.toInt()
                    CaptionPaints.bg.alpha = (80 * pageAlpha).toInt()
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
        padX: Float, padY: Float, corner: Float, vw: Float,
        pageAlpha: Float = 1f
    ) {
        if (style.backgroundOpacity <= 0f) return
        val bgAlpha = (style.backgroundOpacity * 255 * pageAlpha).toInt()
        CaptionPaints.bg.alpha = bgAlpha
        tempLineRect.set(x - padX, lineTop - padY, x + lineW + padX, lineBot + padY)
        when (style.backgroundType) {
            BackgroundType.BOX       -> canvas.drawRoundRect(tempLineRect, corner, corner, CaptionPaints.bg)
            BackgroundType.PILL      -> canvas.drawRoundRect(tempLineRect, tempLineRect.height() / 2f, tempLineRect.height() / 2f, CaptionPaints.bg)
            BackgroundType.FULL_LINE -> canvas.drawRect(0f, tempLineRect.top, vw, tempLineRect.bottom, CaptionPaints.bg)
            BackgroundType.PER_WORD,
            BackgroundType.NONE      -> {}
        }
        // Restore full opacity for next draw
        CaptionPaints.bg.alpha = (style.backgroundOpacity * 255).toInt()
    }

}
