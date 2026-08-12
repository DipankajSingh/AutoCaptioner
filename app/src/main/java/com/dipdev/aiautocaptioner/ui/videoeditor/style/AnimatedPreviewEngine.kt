package com.dipdev.aiautocaptioner.ui.videoeditor.style

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.data.db.entity.AnimationType
import com.dipdev.aiautocaptioner.data.db.entity.BackgroundType
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.data.db.entity.DisplayMode
import com.dipdev.aiautocaptioner.data.db.entity.KaraokeHighlightMode
import com.dipdev.aiautocaptioner.data.db.entity.TextTransform
import com.dipdev.aiautocaptioner.engine.CaptionPaints

@Composable
fun AnimatedCaptionPreview(
    style: CaptionStyleEntity,
    modifier: Modifier = Modifier,
    previewWords: List<String> = listOf("Master", "the", "game")
) {
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()

    // 1. Text Transformation
    val transformedWords = remember(previewWords, style.textTransform) {
        previewWords.map { word ->
            when (style.textTransform) {
                TextTransform.UPPERCASE -> word.uppercase()
                TextTransform.LOWERCASE -> word.lowercase()
                TextTransform.TITLE_CASE -> word.replaceFirstChar { it.titlecase() }
                TextTransform.NONE -> word
                else -> word // catch SENTENCE_CASE or any other
            }
        }
    }

    // Animation Loop: 0f to 1f over 2 seconds
    val infiniteTransition = rememberInfiniteTransition()
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    // Fonts & Styles
    val fontFamily = remember(style.fontFamily, style.fontWeight, style.isItalic) {
        val androidTypeface = CaptionPaints.loadTypeface(
            context,
            style.fontFamily,
            style.fontWeight,
            style.isItalic
        )
        FontFamily(Typeface(androidTypeface))
    }

    // Base Style
    val baseFontSize = style.fontSize.sp
    val textStyle = remember(fontFamily, baseFontSize) {
        TextStyle(fontFamily = fontFamily, fontSize = baseFontSize)
    }
    val fillStyle = textStyle.copy(
        color = Color(style.textColor),
        shadow = if (style.shadowRadius > 0f) {
            androidx.compose.ui.graphics.Shadow(
                color = Color(style.shadowColor),
                offset = Offset(style.shadowOffsetX, style.shadowOffsetY),
                blurRadius = style.shadowRadius
            )
        } else null
    )
    val outlineStyle = textStyle.copy(
        color = Color(style.outlineColor),
        drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(
            width = style.outlineWidth, // Use exact outline width from style
            join = androidx.compose.ui.graphics.StrokeJoin.Round
        )
    )
    val spaceWidth = remember(textStyle) { textMeasurer.measure(" ", style = textStyle).size.width.toFloat() }

    // Pre-measure all words
    val wordMeasurements = remember(transformedWords, fillStyle) {
        transformedWords.map { word -> textMeasurer.measure(word, style = fillStyle) }
    }
    val wordMeasurementsOutline = remember(transformedWords, outlineStyle) {
        transformedWords.map { word -> textMeasurer.measure(word, style = outlineStyle) }
    }

    val totalTextWidth = wordMeasurements.sumOf { it.size.width } + (spaceWidth * (transformedWords.size - 1))
    val maxTextHeight = wordMeasurements.maxOfOrNull { it.size.height }?.toFloat() ?: 0f

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val startX = (canvasWidth - totalTextWidth) / 2f
        val startY = (canvasHeight - maxTextHeight) / 2f

        // Fit to width/height to perfectly center and scale in the chip
        val paddingX = 48f
        val paddingY = 24f
        val scaleX = if (totalTextWidth > 0f) (canvasWidth - paddingX) / totalTextWidth else 1f
        val scaleY = if (maxTextHeight > 0f) (canvasHeight - paddingY) / maxTextHeight else 1f
        val fitScale = minOf(scaleX, scaleY)

        // Determine active word index based on progress
        // Split progress into chunks. e.g. 3 words = 3 chunks.
        // Wait, for Typewriter/Phrase, all might be active.
        val wordCount = transformedWords.size
        val chunk = 1f / wordCount
        val activeWordIndex = (progress / chunk).toInt().coerceIn(0, wordCount - 1)
        
        // Progress within the active word's time chunk (0f to 1f)
        val wordProgress = ((progress - (activeWordIndex * chunk)) / chunk).coerceIn(0f, 1f)

        // Pre-calculate X offsets for each word
        var currentX = startX
        val wordPositions = wordMeasurements.map { meas ->
            val pos = currentX
            currentX += meas.size.width + spaceWidth
            pos
        }

        withTransform({
            scale(fitScale, fitScale, pivot = Offset(canvasWidth / 2f, canvasHeight / 2f))
        }) {
            // Draw Global Background if PHRASE / BOX style
            val hasGlobalBg = style.displayMode == DisplayMode.PHRASE || style.displayMode == DisplayMode.TYPEWRITER
            val isBoxOrPill = style.backgroundType == BackgroundType.BOX || style.backgroundType == BackgroundType.PILL
            if (hasGlobalBg && isBoxOrPill) {
                val padH = style.backgroundPaddingH
                val padV = style.backgroundPaddingV
                val cornerRadius = if (style.backgroundType == BackgroundType.PILL) maxTextHeight else style.backgroundCornerRadius
                drawRoundRect(
                    color = Color(style.backgroundColor).copy(alpha = style.backgroundOpacity),
                    topLeft = Offset(startX - padH, startY - padV),
                    size = Size(totalTextWidth + (padH * 2), maxTextHeight + (padV * 2)),
                    cornerRadius = CornerRadius(cornerRadius)
                )
            }

        for (i in 0 until wordCount) {
            val wordW = wordMeasurements[i].size.width.toFloat()
            val isWordByWord = style.displayMode == DisplayMode.WORD_BY_WORD
            val wordX = if (isWordByWord) (canvasWidth - wordW) / 2f else wordPositions[i]
            
            val isActive = (i == activeWordIndex)
            val isPast = (i < activeWordIndex)
            val isFuture = (i > activeWordIndex)

            // Filtering based on DisplayMode
            val shouldDraw = when (style.displayMode) {
                DisplayMode.WORD_BY_WORD -> isActive // Only show active word!
                DisplayMode.TYPEWRITER -> isActive || isPast
                else -> true // PHRASE, LINE_HIGHLIGHT, KARAOKE_FILL draw all words
            }

            if (!shouldDraw) continue

            // Animations: Scale Pop
            val isScalePop = style.wordEnterAnimation == AnimationType.SCALE_POP
            val scale = if (isActive && isScalePop) {
                // simple pop curve
                if (wordProgress < 0.2f) 0.8f + (wordProgress / 0.2f) * 0.2f else 1.0f
            } else 1.0f

            withTransform({
                scale(scale, scale, pivot = Offset(wordX + wordW / 2f, startY + maxTextHeight / 2f))
            }) {
                
                // 1. Word Backgrounds (e.g. Viral Pill)
                if (style.displayMode == DisplayMode.LINE_HIGHLIGHT && style.karaokeHighlightMode == KaraokeHighlightMode.BACKGROUND_HIGHLIGHT) {
                    if (isActive) {
                        val padH = style.backgroundPaddingH
                        val padV = style.backgroundPaddingV
                        val corner = style.activeWordCornerRadius
                        drawRoundRect(
                            color = Color(style.activeWordBgColor),
                            topLeft = Offset(wordX - padH, startY - padV),
                            size = Size(wordW + (padH * 2), maxTextHeight + (padV * 2)),
                            cornerRadius = CornerRadius(corner)
                        )
                    }
                }

                // 2. Determine Text Colors based on Karaoke Mode
                val isKaraokeFill = (style.displayMode == DisplayMode.KARAOKE_FILL) && (style.karaokeHighlightMode == KaraokeHighlightMode.FILL_LEFT_RIGHT)
                val isViralPill = (style.displayMode == DisplayMode.LINE_HIGHLIGHT) && (style.karaokeHighlightMode == KaraokeHighlightMode.BACKGROUND_HIGHLIGHT)

                var finalFillColor = Color(style.textColor)
                if (isKaraokeFill && (isActive || isPast)) {
                    finalFillColor = Color(style.highlightColor)
                }
                if (isViralPill && isActive) {
                    finalFillColor = Color(style.activeWordTextColor)
                }

                // Determine Alpha based on wordEnterAnimation/wordExitAnimation
                var alpha = 1f
                if (isActive && isWordByWord) {
                    val isFadeEnter = style.wordEnterAnimation == AnimationType.FADE
                    val isFadeExit = style.wordExitAnimation == AnimationType.FADE
                    if (isFadeEnter && wordProgress < 0.2f) {
                        alpha = wordProgress / 0.2f
                    } else if (isFadeExit && wordProgress > 0.8f) {
                        alpha = 1f - ((wordProgress - 0.8f) / 0.2f)
                    }
                }
                
                // 3. Draw Outline
                if (style.outlineWidth > 0f) {
                    val currentOutlineStyle = outlineStyle.copy(
                        color = if (isViralPill && isActive) Color.Transparent else Color(style.outlineColor)
                    )
                    val outlineMeas = textMeasurer.measure(transformedWords[i], style = currentOutlineStyle)
                    drawText(textLayoutResult = outlineMeas, topLeft = Offset(wordX, startY), alpha = alpha)
                }

                // 4. Draw Fill
                val currentFillStyle = fillStyle.copy(color = finalFillColor)
                val fillMeas = textMeasurer.measure(transformedWords[i], style = currentFillStyle)
                
                // Typewriter effect mask
                if (style.displayMode == DisplayMode.TYPEWRITER && isActive) {
                    val charCount = transformedWords[i].length
                    val charsToDraw = (charCount * wordProgress).toInt().coerceIn(0, charCount)
                    if (charsToDraw > 0) {
                        val twMeas = textMeasurer.measure(transformedWords[i].substring(0, charsToDraw), style = currentFillStyle)
                        drawText(textLayoutResult = twMeas, topLeft = Offset(wordX, startY), alpha = alpha)
                    }
                } else {
                    drawText(textLayoutResult = fillMeas, topLeft = Offset(wordX, startY), alpha = alpha)
                }
            }
        }
    }
    }
}
