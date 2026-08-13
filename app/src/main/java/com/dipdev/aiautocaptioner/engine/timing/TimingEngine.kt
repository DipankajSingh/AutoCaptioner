package com.dipdev.aiautocaptioner.engine.timing

import com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionWordEntity
import com.dipdev.aiautocaptioner.data.db.entity.DisplayMode
import com.dipdev.aiautocaptioner.data.db.entity.EmphasisType


data class WordState(
    val text: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val lifecycle: WordLifecycle,
    val isEmphasized: Boolean = false,
    val emphasisType: EmphasisType = EmphasisType.NONE,
    val index: Int = 0
) {
    val isActive get() = lifecycle == WordLifecycle.ACTIVE
}


data class TimingResult(
    val visibleWords: List<WordState>,
    val activeWord: WordState?,
    val activeWordIndex: Int,
    val isNewPage: Boolean,
    val pageIndex: Int
)


object TimingEngine {

    fun findActiveSegment(
        segments: List<CaptionSegmentEntity>,
        posMs: Long
    ): CaptionSegmentEntity? {
        if (segments.isEmpty()) return null
        
        var low = 0
        var high = segments.size - 1
        var candidateIdx = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (segments[mid].startTimeMs <= posMs) {
                candidateIdx = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        if (candidateIdx == -1) return null

        for (i in candidateIdx downTo 0) {
            val segment = segments[i]
            if (posMs <= segment.endTimeMs && posMs >= segment.startTimeMs) {
                return segment
            }
        }

        return null
    }

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
        val words = segment.text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val tpw = if (words.isNotEmpty()) (segment.endTimeMs - segment.startTimeMs) / words.size else 0L
        return words.mapIndexed { i, w ->
            val s = segment.startTimeMs + i * tpw
            val e = s + tpw
            WordState(w, s, e, WordLifecycle.UPCOMING, false, EmphasisType.NONE, i)
        }
    }

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

        val updated = words.map { word ->
            val lifecycle = computeLifecycle(word, posMs, animMs, displayMode)
            word.copy(lifecycle = lifecycle)
        }

        return when (displayMode) {
            DisplayMode.PHRASE -> resolvePhrase(updated, posMs, animMs)
            DisplayMode.LINE_HIGHLIGHT -> resolvePaged(updated,
                maxWordsPerLine, maxLines, previousPageIndex)
            DisplayMode.KARAOKE_FILL -> resolveKaraokeFill(updated, posMs, previousPageIndex)
            DisplayMode.WORD_BY_WORD -> resolveWordByWord(updated, posMs, animMs, wordCursor)
            DisplayMode.TYPEWRITER -> resolveTypewriter(updated, posMs, animMs, maxWordsPerLine, maxLines, previousPageIndex)
        }
    }

    private fun computeLifecycle(
        word: WordState,
        posMs: Long,
        animMs: Long,
        displayMode: DisplayMode
    ): WordLifecycle {
        val isBeforeStart = posMs < word.startTimeMs
        val isActive = posMs in word.startTimeMs..word.endTimeMs

        return when {
            isActive -> WordLifecycle.ACTIVE
            isBeforeStart -> {
                val timeUntilStart = word.startTimeMs - posMs
                if (timeUntilStart <= animMs) WordLifecycle.ENTERING else WordLifecycle.UPCOMING
            }

            else -> {
                if (displayMode == DisplayMode.WORD_BY_WORD) {
                    val effectiveOverlap =
                        calculateExitOverlap(word.endTimeMs - word.startTimeMs, animMs)
                    if (posMs <= word.endTimeMs + effectiveOverlap) {
                        WordLifecycle.EXITING
                    } else {
                        WordLifecycle.REMOVED
                    }
                } else {
                    WordLifecycle.REMOVED
                }
            }
        }
    }


    internal fun calculateExitOverlap(wordDurationMs: Long, animMs: Long): Long {
        val maxOverlap = (wordDurationMs * 0.3f).toLong().coerceAtMost(animMs)
        return maxOverlap.coerceAtLeast(30L)
    }



    private fun resolvePhrase(
        words: List<WordState>,
        posMs: Long,
        animMs: Long
    ): TimingResult {
        val activeIdx = words.indexOfFirst { it.lifecycle == WordLifecycle.ACTIVE }
        val activeWord = if (activeIdx >= 0) words[activeIdx] else null

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

    private fun resolvePaged(
        words: List<WordState>,
        maxWordsPerLine: Int,
        maxLines: Int,
        previousPageIndex: Int
    ): TimingResult {
        val wordsPerPage = (maxWordsPerLine.coerceAtLeast(1) * maxLines.coerceAtLeast(1)).coerceAtLeast(1)

        val trueActiveIdx = words.indexOfFirst { it.lifecycle == WordLifecycle.ACTIVE }
        val trueActiveWord = if (trueActiveIdx >= 0) words[trueActiveIdx] else null

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
            activeWord = trueActiveWord,
            activeWordIndex = pageAnchorIdx,
            isNewPage = pageIndex != previousPageIndex,
            pageIndex = pageIndex
        )
    }

    private fun resolveKaraokeFill(
        words: List<WordState>,
        posMs: Long,
        previousPageIndex: Int
    ): TimingResult {

        val activeIdx = words.indexOfFirst { it.lifecycle == WordLifecycle.ACTIVE }
        val lastPastIdx = words.indexOfLast { posMs > it.endTimeMs }
        val anchorIdx = if (activeIdx >= 0) activeIdx else lastPastIdx

        return TimingResult(
            visibleWords = words,
            activeWord = if (activeIdx >= 0) words[activeIdx] else null,
            activeWordIndex = anchorIdx,

            isNewPage = previousPageIndex != 0,
            pageIndex = 0
        )
    }


    private fun resolveWordByWord(
        words: List<WordState>,
        posMs: Long,
        animMs: Long,
        currentIndex: Int
    ): TimingResult {
        if (currentIndex < 0 || currentIndex >= words.size) {
            return TimingResult(emptyList(), null, -1, false, 0)
        }

        val visibleWords = mutableListOf<WordState>()


        if (currentIndex > 0) {
            val prev = words[currentIndex - 1]
            val effectiveOverlap = calculateExitOverlap(prev.endTimeMs - prev.startTimeMs, animMs)
            if (posMs <= prev.endTimeMs + effectiveOverlap) {
                visibleWords.add(prev)
            }
        }

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
