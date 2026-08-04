package com.dipdev.aiautocaptioner.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.dipdev.aiautocaptioner.AppLinks
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.components.MascotRobot
import com.dipdev.aiautocaptioner.ui.components.MascotMode
import com.dipdev.aiautocaptioner.ui.components.ShimmerBrandText
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowDown
import compose.icons.feathericons.ChevronRight

@Composable
fun WelcomeScreen(
    onGetStartedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF06070A))
    ) {
        // Cinematic ambient background glow accents
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(280.dp)
                .offset(x = (-120).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFFFB800).copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(280.dp)
                .offset(x = 120.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFFF2A85).copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )

        // Main Layout Column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Header & Branding
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                MascotRobot(
                    mode = MascotMode.Idle,
                    modifier = Modifier.size(32.dp),
                    tightCrop = true
                )
                Spacer(Modifier.width(10.dp))
                ShimmerBrandText(
                    text = stringResource(R.string.welcome_brand),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(Modifier.height(8.dp))

            // High-Impact Headline
            Text(
                text = "Make Every Word",
                style = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Impossible To Ignore",
                style = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFFFFB800), Color(0xFFFF2A85))
                    ),
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(6.dp))
            Text(
                text = "Professional captions that grab\nattention from the first second.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB0B6C2),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(16.dp))

            // 2. Center Stacked Video Cards (Before vs After with Down Arrow)
            BeforeAfterVideoCards(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))

            // 3. High Velocity Action Button
            ShimmerButton(
                text = "Start Creating",
                onClick = onGetStartedClick
            )

            Spacer(Modifier.height(12.dp))

            // 4. Footer & Legal
            Text(
                text = "Record • Caption • Style • Edit • Export",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF717784),
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            LegalText()
        }
    }
}

/**
 * Stacked Before & After Video Comparison:
 * Two distinct rounded video cards with an overlapping center downward arrow badge,
 * replacing the previous single-screen divider/slider approach to clearly communicate transformation.
 */
@Composable
private fun BeforeAfterVideoCards(
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // TOP CARD: BEFORE (uncaptioned)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .background(Color(0xFF16181F))
            ) {
                LoopingVideoPlayer(
                    rawResId = R.raw.before_sample,
                    modifier = Modifier.fillMaxSize()
                )

                // "BEFORE" Badge Top-Left
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF262932).copy(alpha = 0.85f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "BEFORE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.95f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            // BOTTOM CARD: AFTER (captioned)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .background(Color(0xFF16181F))
            ) {
                LoopingVideoPlayer(
                    rawResId = R.raw.after_sample,
                    modifier = Modifier.fillMaxSize()
                )

                // "AFTER" Glowing Pink/Orange Badge Top-Left
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFFB800), Color(0xFFFF2A85))
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "AFTER",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Overlapping Center Transformation Arrow Badge
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E212A))
                .border(1.5.dp, Color(0xFFFFB800).copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = FeatherIcons.ArrowDown,
                contentDescription = "Transformation to AutoCaptioned",
                tint = Color(0xFFFFB800),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun LoopingVideoPlayer(
    rawResId: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val uri = Uri.parse("android.resource://${context.packageName}/$rawResId")
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            prepare()
            seekTo(2500)
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        modifier = modifier
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
        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.3f), Color.Transparent),
        start = Offset(offset, 0f),
        end = Offset(offset + 260f, 130f)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFFFFB800), Color(0xFFFF2A85))
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.matchParentSize().background(shimmer))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                letterSpacing = 0.4.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = FeatherIcons.ChevronRight,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun LegalText() {
    val context = LocalContext.current
    val termsColor = Color(0xFF717784)
    val linkColor = Color(0xFFB5BAC6)

    val annotated = buildAnnotatedString {
        append("Fully offline AI processing · You agree to our ")
        pushStringAnnotation(tag = "URL", annotation = AppLinks.TERMS_OF_SERVICE)
        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
            append("Terms")
        }
        pop()
        append(" & ")
        pushStringAnnotation(tag = "URL", annotation = AppLinks.PRIVACY_POLICY)
        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
            append("Privacy")
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
