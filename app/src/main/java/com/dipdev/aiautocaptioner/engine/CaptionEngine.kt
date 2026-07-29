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

        // 1. Find active segment
        val segment = TimingEngine.findActiveSegment(segments, currentPositionMs) ?: run {
            previousPositionMs = currentPositionMs
            return
        }

        // 2. Build word states (only when segment changes)
        val isSegmentChange = cachedSegmentId != segment.id
        if (isSegmentChange) {
            cachedSegmentId = segment.id
            cachedWords = TimingEngine.buildWordStates(segment, wordsMap[segment.id])
            cachedIsRtl = CaptionUtils.isRtl(segment.text)
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

        // 3. Resolve timing — determines lifecycle of each word
        val timing = TimingEngine.resolve(
            words = cachedWords,
            posMs = currentPositionMs,
            animMs = animMs,
            displayMode = style.displayMode,
            maxWordsPerLine = if (style.maxWordsPerLine <= 0) 999 else style.maxWordsPerLine,
            maxLines = if (style.maxLines <= 0) 999 else style.maxLines,
            previousPageIndex = previousPageIndex
        )

        previousPageIndex = timing.pageIndex

        // 4. Compute layout (only when visible words change)
        val layoutFingerprint = computeLayoutFingerprint(timing.visibleWords, style)
        val layout = if (layoutFingerprint != cachedLayoutFingerprint || cachedLayout == null) {
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

        // 5. Compute per-word animation transforms
        val transforms = mutableMapOf<WordState, WordTransform>()
        for (word in timing.visibleWords) {
            transforms[word] = AnimationEngine.computeWordTransform(
                posMs = currentPositionMs,
                word = word,
                style = style,
                animMs = animMs,
                baseScale = baseScale
            )
        }

        // 6. Compute page transition alpha
        val pageAlpha: Float = if (timing.isNewPage) {
            val newestWordStart = timing.visibleWords.firstOrNull { it.isActive }?.startTimeMs
                ?: timing.visibleWords.firstOrNull()?.startTimeMs
                ?: currentPositionMs
            ((currentPositionMs - newestWordStart).toFloat() / animMs).coerceIn(0f, 1f)
        } else {
            1f
        }

        // 7. Execute rendering pipeline
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
    }

    /**
     * Fingerprint of all state that affects layout.
     * Used to avoid redundant layout recomputation.
     */
    private fun computeLayoutFingerprint(words: List<WordState>, style: CaptionStyleEntity): Long {
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
        for (w in words) {
            h = 31 * h + w.text.hashCode()
            h = 31 * h + w.startTimeMs
            h = 31 * h + w.endTimeMs
        }
        return h
    }
}
