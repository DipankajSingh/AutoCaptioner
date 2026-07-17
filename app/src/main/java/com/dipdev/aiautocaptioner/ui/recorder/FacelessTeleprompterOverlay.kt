package com.dipdev.aiautocaptioner.ui.recorder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.ui.theme.AccentCyan
import com.dipdev.aiautocaptioner.ui.theme.DeepSpaceBackground
import compose.icons.FeatherIcons
import compose.icons.feathericons.Play

@Composable
fun FacelessTeleprompterOverlay(
    text: String,
    onTextChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var wpm by remember { mutableFloatStateOf(150f) }
    val scrollState = rememberScrollState()

    // Smooth auto-scroller
    LaunchedEffect(isPlaying, wpm, scrollState.maxValue) {
        if (isPlaying && scrollState.value < scrollState.maxValue) {
            val remainingPixels = (scrollState.maxValue - scrollState.value).toFloat()
            val pixelsPerSecond = (wpm / 150f) * 120f
            val durationMs = (remainingPixels / pixelsPerSecond * 1000).toInt()
            
            if (durationMs > 0) {
                scrollState.animateScrollTo(
                    value = scrollState.maxValue,
                    animationSpec = tween(durationMillis = durationMs, easing = LinearEasing)
                )
            }
        }
    }

    // Heavy shadow for readability when background fades out
    val sharedTextStyle = TextStyle(
        color = Color.White,
        fontSize = 32.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 44.sp,
        textAlign = TextAlign.Center
    )

    // Smooth fade for UI elements when playing
    val uiAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 0f else 1f,
        animationSpec = tween(500)
    )

    // Calculate Karaoke highlight based on scroll position!
    val wordRegex = remember { Regex("[a-zA-Z0-9]+") }
    val wordRanges = remember(text) {
        wordRegex.findAll(text).map { match ->
            WordRange(match.range.first, match.range.last, match.value)
        }.toList()
    }
    val totalWords = wordRanges.size
    val scrollFraction = if (scrollState.maxValue > 0) {
        scrollState.value.toFloat() / scrollState.maxValue
    } else 0f
    
    // As we scroll, active words increase.
    val activeWords = (scrollFraction * totalWords).toInt()
    
    val transformation = remember(activeWords, wordRanges) {
        KaraokeBionicTransformation(activeWords, wordRanges)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSpaceBackground) // Premium Dark Blue
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Controls (fades out)
            if (uiAlpha > 0f) {
                Column(
                    modifier = Modifier.alpha(uiAlpha).fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.height(48.dp)) // Clear top notch area
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        IconButton(
                            onClick = { isPlaying = !isPlaying },
                            modifier = Modifier
                                .size(56.dp) // Larger button
                                .background(AccentCyan.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(FeatherIcons.Play, contentDescription = "Play", tint = AccentCyan, modifier = Modifier.size(28.dp))
                        }
                        Slider(
                            value = wpm,
                            onValueChange = { wpm = it; isPlaying = false },
                            valueRange = 50f..300f,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentCyan,
                                activeTrackColor = AccentCyan.copy(alpha = 0.7f),
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.weight(1f).padding(horizontal = 24.dp)
                        )
                        Text(
                            text = "${wpm.toInt()} WPM", 
                            color = Color.White, 
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Text Engine
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            ) {
                // Unified BasicTextField
                BasicTextField(
                    value = text,
                    onValueChange = onTextChanged,
                    readOnly = isPlaying,
                    textStyle = sharedTextStyle,
                    cursorBrush = SolidColor(AccentCyan),
                    visualTransformation = transformation,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 200.dp) // Massive vertical padding to allow scrolling past center
                )
                
                // Placeholder
                if (text.isEmpty() && !isPlaying) {
                    Text(
                        "Paste your faceless script here...", 
                        color = Color.White.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        style = sharedTextStyle,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp)
                    )
                }

                // Invisible tap layer to Pause when playing
                if (isPlaying) {
                    Surface(
                        color = Color.Transparent,
                        onClick = { isPlaying = false },
                        modifier = Modifier.fillMaxSize()
                    ) {}
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

data class WordRange(val start: Int, val end: Int, val word: String)

class KaraokeBionicTransformation(
    private val activeWordCount: Int,
    private val wordRanges: List<WordRange>
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        val builder = AnnotatedString.Builder()
        
        var lastIndex = 0
        var currentWordIndex = 0
        
        for (range in wordRanges) {
            val start = range.start
            
            if (start > lastIndex) {
                builder.withStyle(SpanStyle(color = Color.White.copy(alpha = 0.3f))) {
                    append(original.substring(lastIndex, start))
                }
            }
            
            val isHighlighted = currentWordIndex < activeWordCount
            val baseColor = if (isHighlighted) AccentCyan else Color.White.copy(alpha = 0.3f)
            
            val word = range.word
            val boldLength = kotlin.math.ceil(word.length / 2.0).toInt()
            
            builder.withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = baseColor)) {
                append(word.substring(0, boldLength))
            }
            val normalAlpha = if (isHighlighted) 0.85f else 0.3f
            builder.withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = baseColor.copy(alpha = normalAlpha))) {
                append(word.substring(boldLength))
            }
            
            lastIndex = range.end + 1
            currentWordIndex++
        }
        
        if (lastIndex < original.length) {
            builder.withStyle(SpanStyle(color = Color.White.copy(alpha = 0.3f))) {
                append(original.substring(lastIndex))
            }
        }
        
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
