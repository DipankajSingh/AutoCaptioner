package com.dipdev.aiautocaptioner.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowRight
import compose.icons.feathericons.Scissors
import compose.icons.feathericons.Video
import compose.icons.feathericons.Zap
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun EmptyProjectView(
    lastRecordingMode: String,
    onNavigateToSmartRecorder: (String) -> Unit,
    onQuickCaption: () -> Unit,
    onAdvancedStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Staggered entry animation states
    val animateIllustration = remember { Animatable(0f) }
    val animateText = remember { Animatable(0f) }
    val animatePrimary = remember { Animatable(0f) }
    val animateSecondary1 = remember { Animatable(0f) }
    val animateSecondary2 = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { animateIllustration.animateTo(1f, tween(900, easing = EaseOutBack)) }
        launch { 
            delay(40.milliseconds)
            animateText.animateTo(1f, tween(800, easing = EaseOutQuint)) 
        }
        launch { 
            delay(80.milliseconds)
            animatePrimary.animateTo(1f, tween(900, easing = EaseOutBack)) 
        }
        launch { 
            delay(120.milliseconds)
            animateSecondary1.animateTo(1f, tween(800, easing = EaseOutQuint)) 
        }
        launch { 
            delay(160.milliseconds)
            animateSecondary2.animateTo(1f, tween(800, easing = EaseOutQuint)) 
        }
    }

    // Floating bobbing animation for the illustration
    val infiniteTransition = rememberInfiniteTransition(label = "floating")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatOffset"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val context = LocalContext.current
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data("file:///android_asset/no_projects.svg")
                .decoderFactory(SvgDecoder.Factory())
                .build(),
            contentDescription = stringResource(R.string.home_no_projects),
            modifier = Modifier
                .padding(bottom = 8.dp)
                .size(200.dp)
                .graphicsLayer {
                    alpha = animateIllustration.value
                    scaleX = animateIllustration.value
                    scaleY = animateIllustration.value
                    translationY = floatOffset * density
                }
        )
        
        // 2. Inspiring Studio Greeting
        Column(
            modifier = Modifier.graphicsLayer {
                alpha = animateText.value
                translationY = (1f - animateText.value) * 20.dp.toPx()
            },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.home_no_projects),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_no_projects_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB0B6C2),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
                lineHeight = 20.sp
            )
        }
        
        Spacer(Modifier.height(20.dp))

        // 3. PRIMARY HERO CARD: 1-Tap Captions (The flagship expressway)
        HeroExpressCard(
            onClick = onQuickCaption,
            contentDescriptionText = stringResource(R.string.home_start_1tap),
            title = stringResource(R.string.home_1_tap_captions),
            subtitle = stringResource(R.string.home_1_tap_desc),
            modifier = Modifier.graphicsLayer {
                alpha = animatePrimary.value
                scaleX = 0.9f + (animatePrimary.value * 0.1f)
                scaleY = 0.9f + (animatePrimary.value * 0.1f)
                translationY = (1f - animatePrimary.value) * 30.dp.toPx()
            }
        )

        Spacer(Modifier.height(20.dp))

        // 4. SECONDARY STUDIO CONSOLE (More Creation Tools)
        Text(
            text = "MORE STUDIO TOOLS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF8A91A0),
            letterSpacing = 1.2.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .graphicsLayer {
                    alpha = animateSecondary1.value
                },
            textAlign = TextAlign.Start
        )
        Spacer(Modifier.height(6.dp))

        // Secondary Card 1: Record Video
        StudioActionCard(
            title = stringResource(R.string.home_record_video),
            subtitle = stringResource(R.string.home_record_desc),
            icon = FeatherIcons.Video,
            onClick = { onNavigateToSmartRecorder(lastRecordingMode) },
            contentDescriptionText = stringResource(R.string.home_record_video),
            modifier = Modifier.graphicsLayer {
                alpha = animateSecondary1.value
                translationY = (1f - animateSecondary1.value) * 20.dp.toPx()
            }
        )

        Spacer(Modifier.height(10.dp))

        // Secondary Card 2: Trim & Custom Setup
        StudioActionCard(
            title = stringResource(R.string.home_advanced_studio),
            subtitle = stringResource(R.string.home_advanced_desc),
            icon = FeatherIcons.Scissors,
            onClick = onAdvancedStudio,
            contentDescriptionText = stringResource(R.string.home_start_advanced),
            modifier = Modifier.graphicsLayer {
                alpha = animateSecondary2.value
                translationY = (1f - animateSecondary2.value) * 20.dp.toPx()
            }
        )
        
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HeroExpressCard(
    onClick: () -> Unit,
    title: String,
    subtitle: String,
    contentDescriptionText: String,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.45f),
        label = "heroScale"
    )

    // Animated glowing border
    val infiniteTransition = rememberInfiniteTransition(label = "borderGlow")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .semantics { contentDescription = contentDescriptionText },
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, Brush.linearGradient(
            colors = listOf(
                AccentAmber.copy(alpha = alphaAnim),
                Color(0xFFFFC947).copy(alpha = alphaAnim * 0.5f)
            )
        )),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E160C)
        ),
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFFFFC947), AccentAmber)
                        ),
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = FeatherIcons.Zap,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 17.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE2E7F0).copy(alpha = 0.85f),
                    lineHeight = 16.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentAmber.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = FeatherIcons.ArrowRight,
                    contentDescription = null,
                    tint = AccentAmber,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun StudioActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescriptionText: String,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = contentDescriptionText },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF161618)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF262629), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFD3D8E5),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9197A6),
                    lineHeight = 15.sp
                )
            }
        }
    }
}
