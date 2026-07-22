package com.dipdev.aiautocaptioner.ui.videoeditor.style.tabs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import compose.icons.FeatherIcons
import compose.icons.feathericons.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.data.db.entity.BackgroundType
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.data.db.entity.GradientDirection
import com.dipdev.aiautocaptioner.ui.videoeditor.style.AdvancedColorPicker
import com.dipdev.aiautocaptioner.ui.videoeditor.style.SubToolButton
import com.dipdev.aiautocaptioner.ui.videoeditor.style.LabeledPremiumSlider

enum class ColorSubTool {
    TEXT, HIGHLIGHT, OUTLINE, SHADOW, GRADIENT, GLOW,
    BACKGROUND, BG_COLOR, PAD_H, PAD_V, CORNER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorTab(
    style: CaptionStyleEntity,
    onTextColorChange: (Long) -> Unit,
    onHighlightColorChange: (Long) -> Unit,
    onOutlineColorChange: (Long) -> Unit,
    onOutlineWidthChange: (Float) -> Unit,
    onOutlineOnlyChange: (Boolean) -> Unit,
    onShadowColorChange: (Long) -> Unit,
    onShadowRadiusChange: (Float) -> Unit,
    onShadowOffsetXChange: (Float) -> Unit,
    onShadowOffsetYChange: (Float) -> Unit,
    onGradientDirectionChange: (GradientDirection) -> Unit,
    onSecondaryColorChange: (Long) -> Unit,
    onGlowEnabledChange: (Boolean) -> Unit,
    onGlowColorChange: (Long) -> Unit,
    onGlowRadiusChange: (Float) -> Unit,
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
            item { SubToolButton(FeatherIcons.Type, stringResource(R.string.color_tab_text)) { activeTool = ColorSubTool.TEXT } }
            item { SubToolButton(FeatherIcons.Edit2, stringResource(R.string.color_tab_outline)) { activeTool = ColorSubTool.OUTLINE } }
            item { SubToolButton(FeatherIcons.Sun, stringResource(R.string.color_tab_highlight)) { activeTool = ColorSubTool.HIGHLIGHT } }
            item { SubToolButton(FeatherIcons.Moon, stringResource(R.string.color_tab_shadow)) { activeTool = ColorSubTool.SHADOW } }
            item { SubToolButton(FeatherIcons.Droplet, stringResource(R.string.color_tab_gradient)) { activeTool = ColorSubTool.GRADIENT } }
            item { SubToolButton(FeatherIcons.Star, stringResource(R.string.color_tab_glow)) { activeTool = ColorSubTool.GLOW } }
            item { SubToolButton(FeatherIcons.Box, stringResource(R.string.color_tab_bg_style)) { activeTool = ColorSubTool.BACKGROUND } }
            item { SubToolButton(FeatherIcons.Droplet, stringResource(R.string.color_tab_bg_color)) { activeTool = ColorSubTool.BG_COLOR } }
            item { SubToolButton(FeatherIcons.Maximize2, stringResource(R.string.color_tab_pad_h)) { activeTool = ColorSubTool.PAD_H } }
            item { SubToolButton(FeatherIcons.Minimize2, stringResource(R.string.color_tab_pad_v)) { activeTool = ColorSubTool.PAD_V } }
            item { SubToolButton(FeatherIcons.Square, stringResource(R.string.color_tab_corners)) { activeTool = ColorSubTool.CORNER } }
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
                                    Row(
                                        modifier = Modifier.padding(bottom = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(stringResource(R.string.color_tab_outline_only), fontSize = 12.sp, modifier = Modifier.weight(1f))
                                        Switch(
                                            checked = style.outlineOnly,
                                            onCheckedChange = onOutlineOnlyChange
                                        )
                                    }
                                    LabeledPremiumSlider(
                                        label = stringResource(R.string.color_tab_width),
                                        value = style.outlineWidth,
                                        onValueChange = onOutlineWidthChange,
                                        valueRange = 0f..10f,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )
                                    AdvancedColorPicker(initialColor = style.outlineColor, onColorChanged = onOutlineColorChange)
                                }
                            }
                            ColorSubTool.SHADOW -> {
                                Column {
                                    AdvancedColorPicker(initialColor = style.shadowColor, onColorChanged = onShadowColorChange)
                                    LabeledPremiumSlider(
                                        label = stringResource(R.string.color_tab_radius),
                                        value = style.shadowRadius,
                                        onValueChange = onShadowRadiusChange,
                                        valueRange = 0f..20f,
                                        modifier = Modifier.padding(top = 16.dp)
                                    )
                                    LabeledPremiumSlider(
                                        label = stringResource(R.string.color_tab_offset_x),
                                        value = style.shadowOffsetX,
                                        onValueChange = onShadowOffsetXChange,
                                        valueRange = -10f..10f
                                    )
                                    LabeledPremiumSlider(
                                        label = stringResource(R.string.color_tab_offset_y),
                                        value = style.shadowOffsetY,
                                        onValueChange = onShadowOffsetYChange,
                                        valueRange = -10f..10f
                                    )
                                }
                            }
                            ColorSubTool.GRADIENT -> {
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .horizontalScroll(rememberScrollState())
                                            .padding(bottom = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        GradientDirection.entries.forEach { dir ->
                                            FilterChip(
                                                selected = style.gradientDirection == dir,
                                                onClick = { onGradientDirectionChange(dir) },
                                                label = { Text(
                                                    when (dir) {
                                                        GradientDirection.NONE -> stringResource(R.string.color_tab_gradient_none)
                                                        GradientDirection.LEFT_RIGHT -> "→"
                                                        GradientDirection.TOP_BOTTOM -> "↓"
                                                        GradientDirection.DIAGONAL -> "↘"
                                                    },
                                                    fontSize = 12.sp
                                                ) }
                                            )
                                        }
                                    }
                                    if (style.gradientDirection != GradientDirection.NONE) {
                                        Text(stringResource(R.string.color_tab_secondary_color), fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                                        AdvancedColorPicker(initialColor = style.secondaryColor, onColorChanged = onSecondaryColorChange)
                                    }
                                }
                            }
                            ColorSubTool.GLOW -> {
                                Column {
                                    Row(
                                        modifier = Modifier.padding(bottom = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(stringResource(R.string.color_tab_enable_glow), fontSize = 12.sp, modifier = Modifier.weight(1f))
                                        Switch(
                                            checked = style.glowEnabled,
                                            onCheckedChange = onGlowEnabledChange
                                        )
                                    }
                                    if (style.glowEnabled) {
                                        AdvancedColorPicker(initialColor = style.glowColor, onColorChanged = onGlowColorChange)
                                        LabeledPremiumSlider(
                                            label = stringResource(R.string.color_tab_glow_radius),
                                            value = style.glowRadius,
                                            onValueChange = onGlowRadiusChange,
                                            valueRange = 2f..30f,
                                            modifier = Modifier.padding(top = 16.dp)
                                        )
                                    }
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
                                            label = stringResource(R.string.text_tab_opacity),
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
                                    label = stringResource(R.string.color_tab_pad_h),
                                    value = style.backgroundPaddingH,
                                    onValueChange = onBackgroundPaddingHChange,
                                    valueRange = 0f..40f
                                )
                            }
                            ColorSubTool.PAD_V -> {
                                LabeledPremiumSlider(
                                    label = stringResource(R.string.color_tab_pad_v),
                                    value = style.backgroundPaddingV,
                                    onValueChange = onBackgroundPaddingVChange,
                                    valueRange = 0f..40f
                                )
                            }
                            ColorSubTool.CORNER -> {
                                LabeledPremiumSlider(
                                    label = stringResource(R.string.color_tab_corners),
                                    value = style.backgroundCornerRadius,
                                    onValueChange = onBackgroundCornerRadiusChange,
                                    valueRange = 0f..60f
                                )
                            }
                            null -> {}
                        }
                    }

                    TextButton(onClick = { activeTool = null }, modifier = Modifier.padding(start = 8.dp)) {
                        Text(stringResource(R.string.text_tab_done), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}