package com.dipdev.aiautocaptioner.ui.paywall

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dipdev.aiautocaptioner.ui.theme.AccentCyan
import com.dipdev.aiautocaptioner.ui.theme.AccentRose
import com.dipdev.aiautocaptioner.ui.theme.AccentViolet
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import kotlinx.coroutines.delay

@Composable
fun CustomPaywallDialog(
    isLoading: Boolean,
    onPurchaseClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onDismissRequest: () -> Unit
) {
    // Staggered entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(60); visible = true }

    // Subtle ambient glow pulse on the icon
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val cardDark = MaterialTheme.colorScheme.surfaceVariant
    val cardBorder = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = !isLoading,
            dismissOnClickOutside = !isLoading,
            usePlatformDefaultWidth = false
        )
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(300)) + slideInVertically(tween(400, easing = FastOutSlowInEasing)) { it / 4 }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(surfaceColor)
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.6f),
                                primaryColor.copy(alpha = 0.2f),
                                cardBorder
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(bottom = 8.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {

                    // ── Primary color gradient header banner ────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        primaryColor.copy(alpha = 0.2f),
                                        Color.Transparent
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Ambient glow behind the icon
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            primaryColor.copy(alpha = glowAlpha),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = CircleShape
                                )
                                .blur(12.dp)
                        )

                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(primaryColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Pro",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // ── Title + subtitle ───────────────────────────────────
                    Text(
                        text = "AutoCaptioner Pro",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = textPrimary,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = "Unlock all features. Pay once. Own it forever.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 28.dp)
                    )

                    Spacer(Modifier.height(22.dp))

                    // ── Feature list ───────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardDark)
                            .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                            .padding(vertical = 4.dp)
                    ) {
                        PaywallFeatureRow(
                            icon = Icons.Default.HighQuality,
                            title = "4K Resolution Export",
                            subtitle = "Crystal-clear videos for every platform",
                            accentColor = AccentAmber,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                        HorizontalDivider(color = cardBorder, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
                        PaywallFeatureRow(
                            icon = Icons.Default.Animation,
                            title = "All Premium Styles",
                            subtitle = "Karaoke, Elastic, Typewriter & more",
                            accentColor = AccentViolet,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                        HorizontalDivider(color = cardBorder, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
                        PaywallFeatureRow(
                            icon = Icons.Default.Lock,
                            title = "Lifetime Access",
                            subtitle = "No subscriptions. No recurring fees.",
                            accentColor = AccentRose,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                        HorizontalDivider(color = cardBorder, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
                        PaywallFeatureRow(
                            icon = Icons.Default.AutoAwesome,
                            title = "Karaoke & Word Highlight",
                            subtitle = "Words light up as they're spoken",
                            accentColor = AccentCyan,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    // ── Price badge ────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(primaryColor.copy(alpha = 0.12f))
                            .border(1.dp, primaryColor.copy(alpha = 0.3f), RoundedCornerShape(50.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "₹499",
                            style = MaterialTheme.typography.labelMedium.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough),
                            color = textSecondary.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "₹99",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = primaryColor
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "one-time",
                            style = MaterialTheme.typography.labelMedium,
                            color = textSecondary
                        )
                    }
                    Spacer(Modifier.height(16.dp))

                    // ── Purchase button ────────────────────────────────────
                    Button(
                        onClick = onPurchaseClick,
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                        )
                    ) {
                        // Background painted inside the button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (!isLoading) primaryColor else cardBorder),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "Unlock for ₹99",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }

                    // ── Restore / legal ────────────────────────────────────
                    TextButton(
                        onClick = onRestoreClick,
                        enabled = !isLoading
                    ) {
                        Text(
                            text = "Restore Purchases",
                            color = textSecondary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    // Trust badges row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text("🔒 Secure", style = MaterialTheme.typography.labelSmall, color = textSecondary.copy(alpha = 0.6f))
                        Text("♻️ Restore", style = MaterialTheme.typography.labelSmall, color = textSecondary.copy(alpha = 0.6f))
                        Text("📵 Offline AI", style = MaterialTheme.typography.labelSmall, color = textSecondary.copy(alpha = 0.6f))
                    }
                    Text(
                        text = "Payment processed securely by Google Play",
                        style = MaterialTheme.typography.labelSmall,
                        color = textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }
        }
    }
}

// ─── Feature row ─────────────────────────────────────────────────────────────

@Composable
private fun PaywallFeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = textSecondary
            )
        }

        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(18.dp)
        )
    }
}
