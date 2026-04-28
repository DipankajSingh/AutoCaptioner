package com.dipdev.autocaptioner.ui.styleeditor.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.autocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.autocaptioner.data.db.entity.TextAlignment
import com.dipdev.autocaptioner.ui.styleeditor.PremiumSlider

enum class TextSubTool { SIZE, WORDS_PER_LINE, MAX_LINES, WEIGHT, ALIGNMENT, PUNCTUATION }

@Composable
fun TextTab(
    style: CaptionStyleEntity,
    onFontSizeChange: (Float) -> Unit,
    onFontWeightChange: (Int) -> Unit,
    onMaxWordsChange: (Int) -> Unit,
    onMaxLinesChange: (Int) -> Unit,
    onRemovePunctuationChange: (Boolean) -> Unit,
    onAlignmentChange: (TextAlignment) -> Unit
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
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { activeTool = TextSubTool.SIZE }) { Icon(Icons.Default.FormatSize, "Size") }
                    Text("Size", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { activeTool = TextSubTool.WEIGHT }) { Icon(Icons.Default.LineWeight, "Weight") }
                    Text("Weight", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { activeTool = TextSubTool.ALIGNMENT }) { Icon(Icons.AutoMirrored.Filled.FormatAlignLeft, "Alignment") }
                    Text("Align", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { activeTool = TextSubTool.WORDS_PER_LINE }) { Icon(Icons.Default.ViewHeadline, "Words per line") }
                    Text("Words", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { activeTool = TextSubTool.MAX_LINES }) { Icon(Icons.Default.Menu, "Max Vertical Lines") }
                    Text("Max Lines", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { activeTool = TextSubTool.PUNCTUATION }) { Icon(Icons.Default.TextFormat, "Punctuation") }
                    Text("Symbols", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            when (activeTool) {
                TextSubTool.SIZE -> {
                    Text("Size", fontSize = 12.sp, modifier = Modifier.width(48.dp))
                    PremiumSlider(
                        value = style.fontSize,
                        onValueChange = onFontSizeChange,
                        valueRange = 24f..96f,
                        modifier = Modifier.weight(1f)
                    )
                }
                TextSubTool.WORDS_PER_LINE -> {
                    Text("Words", fontSize = 12.sp, modifier = Modifier.width(48.dp))
                    PremiumSlider(
                        value = style.maxWordsPerLine.toFloat(),
                        onValueChange = { onMaxWordsChange(it.toInt()) },
                        valueRange = 1f..10f,
                        modifier = Modifier.weight(1f)
                    )
                }
                TextSubTool.MAX_LINES -> {
                    Text("Lines", fontSize = 12.sp, modifier = Modifier.width(48.dp))
                    PremiumSlider(
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
                                label = { Text(align.name.lowercase().replaceFirstChar { it.uppercaseChar() }, fontSize = 12.sp) }
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
                null -> {}
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            TextButton(onClick = { activeTool = null }) {
                Text("Done", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
