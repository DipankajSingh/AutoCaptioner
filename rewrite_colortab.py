import sys

content = """package com.dipdev.autocaptioner.ui.styleeditor.tabs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlignHorizontalCenter
import androidx.compose.material.icons.filled.AlignVerticalCenter
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.autocaptioner.data.db.entity.BackgroundType
import com.dipdev.autocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.autocaptioner.ui.styleeditor.AdvancedColorPicker
import com.dipdev.autocaptioner.ui.styleeditor.PremiumSlider

enum class ColorSubTool { TEXT, HIGHLIGHT, OUTLINE, BACKGROUND, BG_COLOR, PAD_H, PAD_V, CORNER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorTab(
    style: CaptionStyleEntity,
    onTextColorChange: (Long) -> Unit,
    onHighlightColorChange: (Long) -> Unit,
    onOutlineColorChange: (Long) -> Unit,
    onOutlineWidthChange: (Float) -> Unit,
    onBackgroundTypeChange: (BackgroundType) -> Unit,
    onBackgroundColorChange: (Long) -> Unit,
    onBackgroundOpacityChange: (Float) -> Unit,
    onBackgroundPaddingHChange: (Float) -> Unit,
    onBackgroundPaddingVChange: (Float) -> Unit,
    onBackgroundCornerRadiusChange: (Float) -> Unit,
    onShadowRadiusChange: (Float) -> Unit,
    onShadowColorChange: (Long) -> Unit,
) {
    var activeTool by remember { mutableStateOf<ColorSubTool?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                    IconButton(onClick = { activeTool = ColorSubTool.TEXT }) { Icon(Icons.Default.FormatColorText, "Text Color") }
                    Text("Text", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { activeTool = ColorSubTool.OUTLINE }) { Icon(Icons.Default.BorderColor, "Outline") }
                    Text("Outline", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { activeTool = ColorSubTool.HIGHLIGHT }) { Icon(Icons.Default.Highlight, "Highlight") }
                    Text("Highlight", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { activeTool = ColorSubTool.BACKGROUND }) { Icon(Icons.Default.BorderColor, "Bg Style") }
                    Text("Bg Style", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { activeTool = ColorSubTool.BG_COLOR }) { Icon(Icons.Default.FormatColorFill, "Bg Color") }
                    Text("Bg Color", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { activeTool = ColorSubTool.PAD_H }) { Icon(Icons.Default.AlignHorizontalCenter, "Padding H") }
                    Text("Pad H", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { activeTool = ColorSubTool.PAD_V }) { Icon(Icons.Default.AlignVerticalCenter, "Padding V") }
                    Text("Pad V", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { activeTool = ColorSubTool.CORNER }) { Icon(Icons.Default.RoundedCorner, "Corner Radius") }
                    Text("Corners", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (activeTool != null) {
        ModalBottomSheet(
            onDismissRequest = { activeTool = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 16.dp, bottom = 48.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        when (activeTool) {
                            ColorSubTool.TEXT -> {
                                AdvancedColorPicker(initialColor = style.textColor, onColorChanged = onTextColorChange)
                            }
                            ColorSubTool.HIGHLIGHT -> {
                                AdvancedColorPicker(initialColor = style.highlightColor, onColorChanged = onHighlightColorChange)
                            }
                            ColorSubTool.OUTLINE -> {
                                Column {
                                    Text("Width", fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                                    PremiumSlider(
                                        value = style.outlineWidth,
                                        onValueChange = onOutlineWidthChange,
                                        valueRange = 0f..10f,
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                                    )
                                    AdvancedColorPicker(initialColor = style.outlineColor, onColorChanged = onOutlineColorChange)
                                }
                            }
                            ColorSubTool.BACKGROUND -> {
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .horizontalScroll(rememberScrollState())
                                            .padding(bottom = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        BackgroundType.entries.forEach { mode ->
                                            FilterChip(
                                                selected = style.backgroundType == mode,
                                                onClick = { onBackgroundTypeChange(mode) },
                                                label = { Text(mode.name.split('_').joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercaseChar() } }, fontSize = 12.sp) },
                                                modifier = Modifier.padding(end = 4.dp)
                                            )
                                        }
                                    }
                                    if (style.backgroundType != BackgroundType.NONE) {
                                        Text("Opacity", fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                                        PremiumSlider(
                                            value = style.backgroundOpacity,
                                            onValueChange = onBackgroundOpacityChange,
                                            valueRange = 0f..1f,
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                                        )
                                    }
                                }
                            }
                            ColorSubTool.BG_COLOR -> {
                                AdvancedColorPicker(initialColor = style.backgroundColor, onColorChanged = onBackgroundColorChange)
                            }
                            ColorSubTool.PAD_H -> {
                                Column {
                                    Text("Padding H: ${style.backgroundPaddingH.toInt()}", fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                                    PremiumSlider(style.backgroundPaddingH, onBackgroundPaddingHChange, 0f..40f, Modifier.fillMaxWidth())
                                }
                            }
                            ColorSubTool.PAD_V -> {
                                Column {
                                    Text("Padding V: ${style.backgroundPaddingV.toInt()}", fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                                    PremiumSlider(style.backgroundPaddingV, onBackgroundPaddingVChange, 0f..40f, Modifier.fillMaxWidth())
                                }
                            }
                            ColorSubTool.CORNER -> {
                                Column {
                                    Text("Corner Radius: ${style.backgroundCornerRadius.toInt()}", fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                                    PremiumSlider(style.backgroundCornerRadius, onBackgroundCornerRadiusChange, 0f..60f, Modifier.fillMaxWidth())
                                }
                            }
                            null -> {}
                        }
                    }
                    
                    TextButton(onClick = { activeTool = null }, modifier = Modifier.padding(start = 8.dp)) {
                        Text("Done", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
"""

with open("app/src/main/java/com/dipdev/autocaptioner/ui/styleeditor/tabs/ColorTab.kt", "w") as f:
    f.write(content)
