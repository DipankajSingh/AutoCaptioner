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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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
import compose.icons.feathericons.Play

@Composable
fun WelcomeScreen(
    onGetStartedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        // 1. Two normal side-by-side full-screen video players sliced cleanly by diagonal cut
        BeforeAfterReelCard(modifier = Modifier.fillMaxSize())

        // 2. Cinematic vertical gradient overlay with a subtle top shadow for brand legibility & bottom fade
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.65f),
                        0.20f to Color.Transparent,
                        0.50f to Color.Transparent,
                        0.75f to Color.Black.copy(alpha = 0.85f),
                        1.0f to Color.Black
                    )
                )
        )

        // 3. Absolute Floating AutoCaptioner Logo aligned cleanly near top edge (reserving zero layout space)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 12.dp) // Balanced top positioning right under system status bar
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

        // 4. Overlaid Bottom Content over faded video base with subtle shadow typography & CTA
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // High-Impact Two-Line Headline with Gradient Second Line & subtle shadow
            Text(
                text = "Make Every Word",
                style = MaterialTheme.typography.headlineMedium.copy(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.8f),
                        offset = Offset(0f, 4f),
                        blurRadius = 8f
                    )
                ),
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp
            )
            Text(
                text = "Impossible To Ignore",
                style = TextStyle(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFFFFB800), Color(0xFFFF2A85))
                    ),
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.6f),
                        offset = Offset(0f, 3f),
                        blurRadius = 6f
                    ),
                    textAlign = TextAlign.Center
                ),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = "One tap. Automatic captions.\nProfessional results.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFC5C9D3),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(16.dp))

            // Sleek Onboarding Pagination Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4A5060))
                )
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFFFB800))
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4A5060))
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4A5060))
                )
            }

            Spacer(Modifier.height(16.dp))

            // High Velocity Action Button
            Box(modifier = Modifier.fillMaxWidth()) {
                ShimmerButton(
                    text = "Create First Caption ⚡",
                    onClick = onGetStartedClick
                )
            }

            Spacer(Modifier.height(12.dp))
            LegalText()
            Spacer(Modifier.height(6.dp))
        }
    }
}

/**
 * Full-Screen Background Before/After Video:
 * - Top half = BEFORE (uncaptioned), Bottom half = AFTER (captioned)
 * - Horizontal gold divider cleanly separates the two videos
 */
@Composable
private fun BeforeAfterReelCard(
    modifier: Modifier = Modifier
) {
    val splitRatio = 0.5f
    val density = LocalDensity.current
    val dividerHeightPx = with(density) { 2.2f.dp.toPx() }
    val shadowHeightPx = with(density) { 6.0f.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                drawContent()
                // Draw depth shadow behind the divider
                drawLine(
                    color = Color.Black.copy(alpha = 0.65f),
                    start = Offset(0f, size.height * splitRatio - 3f),
                    end = Offset(size.width, size.height * splitRatio - 3f),
                    strokeWidth = shadowHeightPx
                )
                // Draw sharp horizontal gold divider
                drawLine(
                    color = Color(0xFFFFB800),
                    start = Offset(0f, size.height * splitRatio),
                    end = Offset(size.width, size.height * splitRatio),
                    strokeWidth = dividerHeightPx
                )
            }
    ) {
        // TOP LAYER: BEFORE (uncaptioned)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(TopSplitShape(splitRatio))
        ) {
            LoopingVideoPlayer(
                rawResId = R.raw.before_sample,
                modifier = Modifier.fillMaxSize()
            )

            // "BEFORE" Badge Top-Left
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 62.dp, start = 20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.25f))
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

        // BOTTOM LAYER: AFTER (with captions)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(BottomSplitShape(splitRatio))
        ) {
            LoopingVideoPlayer(
                rawResId = R.raw.after_sample,
                modifier = Modifier.fillMaxSize()
            )

            // "AFTER" Glowing Gold Badge Bottom-Right
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = 16.dp, end = 20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFFFFB800))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "AFTER",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp
                )
            }
        }
    }
}

private class TopSplitShape(val splitRatio: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height * splitRatio)
            lineTo(0f, size.height * splitRatio)
            close()
        }
        return Outline.Generic(path)
    }
}

private class BottomSplitShape(val splitRatio: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            moveTo(0f, size.height * splitRatio)
            lineTo(size.width, size.height * splitRatio)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
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
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            letterSpacing = 0.4.sp
        )
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
