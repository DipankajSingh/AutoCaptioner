package com.dipdev.aiautocaptioner.ui.videoeditor.style

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.dipdev.aiautocaptioner.ui.theme.AccentCyan

@Composable
fun StylePanel(
    viewModel: StyleViewModel,
    timelineHeight: Dp,
    maxTimelineHeight: Dp,
    onTimelineHeightChanged: (Dp) -> Unit,
    onGenerateCaptions: () -> Unit = {},
    selectedLanguage: String = "en",
    translateToEnglish: Boolean = false,
    onLanguageSelected: (String, Boolean) -> Unit = { _, _ -> },
    onAdjustExpanded: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val styleUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val styles = styleUiState.styles
    val activeStyle = styleUiState.activeStyle
    val hasCaptions = styleUiState.segments.isNotEmpty()
    val density = LocalDensity.current
    val currentTimelineHeight by rememberUpdatedState(timelineHeight)
    var showLanguageDropdown by remember { mutableStateOf(false) }
    var showAdjust by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        color = androidx.compose.ui.graphics.Color.Black,
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
                    // No captions yet — show a proper empty state with generate prompt
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(AccentCyan.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Subtitles,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = AccentCyan
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.style_empty_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.style_empty_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Surface(
                                onClick = onGenerateCaptions,
                                shape = RoundedCornerShape(12.dp),
                                color = AccentCyan
                            ) {
                                Text(
                                    text = stringResource(R.string.style_generate),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.Black,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Captions exist — show full editor
                    CompactCaptionsHeader(
                        hasCaptions = hasCaptions,
                        selectedLanguage = selectedLanguage,
                        translateToEnglish = translateToEnglish,
                        showLanguageDropdown = showLanguageDropdown,
                        onToggleLanguageDropdown = { showLanguageDropdown = it },
                        onLanguageSelected = onLanguageSelected,
                        onGenerateCaptions = onGenerateCaptions
                    )

                    PresetsTab(
                        styles = styles,
                        activeStyle = style,
                        onPresetSelected = { viewModel.setEvent(StyleEditorUiEvent.SelectPreset(it)) },
                        onPresetLongClicked = { },
                        onAddPreset = { }
                    )

                    CollapsibleAdjust(
                        expanded = showAdjust,
                        onToggle = {
                            showAdjust = it
                            onAdjustExpanded?.invoke(it)
                        },
                        fontSize = style.fontSize,
                        maxWordsPerLine = style.maxWordsPerLine,
                        maxLines = style.maxLines,
                        positionY = style.positionY,
                        onFontSizeChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("fontSize") { s -> s.copy(fontSize = it) }) },
                        onMaxWordsChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("maxWords") { s -> s.copy(maxWordsPerLine = it) }) },
                        onMaxLinesChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("maxLines") { s -> s.copy(maxLines = it) }) },
                        onPositionYChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("positionY") { s -> s.copy(positionY = it) }) }
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

// ── Compact header: Language pill + Generate button ──────────────────────────

@Composable
private fun CompactCaptionsHeader(
    hasCaptions: Boolean,
    selectedLanguage: String,
    translateToEnglish: Boolean,
    showLanguageDropdown: Boolean,
    onToggleLanguageDropdown: (Boolean) -> Unit,
    onLanguageSelected: (String, Boolean) -> Unit,
    onGenerateCaptions: () -> Unit
) {
    val languageName = when (selectedLanguage) {
        "en" -> stringResource(R.string.lang_english)
        "hi" -> stringResource(R.string.lang_hindi)
        "es" -> stringResource(R.string.lang_spanish)
        "fr" -> stringResource(R.string.lang_french)
        "de" -> stringResource(R.string.lang_german)
        "ja" -> stringResource(R.string.lang_japanese)
        "ko" -> stringResource(R.string.lang_korean)
        "pt" -> stringResource(R.string.lang_portuguese)
        "ru" -> stringResource(R.string.lang_russian)
        "ar" -> stringResource(R.string.lang_arabic)
        "multilingual" -> stringResource(R.string.lang_auto_detect_label)
        else -> selectedLanguage.uppercase()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Language pill — opens popup, not inline expansion
        Box {
            Surface(
                onClick = { onToggleLanguageDropdown(!showLanguageDropdown) },
                shape = RoundedCornerShape(8.dp),
                color = if (showLanguageDropdown) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = languageName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }

            // Language dropdown as Popup (doesn't take vertical space)
            if (showLanguageDropdown) {
                Popup(
                    alignment = Alignment.TopStart,
                    onDismissRequest = { onToggleLanguageDropdown(false) },
                    properties = PopupProperties(focusable = true)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            com.dipdev.aiautocaptioner.ui.components.LanguageDropdown(
                                selectedLanguage = selectedLanguage,
                                onLanguageSelected = { lang ->
                                    onLanguageSelected(lang, if (lang == "en") false else translateToEnglish)
                                    onToggleLanguageDropdown(false)
                                },
                                allowedLanguages = listOf("multilingual")
                            )
                            if (selectedLanguage != "en") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(stringResource(R.string.style_translate_to_en), style = MaterialTheme.typography.labelSmall)
                                    Switch(
                                        checked = translateToEnglish,
                                        onCheckedChange = { v ->
                                            onLanguageSelected(selectedLanguage, v)
                                        },
                                        modifier = Modifier.height(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedLanguage != "en" && translateToEnglish) {
            Text(
                text = stringResource(R.string.style_to_en_badge),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
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
}

// ── Collapsible Adjust section ──────────────────────────────────────────────

@Composable
private fun CollapsibleAdjust(
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    fontSize: Float,
    maxWordsPerLine: Int,
    maxLines: Int,
    positionY: Float,
    onFontSizeChange: (Float) -> Unit,
    onMaxWordsChange: (Int) -> Unit,
    onMaxLinesChange: (Int) -> Unit,
    onPositionYChange: (Float) -> Unit
) {
    // Toggle row — always visible
    Surface(
        onClick = { onToggle(!expanded) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(if (expanded) R.string.style_adjust_expand else R.string.style_adjust_collapse),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }

    // Controls — only when expanded
    if (expanded) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Size slider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.style_size),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(36.dp)
                )
                PremiumSlider(
                    value = fontSize,
                    onValueChange = onFontSizeChange,
                    valueRange = 24f..96f,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${fontSize.toInt()}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(28.dp)
                )
            }

            // Words + Lines
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.style_words),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(40.dp)
                    )
                    StepperControl(
                        value = maxWordsPerLine,
                        range = 1..10,
                        onValueChange = onMaxWordsChange
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.style_lines),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(32.dp)
                    )
                    StepperControl(
                        value = maxLines,
                        range = 1..5,
                        onValueChange = onMaxLinesChange
                    )
                }
            }

            // Position
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.style_pos),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    modifier = Modifier.width(36.dp)
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
    val shape = RoundedCornerShape(8.dp)
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

// ── Hidden for v1 ────────────────────────────────────────────────────────────
// The full tabbed style editor with Text / Color / Animation tabs is
// commented out below. Re-enable when premium / payment is ready.
//
// import com.dipdev.aiautocaptioner.ui.videoeditor.style.tabs.AnimationTab
// import com.dipdev.aiautocaptioner.ui.videoeditor.style.tabs.ColorTab
// import com.dipdev.aiautocaptioner.ui.videoeditor.style.tabs.TextTab
//
// To restore:
//   1. Uncomment the three imports above.
//   2. Uncomment StyleEditorBottomBar() call and the when-branches below.
//   3. Uncomment isPremium from styleUiState.
//
// val isPremium = styleUiState.isPremium
//
// StyleEditorBottomBar(
//     selectedTab = selectedTab,
//     isPremium = isPremium,
//     onTabSelected = { tab ->
//         viewModel.setEvent(StyleEditorUiEvent.SelectTab(tab))
//     }
// )
//
// StyleTab.TEXT -> {
//     TextTab(
//         style = style,
//         onFontFamilyChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("fontFamily") { s -> s.copy(fontFamily = it) }) },
//         onFontSizeChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("fontSize") { s -> s.copy(fontSize = it) }) },
//         onFontWeightChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("fontWeight") { s -> s.copy(fontWeight = it) }) },
//         onMaxWordsChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("maxWords") { s -> s.copy(maxWordsPerLine = it) }) },
//         onMaxLinesChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("maxLines") { s -> s.copy(maxLines = it) }) },
//         onRemovePunctuationChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("removePunctuation") { s -> s.copy(removePunctuation = it) }) },
//         onAlignmentChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("alignment") { s -> s.copy(alignment = it) }) },
//         onLetterSpacingChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("letterSpacing") { s -> s.copy(letterSpacing = it) }) },
//         onIsItalicChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("isItalic") { s -> s.copy(isItalic = it) }) },
//         onTextOpacityChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("textOpacity") { s -> s.copy(textOpacity = it) }) },
//         onTextTransformChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("textTransform") { s -> s.copy(textTransform = it) }) },
//         onLineHeightChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("lineHeight") { s -> s.copy(lineHeight = it) }) },
//         onPositionXChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("positionX") { s -> s.copy(positionX = it) }) },
//         onPositionYChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("positionY") { s -> s.copy(positionY = it) }) }
//     )
// }
//
// StyleTab.COLOR -> {
//     ColorTab(
//         style = style,
//         onTextColorChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("textColor") { s -> s.copy(textColor = it) }) },
//         onHighlightColorChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("highlightColor") { s -> s.copy(highlightColor = it) }) },
//         onOutlineColorChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("outlineColor") { s -> s.copy(outlineColor = it) }) },
//         onOutlineWidthChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("outlineWidth") { s -> s.copy(outlineWidth = it) }) },
//         onOutlineOnlyChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("outlineOnly") { s -> s.copy(outlineOnly = it) }) },
//         onShadowColorChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("shadowColor") { s -> s.copy(shadowColor = it) }) },
//         onShadowRadiusChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("shadowRadius") { s -> s.copy(shadowRadius = it) }) },
//         onShadowOffsetXChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("shadowOffsetX") { s -> s.copy(shadowOffsetX = it) }) },
//         onShadowOffsetYChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("shadowOffsetY") { s -> s.copy(shadowOffsetY = it) }) },
//         onGradientDirectionChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("gradientDirection") { s -> s.copy(gradientDirection = it) }) },
//         onSecondaryColorChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("secondaryColor") { s -> s.copy(secondaryColor = it) }) },
//         onGlowEnabledChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("glowEnabled") { s -> s.copy(glowEnabled = it) }) },
//         onGlowColorChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("glowColor") { s -> s.copy(glowColor = it) }) },
//         onGlowRadiusChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("glowRadius") { s -> s.copy(glowRadius = it) }) },
//         onBackgroundTypeChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("backgroundType") { s -> s.copy(backgroundType = it) }) },
//         onBackgroundColorChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("backgroundColor") { s -> s.copy(backgroundColor = it) }) },
//         onBackgroundOpacityChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("backgroundOpacity") { s -> s.copy(backgroundOpacity = it) }) },
//         onBackgroundPaddingHChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("backgroundPaddingH") { s -> s.copy(backgroundPaddingH = it) }) },
//         onBackgroundPaddingVChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("backgroundPaddingV") { s -> s.copy(backgroundPaddingV = it) }) },
//         onBackgroundCornerRadiusChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("backgroundCornerRadius") { s -> s.copy(backgroundCornerRadius = it) }) }
//     )
// }
//
// StyleTab.ANIMATION -> {
//     AnimationTab(
//         style = style,
//         onDisplayModeChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("displayMode") { s -> s.copy(displayMode = it) }) },
//         onWordEnterChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("wordEnter") { s -> s.copy(wordEnterAnimation = it) }) },
//         onWordExitChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("wordExit") { s -> s.copy(wordExitAnimation = it) }) },
//         onKaraokeHighlightChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("karaokeHighlight") { s -> s.copy(karaokeHighlightMode = it) }) },
//         onAnimationDurationChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("animationDuration") { s -> s.copy(animationDurationMs = it) }) }
//     )
// }
