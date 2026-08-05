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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.dipdev.aiautocaptioner.R
import androidx.compose.material3.MaterialTheme
import compose.icons.FeatherIcons
import compose.icons.feathericons.Pause
import compose.icons.feathericons.Play
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

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
    val fontSize = when (fontScale) { 0 -> 28; 1 -> 36; 2 -> 44; else -> 36 }
    var isFinished by remember { mutableStateOf(false) }
    var showCountdown by remember { mutableStateOf(false) }
    var countdownValue by remember { mutableIntStateOf(3) }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val totalWords = remember(text) {
        if (text.isBlank()) 0 else text.trim().split(Regex("\\s+")).size
    }
    val scrolledFraction by remember {
        derivedStateOf {
            if (scrollState.maxValue > 0) {
                scrollState.value.toFloat() / scrollState.maxValue
            } else 0f
        }
    }
    val wordsLeft = (totalWords * (1f - scrolledFraction)).toInt()
    val minutesLeft = ((wordsLeft.toFloat() / wpm) * 60).toInt().coerceAtLeast(0)

    LaunchedEffect(isPlaying, wpm) {
        if (isPlaying) {
            while (isActive) {
                val pxPerFrame = (wpm / 60f) * 0.55f
                val target = (scrollState.value + pxPerFrame).toInt().coerceIn(0, scrollState.maxValue)
                scrollState.scrollTo(target)
                delay(16)
                if (scrollState.value >= scrollState.maxValue) {
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
                delay(800)
            }
            showCountdown = false
            isPlaying = true
        }
    }

    val controlsAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 0f else 1f,
        animationSpec = tween(400)
    )

    val scrimColor = Color.Black.copy(alpha = 0.82f)
    val textStyle = TextStyle(
        color = Color.White,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = (fontSize * 1.4).sp,
        textAlign = TextAlign.Center
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(scrimColor))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Progress bar
            Box(modifier = Modifier.fillMaxWidth().height(3.dp)) {
                LinearProgressIndicator(
                    progress = { scrolledFraction },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.1f),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }

            // Word/time info
            if (!isFinished && totalWords > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.teleprompter_words_left, wordsLeft),
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp
                    )
                    Text(
                        text = stringResource(R.string.teleprompter_minutes_left, minutesLeft),
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp
                    )
                }
            }

            // Text area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer { scaleX = if (mirrorMode) -1f else 1f }
            ) {
                if (text.isBlank() && !isPlaying && !showCountdown) {
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChanged,
                        textStyle = TextStyle(
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.4).sp,
                            textAlign = TextAlign.Center
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 24.dp, vertical = 120.dp),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                if (text.isEmpty()) {
                                    Text(
                                        stringResource(R.string.teleprompter_placeholder),
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = fontSize.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = (fontSize * 1.4).sp
                                    )
                                }
                                inner()
                            }
                        }
                    )
                } else {
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChanged,
                        readOnly = isPlaying,
                        textStyle = textStyle,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        visualTransformation = BionicReadingTransformation(),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 24.dp, vertical = 120.dp)
                    )
                }

                // Focal bar — top & bottom gradient fade
                val fadeTop = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to scrimColor,
                            1f to Color.Transparent
                        )
                    )
                val fadeBottom = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to scrimColor
                        )
                    )

                Box(fadeTop)
                Box(fadeBottom)

                // Focal highlight line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.Center)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isPlaying) 0.35f else 0.15f))
                )

                // Tap to pause when playing
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
                        Text(
                            stringResource(R.string.teleprompter_tap_to_pause),
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 13.sp
                        )
                    }
                }

                // Finished overlay
                if (isFinished) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(R.string.teleprompter_script_complete),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
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
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    stringResource(R.string.teleprompter_back_to_top),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Controls — visible when not playing
            AnimatedVisibility(visible = controlsAlpha > 0f) {
                Column(
                    modifier = Modifier
                        .alpha(controlsAlpha)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Play + WPM
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Close
                        TextButton(onClick = onDismiss) {
                            Text(
                                "✕",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 18.sp
                            )
                        }

                        // Play/Pause
                        Surface(
                            onClick = {
                                if (text.isBlank()) return@Surface
                                if (isFinished) {
                                    isFinished = false
                                    coroutineScope.launch {
                                        scrollState.animateScrollTo(0)
                                    }
                                }
                                showCountdown = true
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    FeatherIcons.Play,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // WPM slider
                        Slider(
                            value = wpm,
                            onValueChange = { wpm = it },
                            valueRange = 50f..300f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${wpm.toInt()}",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(32.dp),
                            textAlign = TextAlign.End
                        )
                    }

                    // Font + Mirror row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Font size toggle
                        val fontLabel = when (fontScale) { 0 -> "S"; 1 -> "M"; 2 -> "L"; else -> "M" }
                        Surface(
                            onClick = { fontScale = (fontScale + 1) % 3 },
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.08f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Aa", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(fontLabel, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Mirror toggle
                        Surface(
                            onClick = { mirrorMode = !mirrorMode },
                            shape = RoundedCornerShape(8.dp),
                            color = if (mirrorMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f)
                        ) {
                            Text(
                                stringResource(R.string.teleprompter_mirror),
                                color = if (mirrorMode) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Countdown overlay
        if (showCountdown) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                val scale = remember { Animatable(0.5f) }
                val alpha = remember { Animatable(1f) }
                LaunchedEffect(countdownValue) {
                    scale.snapTo(0.5f)
                    alpha.snapTo(1f)
                    launch {
                        scale.animateTo(1.4f, tween(700, easing = FastOutSlowInEasing))
                    }
                    launch {
                        alpha.animateTo(0f, tween(700, easing = LinearEasing))
                    }
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
