package com.dipdev.aiautocaptioner.engine.layout

import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.data.db.entity.TextAlignment
import com.dipdev.aiautocaptioner.engine.CaptionPaints
import com.dipdev.aiautocaptioner.engine.CaptionUtils
import com.dipdev.aiautocaptioner.engine.timing.WordState

/**
 * Layout data for a single word within a line.
 */
data class WordLayout(
    val word: WordState,
    val displayText: String,
    val width: Float
)

/**
 * Layout data for a single line of caption text.
 */
data class LineLayout(
    val words: List<WordLayout>,
    val startX: Float,
    val lineWidth: Float
)

/**
 * Complete layout result for a frame.
 */
data class CaptionLayout(
    val lines: List<LineLayout>,
    val totalHeight: Float,
    val startY: Float,
    val lineHeight: Float
)

/**
 * Handles text measurement, line breaking, and positioning.
 *
 * Extracted from CaptionRenderer to separate layout concerns from drawing.
 * The renderer calls [computeLayout] and then iterates over the result
 * to draw each word — no layout logic in the draw path.
 */
object LayoutEngine {

    /**
     * Compute the complete layout for visible words.
     *
     * @param words       Words to lay out (already filtered by TimingEngine)
     * @param style       Active caption style
     * @param videoWidth  Video canvas width in px
     * @param videoHeight Video canvas height in px
     * @param baseScale   videoHeight / 1920f
     * @param isRtl       Whether text is predominantly RTL
     */
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

        val maxW = if (style.maxWordsPerLine <= 0) 999 else style.maxWordsPerLine
        val maxL = if (style.maxLines <= 0) 999 else style.maxLines

        // Group words into lines
        val lines = words.chunked(maxW).take(maxL)

        val lineLayouts = lines.map { lineWords ->
            val wordLayouts = lineWords.map { w ->
                val txt = CaptionUtils.sanitize(w.text, style)
                val wordW = CaptionPaints.text.measureText(txt)
                WordLayout(w, txt, wordW)
            }

            val lineW = wordLayouts.sumOf { (it.width + spaceW).toDouble() }.toFloat() - spaceW

            val x = when (style.alignment) {
                TextAlignment.CENTER -> (videoWidth - lineW) / 2f
                TextAlignment.START -> if (isRtl) videoWidth * 0.92f - lineW else videoWidth * 0.08f
                TextAlignment.END -> if (isRtl) videoWidth * 0.08f else videoWidth * 0.92f - lineW
            }

            LineLayout(wordLayouts, x, lineW)
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
