package com.dipdev.aiautocaptioner.ui.videoeditor.style

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import compose.icons.FeatherIcons
import compose.icons.feathericons.Activity
import compose.icons.feathericons.Star
import compose.icons.feathericons.Droplet
import compose.icons.feathericons.Layers
import compose.icons.feathericons.Type
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
import androidx.compose.animation.animateColorAsState
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import com.dipdev.aiautocaptioner.ui.theme.AccentViolet
import com.dipdev.aiautocaptioner.ui.theme.LocalAccentColor

@Composable
fun BottomTabItem(
    name: String,
    icon: ImageVector,
    selected: Boolean,
    isPremiumLocked: Boolean = false,
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
        Box {
            Icon(icon, contentDescription = name, tint = tint)
            if (isPremiumLocked) {
                Icon(
                    FeatherIcons.Star,
                    contentDescription = "PRO",
                    modifier = Modifier.size(10.dp).align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp),
                    tint = LocalAccentColor.current
                )
            }
        }
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

@Composable
fun StyleEditorBottomBar(
    selectedTab: StyleTab,
    isPremium: Boolean,
    onTabSelected: (StyleTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Color.Transparent),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomTabItem("Presets", FeatherIcons.Layers, selectedTab == StyleTab.PRESETS, selectedTint = AccentAmber) { onTabSelected(StyleTab.PRESETS) }
        BottomTabItem("Text", FeatherIcons.Type, selectedTab == StyleTab.TEXT, isPremiumLocked = !isPremium, selectedTint = AccentAmber) { onTabSelected(StyleTab.TEXT) }
        BottomTabItem("Color", FeatherIcons.Droplet, selectedTab == StyleTab.COLOR, isPremiumLocked = !isPremium, selectedTint = AccentAmber) { onTabSelected(StyleTab.COLOR) }
        BottomTabItem("Animate", FeatherIcons.Activity, selectedTab == StyleTab.ANIMATION, isPremiumLocked = !isPremium, selectedTint = AccentAmber) { onTabSelected(StyleTab.ANIMATION) }
    }
}
