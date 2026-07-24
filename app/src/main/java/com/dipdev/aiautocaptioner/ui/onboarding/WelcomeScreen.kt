package com.dipdev.aiautocaptioner.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.AppLinks
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.components.ShimmerBrandText
import com.dipdev.aiautocaptioner.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectTapGestures

private enum class OnboardingPhase { DEMO, PROMISE, CTA }

@Composable
fun WelcomeScreen(
    onGetStartedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var phase by remember { mutableStateOf(OnboardingPhase.DEMO) }

    LaunchedEffect(phase) {
        when (phase) {
            OnboardingPhase.DEMO -> { delay(4000); phase = OnboardingPhase.PROMISE }
            OnboardingPhase.PROMISE -> { delay(3200); phase = OnboardingPhase.CTA }
            OnboardingPhase.CTA -> {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_icon),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
                ShimmerBrandText(
                    text = stringResource(R.string.welcome_brand),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = phase,
                    transitionSpec = {
                        fadeIn(tween(400)) togetherWith fadeOut(tween(300))
                    },
                    label = "phase"
                ) { currentPhase ->
                    when (currentPhase) {
                        OnboardingPhase.DEMO -> KaraokeDemo()
                        OnboardingPhase.PROMISE -> StyleMorphDemo()
                        OnboardingPhase.CTA -> CtaContent()
                    }
                }
            }

            AnimatedVisibility(
                visible = phase == OnboardingPhase.CTA,
                enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 3 },
                exit = fadeOut(tween(200))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ShimmerButton(
                        text = stringResource(R.string.welcome_get_started),
                        onClick = onGetStartedClick
                    )
                    Spacer(Modifier.height(14.dp))
                    LegalText()
                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        if (phase != OnboardingPhase.CTA) {
            Text(
                text = "Skip",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 24.dp, end = 20.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onGetStartedClick() }
            )
        }
    }
}

@Composable
private fun KaraokeDemo() {
    val words = listOf("this", "actually", "WORKS")
    val wordProgress = words.map { remember { Animatable(0f) } }

    LaunchedEffect(Unit) {
        val delays = listOf(0L, 700L, 1500L)
        words.indices.forEach { i ->
            launch {
                delay(delays[i])
                wordProgress[i].animateTo(1f, tween(1200, easing = LinearEasing))
            }
        }
    }

    val taglineAlpha by animateFloatAsState(
        targetValue = if (wordProgress.last().value > 0.35f) 1f else 0f,
        animationSpec = tween(500),
        label = "tagAlpha"
    )

    val glowPulse = rememberInfiniteTransition(label = "glow")
    val glowRadius by glowPulse.animateFloat(
        initialValue = 14f,
        targetValue = 22f,
        animationSpec = infiniteRepeatable(
            tween(800, easing = LinearEasing), RepeatMode.Reverse
        ),
        label = "glowR"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(260.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(WelcomeGradientStart, WelcomeGradientEnd)))
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(AccentCyan.copy(alpha = 0.06f), Color.Transparent),
                            center = Offset(size.width * 0.5f, size.height * 0.55f),
                            radius = size.width * 0.7f
                        )
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                words.forEachIndexed { index, word ->
                    val p by wordProgress[index].asState()

                    val alpha = when {
                        p < 0.15f -> p / 0.15f * 0.4f
                        p < 0.55f -> 0.4f + (p - 0.15f) / 0.4f * 0.6f
                        p < 0.75f -> 1f
                        else -> 1f - (p - 0.75f) / 0.25f * 0.6f
                    }.coerceIn(0f, 1f)

                    val scale = when {
                        p < 0.15f -> 0.9f
                        p < 0.45f -> 0.9f + (p - 0.15f) / 0.3f * 0.25f
                        p < 0.65f -> 1.15f
                        p < 0.75f -> 1.15f - (p - 0.65f) / 0.1f * 0.1f
                        else -> 1.05f
                    }.coerceIn(0.9f, 1.15f)

                    val isHighlighted = p in 0.4f..0.75f
                    val isFaded = p > 0.8f

                    val textColor = when {
                        isHighlighted -> Color.White
                        isFaded -> Color.White.copy(alpha = alpha * 0.45f)
                        else -> Color.White.copy(alpha = alpha)
                    }

                    val shadow = when {
                        isHighlighted -> Shadow(
                            color = AccentCyan.copy(alpha = 0.8f),
                            offset = Offset.Zero,
                            blurRadius = glowRadius
                        )
                        else -> Shadow(
                            color = Color.Black.copy(alpha = 0.4f),
                            offset = Offset(0f, 1f),
                            blurRadius = 4f
                        )
                    }

                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(RoundedCornerShape(6.dp))
                            .then(
                                if (isHighlighted) {
                                    Modifier.background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                AccentCyan.copy(alpha = 0.3f),
                                                AccentBlue.copy(alpha = 0.3f)
                                            )
                                        )
                                    )
                                } else Modifier
                            )
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = word,
                            color = textColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp,
                            lineHeight = 34.sp,
                            style = TextStyle(shadow = shadow)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = stringResource(R.string.welcome_tagline_1),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = taglineAlpha),
            textAlign = TextAlign.Center,
            lineHeight = 40.sp
        )
    }
}

@Composable
private fun StyleMorphDemo() {
    val words = listOf("this", "actually", "WORKS")

    val inf = rememberInfiniteTransition(label = "morph")
    val cycle by inf.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            tween(3500, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "cycle"
    )
    val styleIndex = cycle.toInt().coerceIn(0, 2)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(260.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(WelcomeGradientStart, WelcomeGradientEnd)))
                .drawBehind {
                    val glowColor = when (styleIndex) {
                        0 -> AccentViolet
                        1 -> Color.White
                        else -> AccentCyan
                    }
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(glowColor.copy(alpha = 0.08f), Color.Transparent),
                            center = Offset(size.width * 0.5f, size.height * 0.5f),
                            radius = size.width * 0.85f
                        )
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = styleIndex,
                transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(300)) },
                label = "style"
            ) { style ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when (style) {
                        0 -> {
                            words.forEachIndexed { i, word ->
                                val isActive = i == words.lastIndex
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .then(
                                            if (isActive) {
                                                Modifier.background(AccentViolet)
                                            } else Modifier
                                        )
                                        .padding(
                                            horizontal = if (isActive) 12.dp else 4.dp,
                                            vertical = if (isActive) 3.dp else 1.dp
                                        )
                                ) {
                                    Text(
                                        text = word,
                                        color = if (isActive) Color.Black else Color.White.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.Black,
                                        fontSize = if (isActive) 30.sp else 22.sp,
                                        lineHeight = if (isActive) 36.sp else 28.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        1 -> {
                            words.forEach { word ->
                                Text(
                                    text = word,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 26.sp,
                                    lineHeight = 34.sp,
                                    textAlign = TextAlign.Center,
                                    style = TextStyle(
                                        shadow = Shadow(
                                            Color.Black.copy(alpha = 0.3f),
                                            Offset(0f, 1f),
                                            3f
                                        )
                                    )
                                )
                            }
                        }
                        else -> {
                            words.forEach { word ->
                                Text(
                                    text = word,
                                    color = AccentCyan,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 28.sp,
                                    lineHeight = 36.sp,
                                    textAlign = TextAlign.Center,
                                    style = TextStyle(
                                        shadow = Shadow(
                                            AccentCyan.copy(alpha = 0.9f),
                                            Offset.Zero,
                                            20f
                                        )
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = stringResource(R.string.welcome_tagline_2),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            lineHeight = 40.sp
        )
    }
}

@Composable
private fun CtaContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
            Image(
                painter = painterResource(id = R.drawable.app_icon),
                contentDescription = null,
                modifier = Modifier.size(96.dp)
            )

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.welcome_subtitle_detail),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 26.sp
        )
    }
}

@Composable
private fun LegalText() {
    val context = LocalContext.current
    val termsColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val linkColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    val annotated = buildAnnotatedString {
        append("By continuing, you agree to our ")
        pushStringAnnotation(tag = "URL", annotation = AppLinks.TERMS_OF_SERVICE)
        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
            append("Terms of Service")
        }
        pop()
        append(" and ")
        pushStringAnnotation(tag = "URL", annotation = AppLinks.PRIVACY_POLICY)
        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
            append("Privacy Policy")
        }
        pop()
    }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotated,
        style = MaterialTheme.typography.labelSmall,
        color = termsColor,
        textAlign = TextAlign.Center,
        lineHeight = 16.sp,
        onTextLayout = { layoutResult = it },
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                layoutResult?.let { result ->
                    val position = result.getOffsetForPosition(offset)
                    annotated.getStringAnnotations("URL", position, position)
                        .firstOrNull()?.let { annotation ->
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                            )
                        }
                }
            }
        }
    )
}

@Composable
private fun ShimmerButton(
    text: String,
    onClick: () -> Unit
) {
    val inf = rememberInfiniteTransition(label = "sh")
    val offset by inf.animateFloat(
        initialValue = -600f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            tween(2400, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "off"
    )
    val shimmer = Brush.linearGradient(
        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.2f), Color.Transparent),
        start = Offset(offset, 0f),
        end = Offset(offset + 250f, 120f)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(AccentAmber, AccentRose)
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.matchParentSize().background(shimmer))
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            letterSpacing = 0.3.sp
        )
    }
}
