package com.dipdev.aiautocaptioner.engine

import android.text.TextDirectionHeuristics
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.data.db.entity.TextTransform

object CaptionUtils {

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
     * This drives RTL word-order and x-advance direction in layout.
     */
    fun isRtl(text: String): Boolean {
        if (text.isBlank()) return false
        return TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(text, 0, text.length)
    }
}
