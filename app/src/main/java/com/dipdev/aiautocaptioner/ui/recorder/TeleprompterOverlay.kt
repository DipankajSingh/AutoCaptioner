package com.dipdev.aiautocaptioner.ui.recorder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.ui.theme.AccentCyan
import compose.icons.FeatherIcons
import compose.icons.feathericons.Pause
import compose.icons.feathericons.Play
import compose.icons.feathericons.Move
import kotlin.math.roundToInt

@Composable
fun TeleprompterOverlay(
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
            val pixelsPerSecond = (wpm / 150f) * 100f
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
        fontSize = 28.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 40.sp,
        textAlign = TextAlign.Center,
        shadow = Shadow(
            color = Color.Black,
            offset = Offset(2f, 2f),
            blurRadius = 8f
        )
    )

    // Smooth fade for UI elements when playing
    val uiAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 0f else 1f,
        animationSpec = tween(500)
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        val parentWidthPx = constraints.maxWidth.toFloat()
        val parentHeightPx = constraints.maxHeight.toFloat()
        
        var boxSize by remember { mutableStateOf(IntSize.Zero) }
        
        // Start near the top, centered horizontally
        var offsetX by remember { mutableStateOf(0f) }
        var offsetY by remember { mutableStateOf(50f) }
        
        // Auto-center horizontally on first layout
        var hasCentered by remember { mutableStateOf(false) }
        if (!hasCentered && boxSize.width > 0) {
            offsetX = (parentWidthPx - boxSize.width) / 2f
            hasCentered = true
        }

        Column(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .onSizeChanged { boxSize = it }
                .width(320.dp) // Slightly wider for better text flow
                .clip(RoundedCornerShape(24.dp))
                // Background fades completely out when playing to show user's face
                .background(Color(0x00000000).copy(alpha = 0.5f * uiAlpha)) 
                .border(1.dp, Color.White.copy(alpha = 0.15f * uiAlpha), RoundedCornerShape(24.dp))
                .padding(top = 12.dp, bottom = 4.dp, start = 12.dp, end = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag handle and controls (fades out)
            if (uiAlpha > 0f) {
                Column(
                    modifier = Modifier.alpha(uiAlpha).fillMaxWidth()
                ) {
                    // Drag Handle
                    Icon(
                        imageVector = FeatherIcons.Move,
                        contentDescription = "Drag Handle",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(20.dp)
                            .align(Alignment.CenterHorizontally)
                            .pointerInput(parentWidthPx, parentHeightPx, boxSize) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val newX = offsetX + dragAmount.x
                                    val newY = offsetY + dragAmount.y
                                    val maxX = (parentWidthPx - boxSize.width).coerceAtLeast(0f)
                                    val maxY = (parentHeightPx - boxSize.height).coerceAtLeast(0f)
                                    offsetX = newX.coerceIn(0f, maxX)
                                    offsetY = newY.coerceIn(0f, maxY)
                                }
                            }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        IconButton(
                            onClick = { isPlaying = !isPlaying },
                            modifier = Modifier
                                .size(40.dp)
                                .background(AccentCyan.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(FeatherIcons.Play, contentDescription = "Play", tint = AccentCyan)
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
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                        )
                        Text(
                            text = "${wpm.toInt()} WPM", 
                            color = Color.White, 
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // The invisible tap zone to stop playing
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                // Gradient masks for top and bottom to create smooth fade-out for text
                val fadeGradient = Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.15f to Color.Black,
                    0.85f to Color.Black,
                    1.0f to Color.Transparent
                )

                // Unified BasicTextField
                BasicTextField(
                    value = text,
                    onValueChange = onTextChanged,
                    readOnly = isPlaying,
                    textStyle = sharedTextStyle,
                    cursorBrush = SolidColor(AccentCyan),
                    visualTransformation = BionicReadingTransformation(),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(vertical = 100.dp) // Padding for focal point alignment
                )
                
                // Placeholder
                if (text.isEmpty() && !isPlaying) {
                    Text(
                        "Paste your script here...", 
                        color = Color.White.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        style = sharedTextStyle,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                // Focal Highlight Bar (Subtle when playing)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .align(Alignment.Center)
                        .background(AccentCyan.copy(alpha = if (isPlaying) 0.05f else 0.15f))
                        .border(1.dp, AccentCyan.copy(alpha = if (isPlaying) 0.1f else 0.3f), RoundedCornerShape(12.dp))
                )

                // Invisible tap layer to Pause when playing
                if (isPlaying) {
                    Surface(
                        color = Color.Transparent,
                        onClick = { isPlaying = false },
                        modifier = Modifier.fillMaxSize()
                    ) {}
                }
            }
        }
    }
}

class BionicReadingTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        val builder = AnnotatedString.Builder()
        
        val wordRegex = Regex("[a-zA-Z0-9]+")
        var lastIndex = 0
        
        for (match in wordRegex.findAll(original)) {
            val word = match.value
            val start = match.range.first
            
            if (start > lastIndex) {
                builder.append(original.substring(lastIndex, start))
            }
            
            val boldLength = kotlin.math.ceil(word.length / 2.0).toInt()
            
            builder.withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = Color.White)) {
                append(word.substring(0, boldLength))
            }
            builder.withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = Color.White.copy(alpha = 0.85f))) {
                append(word.substring(boldLength))
            }
            
            lastIndex = match.range.last + 1
        }
        
        if (lastIndex < original.length) {
            builder.append(original.substring(lastIndex))
        }
        
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
