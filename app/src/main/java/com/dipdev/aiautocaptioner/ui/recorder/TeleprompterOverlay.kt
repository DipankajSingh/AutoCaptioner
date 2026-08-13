package com.dipdev.aiautocaptioner.ui.recorder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.videoeditor.style.PremiumSlider
import compose.icons.FeatherIcons
import compose.icons.feathericons.Maximize2
import compose.icons.feathericons.Pause
import compose.icons.feathericons.Play
import compose.icons.feathericons.X
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun TeleprompterOverlay(
    text: String,
    onTextChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var wpm by remember { mutableFloatStateOf(150f) }
    var mirrorMode by remember { mutableStateOf(false) }
    var fontScale by remember { mutableIntStateOf(1) }
    val fontSize = when (fontScale) { 0 -> 24; 1 -> 32; 2 -> 40; else -> 32 }
    var isFinished by remember { mutableStateOf(false) }
    var showCountdown by remember { mutableStateOf(false) }
    var countdownValue by remember { mutableIntStateOf(3) }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var textFieldValue by remember { mutableStateOf(TextFieldValue(text)) }
    
    // Sync external text changes into our robust internal state
    LaunchedEffect(text) {
        if (text != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(text = text)
        }
    }

    val totalWords = remember(text) {
        if (text.isBlank()) 0 else text.trim().split(Regex("\\s+")).size
    }

    var exactScroll by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying, wpm) {
        if (isPlaying) {
            exactScroll = scrollState.value.toFloat()
            var lastFrameTimeNanos = withFrameNanos { it }
            while (isActive) {
                val frameTimeNanos = withFrameNanos { it }
                val deltaNanos = frameTimeNanos - lastFrameTimeNanos
                lastFrameTimeNanos = frameTimeNanos
                
                if (scrollState.maxValue > 0) {
                    val durationSec = totalWords.toFloat() / (wpm / 60f)
                    if (durationSec > 0) {
                        val pixelsPerSecond = scrollState.maxValue / durationSec
                        val pixelsPerNanos = pixelsPerSecond / 1_000_000_000f
                        
                        exactScroll += (deltaNanos * pixelsPerNanos)
                        val target = exactScroll.toInt().coerceIn(0, scrollState.maxValue)
                        if (target != scrollState.value) {
                            scrollState.scrollTo(target)
                        }
                    }
                }
                if (scrollState.value >= scrollState.maxValue && scrollState.maxValue > 0) {
                    isFinished = true
                    isPlaying = false
                    break
                }
            }
        }
    }

    LaunchedEffect(showCountdown) {
        if (showCountdown) {
            for (i in 3 downTo 1) {
                countdownValue = i
                delay(800.milliseconds)
            }
            showCountdown = false
            isPlaying = true
        }
    }

    val controlsAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 0f else 1f,
        animationSpec = tween(400)
    )

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    
    // Adjusted initial size and position to avoid overlap with right-side controls
    val initialWidth = with(density) { (screenWidthPx.toDp() - 80.dp).coerceAtLeast(240.dp).toPx() }
    var widgetWidthPx by remember { mutableFloatStateOf(initialWidth) }
    var widgetHeightPx by remember { mutableFloatStateOf(with(density) { 400.dp.toPx() }) }
    var offsetX by remember { mutableFloatStateOf(with(density) { 16.dp.toPx() }) }
    var offsetY by remember { mutableFloatStateOf(with(density) { 140.dp.toPx() }) }

    val minWidthPx = with(density) { 220.dp.toPx() }
    val minHeightPx = with(density) { 240.dp.toPx() }

    val scrimColor = Color(0xCC111111) // Deep translucent black for unified container
    val textStyle = TextStyle(
        color = Color.White,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = (fontSize * 1.4).sp,
        textAlign = TextAlign.Center
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(
                    width = with(density) { widgetWidthPx.toDp() },
                    height = with(density) { widgetHeightPx.toDp() }
                )
                .clip(RoundedCornerShape(24.dp))
                .background(scrimColor)
                .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Drag Handle & Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                offsetX = (offsetX + dragAmount.x).coerceIn(0f, screenWidthPx - widgetWidthPx)
                                offsetY = (offsetY + dragAmount.y).coerceIn(0f, screenHeightPx - widgetHeightPx)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.4f))
                    )
                    // Close Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).size(40.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.1f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(FeatherIcons.X, contentDescription = "Close", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(6.dp))
                        }
                    }
                }

                // Main Text Area
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .graphicsLayer { scaleX = if (mirrorMode) -1f else 1f }
                ) {
                    val bionicTransformation = remember { BionicReadingTransformation() }
                    val activeTransformation = if (textFieldValue.text.isBlank()) {
                        VisualTransformation.None
                    } else {
                        bionicTransformation
                    }
                    val currentTextStyle = if (textFieldValue.text.isBlank() && !isPlaying && !showCountdown) {
                        TextStyle(
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.4).sp,
                            textAlign = TextAlign.Center
                        )
                    } else textStyle

                    val localMaxHeight = maxHeight

                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            textFieldValue = newValue
                            if (newValue.text != text) {
                                onTextChanged(newValue.text)
                            }
                        },
                        readOnly = isPlaying,
                        textStyle = currentTextStyle,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        visualTransformation = activeTransformation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = localMaxHeight),
                        decorationBox = { inner ->
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (textFieldValue.text.isEmpty() && !isPlaying && !showCountdown) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Text(
                                            stringResource(R.string.teleprompter_placeholder),
                                            color = Color.White.copy(alpha = 0.4f),
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Light,
                                            textAlign = TextAlign.Center,
                                            lineHeight = (20 * 1.4).sp
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(scrollState)
                                        .padding(
                                            start = 24.dp,
                                            end = 24.dp,
                                            top = localMaxHeight / 2,
                                            bottom = localMaxHeight / 2
                                        )
                                ) {
                                    inner()
                                }
                            }
                        }
                    )

                    // Focal highlight line
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .align(Alignment.Center)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isPlaying) 0.35f else 0.15f))
                    )

                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { isPlaying = false }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.Black.copy(alpha = 0.5f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(FeatherIcons.Pause, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Text(
                                        stringResource(R.string.teleprompter_tap_to_pause),
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    if (isFinished) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    stringResource(R.string.teleprompter_script_complete),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Surface(
                                    onClick = {
                                        isFinished = false
                                        isPlaying = false
                                        coroutineScope.launch {
                                            scrollState.animateScrollTo(0)
                                        }
                                    },
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        stringResource(R.string.teleprompter_back_to_top),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Controls Area
                AnimatedVisibility(visible = controlsAlpha > 0f) {
                    Column(
                        modifier = Modifier
                            .alpha(controlsAlpha)
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Play & Slider Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                onClick = {
                                    if (text.isBlank()) return@Surface
                                    if (isFinished) {
                                        isFinished = false
                                        coroutineScope.launch { scrollState.animateScrollTo(0) }
                                    }
                                    showCountdown = true
                                },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(FeatherIcons.Play, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Speed", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = "${wpm.toInt()} wpm",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                PremiumSlider(
                                    value = wpm,
                                    onValueChange = { wpm = it },
                                    valueRange = 50f..300f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Settings Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val fontLabel = when (fontScale) { 0 -> "S"; 1 -> "M"; 2 -> "L"; else -> "M" }
                            Surface(
                                onClick = { fontScale = (fontScale + 1) % 3 },
                                shape = RoundedCornerShape(50),
                                color = Color.White.copy(alpha = 0.1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("Text Size", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    Text(fontLabel, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Surface(
                                onClick = { mirrorMode = !mirrorMode },
                                shape = RoundedCornerShape(50),
                                color = if (mirrorMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.1f),
                                border = if (mirrorMode) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null
                            ) {
                                Text(
                                    stringResource(R.string.teleprompter_mirror),
                                    color = if (mirrorMode) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Resize Handle at Bottom End (completely isolated)
            Icon(
                imageVector = FeatherIcons.Maximize2,
                contentDescription = "Resize",
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 12.dp, end = 12.dp)
                    .size(18.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            widgetWidthPx = max(minWidthPx, widgetWidthPx + dragAmount.x).coerceAtMost(screenWidthPx - offsetX)
                            widgetHeightPx = max(minHeightPx, widgetHeightPx + dragAmount.y).coerceAtMost(screenHeightPx - offsetY)
                        }
                    }
            )
        }

        if (showCountdown) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                val scale = remember { Animatable(0.5f) }
                val alpha = remember { Animatable(1f) }
                LaunchedEffect(countdownValue) {
                    scale.snapTo(0.5f)
                    alpha.snapTo(1f)
                    launch { scale.animateTo(1.4f, tween(700, easing = FastOutSlowInEasing)) }
                    launch { alpha.animateTo(0f, tween(700, easing = LinearEasing)) }
                }
                Text(
                    text = countdownValue.toString(),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 120.sp),
                    color = Color.White.copy(alpha = alpha.value),
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    }
                )
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
            if (start > lastIndex) builder.append(original.substring(lastIndex, start))

            val boldLen = ceil(word.length / 2.0).toInt()
            builder.withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = Color.White)) {
                append(word.substring(0, boldLen))
            }
            builder.withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = Color.White.copy(alpha = 0.85f))) {
                append(word.substring(boldLen))
            }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < original.length) builder.append(original.substring(lastIndex))
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
