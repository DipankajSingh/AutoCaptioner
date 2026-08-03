package com.dipdev.aiautocaptioner.engine

import android.content.Context
import android.graphics.Canvas
import com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionWordEntity
import com.dipdev.aiautocaptioner.engine.animation.AnimationEngine
import com.dipdev.aiautocaptioner.engine.animation.WordTransform
import com.dipdev.aiautocaptioner.engine.layout.CaptionLayout
import com.dipdev.aiautocaptioner.engine.layout.LayoutEngine
import com.dipdev.aiautocaptioner.engine.render.FrameData
import com.dipdev.aiautocaptioner.engine.render.RenderingPipeline
import com.dipdev.aiautocaptioner.engine.timing.TimingEngine
import com.dipdev.aiautocaptioner.engine.timing.WordState

/**
 * Caption rendering orchestrator.
 *
 * Pipeline:
 *   1. TimingEngine  → which words are visible, their lifecycle states
 *   2. LayoutEngine  → where words are positioned on canvas
 *   3. AnimationEngine → how each word looks (scale, alpha, position)
 *   4. RenderingPipeline → draws everything via composable passes
 *
 * This is the ONLY entry point for caption rendering. Both preview
 * (PreviewSection, StylePreview) and export (CaptionOverlayEffect)
 * call this.
 */
class CaptionEngine(
    private val pipeline: RenderingPipeline = RenderingPipeline()
) {

    // Cached state across frames for page transition detection
    private var previousPageIndex: Int = -1
    private var previousPositionMs: Long = -1L
    private var cachedSegmentId: String? = null
    private var cachedWords: List<WordState> = emptyList()
    private var cachedIsRtl: Boolean = false

    // Layout cache — only rebuilds when words or style change
    private var cachedLayoutFingerprint: Long = 0L
    private var cachedLayout: CaptionLayout? = null
    private var cachedLayoutWords: List<WordState> = emptyList()
    private var cachedFrameData: FrameData? = null

    // Sequential word cursor (WORD_BY_WORD). Advanced by AT MOST ONE index per
    // frame toward the start-time-derived target, so every word gets its own
    // pop-up — no array index is ever skipped when frame sampling jumps over
    // sub-frame word durations. Reset on segment change / seek.
    private var wordCursor = -1

    /**
     * Main entry point — draw captions onto a Canvas.
     */
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
        val animMs = style.animationDurationMs.toLong().coerceAtLeast(50L)
        val baseScale = videoHeight / 1920f

        // Configure paints
        CaptionPaints.configure(context, style, baseScale)

        // 1. Find active segment — during gaps (silence) between blocks, hold the
        // last rendered block on screen instead of blanking out.
        val segment = TimingEngine.findActiveSegment(segments, currentPositionMs)
        if (segment == null) {
            cachedFrameData?.let { pipeline.renderFrame(canvas, it.copy(pageAlpha = 1f)) }
            previousPositionMs = currentPositionMs
            return
        }

        // 2. Build word states (only when segment changes)
        val isSegmentChange = cachedSegmentId != segment.id
        if (isSegmentChange) {
            cachedSegmentId = segment.id
            cachedWords = TimingEngine.buildWordStates(segment, wordsMap[segment.id])
            cachedIsRtl = CaptionUtils.isRtl(segment.text)
            wordCursor = -1
        }

        // Detect seek (backward jump or large gap) — reset page tracking
        val isSeek = previousPositionMs >= 0 && (
            currentPositionMs < previousPositionMs - 50 ||
            currentPositionMs - previousPositionMs > 2000
        )
        if (isSegmentChange || isSeek) {
            previousPageIndex = -1
        }
        previousPositionMs = currentPositionMs

        if (cachedWords.isEmpty()) return

        // 3. Advance the sequential word cursor for WORD_BY_WORD.
        // Target = the last word whose START TIME has passed (start-time based,
        // so the visited sequence is strictly monotonic and never lands in a gap).
        val targetIndex = computeTargetWordIndex(cachedWords, currentPositionMs)
        if (isSeek || previousPositionMs < 0L) {
            // Seek, style switch, or first frame — jump straight to the word at
            // the current position (no catch-up replay).
            wordCursor = targetIndex
        } else if (targetIndex > wordCursor) {
            // Forward playback — advance at most ONE word per frame so every
            // array index is visited; never skip, never go backward.
            wordCursor = (wordCursor + 1).coerceAtMost(targetIndex)
        }

        // 4. Resolve timing — determines lifecycle of each word
        val timing = TimingEngine.resolve(
            words = cachedWords,
            posMs = currentPositionMs,
            animMs = animMs,
            displayMode = style.displayMode,
            maxWordsPerLine = if (style.maxWordsPerLine <= 0) 999 else style.maxWordsPerLine,
            maxLines = if (style.maxLines <= 0) 999 else style.maxLines,
            previousPageIndex = previousPageIndex,
            wordCursor = wordCursor
        )

        previousPageIndex = timing.pageIndex

        // 5. Compute layout (only when visible words or geometry change).
        // The fingerprint is a fast pre-check; the structural key-equality is
        // the authoritative guard so a stale WordLayout (built for different
        // words, times, indices, or canvas geometry) can never bleed into the
        // current frame.
        val layoutFingerprint = computeLayoutFingerprint(timing.visibleWords, style, videoWidth, videoHeight, baseScale)
        val layout = if (layoutFingerprint != cachedLayoutFingerprint ||
            cachedLayout == null ||
            !layoutKeysEqual(timing.visibleWords, cachedLayoutWords)
        ) {
            val newLayout = LayoutEngine.computeLayout(
                words = timing.visibleWords,
                style = style,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                baseScale = baseScale,
                isRtl = cachedIsRtl
            )
            cachedLayoutFingerprint = layoutFingerprint
            cachedLayout = newLayout
            cachedLayoutWords = timing.visibleWords
            newLayout
        } else {
            cachedLayout!!
        }

        // 6. Compute per-word animation transforms — keyed by stable word index
        // (the layout's WordState snapshot goes stale as lifecycles change).
        val transforms = mutableMapOf<Int, WordTransform>()
        for (word in timing.visibleWords) {
            transforms[word.index] = AnimationEngine.computeWordTransform(
                posMs = currentPositionMs,
                word = word,
                style = style,
                animMs = animMs,
                baseScale = baseScale
            )
        }

        // 7. Compute page transition alpha
        val pageAlpha: Float = if (timing.isNewPage) {
            val newestWordStart = timing.visibleWords.firstOrNull { it.isActive }?.startTimeMs
                ?: timing.visibleWords.firstOrNull()?.startTimeMs
                ?: currentPositionMs
            ((currentPositionMs - newestWordStart).toFloat() / animMs).coerceIn(0f, 1f)
        } else {
            1f
        }

        // 8. Execute rendering pipeline
        val frameData = FrameData(
            timing = timing,
            layout = layout,
            transforms = transforms,
            style = style,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            baseScale = baseScale,
            currentPositionMs = currentPositionMs,
            isRtl = cachedIsRtl,
            pageAlpha = pageAlpha
        )

        pipeline.renderFrame(canvas, frameData)
        cachedFrameData = frameData
    }

    /**
     * Reset cached state — call when switching projects or styles.
     */
    fun reset() {
        previousPageIndex = -1
        previousPositionMs = -1L
        cachedSegmentId = null
        cachedWords = emptyList()
        cachedLayout = null
        cachedLayoutFingerprint = 0L
        cachedFrameData = null
        wordCursor = -1
    }

    /**
     * Index of the last word whose START TIME has already passed. Start-time
     * selection (rather than sampling the active [start,end] window) makes the
     * visited word sequence strictly monotonic and gap-free — the engine can
     * never land "between words" and drop them.
     */
    private fun computeTargetWordIndex(words: List<WordState>, posMs: Long): Int {
        var target = -1
        // Full scan (not early-break) so it is correct even if a user-edited
        // segment contains words out of time order. Segments hold <~30 words,
        // so this is negligible at frame rate.
        for (i in words.indices) {
            if (words[i].startTimeMs <= posMs) target = i
        }
        return target
    }

    /**
     * Structural equality of everything that affects a word's layout slot.
     * Lifecycle state is intentionally excluded — it changes every frame.
     */
    private fun layoutKeysEqual(a: List<WordState>, b: List<WordState>): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) {
            val x = a[i]
            val y = b[i]
            if (x.index != y.index || x.text != y.text ||
                x.startTimeMs != y.startTimeMs || x.endTimeMs != y.endTimeMs
            ) {
                return false
            }
        }
        return true
    }

    /**
     * Fingerprint of all state that affects layout.
     * Used to avoid redundant layout recomputation.
     */
    private fun computeLayoutFingerprint(
        words: List<WordState>,
        style: CaptionStyleEntity,
        videoWidth: Int,
        videoHeight: Int,
        baseScale: Float
    ): Long {
        var h = 17L
        h = 31 * h + style.fontSize.toRawBits()
        h = 31 * h + style.fontFamily.hashCode()
        h = 31 * h + style.fontWeight
        h = 31 * h + if (style.isItalic) 1 else 0
        h = 31 * h + style.letterSpacing.toRawBits()
        h = 31 * h + style.maxWordsPerLine
        h = 31 * h + style.maxLines
        h = 31 * h + style.lineHeight.toRawBits()
        h = 31 * h + style.alignment.ordinal
        h = 31 * h + style.outlineWidth.toRawBits()
        h = 31 * h + if (style.outlineOnly) 1 else 0
        h = 31 * h + if (style.removePunctuation) 1 else 0
        h = 31 * h + style.textTransform.ordinal
        h = 31 * h + style.backgroundPaddingH.toRawBits()
        h = 31 * h + style.backgroundPaddingV.toRawBits()
        h = 31 * h + style.displayMode.ordinal
        h = 31 * h + style.karaokeHighlightMode.ordinal
        h = 31 * h + videoWidth
        h = 31 * h + videoHeight
        h = 31 * h + baseScale.toRawBits()
        for (w in words) {
            h = 31 * h + w.index
            h = 31 * h + w.text.hashCode()
            h = 31 * h + w.startTimeMs
            h = 31 * h + w.endTimeMs
        }
        return h
    }
}
