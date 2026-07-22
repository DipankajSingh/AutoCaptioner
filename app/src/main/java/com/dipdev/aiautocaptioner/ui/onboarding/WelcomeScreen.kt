package com.dipdev.aiautocaptioner.ui.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import compose.icons.FeatherIcons
import compose.icons.feathericons.Play
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.theme.AutoCaptionerTheme
import com.dipdev.aiautocaptioner.ui.theme.WelcomeGradientEnd
import com.dipdev.aiautocaptioner.ui.theme.WelcomeGradientStart

@Composable
fun WelcomeScreen(
    onGetStartedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Shimmer animation for the CTA button
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -600f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            Color.White.copy(alpha = 0.22f),
            Color.Transparent
        ),
        start = Offset(shimmerOffset, 0f),
        end = Offset(shimmerOffset + 250f, 120f)
    )

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))

            // ── Logo Wordmark ──────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_logo_ui),
                    contentDescription = "AutoCaptioner Logo",
                    modifier = Modifier.size(38.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.welcome_brand),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-0.3).sp
                )
            }

            Spacer(Modifier.height(32.dp))

            // ── Hero Card ──────────────────────────────────────────────────────
            // Placeholder: swap in a real video thumbnail + caption overlay when ready.
            // The Card is sized to hold a short-form (9:16-ish) clip preview.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.verticalGradient(listOf(WelcomeGradientStart, WelcomeGradientEnd)))
                    .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(24.dp))
            ) {
                // Timestamp badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        "0:12",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Play button
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        FeatherIcons.Play,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Caption preview row — replace with real caption styles later
                MockCaptionRow(
                    words = listOf("this", "actually", "WORKS"),
                    highlightIndex = 2,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 28.dp)
                )
            }

            Spacer(Modifier.height(36.dp))

            // ── Headline ───────────────────────────────────────────────────────
            Text(
                text = stringResource(R.string.welcome_tagline),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                lineHeight = 42.sp
            )

            Spacer(Modifier.height(14.dp))

            // ── Subtitle ───────────────────────────────────────────────────────
            Text(
                text = stringResource(R.string.welcome_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.weight(1f))

            // ── CTA Button with shimmer ────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    )
                    .clickable(onClick = onGetStartedClick),
                contentAlignment = Alignment.Center
            ) {
                // Shimmer sweep overlay
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(shimmerBrush)
                )
                Text(
                    text = stringResource(R.string.welcome_get_started),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    letterSpacing = 0.2.sp
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Legal Footer ───────────────────────────────────────────────────
            Text(
                text = stringResource(R.string.welcome_terms),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun MockCaptionRow(
    words: List<String>,
    highlightIndex: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        words.forEachIndexed { index, word ->
            if (index == highlightIndex) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        word,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp
                    )
                }
            } else {
                Text(
                    text = word,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 19.sp,
                    style = TextStyle(
                        shadow = Shadow(
                            Color.Black.copy(alpha = 0.6f),
                            Offset(0f, 2f),
                            blurRadius = 6f
                        )
                    )
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
private fun WelcomeScreenPreview() {
    AutoCaptionerTheme {
        WelcomeScreen(onGetStartedClick = {})
    }
}
