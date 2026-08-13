package com.dipdev.aiautocaptioner.ui.onboarding

import android.content.Intent
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.animation.core.*
import androidx.core.net.toUri
import android.annotation.SuppressLint
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
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
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.dipdev.aiautocaptioner.AppLinks
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.components.MascotRobot
import com.dipdev.aiautocaptioner.ui.components.MascotMode
import com.dipdev.aiautocaptioner.ui.components.ShimmerBrandText
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowRight
import compose.icons.feathericons.ChevronRight

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
        // Cinematic ambient background glow accents
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(280.dp)
                .offset(x = (-120).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), Color.Transparent)
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
                        colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )
        // Rose accent glow behind the header
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(260.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(MaterialTheme.colorScheme.error.copy(alpha = 0.18f), Color.Transparent)
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

            Spacer(Modifier.height(12.dp))

            // 2. Stacked Video Cards positioned directly between Logo and Headline
            BeforeAfterVideoCards(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            // 3. High-Impact Headline & Subtitle positioned below the video showcase
            val brightAmber = lerp(MaterialTheme.colorScheme.primary, Color.White, 0.3f)
            Text(
                text = stringResource(R.string.welcome_headline_line1),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.welcome_headline_line2),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    brush = Brush.horizontalGradient(
                        colors = listOf(brightAmber, MaterialTheme.colorScheme.primary)
                    ),
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(6.dp))
            Text(
                text = "Add animated, eye-catching subtitles\ninstantly—without ever leaving your phone.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(16.dp))

            // 4. High Velocity Action Button
            ShimmerButton(
                text = "Get started",
                onClick = onGetStartedClick
            )

            Spacer(Modifier.height(12.dp))

            // 5. Footer & Legal
            Text(
                text = stringResource(R.string.settings_from_dipdev),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
            )
            Spacer(Modifier.height(6.dp))
            LegalText()
        }
    }
}

/**
 * Stacked Side-by-Side Vertical Video Comparison:
 * Two tall portrait video cards (Left: Before, Right: After) designed specifically for short-form vertical content,
 * separated by an overlapping center right-arrow badge to instinctively convey AI transformation.
 */
@Composable
private fun BeforeAfterVideoCards(
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        val brightAmber = lerp(MaterialTheme.colorScheme.primary, Color.White, 0.3f)
        Row(
            modifier = Modifier.fillMaxSize().clipToBounds(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // LEFT CARD: BEFORE (uncaptioned vertical reel)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds()
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                LoopingVideoPlayer(
                    rawResId = R.raw.before_sample,
                    modifier = Modifier.fillMaxSize().clipToBounds()
                )

                // "BEFORE" Badge Top-Left
                Box(
                    modifier = Modifier
                        .padding(10.dp)
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "BEFORE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }

            // RIGHT CARD: AFTER (captioned vertical reel)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds()
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                LoopingVideoPlayer(
                    rawResId = R.raw.after_sample,
                    modifier = Modifier.fillMaxSize().clipToBounds()
                )

                // "AFTER" Glowing Gradient Badge Top-Left
                Box(
                    modifier = Modifier
                        .padding(10.dp)
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(brightAmber, MaterialTheme.colorScheme.primary)
                            )
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "AFTER",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Overlapping Center Transformation Right Arrow Badge
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = FeatherIcons.ArrowRight,
                contentDescription = "Transformation to AutoCaptioned",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
@SuppressLint("UnsafeOptInUsageError")
private fun LoopingVideoPlayer(
    rawResId: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val uri = "android.resource://${context.packageName}/$rawResId".toUri()
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            prepare()
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
            val textureView = TextureView(ctx)
            exoPlayer.setVideoTextureView(textureView)

            AspectRatioFrameLayout(ctx).apply {
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                addView(
                    textureView,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // Dynamically sync zoom aspect ratio with video track dimensions
                val listener = object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.height > 0 && videoSize.width > 0) {
                            setAspectRatio(videoSize.width.toFloat() / videoSize.height.toFloat())
                        }
                    }
                }
                exoPlayer.addListener(listener)

                if (exoPlayer.videoSize.height > 0 && exoPlayer.videoSize.width > 0) {
                    setAspectRatio(exoPlayer.videoSize.width.toFloat() / exoPlayer.videoSize.height.toFloat())
                }
            }
        },
        modifier = modifier.clipToBounds()
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
    val brightAmber = lerp(MaterialTheme.colorScheme.primary, Color.White, 0.3f)
    val shimmer = Brush.linearGradient(
        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f), Color.Transparent),
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
                    listOf(brightAmber, MaterialTheme.colorScheme.primary)
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
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                letterSpacing = 0.4.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = FeatherIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun LegalText() {
    val context = LocalContext.current
    val termsColor = MaterialTheme.colorScheme.onSurfaceVariant
    val linkColor = MaterialTheme.colorScheme.primary

    val annotated = buildAnnotatedString {
        append("You agree to our ")
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
        style = MaterialTheme.typography.bodyMedium,
        color = termsColor,
        textAlign = TextAlign.Center,
        lineHeight = 18.sp,
        onTextLayout = { layoutResult = it },
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                layoutResult?.let { result ->
                    val position = result.getOffsetForPosition(offset)
                    annotated.getStringAnnotations("URL", position, position)
                        .firstOrNull()?.let { annotation ->
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, annotation.item.toUri())
                            )
                        }
                }
            }
        }
    )
}
