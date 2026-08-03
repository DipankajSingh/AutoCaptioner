package com.dipdev.aiautocaptioner.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import com.dipdev.aiautocaptioner.ui.components.MascotRobot
import com.dipdev.aiautocaptioner.ui.components.MascotMode
import com.dipdev.aiautocaptioner.ui.components.ShimmerBrandText
import com.dipdev.aiautocaptioner.ui.theme.*

@Composable
fun WelcomeScreen(
    onGetStartedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // Refined Brand Header (Quiet Corporate Confidence)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MascotRobot(
                    mode = MascotMode.Idle,
                    modifier = Modifier.size(28.dp),
                    tightCrop = true
                )
                ShimmerBrandText(
                    text = stringResource(R.string.welcome_brand),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(Modifier.height(18.dp))

            // Hero Centerpiece: The 9:16 Studio Reel Card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                StudioReelCard()
            }

            Spacer(Modifier.height(20.dp))

            // Punchy Sub-headline & Immediate CTA (Zero Waiting)
            Text(
                text = stringResource(R.string.welcome_tagline_1),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.welcome_subtitle_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            ShimmerButton(
                text = stringResource(R.string.welcome_get_started),
                onClick = onGetStartedClick
            )

            Spacer(Modifier.height(14.dp))
            LegalText()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StudioReelCard(
    modifier: Modifier = Modifier
) {
    val words = listOf("CAPTIONS", "THAT", "STOP", "SCROLLS.")

    // Infinite style cycling (0: Hormozi Viral, 1: Neon Cyber, 2: Studio Minimal)
    val styleTransition = rememberInfiniteTransition(label = "styleCycle")
    val styleCycle by styleTransition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "styleIndex"
    )
    val styleIndex = styleCycle.toInt().coerceIn(0, 2)

    // Word highlighting cycle within each style
    val wordCycle by styleTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wordIndex"
    )
    val activeWordIndex = wordCycle.toInt().coerceIn(0, 3)

    // Ambient background pulsing glow
    val glowPulse by styleTransition.animateFloat(
        initialValue = 20f,
        targetValue = 35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowRadius"
    )

    val styleBadgeName = when (styleIndex) {
        0 -> "✨ Style: Hormozi"
        1 -> "🔥 Style: Neon Cyber"
        else -> "🎬 Style: Studio Pro"
    }

    Box(
        modifier = modifier
            .fillMaxHeight(0.96f)
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0D1224),
                        Color(0xFF161B30)
                    ),
                    start = Offset.Zero,
                    end = Offset(400f, 900f)
                )
            )
            .border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.3f),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                shape = RoundedCornerShape(28.dp)
            )
            .drawBehind {
                val accentGlow = when (styleIndex) {
                    0 -> Color(0xFFFFB800).copy(alpha = 0.18f)
                    1 -> Color(0xFFFF2A85).copy(alpha = 0.22f)
                    else -> Color(0xFF4C8CFF).copy(alpha = 0.15f)
                }
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(accentGlow, Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.45f),
                        radius = size.width * 0.95f
                    )
                )
            }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Top preset badge with clean sequential transition (zero text overlap)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            AnimatedContent(
                targetState = styleBadgeName,
                transitionSpec = { 
                    fadeIn(tween(200, delayMillis = 100)) togetherWith fadeOut(tween(100)) 
                },
                label = "badgeText"
            ) { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Kinetic typography words display
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            words.forEachIndexed { index, word ->
                val isCurrent = (index == activeWordIndex)
                val scale by animateFloatAsState(
                    targetValue = if (isCurrent) 1.15f else 0.95f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "scale-$index"
                )

                AnimatedContent(
                    targetState = styleIndex,
                    transitionSpec = { 
                        fadeIn(tween(150, delayMillis = 50)) togetherWith fadeOut(tween(50)) 
                    },
                    label = "wordStyle-$index"
                ) { style ->
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(RoundedCornerShape(10.dp))
                            .then(
                                when {
                                    style == 0 && isCurrent -> Modifier
                                        .background(Color(0xFFFFB800))
                                    style == 1 && isCurrent -> Modifier
                                        .background(Color(0xFFFF2A85))
                                    style == 2 && isCurrent -> Modifier
                                        .background(Color.White)
                                    else -> Modifier
                                }
                            )
                            .padding(
                                horizontal = if (isCurrent) 16.dp else 6.dp,
                                vertical = if (isCurrent) 6.dp else 2.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val textColor = when (style) {
                            0 -> if (isCurrent) Color.Black else Color.White.copy(alpha = 0.55f)
                            1 -> if (isCurrent) Color.White else Color.White.copy(alpha = 0.5f)
                            else -> if (isCurrent) Color.Black else Color.White.copy(alpha = 0.6f)
                        }
                        
                        val shadow = if (style == 1 && isCurrent) {
                            Shadow(Color(0xFFFF2A85), Offset.Zero, glowPulse)
                        } else if (!isCurrent) {
                            Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 2f), 4f)
                        } else {
                            Shadow.None
                        }

                        Text(
                            text = word,
                            color = textColor,
                            fontWeight = if (isCurrent) FontWeight.Black else FontWeight.ExtraBold,
                            fontSize = if (isCurrent) 30.sp else 23.sp,
                            lineHeight = 34.sp,
                            style = TextStyle(shadow = shadow),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Simulated high-end studio timeline waveform indicator at bottom of card
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            repeat(16) { i ->
                val isSpikeActive = (i % 4 == activeWordIndex)
                val barHeight by animateFloatAsState(
                    targetValue = if (isSpikeActive) 28f else 8f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "bar-$i"
                )
                val barColor = when {
                    !isSpikeActive -> Color.White.copy(alpha = 0.2f)
                    styleIndex == 0 -> Color(0xFFFFB800)
                    styleIndex == 1 -> Color(0xFFFF2A85)
                    else -> Color(0xFF4C8CFF)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(barHeight.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(barColor)
                )
            }
        }
    }
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
        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.25f), Color.Transparent),
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
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            letterSpacing = 0.3.sp
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
