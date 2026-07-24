package com.dipdev.aiautocaptioner.engine

import com.dipdev.aiautocaptioner.data.db.entity.*
import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
// CaptionAnimator
//
// Pure animation math — no Canvas, no Paint, no Android framework dependencies.
// Responsible for computing a WordTransform for any word at any point in time,
// and for determining which words belong in the current display window.
// ─────────────────────────────────────────────────────────────────────────────
object CaptionAnimator {

    // ── Public data types ─────────────────────────────────────────────────────

    data class TimedWord(
        val text: String,
        val startTimeMs: Long,
        val endTimeMs: Long,
        var isActive: Boolean,
        var isPast: Boolean,
        val isEmphasized: Boolean,
        val emphasisType: EmphasisType
    )

    data class WordTransform(
        val alpha: Float = 1f,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val translateX: Float = 0f,
        val translateY: Float = 0f,
        /** 0..1 — fraction of the word's characters/width to reveal (TYPEWRITER) */
        val clipFraction: Float = 1f,
        /** When non-null, overrides the word's fill colour (COLOR_POP emphasis) */
        val colorOverride: Int? = null
    )

    /**
     * The result of [computeDisplayWindow] — contains the subset of words to
     * lay out and draw for the current playback position, plus page metadata
     * used to drive inter-page transition animations.
     */
    data class DisplayWindow(
        /** The words to lay out and render for this frame. */
        val words: List<TimedWord>,
        /** Which "page" these words belong to (0-based). */
        val pageIndex: Int,
        /**
         * True when the page just changed this frame — used by the renderer
         * to play a fade-in transition on the entire new page.
         */
        val isNewPage: Boolean
    )

    // ── Display window ────────────────────────────────────────────────────────

    /**
     * Computes the [DisplayWindow] — the subset of words that should be
     * laid out and drawn for the current playback position.
     *
     * Each display mode has its own windowing strategy:
     *
     *  - PHRASE         : all words in the segment simultaneously (unchanged)
     *  - LINE_HIGHLIGHT : "page" of (maxWordsPerLine × maxLines) words; flips
     *                     forward when the active word crosses a page boundary
     *  - KARAOKE_FILL   : same paging as LINE_HIGHLIGHT (fill animation is
     *                     handled separately in the renderer)
     *  - WORD_BY_WORD   : active word only; the previous word's exit animation
     *                     overlaps briefly via [animMs]
     *  - TYPEWRITER     : accumulates past+active words up to page capacity,
     *                     clears page on overflow
     */
    fun computeDisplayWindow(
        words: List<TimedWord>,
        displayMode: DisplayMode,
        posMs: Long,
        animMs: Long,
        maxWordsPerLine: Int,
        maxLines: Int,
        previousPageIndex: Int
    ): DisplayWindow {
        if (words.isEmpty()) return DisplayWindow(emptyList(), 0, false)

        return when (displayMode) {

            // ── PHRASE ────────────────────────────────────────────────────────
            DisplayMode.PHRASE -> DisplayWindow(
                words = words,
                pageIndex = 0,
                isNewPage = false
            )

            // ── LINE_HIGHLIGHT / KARAOKE_FILL ─────────────────────────────────
            // Divide the word list into fixed-size "pages".
            // Show whichever page contains the currently active word.
            DisplayMode.LINE_HIGHLIGHT,
            DisplayMode.KARAOKE_FILL -> {
                val wordsPerPage = (maxWordsPerLine.coerceAtLeast(1) * maxLines.coerceAtLeast(1))
                    .coerceAtLeast(1)

                // Find the active word index; if none, use the last past word.
                val activeIdx = words.indexOfFirst { it.isActive }
                    .takeIf { it >= 0 }
                    ?: (words.indexOfLast { it.isPast }.takeIf { it >= 0 } ?: 0)

                val pageIndex = activeIdx / wordsPerPage
                val pageStart = pageIndex * wordsPerPage
                val pageEnd   = (pageStart + wordsPerPage).coerceAtMost(words.size)
                val pageWords = words.subList(pageStart, pageEnd)
                val isNewPage = pageIndex != previousPageIndex

                DisplayWindow(words = pageWords, pageIndex = pageIndex, isNewPage = isNewPage)
            }

            // ── WORD_BY_WORD ──────────────────────────────────────────────────
            // Show only the currently active word. The renderer's WordTransform
            // drives the enter/exit animations. We include one word of "exit
            // overlap" — the previous word — only while it is still within its
            // exit animation window (posMs < prevWord.endTimeMs + animMs).
            DisplayMode.WORD_BY_WORD -> {
                val activeIdx = words.indexOfFirst { it.isActive }

                if (activeIdx < 0) {
                    // Between segments or after last word — nothing to show
                    DisplayWindow(words = emptyList(), pageIndex = 0, isNewPage = false)
                } else {
                    val visibleWords = mutableListOf<TimedWord>()

                    // Include previous word if it's still within its exit window
                    if (activeIdx > 0) {
                        val prev = words[activeIdx - 1]
                        if (posMs < prev.endTimeMs + animMs) {
                            visibleWords.add(prev)
                        }
                    }
                    visibleWords.add(words[activeIdx])

                    DisplayWindow(
                        words = visibleWords,
                        pageIndex = activeIdx, // use word index as "page" for transition tracking
                        isNewPage = activeIdx != previousPageIndex
                    )
                }
            }

            // ── TYPEWRITER ────────────────────────────────────────────────────
            // Accumulate words from the start of the current "page" up to and
            // including the active word. Clear the page (start a new one) when
            // the accumulated count would exceed page capacity.
            DisplayMode.TYPEWRITER -> {
                val capacity = (maxWordsPerLine.coerceAtLeast(1) * maxLines.coerceAtLeast(1))
                    .coerceAtLeast(1)

                val activeIdx = words.indexOfFirst { it.isActive }
                    .takeIf { it >= 0 }
                    ?: words.indexOfLast { it.isPast }.takeIf { it >= 0 }
                    ?: 0

                // Which page does the active word belong to?
                val pageIndex = activeIdx / capacity
                val pageStart = pageIndex * capacity

                // Include all words from page start up to active (inclusive),
                // plus words that entered soon (entering within animMs).
                val visible = words.subList(pageStart, words.size).filter {
                    val isActiveOrPast = it.isActive || it.isPast
                    val isEnteringSoon = !it.isPast && !it.isActive &&
                            (it.startTimeMs - posMs) < animMs
                    (isActiveOrPast || isEnteringSoon) &&
                            words.indexOf(it) < pageStart + capacity
                }

                DisplayWindow(
                    words = visible,
                    pageIndex = pageIndex,
                    isNewPage = pageIndex != previousPageIndex
                )
            }
        }
    }

    // ── Main entry point ──────────────────────────────────────────────────────

    fun computeWordTransform(
        posMs: Long,
        word: TimedWord,
        style: CaptionStyleEntity,
        animMs: Long,
        baseScale: Float = 1f
    ): WordTransform {
        // Enter progress: 0→1 over [startTimeMs, startTimeMs + animMs]
        val enterRaw = ((posMs - word.startTimeMs).toFloat() / animMs).coerceIn(0f, 1f)
        // Exit progress:  0→1 over [endTimeMs, endTimeMs + animMs]
        val exitRaw  = ((posMs - word.endTimeMs).toFloat() / animMs).coerceIn(0f, 1f)

        val isEntering = !word.isActive && !word.isPast
        val isExiting  = word.isPast && style.displayMode == DisplayMode.WORD_BY_WORD

        val enter = if (isEntering || word.isActive) enterRaw else 1f
        val exit  = if (isExiting) exitRaw else 0f

        val et = applyAnim(style.wordEnterAnimation, enter, entering = true, baseScale)
        val xt = if (isExiting) applyAnim(style.wordExitAnimation, 1f - exit, entering = false, baseScale) else RawTransform()

        val alpha  = et.alpha * (if (isExiting) {
            if (style.wordExitAnimation == AnimationType.NONE) (1f - AnimationUtils.easeInCubic(exit)).coerceIn(0f, 1f) else xt.alpha
        } else 1f)
        var scaleX = et.scaleX * (if (isExiting) xt.scaleX else 1f)
        var scaleY = et.scaleY * (if (isExiting) xt.scaleY else 1f)
        var tx     = et.translateX + (if (isExiting) xt.translateX else 0f)
        var ty     = et.translateY + (if (isExiting) xt.translateY else 0f)
        val clip   = et.clipFraction

        // KARAOKE SCALE_UP — boost scale on the active word
        if (word.isActive && style.karaokeHighlightMode == KaraokeHighlightMode.SCALE_UP) {
            scaleX *= 1.12f
            scaleY *= 1.12f
        }

        // Emphasis oscillations
        var colorOverride: Int? = null
        if (word.isActive && word.isEmphasized) {
            val phase = (posMs % 600L).toFloat() / 600f * 2f * PI.toFloat()
            when (word.emphasisType) {
                EmphasisType.BOUNCE    -> ty -= sin(phase) * 12f * baseScale
                EmphasisType.SCALE     -> { val s = 1f + 0.12f * sin(phase); scaleX *= s; scaleY *= s }
                EmphasisType.SHAKE     -> tx += sin(phase * 3f) * 8f * baseScale
                EmphasisType.COLOR_POP -> colorOverride = AnimationUtils.blendColor(
                    style.textColor.toInt(), style.highlightColor.toInt(),
                    (sin(phase) + 1f) / 2f
                )
                EmphasisType.NONE -> {}
            }
        }

        return WordTransform(
            alpha         = alpha.coerceIn(0f, 1f),
            scaleX        = scaleX.coerceAtLeast(0f),
            scaleY        = scaleY.coerceAtLeast(0f),
            translateX    = tx,
            translateY    = ty,
            clipFraction  = clip,
            colorOverride = colorOverride
        )
    }

    // ── Internal animation type resolver ──────────────────────────────────────

    private data class RawTransform(
        val alpha: Float = 1f,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val translateX: Float = 0f,
        val translateY: Float = 0f,
        val clipFraction: Float = 1f
    )

    private fun applyAnim(type: AnimationType, p: Float, entering: Boolean, baseScale: Float = 1f): RawTransform {
        val e = AnimationUtils.easeOutCubic(p)
        val dir = if (entering) 1f else -1f
        return when (type) {
            AnimationType.NONE       -> RawTransform()
            AnimationType.FADE       -> RawTransform(alpha = e)
            AnimationType.SLIDE_UP   -> RawTransform(alpha = e, translateY = (1f - e) * 40f * baseScale * dir)
            AnimationType.SLIDE_DOWN -> RawTransform(alpha = e, translateY = -(1f - e) * 40f * baseScale * dir)
            AnimationType.SCALE_POP  -> RawTransform(alpha = e, scaleX = e.coerceAtLeast(0.01f), scaleY = e.coerceAtLeast(0.01f))
            AnimationType.BOUNCE     -> {
                val s = AnimationUtils.bounceOut(p).coerceAtLeast(0.01f)
                RawTransform(alpha = if (p > 0.05f) 1f else p * 20f, scaleX = s, scaleY = s)
            }
            AnimationType.ELASTIC    -> {
                val s = AnimationUtils.elasticOut(p).coerceAtLeast(0.01f)
                RawTransform(alpha = if (p > 0.05f) 1f else p * 20f, scaleX = s, scaleY = s)
            }
            AnimationType.TYPEWRITER -> RawTransform(clipFraction = p)
            AnimationType.SHAKE      -> RawTransform(alpha = e, translateX = sin(p * PI.toFloat() * 5f) * (1f - p) * 20f * baseScale)
            AnimationType.FLIP       -> {
                val sx = abs(cos(p * PI.toFloat())).coerceAtLeast(0.01f)
                RawTransform(alpha = if (p > 0.5f) 1f else p * 2f, scaleX = sx)
            }
        }
    }
}
