package com.dipdev.aiautocaptioner.engine

import android.text.TextDirectionHeuristics
import com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionWordEntity
import com.dipdev.aiautocaptioner.data.db.entity.EmphasisType
import com.dipdev.aiautocaptioner.data.db.entity.TextTransform

object CaptionUtils {

    fun buildTimedWords(
        seg: CaptionSegmentEntity,
        rawWords: List<CaptionWordEntity>?
    ): List<CaptionAnimator.TimedWord> {
        if (!rawWords.isNullOrEmpty()) {
            return rawWords.map { w ->
                CaptionAnimator.TimedWord(
                    text         = w.word,
                    startTimeMs  = w.startTimeMs,
                    endTimeMs    = w.endTimeMs,
                    isActive     = false,
                    isPast       = false,
                    isEmphasized = w.isEmphasized,
                    emphasisType = w.emphasisType
                )
            }
        }
        // Fallback: no word timestamps — distribute segment time equally.
        // Use a Unicode-aware whitespace regex so non-breaking spaces and
        // zero-width spaces (common in Arabic/Thai output) don't create empty tokens.
        val words = seg.text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val tpw = if (words.isNotEmpty()) (seg.endTimeMs - seg.startTimeMs) / words.size else 0L
        return words.mapIndexed { i, w ->
            val s = seg.startTimeMs + i * tpw
            val e = s + tpw
            CaptionAnimator.TimedWord(w, s, e, false, false, false, EmphasisType.NONE)
        }
    }

    /**
     * Strip punctuation from [text] before rendering.
     *
     * Uses the Unicode `\p{P}` (Punctuation) category so that non-ASCII
     * punctuation is also removed:
     *   - Arabic:   ، ؟ ؛ .
     *   - Chinese:  ，。！？；：
     *   - Hindi:    । ॥
     *   - Japanese: 。、！？
     *   - Greek, Hebrew, Thai, etc.
     */
    fun sanitize(text: String, style: CaptionStyleEntity): String {
        var result = if (style.removePunctuation) text.replace(Regex("\\p{P}"), "").trimEnd() else text
        result = when (style.textTransform) {
            TextTransform.UPPERCASE -> result.uppercase()
            TextTransform.LOWERCASE -> result.lowercase()
            TextTransform.TITLE_CASE -> result.split(" ").joinToString(" ") {
                it.replaceFirstChar { c -> c.uppercaseChar() }
            }
            TextTransform.SENTENCE_CASE -> result.replaceFirstChar { it.uppercaseChar() }
            TextTransform.NONE -> result
        }
        return result
    }

    /**
     * Returns true if [text] is predominantly right-to-left (Arabic, Hebrew,
     * Syriac, Thaana, etc.) by inspecting the first strongly-directional
     * character using Android's built-in heuristic (API 17+).
     *
     * This drives RTL word-order and x-advance direction in [CaptionRenderer].
     */
    fun isRtl(text: String): Boolean {
        if (text.isBlank()) return false
        return TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(text, 0, text.length)
    }
}
