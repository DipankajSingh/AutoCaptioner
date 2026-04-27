package com.dipdev.autocaptioner.ui.styleeditor.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.autocaptioner.data.db.entity.AnimationType
import com.dipdev.autocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.autocaptioner.data.db.entity.DisplayMode
import com.dipdev.autocaptioner.data.db.entity.KaraokeHighlightMode

enum class AnimSubTool { MODE, HIGHLIGHT, ENTER }

@Composable
fun AnimationTab(
    style: CaptionStyleEntity,
    onDisplayModeChange: (DisplayMode) -> Unit,
    onWordEnterChange: (AnimationType) -> Unit,
    onWordExitChange: (AnimationType) -> Unit,
    onKaraokeHighlightChange: (KaraokeHighlightMode) -> Unit
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
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { activeTool = AnimSubTool.MODE }) { Icon(Icons.Default.Slideshow, "Display Mode") }
                    Text("Display", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { activeTool = AnimSubTool.HIGHLIGHT }) { Icon(Icons.AutoMirrored.Filled.StarHalf, "Karaoke Style") }
                    Text("Karaoke", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { activeTool = AnimSubTool.ENTER }) { Icon(Icons.Default.Animation, "Enter Animation") }
                    Text("Animation", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                DisplayMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = style.displayMode == mode,
                                        onClick = { onDisplayModeChange(mode) },
                                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercaseChar() }, fontSize = 12.sp) },
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                    AnimSubTool.HIGHLIGHT -> {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                KaraokeHighlightMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = style.karaokeHighlightMode == mode,
                                        onClick = { onKaraokeHighlightChange(mode) },
                                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercaseChar() }, fontSize = 12.sp) },
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                    AnimSubTool.ENTER -> {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                AnimationType.entries.filter { it != AnimationType.ELASTIC }.forEach { anim ->
                                    FilterChip(
                                        selected = style.wordEnterAnimation == anim,
                                        onClick = { onWordEnterChange(anim) },
                                        label = { Text(anim.name.lowercase().replaceFirstChar { it.uppercaseChar() }, fontSize = 12.sp) },
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                }
                            }
                        }
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