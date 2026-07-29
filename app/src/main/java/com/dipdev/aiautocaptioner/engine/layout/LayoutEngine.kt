package com.dipdev.aiautocaptioner.engine.layout

import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.data.db.entity.TextAlignment
import com.dipdev.aiautocaptioner.engine.CaptionPaints
import com.dipdev.aiautocaptioner.engine.CaptionUtils
import com.dipdev.aiautocaptioner.engine.timing.WordState

data class WordLayout(
    val word: WordState,
    val displayText: String,
    val width: Float
)

data class LineLayout(
    val words: List<WordLayout>,
    val startX: Float,
    val lineWidth: Float
)

data class CaptionLayout(
    val lines: List<LineLayout>,
    val totalHeight: Float,
    val startY: Float,
    val lineHeight: Float
)

object LayoutEngine {

    fun computeLayout(
        words: List<WordState>,
        style: CaptionStyleEntity,
        videoWidth: Int,
        videoHeight: Int,
        baseScale: Float,
        isRtl: Boolean
    ): CaptionLayout {
        if (words.isEmpty()) return CaptionLayout(emptyList(), 0f, 0f, 0f)

        val fm = CaptionPaints.text.fontMetrics
        val lineH = (fm.bottom - fm.top) * style.lineHeight
        val spaceW = CaptionPaints.text.measureText(" ")

        val maxWordsPerLine = if (style.maxWordsPerLine <= 0) 999 else style.maxWordsPerLine
        val maxLines = if (style.maxLines <= 0) 999 else style.maxLines

        val padH = style.backgroundPaddingH * baseScale
        val marginH = videoWidth * 0.08f
        val availableWidth = videoWidth - 2f * marginH - 2f * padH

        // Build word layouts
        val wordLayouts = words.map { w ->
            val txt = CaptionUtils.sanitize(w.text, style)
            val wordW = CaptionPaints.text.measureText(txt)
            WordLayout(w, txt, wordW)
        }

        // Width-aware + count-aware line breaking
        val lines = mutableListOf<List<WordLayout>>()
        var currentLine = mutableListOf<WordLayout>()
        var currentLineWidth = 0f
        for (wl in wordLayouts) {
            if (currentLine.size >= maxWordsPerLine || (currentLine.isNotEmpty() &&
                currentLineWidth + spaceW + wl.width > availableWidth && currentLineWidth > 0f)
            ) {
                lines.add(currentLine)
                currentLine = mutableListOf()
                currentLineWidth = 0f
                if (lines.size >= maxLines) break
            }
            currentLine.add(wl)
            currentLineWidth += (if (currentLine.size > 1) spaceW else 0f) + wl.width
        }
        if (currentLine.isNotEmpty() && lines.size < maxLines) {
            lines.add(currentLine)
        }

        val lineLayouts = lines.map { lineWords ->
            val lineW = lineWords.sumOf { (it.width + spaceW).toDouble() }.toFloat() - spaceW

            val positionCenter = videoWidth * style.positionX
            val x = when (style.alignment) {
                TextAlignment.CENTER -> positionCenter - lineW / 2f
                TextAlignment.START -> if (isRtl) positionCenter - lineW else positionCenter
                TextAlignment.END -> if (isRtl) positionCenter else positionCenter - lineW
            }
            val clampedX = x.coerceIn(marginH, (videoWidth - lineW - marginH).coerceAtLeast(0f))

            LineLayout(lineWords, clampedX, lineW)
        }

        val totalH = lineLayouts.size * lineH
        val padY = style.backgroundPaddingV * baseScale
        val rawY = (videoHeight * style.positionY) - (totalH + padY * 2f) / 2f
        val maxStart = (videoHeight - totalH - padY * 2f).coerceAtLeast(0f)
        val startY = rawY.coerceIn(0f, maxStart)

        return CaptionLayout(
            lines = lineLayouts,
            totalHeight = totalH,
            startY = startY,
            lineHeight = lineH
        )
    }
}
