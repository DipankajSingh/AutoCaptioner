package com.dipdev.aiautocaptioner.ui.videoeditor.style.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.data.db.entity.TextAlignment
import com.dipdev.aiautocaptioner.data.db.entity.TextTransform
import com.dipdev.aiautocaptioner.ui.videoeditor.style.SubToolButton
import com.dipdev.aiautocaptioner.ui.videoeditor.style.LabeledPremiumSlider

enum class TextSubTool { SIZE, WORDS_PER_LINE, MAX_LINES, WEIGHT, ALIGNMENT, PUNCTUATION, ITALIC, SPACING, FONT, OPACITY, TRANSFORM, LINE_HEIGHT, POSITION }

@Composable
fun TextTab(
    style: CaptionStyleEntity,
    onFontFamilyChange: (String) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onFontWeightChange: (Int) -> Unit,
    onMaxWordsChange: (Int) -> Unit,
    onMaxLinesChange: (Int) -> Unit,
    onRemovePunctuationChange: (Boolean) -> Unit,
    onAlignmentChange: (TextAlignment) -> Unit,
    onLetterSpacingChange: (Float) -> Unit,
    onIsItalicChange: (Boolean) -> Unit,
    onTextOpacityChange: (Float) -> Unit,
    onTextTransformChange: (TextTransform) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onPositionXChange: (Float) -> Unit,
    onPositionYChange: (Float) -> Unit,
) {
    var activeTool by remember { mutableStateOf<TextSubTool?>(null) }

    if (activeTool == null) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            item { SubToolButton(FeatherIcons.FileText, stringResource(R.string.text_tab_font)) { activeTool = TextSubTool.FONT } }
            item { SubToolButton(FeatherIcons.Type, stringResource(R.string.text_tab_size)) { activeTool = TextSubTool.SIZE } }
            item { SubToolButton(FeatherIcons.Bold, stringResource(R.string.text_tab_weight)) { activeTool = TextSubTool.WEIGHT } }
            item { SubToolButton(FeatherIcons.AlignLeft, stringResource(R.string.text_tab_align)) { activeTool = TextSubTool.ALIGNMENT } }
            item { SubToolButton(FeatherIcons.Minimize2, stringResource(R.string.text_tab_opacity)) { activeTool = TextSubTool.OPACITY } }
            item { SubToolButton(FeatherIcons.Edit2, stringResource(R.string.text_tab_case)) { activeTool = TextSubTool.TRANSFORM } }
            item { SubToolButton(FeatherIcons.Move, stringResource(R.string.text_tab_position)) { activeTool = TextSubTool.POSITION } }
            item { SubToolButton(FeatherIcons.AlignJustify, stringResource(R.string.text_tab_words)) { activeTool = TextSubTool.WORDS_PER_LINE } }
            item { SubToolButton(FeatherIcons.List, stringResource(R.string.text_tab_max_lines)) { activeTool = TextSubTool.MAX_LINES } }
            item { SubToolButton(FeatherIcons.Maximize2, stringResource(R.string.text_tab_line_ht)) { activeTool = TextSubTool.LINE_HEIGHT } }
            item { SubToolButton(FeatherIcons.Hash, stringResource(R.string.text_tab_symbols)) { activeTool = TextSubTool.PUNCTUATION } }
            item { SubToolButton(FeatherIcons.Italic, stringResource(R.string.text_tab_italic)) { activeTool = TextSubTool.ITALIC } }
            item { SubToolButton(FeatherIcons.Maximize, stringResource(R.string.text_tab_spacing)) { activeTool = TextSubTool.SPACING } }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (activeTool) {
                TextSubTool.FONT -> {
                    Box(modifier = Modifier.weight(1f)) {
                        FontPickerSheet(
                            currentFont = style.fontFamily,
                            onFontSelected = { onFontFamilyChange(it) },
                            onDismiss = { activeTool = null }
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    TextButton(onClick = { activeTool = null }) {
                        Text(stringResource(R.string.text_tab_done), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                TextSubTool.SIZE -> {
                    LabeledPremiumSlider(
                        label = stringResource(R.string.text_tab_size),
                        value = style.fontSize,
                        onValueChange = onFontSizeChange,
                        valueRange = 24f..96f,
                        modifier = Modifier.weight(1f)
                    )
                }
                TextSubTool.WORDS_PER_LINE -> {
                    LabeledPremiumSlider(
                        label = stringResource(R.string.text_tab_words),
                        value = style.maxWordsPerLine.toFloat(),
                        onValueChange = { onMaxWordsChange(it.toInt()) },
                        valueRange = 1f..10f,
                        modifier = Modifier.weight(1f)
                    )
                }
                TextSubTool.MAX_LINES -> {
                    LabeledPremiumSlider(
                        label = stringResource(R.string.style_lines),
                        value = style.maxLines.toFloat(),
                        onValueChange = { onMaxLinesChange(it.toInt()) },
                        valueRange = 1f..10f,
                        modifier = Modifier.weight(1f)
                    )
                }
                TextSubTool.WEIGHT -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        listOf(400 to stringResource(R.string.text_tab_light), 700 to stringResource(R.string.text_tab_bold), 900 to stringResource(R.string.text_tab_black)).forEach { (weight, label) ->
                            FilterChip(
                                selected = style.fontWeight == weight,
                                onClick = { onFontWeightChange(weight) },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                }
                TextSubTool.ALIGNMENT -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        TextAlignment.entries.forEach { align ->
                            FilterChip(
                                selected = style.alignment == align,
                                onClick = { onAlignmentChange(align) },
                                label = { Text(align.name.split('_').joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercaseChar() } }, fontSize = 12.sp) }
                            )
                        }
                    }
                }
                TextSubTool.PUNCTUATION -> {
                    Text(stringResource(R.string.text_tab_strip_punctuation), fontSize = 12.sp)
                    Switch(
                        checked = style.removePunctuation,
                        onCheckedChange = { onRemovePunctuationChange(it) },
                        modifier = Modifier.padding(start = 16.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
                TextSubTool.ITALIC -> {
                    Text(stringResource(R.string.text_tab_italic), fontSize = 12.sp)
                    Switch(
                        checked = style.isItalic,
                        onCheckedChange = { onIsItalicChange(it) },
                        modifier = Modifier.padding(start = 16.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
                TextSubTool.SPACING -> {
                    LabeledPremiumSlider(
                        label = stringResource(R.string.text_tab_spacing),
                        value = style.letterSpacing,
                        onValueChange = onLetterSpacingChange,
                        valueRange = 0f..0.3f,
                        modifier = Modifier.weight(1f)
                    )
                }
                TextSubTool.OPACITY -> {
                    LabeledPremiumSlider(
                        label = stringResource(R.string.text_tab_opacity),
                        value = style.textOpacity,
                        onValueChange = onTextOpacityChange,
                        valueRange = 0.1f..1f,
                        modifier = Modifier.weight(1f)
                    )
                }
                TextSubTool.TRANSFORM -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        TextTransform.entries.forEach { transform ->
                            FilterChip(
                                selected = style.textTransform == transform,
                                onClick = { onTextTransformChange(transform) },
                                label = { Text(
                                    when (transform) {
                                        TextTransform.NONE -> stringResource(R.string.text_tab_transform_none)
                                        TextTransform.UPPERCASE -> stringResource(R.string.text_tab_transform_uppercase)
                                        TextTransform.LOWERCASE -> "abc"
                                        TextTransform.TITLE_CASE -> stringResource(R.string.text_tab_transform_titlecase)
                                        TextTransform.SENTENCE_CASE -> stringResource(R.string.text_tab_transform_sentence)
                                    },
                                    fontSize = 12.sp
                                ) }
                            )
                        }
                    }
                }
                TextSubTool.LINE_HEIGHT -> {
                    LabeledPremiumSlider(
                        label = stringResource(R.string.text_tab_line_height),
                        value = style.lineHeight,
                        onValueChange = onLineHeightChange,
                        valueRange = 0.8f..2.5f,
                        modifier = Modifier.weight(1f)
                    )
                }
                TextSubTool.POSITION -> {
                    Column(modifier = Modifier.weight(1f)) {
                        LabeledPremiumSlider(
                            label = stringResource(R.string.text_tab_x_position),
                            value = style.positionX,
                            onValueChange = onPositionXChange,
                            valueRange = 0.05f..0.95f
                        )
                        LabeledPremiumSlider(
                            label = stringResource(R.string.text_tab_y_position),
                            value = style.positionY,
                            onValueChange = onPositionYChange,
                            valueRange = 0.05f..0.95f
                        )
                    }
                }
                null -> {}
            }
            
            if (activeTool != TextSubTool.FONT) {
                Spacer(modifier = Modifier.width(16.dp))
                TextButton(onClick = { activeTool = null }) {
                    Text(stringResource(R.string.text_tab_done), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
