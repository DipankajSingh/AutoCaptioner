package com.dipdev.aiautocaptioner.engine.timing

import com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionWordEntity
import com.dipdev.aiautocaptioner.data.db.entity.DisplayMode
import com.dipdev.aiautocaptioner.data.db.entity.EmphasisType
import com.dipdev.aiautocaptioner.engine.CaptionUtils

/**
 * Enriched word with lifecycle state, replacing the flat isActive/isPast model.
 *
 * Every word in the current segment gets one of these, with its [lifecycle]
 * determined by [TimingEngine] based on playback position and display mode.
 */
data class WordState(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val lifecycle: WordLifecycle,
    val isEmphasized: Boolean = false,
    val emphasisType: EmphasisType = EmphasisType.NONE,
    /** Index of this word in the original segment word list. */
    val index: Int = 0
) {
    val isActive get() = lifecycle == WordLifecycle.ACTIVE
    val isPast get() = lifecycle == WordLifecycle.EXITING || lifecycle == WordLifecycle.REMOVED
}

/**
 * Result of timing resolution for a single frame.
 */
data class TimingResult(
    /** Words that should be rendered (lifecycle != REMOVED). */
    val visibleWords: List<WordState>,
    /** The active word, if any. */
    val activeWord: WordState?,
    /** Index of the active word in the full word list. */
    val activeWordIndex: Int,
    /** Whether the display page just changed (triggers page transition animation). */
    val isNewPage: Boolean,
    /** Page index for page-based modes. */
    val pageIndex: Int
)

/**
 * Determines the lifecycle state of every word at a given playback position.
 *
 * This is the single source of truth for word visibility. It replaces the
 * scattered isActive/isPast logic in the old CaptionAnimator and CaptionRenderer.
 *
 * Key behavioral fixes:
 *  - WORD_BY_WORD: only shows current word (plus dynamic exit overlap)
 *  - PHRASE: past words get dimmed (via pastWordOpacity), not left at full brightness
 *  - KARAOKE_FILL: shows entire phrase (no paging), fill animation handled by renderer
 *  - Dynamic exit overlap: adapts to speech speed (fast speech = shorter overlap)
 */
object TimingEngine {

    /**
     * Find the segment that contains the given playback position.
     */
    fun findActiveSegment(
        segments: List<CaptionSegmentEntity>,
        posMs: Long
    ): CaptionSegmentEntity? {
        return segments.find { posMs in it.startTimeMs..it.endTimeMs }
    }

    /**
     * Build WordState list from DB entities.
     */
    fun buildWordStates(
        segment: CaptionSegmentEntity,
        rawWords: List<CaptionWordEntity>?
    ): List<WordState> {
        if (!rawWords.isNullOrEmpty()) {
            return rawWords.mapIndexed { i, w ->
                WordState(
                    text = w.word,
                    startTimeMs = w.startTimeMs,
                    endTimeMs = w.endTimeMs,
                    lifecycle = WordLifecycle.UPCOMING,
                    isEmphasized = w.isEmphasized,
                    emphasisType = w.emphasisType,
                    index = i
                )
            }
        }
        // Fallback: distribute segment time equally
        val words = segment.text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val tpw = if (words.isNotEmpty()) (segment.endTimeMs - segment.startTimeMs) / words.size else 0L
        return words.mapIndexed { i, w ->
            val s = segment.startTimeMs + i * tpw
            val e = s + tpw
            WordState(w, s, e, WordLifecycle.UPCOMING, false, EmphasisType.NONE, i)
        }
    }

    /**
     * Resolve lifecycle states for all words at the current playback position.
     *
     * This is the core timing logic that fixes word visibility bugs.
     */
    fun resolve(
        words: List<WordState>,
        posMs: Long,
        animMs: Long,
        displayMode: DisplayMode,
        maxWordsPerLine: Int,
        maxLines: Int,
        previousPageIndex: Int
    ): TimingResult {
        if (words.isEmpty()) return TimingResult(emptyList(), null, -1, false, 0)

        // Step 1: Assign lifecycle states to every word
        val updated = words.map { word ->
            val lifecycle = computeLifecycle(word, posMs, animMs, displayMode)
            word.copy(lifecycle = lifecycle)
        }

        // Step 2: Determine display window based on display mode
        return when (displayMode) {
            DisplayMode.PHRASE -> resolvePhrase(updated, posMs, animMs, displayMode)
            DisplayMode.LINE_HIGHLIGHT -> resolvePaged(updated, posMs, animMs, maxWordsPerLine, maxLines, previousPageIndex)
            DisplayMode.KARAOKE_FILL -> resolveKaraokeFill(updated, posMs, maxWordsPerLine, maxLines, previousPageIndex)
            DisplayMode.WORD_BY_WORD -> resolveWordByWord(updated, posMs, animMs, previousPageIndex)
            DisplayMode.TYPEWRITER -> resolveTypewriter(updated, posMs, animMs, maxWordsPerLine, maxLines, previousPageIndex)
        }
    }

    // ── Lifecycle Computation ──────────────────────────────────────────────

    private fun computeLifecycle(
        word: WordState,
        posMs: Long,
        animMs: Long,
        displayMode: DisplayMode
    ): WordLifecycle {
        val isBeforeStart = posMs < word.startTimeMs
        val isActive = posMs in word.startTimeMs..word.endTimeMs
        val isAfterEnd = posMs > word.endTimeMs

        return when {
            isActive -> WordLifecycle.ACTIVE
            isBeforeStart -> {
                // ENTERING if within animMs of start time
                val timeUntilStart = word.startTimeMs - posMs
                if (timeUntilStart <= animMs) WordLifecycle.ENTERING else WordLifecycle.UPCOMING
            }
            isAfterEnd -> {
                // EXITING only for modes with per-word exit (WORD_BY_WORD)
                if (displayMode == DisplayMode.WORD_BY_WORD) {
                    val effectiveOverlap = calculateExitOverlap(word.endTimeMs - word.startTimeMs, animMs)
                    if (posMs <= word.endTimeMs + effectiveOverlap) {
                        WordLifecycle.EXITING
                    } else {
                        WordLifecycle.REMOVED
                    }
                } else {
                    // For non WORD_BY_WORD modes: past words are still "visible" but not ACTIVE
                    // We use REMOVED for lifecycle but the display window logic decides visibility
                    WordLifecycle.REMOVED
                }
            }
            else -> WordLifecycle.UPCOMING
        }
    }

    /**
     * Dynamic exit overlap — adapts to speech speed.
     * Fast speech (short word duration) gets shorter overlap.
     */
    internal fun calculateExitOverlap(wordDurationMs: Long, animMs: Long): Long {
        val maxOverlap = (wordDurationMs * 0.3f).toLong().coerceAtMost(animMs)
        return maxOverlap.coerceAtLeast(30L)
    }

    // ── Display Mode Resolvers ─────────────────────────────────────────────

    /**
     * PHRASE mode: show ALL words in the segment simultaneously.
     * Past words are visible but dimmed (opacity handled by renderer via pastWordOpacity).
     * Matches CapCut's standard subtitle behavior.
     */
    private fun resolvePhrase(
        words: List<WordState>,
        posMs: Long,
        animMs: Long,
        displayMode: DisplayMode
    ): TimingResult {
        val activeIdx = words.indexOfFirst { it.lifecycle == WordLifecycle.ACTIVE }
        val activeWord = if (activeIdx >= 0) words[activeIdx] else null

        // Show all words — past words stay visible (dimmed by renderer)
        // Only hide words that are far in the future (UPCOMING, more than animMs away)
        val visible = words.filter { it.lifecycle != WordLifecycle.UPCOMING || 
            (it.startTimeMs - posMs) <= animMs }

        return TimingResult(
            visibleWords = visible.ifEmpty { words },
            activeWord = activeWord,
            activeWordIndex = activeIdx,
            isNewPage = false,
            pageIndex = 0
        )
    }

    /**
     * Paged mode (LINE_HIGHLIGHT): show a fixed-size page of words.
     * Page flips when active word crosses a page boundary.
     * All words on the current page are visible; past words stay at full opacity.
     */
    private fun resolvePaged(
        words: List<WordState>,
        posMs: Long,
        animMs: Long,
        maxWordsPerLine: Int,
        maxLines: Int,
        previousPageIndex: Int
    ): TimingResult {
        val wordsPerPage = (maxWordsPerLine.coerceAtLeast(1) * maxLines.coerceAtLeast(1)).coerceAtLeast(1)

        val activeIdx = words.indexOfFirst { it.lifecycle == WordLifecycle.ACTIVE }
            .takeIf { it >= 0 }
            ?: run {
                val nextUpcoming = words.indexOfFirst {
                    it.lifecycle == WordLifecycle.UPCOMING || it.lifecycle == WordLifecycle.ENTERING
                }
                if (nextUpcoming >= 0) nextUpcoming
                else words.indexOfLast { it.lifecycle == WordLifecycle.REMOVED || it.lifecycle == WordLifecycle.EXITING }
                    .takeIf { it >= 0 } ?: 0
            }

        val pageIndex = activeIdx / wordsPerPage
        val pageStart = pageIndex * wordsPerPage
        val pageEnd = (pageStart + wordsPerPage).coerceAtMost(words.size)
        val pageWords = words.subList(pageStart, pageEnd)

        return TimingResult(
            visibleWords = pageWords,
            activeWord = if (activeIdx >= 0) words[activeIdx] else null,
            activeWordIndex = activeIdx,
            isNewPage = pageIndex != previousPageIndex,
            pageIndex = pageIndex
        )
    }

    /**
     * KARAOKE_FILL: displays text in short, readable page windows (e.g. 4-8 words across 1-2 lines)
     * just like TikTok and CapCut viral style videos. Uses the most recently spoken word
     * as an anchor so past words on the current page stay highlighted even during vocal gaps.
     */
    private fun resolveKaraokeFill(
        words: List<WordState>,
        posMs: Long,
        maxWordsPerLine: Int,
        maxLines: Int,
        previousPageIndex: Int
    ): TimingResult {
        val wordsPerPage = (maxWordsPerLine.coerceAtLeast(1) * maxLines.coerceAtLeast(1)).coerceAtLeast(1)

        // Find the word currently being spoken
        val activeIdx = words.indexOfFirst { it.lifecycle == WordLifecycle.ACTIVE }

        // If no word is actively being spoken (gap between words), find the
        // most recently finished word so past words stay highlighted without premature page flips.
        val anchorIdx = if (activeIdx >= 0) {
            activeIdx
        } else {
            val lastPast = words.indexOfLast { posMs > it.endTimeMs }
            if (lastPast >= 0) lastPast else {
                val nextUpcoming = words.indexOfFirst { it.lifecycle == WordLifecycle.UPCOMING || it.lifecycle == WordLifecycle.ENTERING }
                if (nextUpcoming >= 0) nextUpcoming else 0
            }
        }

        val pageIndex = anchorIdx / wordsPerPage
        val pageStart = pageIndex * wordsPerPage
        val pageEnd = (pageStart + wordsPerPage).coerceAtMost(words.size)
        val pageWords = words.subList(pageStart, pageEnd)

        return TimingResult(
            visibleWords = pageWords,
            activeWord = if (activeIdx >= 0) words[activeIdx] else null,
            activeWordIndex = anchorIdx,
            isNewPage = pageIndex != previousPageIndex,
            pageIndex = pageIndex
        )
    }

    /**
     * WORD_BY_WORD: show ONLY the current word (plus dynamic exit overlap).
     * This fixes the bug where too many words were visible.
     *
     * The exit overlap is dynamically calculated based on speech speed:
     *  - Fast speech (200ms word): ~60ms overlap
     *  - Slow speech (500ms word): ~150ms overlap (capped at animMs)
     */
    private fun resolveWordByWord(
        words: List<WordState>,
        posMs: Long,
        animMs: Long,
        previousPageIndex: Int
    ): TimingResult {
        val activeIdx = words.indexOfFirst { it.lifecycle == WordLifecycle.ACTIVE }

        if (activeIdx < 0) {
            // Between words — check if previous word is still in exit animation
            val lastExiting = words.indexOfLast { it.lifecycle == WordLifecycle.EXITING }
            if (lastExiting >= 0) {
                return TimingResult(
                    visibleWords = listOf(words[lastExiting]),
                    activeWord = null,
                    activeWordIndex = lastExiting,
                    isNewPage = false,
                    pageIndex = lastExiting
                )
            }
            return TimingResult(emptyList(), null, -1, false, 0)
        }

        val visibleWords = mutableListOf<WordState>()

        // Include previous word ONLY if it's still in its exit window
        if (activeIdx > 0) {
            val prev = words[activeIdx - 1]
            val effectiveOverlap = calculateExitOverlap(prev.endTimeMs - prev.startTimeMs, animMs)
            if (prev.lifecycle == WordLifecycle.EXITING ||
                (posMs > prev.endTimeMs && posMs <= prev.endTimeMs + effectiveOverlap)
            ) {
                visibleWords.add(prev)
            }
        }

        visibleWords.add(words[activeIdx])

        return TimingResult(
            visibleWords = visibleWords,
            activeWord = words[activeIdx],
            activeWordIndex = activeIdx,
            isNewPage = activeIdx != previousPageIndex,
            pageIndex = activeIdx
        )
    }

    /**
     * TYPEWRITER: accumulate past + active words up to page capacity.
     * Letters reveal one-by-one as words are spoken.
     */
    private fun resolveTypewriter(
        words: List<WordState>,
        posMs: Long,
        animMs: Long,
        maxWordsPerLine: Int,
        maxLines: Int,
        previousPageIndex: Int
    ): TimingResult {
        val capacity = (maxWordsPerLine.coerceAtLeast(1) * maxLines.coerceAtLeast(1)).coerceAtLeast(1)

        val activeIdx = words.indexOfFirst { it.lifecycle == WordLifecycle.ACTIVE }
            .takeIf { it >= 0 }
            ?: run {
                val nextUpcoming = words.indexOfFirst {
                    it.lifecycle == WordLifecycle.UPCOMING || it.lifecycle == WordLifecycle.ENTERING
                }
                if (nextUpcoming >= 0) nextUpcoming
                else words.indexOfLast { it.lifecycle == WordLifecycle.REMOVED }
                    .takeIf { it >= 0 } ?: 0
            }

        val pageIndex = activeIdx / capacity
        val pageStart = pageIndex * capacity

        val visible = words.subList(pageStart, words.size).filter {
            val isActiveOrPast = it.lifecycle == WordLifecycle.ACTIVE ||
                    it.lifecycle == WordLifecycle.REMOVED ||
                    it.lifecycle == WordLifecycle.EXITING
            val isEnteringSoon = it.lifecycle == WordLifecycle.UPCOMING &&
                    (it.startTimeMs - posMs) < animMs
            (isActiveOrPast || isEnteringSoon) &&
                    words.indexOf(it) < pageStart + capacity
        }

        return TimingResult(
            visibleWords = visible,
            activeWord = if (activeIdx >= 0) words[activeIdx] else null,
            activeWordIndex = activeIdx,
            isNewPage = pageIndex != previousPageIndex,
            pageIndex = pageIndex
        )
    }
}
