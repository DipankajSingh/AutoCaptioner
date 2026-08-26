package com.dipdev.aiautocaptioner.ui.recorder.ui.effects

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dipdev.aiautocaptioner.engine.effects.CreatorFilter
import com.dipdev.aiautocaptioner.ui.recorder.components.FloatingFilterBadge

@Composable
fun FilterBadgeOverlay(
    activeFilter: CreatorFilter,
    modifier: Modifier = Modifier
) {
    FloatingFilterBadge(
        activeFilter = activeFilter,
        modifier = modifier
    )
}
