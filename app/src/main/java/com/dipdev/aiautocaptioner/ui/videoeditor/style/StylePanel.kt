package com.dipdev.aiautocaptioner.ui.videoeditor.style

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipdev.aiautocaptioner.R

@Composable
fun StylePanel(
    viewModel: StyleViewModel,
    timelineHeight: Dp,
    maxTimelineHeight: Dp,
    onTimelineHeightChanged: (Dp) -> Unit,
    modifier: Modifier = Modifier,
    onGenerateCaptions: () -> Unit = {},
    selectedLanguage: String = "en",
    translateToEnglish: Boolean = false,
    onLanguageSelected: (String, Boolean) -> Unit = { _, _ -> },
    onAdjustExpanded: ((Boolean) -> Unit)? = null,
    allowedLanguages: List<String> = listOf("multilingual"),
) {
    val styleUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val styles = styleUiState.styles
    val activeStyle = styleUiState.activeStyle
    val hasCaptions = styleUiState.segments.isNotEmpty()
    val density = LocalDensity.current
    val currentTimelineHeight by rememberUpdatedState(timelineHeight)
    var showAdjust by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Drag handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            val dragAmountDp = with(density) { dragAmount.toDp() }
                            onTimelineHeightChanged(
                                (currentTimelineHeight - dragAmountDp).coerceIn(200.dp, maxTimelineHeight)
                            )
                        }
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                )
            }

            activeStyle?.let { style ->
                if (!hasCaptions) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        Text(
                            text = "No captions yet",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.material3.Button(
                            onClick = onGenerateCaptions,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = com.dipdev.aiautocaptioner.ui.theme.AccentAmber,
                                contentColor = com.dipdev.aiautocaptioner.ui.theme.TextPrimary
                            )
                        ) {
                            Text("Generate Captions", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Captions exist — show full editor
                    CompactCaptionsHeader(
                        hasCaptions = hasCaptions,
                        showAdjust = showAdjust,
                        onToggleAdjust = { showAdjust = it },
                        displayMode = style.displayMode,
                        showLayoutControls = com.dipdev.aiautocaptioner.engine.style.StyleCapabilityResolver.resolve(style).showLayoutSliders,
                        fontSize = style.fontSize,
                        textThickness = style.textThickness,
                        maxWordsPerLine = style.maxWordsPerLine,
                        maxLines = style.maxLines,
                        positionY = style.positionY,
                        onFontSizeChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("fontSize") { s -> s.copy(fontSize = it) }) },
                        onTextThicknessChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("textThickness") { s -> s.copy(textThickness = it) }) },
                        onMaxWordsChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("maxWords") { s -> s.copy(maxWordsPerLine = it) }) },
                        onMaxLinesChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("maxLines") { s -> s.copy(maxLines = it) }) },
                        onPositionYChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("positionY") { s -> s.copy(positionY = it) }) },
                        onGenerateCaptions = onGenerateCaptions,
                        textColor = style.textColor,
                        backgroundColor = style.backgroundColor,
                        activeWordBgColor = style.activeWordBgColor,
                        activeWordTextColor = style.activeWordTextColor,
                        onColorChanged = { field, color ->
                            viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("color") { s ->
                                when (field) {
                                    CaptionColorField.TEXT -> s.copy(textColor = color, highlightColor = color)
                                    CaptionColorField.BACKGROUND -> s.copy(backgroundColor = color)
                                    CaptionColorField.ACTIVE_BG -> s.copy(activeWordBgColor = color)
                                    CaptionColorField.ACTIVE_TEXT -> s.copy(activeWordTextColor = color)
                                }
                            })
                        }
                    )

                    PresetsTab(
                        styles = styles,
                        activeStyle = style,
                        onPresetSelected = { viewModel.setEvent(StyleEditorUiEvent.SelectPreset(it)) },
                        onPresetLongClicked = { },
                        onAddPreset = { }
                    )
                }
            } ?: run {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

// ── Compact header: Adjust pill + Generate button ──────────────────────────

@Composable
private fun CompactCaptionsHeader(
    hasCaptions: Boolean,
    showAdjust: Boolean,
    onToggleAdjust: (Boolean) -> Unit,
    displayMode: com.dipdev.aiautocaptioner.data.db.entity.DisplayMode,
    showLayoutControls: Boolean,
    fontSize: Float,
    textThickness: Float,
    maxWordsPerLine: Int,
    maxLines: Int,
    positionY: Float,
    onFontSizeChange: (Float) -> Unit,
    onTextThicknessChange: (Float) -> Unit,
    onMaxWordsChange: (Int) -> Unit,
    onMaxLinesChange: (Int) -> Unit,
    onPositionYChange: (Float) -> Unit,
    onGenerateCaptions: () -> Unit,
    textColor: Long,
    backgroundColor: Long,
    activeWordBgColor: Long,
    activeWordTextColor: Long,
    onColorChanged: (CaptionColorField, Long) -> Unit
) {
    var showColor by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Adjust pill — opens adjust controls popup
        Surface(
            onClick = {
                onToggleAdjust(!showAdjust)
                if (!showAdjust) showColor = false
            },
            shape = RoundedCornerShape(8.dp),
            color = if (showAdjust) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (showAdjust) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = stringResource(if (showAdjust) R.string.style_adjust_expand else R.string.style_adjust_collapse),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (showAdjust) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }

        // Color pill — opens color picker popup
        Surface(
            onClick = {
                showColor = !showColor
                if (showColor) onToggleAdjust(false)
            },
            shape = RoundedCornerShape(8.dp),
            color = if (showColor) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ColorLens,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (showColor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = stringResource(R.string.style_color),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (showColor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Surface(
            onClick = onGenerateCaptions,
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary
        ) {
            Text(
                text = stringResource(if (hasCaptions) R.string.style_regenerate else R.string.style_generate),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }

    // Adjust controls dropdown as Popup
    if (showAdjust) {
        Popup(
            alignment = Alignment.TopCenter,
            onDismissRequest = { onToggleAdjust(false) },
            properties = PopupProperties(focusable = true)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 6.dp,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                modifier = Modifier
                    .padding(top = 44.dp)
                    .widthIn(max = 340.dp)
                    .fillMaxWidth(0.95f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Size slider
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.style_size),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.width(36.dp)
                        )
                        PremiumSlider(
                            value = fontSize,
                            onValueChange = onFontSizeChange,
                            valueRange = 12f..160f,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${fontSize.toInt()}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            modifier = Modifier.widthIn(min = 32.dp)
                        )
                    }

                    // Thickness (synthetic emboldening)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.style_thickness),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.width(64.dp)
                        )
                        PremiumSlider(
                            value = textThickness,
                            onValueChange = onTextThicknessChange,
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(textThickness * 100).toInt()}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            modifier = Modifier.widthIn(min = 36.dp)
                        )
                    }

                    val showMaxWords = showLayoutControls && com.dipdev.aiautocaptioner.engine.DisplayModeBehavior.isControlVisible(
                        com.dipdev.aiautocaptioner.engine.DisplayModeBehavior.StyleControl.MAX_WORDS_PER_LINE, displayMode)
                    val showMaxLines = showLayoutControls && com.dipdev.aiautocaptioner.engine.DisplayModeBehavior.isControlVisible(
                        com.dipdev.aiautocaptioner.engine.DisplayModeBehavior.StyleControl.MAX_LINES, displayMode)

                    // Words + Lines (hidden for WORD_BY_WORD mode)
                    if (showMaxWords || showMaxLines) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (showMaxWords) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.style_words),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.width(40.dp)
                                    )
                                    StepperControl(
                                        value = maxWordsPerLine,
                                        range = 1..10,
                                        onValueChange = onMaxWordsChange
                                    )
                                }
                            }
                            if (showMaxLines) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.style_lines),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.width(32.dp)
                                    )
                                    StepperControl(
                                        value = maxLines,
                                        range = 1..5,
                                        onValueChange = onMaxLinesChange
                                    )
                                }
                            }
                        }
                    }

                    // Position
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.style_pos),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.width(36.dp)
                        )
                        PremiumSlider(
                            value = positionY,
                            onValueChange = onPositionYChange,
                            valueRange = 0.05f..0.95f,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(positionY * 100).toInt()}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            modifier = Modifier.widthIn(min = 36.dp)
                        )
                    }
                }
            }
        }
    }

    if (showColor) {
        Popup(
            alignment = Alignment.TopCenter,
            onDismissRequest = { showColor = false },
            properties = PopupProperties(focusable = true)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                modifier = Modifier
                    .padding(top = 44.dp)
                    .widthIn(max = 340.dp)
                    .fillMaxWidth(0.95f)
            ) {
                ColorPickerPopupContent(
                    textColor = textColor,
                    backgroundColor = backgroundColor,
                    activeWordBgColor = activeWordBgColor,
                    activeWordTextColor = activeWordTextColor,
                    onColorChanged = onColorChanged
                )
            }
        }
    }
}


// ── Stepper ─────────────────────────────────────────────────────────────────

@Composable
private fun StepperControl(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    val shape = androidx.compose.foundation.shape.CircleShape
    val containerColor = MaterialTheme.colorScheme.surfaceVariant

    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = shape,
            color = containerColor,
            modifier = Modifier.size(30.dp),
            onClick = { if (value > range.first) onValueChange(value - 1) }
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.style_decrease), modifier = Modifier.size(14.dp))
            }
        }

        Text(
            text = "$value",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Surface(
            shape = shape,
            color = containerColor,
            modifier = Modifier.size(30.dp),
            onClick = { if (value < range.last) onValueChange(value + 1) }
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.style_increase), modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ── Color picker popup ───────────────────────────────────────────────────────

/** Which caption color the user is currently editing in the color popup. */
enum class CaptionColorField {
    TEXT,
    BACKGROUND,
    ACTIVE_BG,
    ACTIVE_TEXT
}

@Composable
private fun ColorPickerPopupContent(
    textColor: Long,
    backgroundColor: Long,
    activeWordBgColor: Long,
    activeWordTextColor: Long,
    onColorChanged: (CaptionColorField, Long) -> Unit
) {
    var selectedField by remember { mutableStateOf(CaptionColorField.TEXT) }

    val currentColor = when (selectedField) {
        CaptionColorField.TEXT -> textColor
        CaptionColorField.BACKGROUND -> backgroundColor
        CaptionColorField.ACTIVE_BG -> activeWordBgColor
        CaptionColorField.ACTIVE_TEXT -> activeWordTextColor
    }

    Column(modifier = Modifier.padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ColorFieldSwatch(
                label = stringResource(R.string.color_tab_text),
                color = textColor,
                selected = selectedField == CaptionColorField.TEXT,
                onClick = { selectedField = CaptionColorField.TEXT },
                modifier = Modifier.weight(1f)
            )
            ColorFieldSwatch(
                label = stringResource(R.string.color_tab_bg_color),
                color = backgroundColor,
                selected = selectedField == CaptionColorField.BACKGROUND,
                onClick = { selectedField = CaptionColorField.BACKGROUND },
                modifier = Modifier.weight(1f)
            )
            ColorFieldSwatch(
                label = stringResource(R.string.color_tab_active_bg),
                color = activeWordBgColor,
                selected = selectedField == CaptionColorField.ACTIVE_BG,
                onClick = { selectedField = CaptionColorField.ACTIVE_BG },
                modifier = Modifier.weight(1f)
            )
            ColorFieldSwatch(
                label = stringResource(R.string.color_tab_active_text),
                color = activeWordTextColor,
                selected = selectedField == CaptionColorField.ACTIVE_TEXT,
                onClick = { selectedField = CaptionColorField.ACTIVE_TEXT },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // key(selectedField) resets the picker's internal state when switching fields
        key(selectedField) {
            AdvancedColorPicker(
                initialColor = currentColor,
                onColorChanged = { onColorChanged(selectedField, it) }
            )
        }
    }
}

@Composable
private fun ColorFieldSwatch(
    label: String,
    color: Long,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = androidx.compose.foundation.shape.CircleShape
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 1f else 0.6f),
            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Medium else androidx.compose.ui.text.font.FontWeight.Normal,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(shape)
                .background(Color(color))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                    shape = shape
                )
                .clickable(onClick = onClick)
        )
    }
}
