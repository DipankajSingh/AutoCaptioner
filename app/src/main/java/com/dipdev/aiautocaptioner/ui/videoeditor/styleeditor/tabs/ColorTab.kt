package com.dipdev.aiautocaptioner.ui.videoeditor.styleeditor.tabs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlignHorizontalCenter
import androidx.compose.material.icons.filled.AlignVerticalCenter
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.data.db.entity.BackgroundType
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.ui.videoeditor.styleeditor.AdvancedColorPicker
import com.dipdev.aiautocaptioner.ui.videoeditor.styleeditor.SubToolButton
import com.dipdev.aiautocaptioner.ui.videoeditor.styleeditor.LabeledPremiumSlider

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
            item { SubToolButton(Icons.Default.FormatColorText, "Text") { activeTool = ColorSubTool.TEXT } }
            item { SubToolButton(Icons.Default.BorderColor, "Outline") { activeTool = ColorSubTool.OUTLINE } }
            item { SubToolButton(Icons.Default.Highlight, "Highlight") { activeTool = ColorSubTool.HIGHLIGHT } }
            item { SubToolButton(Icons.Default.FormatColorFill, "Bg Style") { activeTool = ColorSubTool.BACKGROUND } }
            item { SubToolButton(Icons.Default.Palette, "Bg Color") { activeTool = ColorSubTool.BG_COLOR } }
            item { SubToolButton(Icons.Default.AlignHorizontalCenter, "Pad H") { activeTool = ColorSubTool.PAD_H } }
            item { SubToolButton(Icons.Default.AlignVerticalCenter, "Pad V") { activeTool = ColorSubTool.PAD_V } }
            item { SubToolButton(Icons.Default.RoundedCorner, "Corners") { activeTool = ColorSubTool.CORNER } }
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
                                    LabeledPremiumSlider(
                                        label = "Width",
                                        value = style.outlineWidth,
                                        onValueChange = onOutlineWidthChange,
                                        valueRange = 0f..10f,
                                        modifier = Modifier.padding(bottom = 16.dp)
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
                                        LabeledPremiumSlider(
                                            label = "Opacity",
                                            value = style.backgroundOpacity,
                                            onValueChange = onBackgroundOpacityChange,
                                            valueRange = 0f..1f,
                                            modifier = Modifier.padding(bottom = 16.dp)
                                        )
                                    }
                                }
                            }
                            ColorSubTool.BG_COLOR -> {
                                AdvancedColorPicker(initialColor = style.backgroundColor, onColorChanged = onBackgroundColorChange)
                            }
                            ColorSubTool.PAD_H -> {
                                LabeledPremiumSlider(
                                    label = "Pad H",
                                    value = style.backgroundPaddingH,
                                    onValueChange = onBackgroundPaddingHChange,
                                    valueRange = 0f..40f
                                )
                            }
                            ColorSubTool.PAD_V -> {
                                LabeledPremiumSlider(
                                    label = "Pad V",
                                    value = style.backgroundPaddingV,
                                    onValueChange = onBackgroundPaddingVChange,
                                    valueRange = 0f..40f
                                )
                            }
                            ColorSubTool.CORNER -> {
                                LabeledPremiumSlider(
                                    label = "Corners",
                                    value = style.backgroundCornerRadius,
                                    onValueChange = onBackgroundCornerRadiusChange,
                                    valueRange = 0f..60f
                                )
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
