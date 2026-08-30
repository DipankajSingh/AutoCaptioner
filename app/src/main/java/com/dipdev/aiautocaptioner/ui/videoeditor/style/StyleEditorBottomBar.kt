package com.dipdev.aiautocaptioner.ui.videoeditor.style

/*
 * v1: Presets-only style editor
 *
 * The Text / Color / Animate tabs are hidden for the initial release.
 * The tab bar (StyleEditorBottomBar) is not rendered in the UI.
 * StylePanel.kt renders PresetsTab directly instead of routing through
 * a tabbed layout.
 *
 * The StyleTab enum (in StyleViewModel.kt) and all tab composables
 * (TextTab, ColorTab, AnimationTab) are kept as-is.
 */

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BottomTabItem(
    name: String,
    icon: ImageVector,
    selected: Boolean,
    selectedTint: Color,
    onClick: () -> Unit
) {
    val tint by animateColorAsState(
        targetValue = if (selected) selectedTint else MaterialTheme.colorScheme.onSurface,
        label = "tabTint"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(8.dp)
    ) {
        Icon(icon, contentDescription = name, tint = tint)
        Text(name, fontSize = 10.sp, color = tint, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        Box(
            modifier = Modifier
                .height(2.dp)
                .width(20.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(if (selected) tint else Color.Transparent)
        )
    }
}

// ── Hidden for v1 ────────────────────────────────────────────────────────────
// Uncomment the function body below to restore the tab bar.
//
// @Composable
// fun StyleEditorBottomBar(
//     selectedTab: StyleTab,
//     onTabSelected: (StyleTab) -> Unit
// ) {
//     Row(
//         modifier = Modifier
//             .fillMaxWidth()
//             .height(64.dp),
//         horizontalArrangement = Arrangement.SpaceEvenly,
//         verticalAlignment = Alignment.CenterVertically
//     ) {
//         BottomTabItem("Presets", FeatherIcons.Layers, selectedTab == StyleTab.PRESETS, selectedTint = AccentAmber) { onTabSelected(StyleTab.PRESETS) }
//         BottomTabItem("Text", FeatherIcons.Type, selectedTab == StyleTab.TEXT, selectedTint = AccentAmber) { onTabSelected(StyleTab.TEXT) }
//         BottomTabItem("Color", FeatherIcons.Droplet, selectedTab == StyleTab.COLOR, selectedTint = AccentAmber) { onTabSelected(StyleTab.COLOR) }
//         BottomTabItem("Animate", FeatherIcons.Activity, selectedTab == StyleTab.ANIMATION, selectedTint = AccentAmber) { onTabSelected(StyleTab.ANIMATION) }
//     }
// }
