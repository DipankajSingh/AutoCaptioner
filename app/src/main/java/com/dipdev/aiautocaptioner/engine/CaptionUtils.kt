package com.dipdev.aiautocaptioner.engine

import com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionWordEntity
import com.dipdev.aiautocaptioner.data.db.entity.EmphasisType

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
        // Fallback: no word timestamps — distribute segment time equally
        val words = seg.text.split(" ").filter { it.isNotBlank() }
        val tpw = if (words.isNotEmpty()) (seg.endTimeMs - seg.startTimeMs) / words.size else 0L
        return words.mapIndexed { i, w ->
            val s = seg.startTimeMs + i * tpw
            val e = s + tpw
            CaptionAnimator.TimedWord(w, s, e, false, false, false, EmphasisType.NONE)
        }
    }

    fun sanitize(text: String, style: CaptionStyleEntity): String =
        if (style.removePunctuation) text.replace(Regex("[,.!?;:]"), "").trimEnd()
        else text
}
