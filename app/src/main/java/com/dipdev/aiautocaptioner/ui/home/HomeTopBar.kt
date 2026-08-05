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
            MascotRobot(
                mode = MascotMode.Idle,
                modifier = Modifier.size(40.dp),
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
