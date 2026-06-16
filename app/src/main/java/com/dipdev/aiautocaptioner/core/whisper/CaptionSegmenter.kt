package com.dipdev.aiautocaptioner.core.whisper

import com.dipdev.aiautocaptioner.data.repository.TranscriptionSegment
import com.dipdev.aiautocaptioner.data.repository.TranscriptionWord

object CaptionSegmenter {

    fun buildFinalSegments(allWords: List<WhisperEngine.WordTimestamp>): List<TranscriptionSegment> {
        val mergedTimestamps = mergeContractions(allWords)

        // Keep punctuation for the smart grouping pass
        val rawTimestamps = mergedTimestamps.map { w ->
            w.copy(word = w.word.trim())
        }.filter { it.word.isNotBlank() }

        val initialSegments = groupWordsIntoSegments(rawTimestamps)

        // Apply smart merge and split
        val smartlyGrouped = mergeAndSplitSegments(initialSegments)

        // Now strip the punctuation as originally intended
        val finalSegments = smartlyGrouped.mapNotNull { seg ->
            val cleanedWords = seg.words.map { w ->
                w.copy(word = w.word.trimEnd(',', '.', '!', '?', ';', ':'))
            }.filter { it.word.isNotBlank() }

            if (cleanedWords.isNotEmpty()) {
                seg.copy(
                    startTimeMs = cleanedWords.first().startTimeMs,
                    endTimeMs = cleanedWords.last().endTimeMs,
                    words = cleanedWords
                )
            } else {
                null
            }
        }
        return finalSegments
    }

    private fun groupWordsIntoSegments(words: List<WhisperEngine.WordTimestamp>): List<TranscriptionSegment> {
        if (words.isEmpty()) return emptyList()
        val segments = mutableListOf<TranscriptionSegment>()
        var currentWords = mutableListOf<WhisperEngine.WordTimestamp>()

        for (i in words.indices) {
            val word = words[i]
            currentWords.add(word)
            val isLastWord = i == words.size - 1
            val nextWord = if (!isLastWord) words[i + 1] else null
            val gapToNext = if (nextWord != null) nextWord.startTimeMs - word.endTimeMs else Long.MAX_VALUE
            val shouldSplit = gapToNext > 1000 || currentWords.size >= 8
            if (shouldSplit && currentWords.isNotEmpty()) {
                segments.add(
                    TranscriptionSegment(
                        startTimeMs = currentWords.first().startTimeMs,
                        endTimeMs = currentWords.last().endTimeMs,
                        words = currentWords.map { w ->
                            TranscriptionWord(
                                word = w.word,
                                startTimeMs = w.startTimeMs,
                                endTimeMs = w.endTimeMs,
                                confidence = w.confidence
                            )
                        }
                    )
                )
                currentWords = mutableListOf()
            }
        }
        return segments
    }

    /**
     * Merges split contractions: "it" + "'s" → "it's"
     */
    private fun mergeContractions(words: List<WhisperEngine.WordTimestamp>): List<WhisperEngine.WordTimestamp> =
        words.fold(mutableListOf()) { acc, word ->
            val trimmed = word.word.trim()
            if (trimmed.startsWith("'") && acc.isNotEmpty()) {
                val prev = acc.removeAt(acc.lastIndex)
                acc.add(prev.copy(word = prev.word.trimEnd() + trimmed, endTimeMs = word.endTimeMs))
            } else {
                acc.add(word)
            }
            acc
        }

    private fun mergeAndSplitSegments(rawSegments: List<TranscriptionSegment>): List<TranscriptionSegment> {
        if (rawSegments.isEmpty()) return emptyList()

        // 1. Merge segments < 3 words
        val merged = mutableListOf<TranscriptionSegment>()
        var i = 0
        while (i < rawSegments.size) {
            var current = rawSegments[i]
            // Merge forward if < 3 words and not the last one
            while (current.words.size < 3 && i < rawSegments.size - 1) {
                i++
                val next = rawSegments[i]
                current = TranscriptionSegment(
                    startTimeMs = minOf(current.startTimeMs, next.startTimeMs),
                    endTimeMs = maxOf(current.endTimeMs, next.endTimeMs),
                    words = current.words + next.words
                )
            }
            merged.add(current)
            i++
        }

        // If the very last segment ended up with < 3 words, try merging it backwards
        if (merged.size > 1 && merged.last().words.size < 3) {
            val last = merged.removeAt(merged.lastIndex)
            val prev = merged.removeAt(merged.lastIndex)
            val combined = TranscriptionSegment(
                startTimeMs = minOf(prev.startTimeMs, last.startTimeMs),
                endTimeMs = maxOf(prev.endTimeMs, last.endTimeMs),
                words = prev.words + last.words
            )
            merged.add(combined)
        }

        // 2. Split segments > 10 words
        val finalSegments = mutableListOf<TranscriptionSegment>()
        for (seg in merged) {
            var remainingSeg = seg
            while (remainingSeg.words.size > 10) {
                val words = remainingSeg.words
                val mid = words.size / 2
                var splitIndex = -1
                var minDistance = Int.MAX_VALUE

                // Try to find a sentence boundary near the middle
                for (j in 0 until words.size - 1) {
                    val wordText = words[j].word
                    if (wordText.matches(Regex(".*[.!?][\"')\\]]*$"))) {
                        val distance = kotlin.math.abs(j - mid)
                        if (distance < minDistance) {
                            minDistance = distance
                            splitIndex = j + 1 // split AFTER this word
                        }
                    }
                }

                // If no punctuation found, or it's too far from the middle, split at exact midpoint
                if (splitIndex == -1 || minDistance > words.size / 3) {
                    splitIndex = mid
                }

                // Safety bounds
                if (splitIndex <= 0 || splitIndex >= words.size) {
                    splitIndex = mid
                }

                val leftWords = words.subList(0, splitIndex)
                val rightWords = words.subList(splitIndex, words.size)

                finalSegments.add(
                    TranscriptionSegment(
                        startTimeMs = leftWords.first().startTimeMs,
                        endTimeMs = leftWords.last().endTimeMs,
                        words = leftWords
                    )
                )

                remainingSeg = TranscriptionSegment(
                    startTimeMs = rightWords.first().startTimeMs,
                    endTimeMs = rightWords.last().endTimeMs,
                    words = rightWords
                )
            }
            finalSegments.add(remainingSeg)
        }

        return finalSegments
    }
}
