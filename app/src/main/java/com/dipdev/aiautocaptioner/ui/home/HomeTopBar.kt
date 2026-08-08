package com.dipdev.aiautocaptioner.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.components.MascotMode
import com.dipdev.aiautocaptioner.ui.components.MascotRobot
import com.dipdev.aiautocaptioner.ui.components.ShimmerBrandText
import compose.icons.FeatherIcons
import compose.icons.feathericons.Settings

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.graphics.graphicsLayer

@Composable
internal fun HomeTopBar(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Brand: mascot + wordmark
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "mascotIdle")
            val rotation by infiniteTransition.animateFloat(
                initialValue = -5f,
                targetValue = 5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "mascotRotation"
            )
            
            MascotRobot(
                mode = MascotMode.Idle,
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer {
                        rotationZ = rotation
                    },
                tightCrop = true
            )
            ShimmerBrandText(
                text = stringResource(R.string.home_brand_name)
            )
        }
        // Settings icon
        IconButton(onClick = onNavigateToSettings) {
            Icon(
                imageVector = FeatherIcons.Settings,
                contentDescription = stringResource(R.string.home_settings),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
