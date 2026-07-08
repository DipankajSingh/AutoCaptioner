package com.dipdev.aiautocaptioner.ui.videoeditor.styleeditor.tabs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.data.db.entity.AnimationType
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.data.db.entity.DisplayMode
import com.dipdev.aiautocaptioner.data.db.entity.KaraokeHighlightMode
import com.dipdev.aiautocaptioner.ui.videoeditor.styleeditor.SubToolButton
import com.dipdev.aiautocaptioner.ui.videoeditor.styleeditor.LabeledPremiumSlider

enum class AnimSubTool { MODE, HIGHLIGHT, ENTER, SPEED }

@Composable
fun AnimationTab(
    style: CaptionStyleEntity,
    onDisplayModeChange: (DisplayMode) -> Unit,
    onWordEnterChange: (AnimationType) -> Unit,
    onKaraokeHighlightChange: (KaraokeHighlightMode) -> Unit,
    onAnimationDurationChange: (Int) -> Unit,
) {
    var activeTool by remember { mutableStateOf<AnimSubTool?>(null) }

    if (activeTool == null) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            item { SubToolButton(Icons.Default.Slideshow, "Display") { activeTool = AnimSubTool.MODE } }
            item { SubToolButton(Icons.AutoMirrored.Filled.StarHalf, "Karaoke") { activeTool = AnimSubTool.HIGHLIGHT } }
            item { SubToolButton(Icons.Default.Animation, "Animation") { activeTool = AnimSubTool.ENTER } }
            item { SubToolButton(Icons.Default.Speed, "Speed") { activeTool = AnimSubTool.SPEED } }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when (activeTool) {
                    AnimSubTool.MODE -> {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DisplayMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = style.displayMode == mode,
                                    onClick = { onDisplayModeChange(mode) },
                                    label = { Text(mode.name.split('_').joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercaseChar() } }, fontSize = 12.sp) },
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                            }
                        }
                    }
                    AnimSubTool.HIGHLIGHT -> {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            KaraokeHighlightMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = style.karaokeHighlightMode == mode,
                                    onClick = { onKaraokeHighlightChange(mode) },
                                    label = { Text(mode.name.split('_').joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercaseChar() } }, fontSize = 12.sp) },
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                            }
                        }
                    }
                    AnimSubTool.ENTER -> {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AnimationType.entries.filter { it != AnimationType.ELASTIC }.forEach { anim ->
                                FilterChip(
                                    selected = style.wordEnterAnimation == anim,
                                    onClick = { onWordEnterChange(anim) },
                                    label = { Text(anim.name.split('_').joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercaseChar() } }, fontSize = 12.sp) },
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                            }
                        }
                    }
                    AnimSubTool.SPEED -> {
                        LabeledPremiumSlider(
                            label = "Speed",
                            value = style.animationDurationMs.toFloat(),
                            onValueChange = { onAnimationDurationChange(it.toInt()) },
                            valueRange = 50f..600f
                        )
                    }
                    null -> {}
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            TextButton(onClick = { activeTool = null }) {
                Text("Done", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}