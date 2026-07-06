package com.dipdev.aiautocaptioner.ui.styleeditor.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.data.db.entity.TextAlignment
import com.dipdev.aiautocaptioner.ui.styleeditor.PremiumSlider
import com.dipdev.aiautocaptioner.ui.styleeditor.SubToolButton
import com.dipdev.aiautocaptioner.ui.styleeditor.LabeledPremiumSlider

enum class TextSubTool { SIZE, WORDS_PER_LINE, MAX_LINES, WEIGHT, ALIGNMENT, PUNCTUATION, ITALIC, SPACING }

@Composable
fun TextTab(
    style: CaptionStyleEntity,
    onFontSizeChange: (Float) -> Unit,
    onFontWeightChange: (Int) -> Unit,
    onMaxWordsChange: (Int) -> Unit,
    onMaxLinesChange: (Int) -> Unit,
    onRemovePunctuationChange: (Boolean) -> Unit,
    onAlignmentChange: (TextAlignment) -> Unit,
    onLetterSpacingChange: (Float) -> Unit,
    onIsItalicChange: (Boolean) -> Unit,
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
            item { SubToolButton(Icons.Default.FormatSize, "Size") { activeTool = TextSubTool.SIZE } }
            item { SubToolButton(Icons.Default.LineWeight, "Weight") { activeTool = TextSubTool.WEIGHT } }
            item { SubToolButton(Icons.AutoMirrored.Filled.FormatAlignLeft, "Align") { activeTool = TextSubTool.ALIGNMENT } }
            item { SubToolButton(Icons.Default.ViewHeadline, "Words") { activeTool = TextSubTool.WORDS_PER_LINE } }
            item { SubToolButton(Icons.Default.Menu, "Max Lines") { activeTool = TextSubTool.MAX_LINES } }
            item { SubToolButton(Icons.Default.TextFormat, "Symbols") { activeTool = TextSubTool.PUNCTUATION } }
            item { SubToolButton(Icons.Default.FormatItalic, "Italic") { activeTool = TextSubTool.ITALIC } }
            item { SubToolButton(Icons.Default.SpaceBar, "Spacing") { activeTool = TextSubTool.SPACING } }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (activeTool) {
                TextSubTool.SIZE -> {
                    LabeledPremiumSlider(
                        label = "Size",
                        value = style.fontSize,
                        onValueChange = onFontSizeChange,
                        valueRange = 24f..96f,
                        modifier = Modifier.weight(1f)
                    )
                }
                TextSubTool.WORDS_PER_LINE -> {
                    LabeledPremiumSlider(
                        label = "Words",
                        value = style.maxWordsPerLine.toFloat(),
                        onValueChange = { onMaxWordsChange(it.toInt()) },
                        valueRange = 1f..10f,
                        modifier = Modifier.weight(1f)
                    )
                }
                TextSubTool.MAX_LINES -> {
                    LabeledPremiumSlider(
                        label = "Lines",
                        value = style.maxLines.toFloat(),
                        onValueChange = { onMaxLinesChange(it.toInt()) },
                        valueRange = 1f..10f,
                        modifier = Modifier.weight(1f)
                    )
                }
                TextSubTool.WEIGHT -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        listOf(400 to "Light", 700 to "Bold", 900 to "Black").forEach { (weight, label) ->
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
                    Text("Strip Punctuation", fontSize = 12.sp)
                    Switch(
                        checked = style.removePunctuation,
                        onCheckedChange = { onRemovePunctuationChange(it) },
                        modifier = Modifier.padding(start = 16.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
                TextSubTool.ITALIC -> {
                    Text("Italic", fontSize = 12.sp)
                    Switch(
                        checked = style.isItalic,
                        onCheckedChange = { onIsItalicChange(it) },
                        modifier = Modifier.padding(start = 16.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
                TextSubTool.SPACING -> {
                    LabeledPremiumSlider(
                        label = "Spacing",
                        value = style.letterSpacing,
                        onValueChange = onLetterSpacingChange,
                        valueRange = 0f..0.3f,
                        modifier = Modifier.weight(1f)
                    )
                }
                null -> {}
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            TextButton(onClick = { activeTool = null }) {
                Text("Done", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
