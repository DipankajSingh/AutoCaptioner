package com.dipdev.aiautocaptioner.ui.recorder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import com.dipdev.aiautocaptioner.ui.theme.ScreenThemeProvider

@Composable
fun PermissionRequestScreen(
    cameraGranted: Boolean,
    micGranted: Boolean,
    cameraPermanentlyDenied: Boolean,
    micPermanentlyDenied: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    onPrivacyPolicy: () -> Unit
) {
    ScreenThemeProvider(accentColor = AccentAmber) {
        val context = LocalContext.current

        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }

        val heroScale = remember { Animatable(0.92f) }
        LaunchedEffect(Unit) {
            heroScale.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .displayCutoutPadding()
        ) {


            Column(modifier = Modifier.fillMaxSize()) {
                // Scrollable content that centers when it fits the viewport and scrolls from
                // the top when it doesn't. Arrangement.Center alone is a no-op inside a
                // verticalScroll column (infinite-height measurement), so we measure both
                // heights and pick the arrangement explicitly.
                val contentScrollState = rememberScrollState()
                var contentHeight by remember { mutableIntStateOf(0) }
                var viewportHeight by remember { mutableIntStateOf(0) }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .onSizeChanged { viewportHeight = it.height }
                        .verticalScroll(contentScrollState)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = if (contentHeight <= viewportHeight) Arrangement.Center else Arrangement.Top
                ) {
                    Column(
                        modifier = Modifier.onSizeChanged { contentHeight = it.height },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Hero illustration (camera_permission.svg) — amber duotone
                Box(
                    modifier = Modifier
                        .size(width = 200.dp, height = 194.dp)
                        .graphicsLayer {
                            scaleX = heroScale.value
                            scaleY = heroScale.value
                            alpha = heroScale.value
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("file:///android_asset/camera_permission.svg")
                            .decoderFactory(SvgDecoder.Factory())
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(350, delayMillis = 150)) +
                            slideInVertically(tween(400, easing = FastOutSlowInEasing)) { it / 8 }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.recorder_permission_eyebrow),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 2.sp,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.recorder_permission_screen_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.recorder_permission_screen_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(350, delayMillis = 220)) +
                            slideInVertically(tween(400, easing = FastOutSlowInEasing)) { it / 6 }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PermissionStatusCard(
                            icon = Icons.Rounded.Videocam,
                            title = stringResource(R.string.recorder_permission_camera_title),
                            description = stringResource(R.string.recorder_permission_camera_desc),
                            granted = cameraGranted,
                            modifier = Modifier.weight(1f)
                        )
                        PermissionStatusCard(
                            icon = Icons.Rounded.Mic,
                            title = stringResource(R.string.recorder_permission_microphone_title),
                            description = stringResource(R.string.recorder_permission_microphone_desc),
                            granted = micGranted,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                    Spacer(modifier = Modifier.height(20.dp))
                    }
                }

                // Pinned bottom CTA — always visible, no scrolling needed
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val anyPermanentlyDenied = cameraPermanentlyDenied || micPermanentlyDenied
                    Button(
                        onClick = if (anyPermanentlyDenied) onOpenSettings else onRequestPermissions,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = stringResource(
                                if (anyPermanentlyDenied) R.string.recorder_open_settings
                                else R.string.recorder_permission_allow_both
                            ),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.recorder_permission_not_now),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    TextButton(onClick = onPrivacyPolicy) {
                        Text(
                            text = stringResource(R.string.settings_privacy_policy),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusCard(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon badge
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (granted) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(14.dp))
        // Status pill
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    if (granted) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (granted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
            )
            Text(
                text = stringResource(
                    if (granted) R.string.recorder_permission_granted
                    else R.string.recorder_permission_needed
                ),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
