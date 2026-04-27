package com.dipdev.autocaptioner.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.dipdev.autocaptioner.data.db.entity.*

object CaptionRenderer {

    fun draw(
        canvas: Canvas,
        currentPositionMs: Long,
        videoWidth: Int,
        videoHeight: Int,
        style: CaptionStyleEntity,
        segments: List<CaptionSegmentEntity>,
        wordsMap: Map<String, List<CaptionWordEntity>>
    ) {
        // 1. Find active segment
        val activeSegment = segments.find { currentPositionMs in it.startTimeMs..it.endTimeMs } ?: return
        
        // 2. Extract words
        val rawWords = wordsMap[activeSegment.id]
        
        // 3. Fallback if Whisper hasn't provided word timestamps (just chunk the text)
        val wordsToRender: List<RenderWord> = if (rawWords.isNullOrEmpty()) {
            activeSegment.text.split(" ").mapIndexed { index, s ->
                RenderWord(
                    text = s,
                    isActive = true, // highlight entire block if no precise timestamps available
                    isPast = false
                )
            }
        } else {
            rawWords.map { w ->
                RenderWord(
                    text = w.word,
                    isActive = currentPositionMs in w.startTimeMs..w.endTimeMs,
                    isPast = currentPositionMs > w.endTimeMs
                )
            }
        }
        
        if (wordsToRender.isEmpty()) return
        
        // 4. Line Wrapping (maxWordsPerLine) and Limits (maxLines)
        val maxWords = if (style.maxWordsPerLine <= 0) 999 else style.maxWordsPerLine
        val maxLinesCount = if (style.maxLines <= 0) 999 else style.maxLines
        val lines = wordsToRender.chunked(maxWords).take(maxLinesCount)
        
        // Ensure anything drawn strictly stays within video bounds without overflow
        canvas.clipRect(0f, 0f, videoWidth.toFloat(), videoHeight.toFloat())
        
        // 5. Paint Setup
        // Convert scalable SP to literal relative pixels 
        // 48 SP is standard for 1920 height vertical video.
        val baseScale = videoHeight / 1920f
        val textSizePx = style.fontSize * baseScale
        
        val tsTypeface = Typeface.create(
            if (style.fontFamily == "System") Typeface.DEFAULT else Typeface.create(style.fontFamily, Typeface.NORMAL),
            if (style.fontWeight > 600) Typeface.BOLD else Typeface.NORMAL
        )
        
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx
            typeface = tsTypeface
            color = style.textColor.toInt()
            textAlign = Paint.Align.LEFT
        }
        
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx
            typeface = tsTypeface
            color = style.outlineColor.toInt()
            this.style = Paint.Style.STROKE
            strokeWidth = style.outlineWidth * baseScale
            strokeJoin = Paint.Join.ROUND
            textAlign = Paint.Align.LEFT
        }
        
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.backgroundColor.toInt()
            alpha = (style.backgroundOpacity * 255).toInt()
            this.style = Paint.Style.FILL
        }

        // Metrics
        val fontMetrics = textPaint.fontMetrics
        val lineHeight = fontMetrics.bottom - fontMetrics.top
        val paddingX = 24f * baseScale
        val paddingY = 16f * baseScale
        
        val totalHeight = (lines.size * lineHeight) + (paddingY * 2)
        
        // Calculate Y position based on 0f..1f (top to bottom)
        var startY = (videoHeight * style.positionY) - (totalHeight / 2f)
        
        // 6. Draw loop
        lines.forEach { lineWords ->
            
            // Measure this line's total width to apply Alignment
            var lineWidth = 0f
            lineWords.forEach { w ->
                val wWidth = textPaint.measureText(w.text + " ")
                lineWidth += wWidth
            }
            // remove trailing space width
            lineWidth -= textPaint.measureText(" ")
            
            var startX = when (style.alignment) {
                TextAlignment.CENTER -> (videoWidth - lineWidth) / 2f
                TextAlignment.START -> (videoWidth * 0.1f)
                TextAlignment.END -> (videoWidth * 0.9f) - lineWidth
            }
            
            // Calculate Background Rect for the line
            val lineTop = startY + fontMetrics.top
            val lineBottom = startY + fontMetrics.bottom
            val lineRect = RectF(
                startX - paddingX,
                lineTop - paddingY,
                startX + lineWidth + paddingX,
                lineBottom + paddingY
            )
            
            // Draw Backgrounds
            if (style.backgroundOpacity > 0f) {
                when (style.backgroundType) {
                    BackgroundType.NONE -> {}
                    BackgroundType.BOX -> canvas.drawRect(lineRect, bgPaint)
                    BackgroundType.PILL -> {
                        val radius = lineRect.height() / 2f
                        canvas.drawRoundRect(lineRect, radius, radius, bgPaint)
                    }
                    BackgroundType.FULL_LINE -> {
                        canvas.drawRect(
                            0f,
                            lineRect.top,
                            videoWidth.toFloat(),
                            lineRect.bottom,
                            bgPaint
                        )
                    }
                    BackgroundType.PER_WORD -> {
                        var wordX = startX
                        val spaceWidth = textPaint.measureText(" ")
                        val radius = lineRect.height() / 4f
                        lineWords.forEach { w ->
                            val drawBoxWidth = textPaint.measureText(w.text)
                            val wordRect = RectF(
                                wordX - (paddingX / 2f),
                                lineTop - paddingY,
                                wordX + drawBoxWidth + (paddingX / 2f),
                                lineBottom + paddingY
                            )
                            canvas.drawRoundRect(wordRect, radius, radius, bgPaint)
                            wordX += drawBoxWidth + spaceWidth
                        }
                    }
                }
            }
            
            // Draw text
            lineWords.forEach { w ->
                val baseText = w.text
                val drawColor = if (w.isActive && style.displayMode != DisplayMode.PHRASE) {
                    style.highlightColor.toInt()
                } else {
                    style.textColor.toInt()
                }
                
                // TODO: Apply complex word bounce/scale animations using Canvas matrices here
                
                // Outline pass
                if (style.outlineWidth > 0f) {
                    canvas.drawText(baseText, startX, startY, outlinePaint)
                }
                
                // Fill pass
                textPaint.color = drawColor
                canvas.drawText(baseText, startX, startY, textPaint)
                
                startX += textPaint.measureText("$baseText ")
            }
            
            startY += lineHeight
        }
    }
}

data class RenderWord(
    val text: String,
    val isActive: Boolean,
    val isPast: Boolean
)
