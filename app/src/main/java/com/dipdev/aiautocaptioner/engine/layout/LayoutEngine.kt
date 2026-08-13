package com.dipdev.aiautocaptioner.engine.layout

import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.data.db.entity.DisplayMode
import com.dipdev.aiautocaptioner.data.db.entity.TextAlignment
import com.dipdev.aiautocaptioner.engine.CaptionPaints
import com.dipdev.aiautocaptioner.engine.CaptionUtils
import com.dipdev.aiautocaptioner.engine.timing.WordState

data class WordLayout(
    val word: WordState,
    val displayText: String,
    val width: Float,
    val shader: android.graphics.Shader? = null
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
        val thicknessPx = CaptionPaints.thicknessStrokeWidth(style.textThickness, baseScale)
        val lineH = (fm.bottom - fm.top) * style.lineHeight + thicknessPx
        val spaceW = CaptionPaints.text.measureText(" ") + thicknessPx

        val maxWordsPerLine = if (style.maxWordsPerLine <= 0) 999 else style.maxWordsPerLine

        val maxLines = if (style.displayMode == DisplayMode.KARAOKE_FILL) 999
            else if (style.maxLines <= 0) 999 else style.maxLines

        val padH = style.backgroundPaddingH * baseScale
        val marginH = videoWidth * 0.08f
        val availableWidth = videoWidth - 2f * marginH - 2f * padH

        // Build word layouts
        val wordLayouts = words.map { w ->
            val txt = CaptionUtils.sanitize(w.text, style)
            val wordW = CaptionPaints.text.measureText(txt) + thicknessPx
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
            val maxAllowed = (videoWidth - lineW - marginH).coerceAtLeast(marginH)
            val clampedX = x.coerceIn(marginH, maxAllowed)

            LineLayout(lineWords, clampedX, lineW)
        }

        val totalH = lineLayouts.size * lineH
        val padY = style.backgroundPaddingV * baseScale
        val rawY = (videoHeight * style.positionY) - (totalH + padY * 2f) / 2f
        val maxStart = (videoHeight - totalH - padY * 2f).coerceAtLeast(0f)
        val startY = rawY.coerceIn(0f, maxStart)

        // Pre-calculate shaders to avoid JNI allocation per frame
        val finalLines = if (style.gradientDirection != com.dipdev.aiautocaptioner.data.db.entity.GradientDirection.NONE) {
            var lineY = startY
            lineLayouts.map { line ->
                val lineTop = lineY + fm.top
                val lineBot = lineY + fm.bottom
                var x = if (isRtl) line.startX + line.lineWidth else line.startX
                val wordsWithShader = line.words.map { wl ->
                    if (isRtl) x -= wl.width
                    val shader = when (style.gradientDirection) {
                        com.dipdev.aiautocaptioner.data.db.entity.GradientDirection.LEFT_RIGHT -> android.graphics.LinearGradient(
                            x, 0f, x + wl.width, 0f,
                            style.textColor.toInt(), style.secondaryColor.toInt(),
                            android.graphics.Shader.TileMode.CLAMP
                        )
                        com.dipdev.aiautocaptioner.data.db.entity.GradientDirection.TOP_BOTTOM -> android.graphics.LinearGradient(
                            0f, lineTop, 0f, lineBot,
                            style.textColor.toInt(), style.secondaryColor.toInt(),
                            android.graphics.Shader.TileMode.CLAMP
                        )
                        com.dipdev.aiautocaptioner.data.db.entity.GradientDirection.DIAGONAL -> android.graphics.LinearGradient(
                            x, lineTop, x + wl.width, lineBot,
                            style.textColor.toInt(), style.secondaryColor.toInt(),
                            android.graphics.Shader.TileMode.CLAMP
                        )
                        }
                    if (!isRtl) x += wl.width + spaceW
                    wl.copy(shader = shader)
                }
                lineY += lineH
                line.copy(words = wordsWithShader)
            }
        } else lineLayouts

        return CaptionLayout(
            lines = finalLines,
            totalHeight = totalH,
            startY = startY,
            lineHeight = lineH
        )
    }
}
