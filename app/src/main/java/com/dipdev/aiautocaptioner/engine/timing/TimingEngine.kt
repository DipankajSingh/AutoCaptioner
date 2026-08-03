package com.dipdev.aiautocaptioner.engine.timing

import com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionWordEntity
import com.dipdev.aiautocaptioner.data.db.entity.DisplayMode
import com.dipdev.aiautocaptioner.data.db.entity.EmphasisType

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
 * This is the single source of truth for word visibility.
 *
 * Key behavioral details:
 *  - WORD_BY_WORD: only shows current word (plus dynamic exit overlap)
 *  - PHRASE: past words get dimmed (via pastWordOpacity), not left at full brightness
 *  - KARAOKE_FILL: shows entire phrase (no paging), fill animation handled by renderer
 *  - Dynamic exit overlap: adapts to speech speed (fast speech = shorter overlap)
 */
object TimingEngine {

    /**
     * Find the segment that contains the given playback position.
     * When multiple segments overlap, returns the one with the latest start time
     * (most recently started) for correct behavior after manual editing.
     */
    fun findActiveSegment(
        segments: List<CaptionSegmentEntity>,
        posMs: Long
    ): CaptionSegmentEntity? {
        return segments
            .filter { posMs in it.startTimeMs..it.endTimeMs }
            .maxByOrNull { it.startTimeMs }
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
        previousPageIndex: Int,
        wordCursor: Int = -1
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
            DisplayMode.KARAOKE_FILL -> resolveKaraokeFill(updated, posMs, previousPageIndex)
            DisplayMode.WORD_BY_WORD -> resolveWordByWord(updated, posMs, animMs, wordCursor)
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
     *
     * IMPORTANT: We separate two distinct concerns here:
     *   - pageAnchorIdx: which word to use when calculating the current page.
     *     Uses a fallback (next upcoming, last past) so the correct 4-word
     *     block is always shown even in gaps between words.
     *   - activeWord: the word currently being spoken, used ONLY for highlight
     *     rendering (yellow pill box). This is strictly null when no word has
     *     lifecycle == ACTIVE, so the pill is never drawn during silence gaps
     *     or before the first word starts. Without this separation the box
     *     freezes on a random "next upcoming" word that is not being spoken.
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

        // The genuinely active word — null during gaps. Used for highlight rendering only.
        val trueActiveIdx = words.indexOfFirst { it.lifecycle == WordLifecycle.ACTIVE }
        val trueActiveWord = if (trueActiveIdx >= 0) words[trueActiveIdx] else null

        // Page anchor — allowed to fall back so the right block is always on screen.
        // Uses next-upcoming or last-past as a positional anchor, NOT as a highlight target.
        val pageAnchorIdx = if (trueActiveIdx >= 0) {
            trueActiveIdx
        } else {
            val nextUpcoming = words.indexOfFirst {
                it.lifecycle == WordLifecycle.UPCOMING || it.lifecycle == WordLifecycle.ENTERING
            }
            if (nextUpcoming >= 0) nextUpcoming
            else words.indexOfLast { it.lifecycle == WordLifecycle.REMOVED || it.lifecycle == WordLifecycle.EXITING }
                .takeIf { it >= 0 } ?: 0
        }

        val pageIndex = pageAnchorIdx / wordsPerPage
        val pageStart = pageIndex * wordsPerPage
        val pageEnd = (pageStart + wordsPerPage).coerceAtMost(words.size)
        val pageWords = words.subList(pageStart, pageEnd)

        return TimingResult(
            visibleWords = pageWords,
            activeWord = trueActiveWord,            // null during gaps → no pill drawn
            activeWordIndex = pageAnchorIdx,        // page position anchor (not highlight)
            isNewPage = pageIndex != previousPageIndex,
            pageIndex = pageIndex
        )
    }

    /**
     * KARAOKE_FILL: the whole phrase is locked onto a static block.
     *
     * Every word of the sentence is visible from the moment the block loads and
     * stays in place while the highlight sweeps through it word by word — no
     * rolling window, no mutation, no reflow, so the reader always has a stable
     * anchor to read ahead of the audio. The screen only clears to load the next
     * phrase once the entire block has finished (segment switch).
     */
    private fun resolveKaraokeFill(
        words: List<WordState>,
        posMs: Long,
        previousPageIndex: Int
    ): TimingResult {
        // Anchor = the word currently being spoken, or the most recently spoken
        // word during vocal gaps. -1 while nothing has been spoken yet.
        val activeIdx = words.indexOfFirst { it.lifecycle == WordLifecycle.ACTIVE }
        val lastPastIdx = words.indexOfLast { posMs > it.endTimeMs }
        val anchorIdx = if (activeIdx >= 0) activeIdx else lastPastIdx

        return TimingResult(
            visibleWords = words,
            activeWord = if (activeIdx >= 0) words[activeIdx] else null,
            activeWordIndex = anchorIdx,
            // One static page per phrase — a transition only fires on segment
            // change, when CaptionEngine resets previousPageIndex to -1.
            isNewPage = previousPageIndex != 0,
            pageIndex = 0
        )
    }

    /**
     * WORD_BY_WORD: show ONLY the current word (plus dynamic exit overlap).
     *
     * The current word is driven by [currentIndex] — CaptionEngine's sequential
     * word cursor, which advances at most ONE index per frame toward the
     * position-derived target. This guarantees a strict 1:1 mapping of word
     * start-times to UI pop-ups: no array index is ever skipped, even when
     * frame sampling jumps over sub-frame word durations.
     *
     * The exit overlap is dynamically calculated based on speech speed:
     *  - Fast speech (200ms word): ~60ms overlap
     *  - Slow speech (500ms word): ~150ms overlap (capped at animMs)
     */
    private fun resolveWordByWord(
        words: List<WordState>,
        posMs: Long,
        animMs: Long,
        currentIndex: Int
    ): TimingResult {
        if (currentIndex < 0 || currentIndex >= words.size) {
            // Nothing spoken yet in this segment — no word to show.
            return TimingResult(emptyList(), null, -1, false, 0)
        }

        val visibleWords = mutableListOf<WordState>()

        // Include previous word ONLY if it's still in its exit window so the
        // transition overlaps smoothly instead of hard-cutting.
        if (currentIndex > 0) {
            val prev = words[currentIndex - 1]
            val effectiveOverlap = calculateExitOverlap(prev.endTimeMs - prev.startTimeMs, animMs)
            if (posMs <= prev.endTimeMs + effectiveOverlap) {
                visibleWords.add(prev)
            }
        }

        // The cursor word is THE word on screen. Force its lifecycle to ACTIVE
        // so it pops in and then HOLDS solid — it must never run its exit
        // animation while it is still the displayed word, or it would fade out
        // mid-gap and then pop back solid (EXITING/REMOVED re-render at full
        // alpha).
        val current = words[currentIndex].copy(lifecycle = WordLifecycle.ACTIVE)
        visibleWords.add(current)

        return TimingResult(
            visibleWords = visibleWords,
            activeWord = current,
            activeWordIndex = currentIndex,
            isNewPage = false,
            pageIndex = currentIndex
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
