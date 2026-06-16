package com.dipdev.aiautocaptioner.ui.captioneditor

import com.dipdev.aiautocaptioner.data.db.entity.CaptionWordEntity
import com.dipdev.aiautocaptioner.data.db.entity.EmphasisType
import java.util.UUID

object CaptionAlignmentUtils {

    fun alignWords(
        oldWords: List<CaptionWordEntity>,
        newText: String,
        segmentId: String,
        projectId: String,
        segmentStartTimeMs: Long,
        segmentEndTimeMs: Long
    ): List<CaptionWordEntity> {
        val newWordsList = newText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (newWordsList.isEmpty()) {
            return listOf(
                CaptionWordEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    segmentId = segmentId,
                    word = " ",
                    index = 0,
                    startTimeMs = segmentStartTimeMs,
                    endTimeMs = segmentEndTimeMs,
                    confidence = 1.0f,
                    isEmphasized = false,
                    emphasisType = EmphasisType.NONE
                )
            )
        }

        val oldStrings = oldWords.map { it.word }
        val m = oldStrings.size
        val n = newWordsList.size

        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 1..m) {
            for (j in 1..n) {
                if (oldStrings[i - 1] == newWordsList[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1] + 1
                } else {
                    dp[i][j] = maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }

        val mappedIndices = mutableMapOf<Int, Int>()
        var i = m
        var j = n
        while (i > 0 && j > 0) {
            if (oldStrings[i - 1] == newWordsList[j - 1]) {
                mappedIndices[j - 1] = i - 1
                i--
                j--
            } else if (dp[i - 1][j] > dp[i][j - 1]) {
                i--
            } else {
                j--
            }
        }

        val result = mutableListOf<CaptionWordEntity>()

        for (k in 0 until n) {
            val oldIndex = mappedIndices[k]
            if (oldIndex != null) {
                val oldWord = oldWords[oldIndex]
                result.add(
                    oldWord.copy(
                        id = UUID.randomUUID().toString(), // Use new ID since replaceWordsForSegment probably deletes all previous by segmentId
                        word = newWordsList[k],
                        index = k
                    )
                )
            } else {
                result.add(
                    CaptionWordEntity(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        segmentId = segmentId,
                        word = newWordsList[k],
                        index = k,
                        startTimeMs = -1L,
                        endTimeMs = -1L,
                        confidence = 1.0f,
                        isEmphasized = false,
                        emphasisType = EmphasisType.NONE
                    )
                )
            }
        }

        var prevMatchedIndex = -1
        var prevEndTime = segmentStartTimeMs

        for (k in 0 until n) {
            if (mappedIndices.containsKey(k)) {
                val oldIndex = mappedIndices[k]!!
                prevMatchedIndex = k
                prevEndTime = oldWords[oldIndex].endTimeMs
            } else {
                var nextMatchedIndex = -1
                var nextStartTime = segmentEndTimeMs
                for (l in k + 1 until n) {
                    if (mappedIndices.containsKey(l)) {
                        nextMatchedIndex = l
                        nextStartTime = oldWords[mappedIndices[l]!!].startTimeMs
                        break
                    }
                }

                val gapSize = if (nextMatchedIndex == -1) n - k else nextMatchedIndex - k
                val duration = maxOf(0L, nextStartTime - prevEndTime)
                val timePerWord = if (gapSize > 0) duration / gapSize else 0L

                val gapIndex = k - (prevMatchedIndex + 1)
                
                val start = prevEndTime + timePerWord * gapIndex
                val end = start + timePerWord

                result[k] = result[k].copy(
                    startTimeMs = start,
                    endTimeMs = end
                )
            }
        }

        return result
    }
}
