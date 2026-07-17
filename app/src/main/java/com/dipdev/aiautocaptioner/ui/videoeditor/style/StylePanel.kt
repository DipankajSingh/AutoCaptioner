package com.dipdev.aiautocaptioner.ui.videoeditor.style

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dipdev.aiautocaptioner.ui.videoeditor.style.tabs.AnimationTab
import com.dipdev.aiautocaptioner.ui.videoeditor.style.tabs.ColorTab
import com.dipdev.aiautocaptioner.ui.videoeditor.style.tabs.TextTab

@Composable
fun StylePanel(
    viewModel: StyleViewModel,
    modifier: Modifier = Modifier
) {
    val styleUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val styles = styleUiState.styles
    val activeStyle = styleUiState.activeStyle
    val selectedTab = styleUiState.selectedTab
    val isPremium = styleUiState.isPremium

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Style Editor",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            StyleEditorBottomBar(
                selectedTab = selectedTab,
                isPremium = isPremium,
                onTabSelected = { tab ->
                    viewModel.setEvent(StyleEditorUiEvent.SelectTab(tab))
                }
            )

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                activeStyle?.let { style ->
                    when (selectedTab) {
                        StyleTab.PRESETS -> {
                            PresetsTab(
                                styles = styles,
                                activeStyle = style,
                                onPresetSelected = { viewModel.setEvent(StyleEditorUiEvent.SelectPreset(it)) },
                                onPresetLongClicked = { },
                                onAddPreset = { }
                            )
                        }
                        StyleTab.TEXT -> {
                            TextTab(
                                style = style,
                                onFontSizeChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("fontSize") { s -> s.copy(fontSize = it) }) },
                                onFontWeightChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("fontWeight") { s -> s.copy(fontWeight = it) }) },
                                onMaxWordsChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("maxWords") { s -> s.copy(maxWordsPerLine = it) }) },
                                onMaxLinesChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("maxLines") { s -> s.copy(maxLines = it) }) },
                                onRemovePunctuationChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("removePunctuation") { s -> s.copy(removePunctuation = it) }) },
                                onAlignmentChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("alignment") { s -> s.copy(alignment = it) }) },
                                onLetterSpacingChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("letterSpacing") { s -> s.copy(letterSpacing = it) }) },
                                onIsItalicChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("isItalic") { s -> s.copy(isItalic = it) }) }
                            )
                        }
                        StyleTab.COLOR -> {
                            ColorTab(
                                style = style,
                                onTextColorChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("textColor") { s -> s.copy(textColor = it) }) },
                                onHighlightColorChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("highlightColor") { s -> s.copy(highlightColor = it) }) },
                                onOutlineColorChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("outlineColor") { s -> s.copy(outlineColor = it) }) },
                                onOutlineWidthChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("outlineWidth") { s -> s.copy(outlineWidth = it) }) },
                                onBackgroundTypeChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("backgroundType") { s -> s.copy(backgroundType = it) }) },
                                onBackgroundColorChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("backgroundColor") { s -> s.copy(backgroundColor = it) }) },
                                onBackgroundOpacityChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("backgroundOpacity") { s -> s.copy(backgroundOpacity = it) }) },
                                onBackgroundPaddingHChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("backgroundPaddingH") { s -> s.copy(backgroundPaddingH = it) }) },
                                onBackgroundPaddingVChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("backgroundPaddingV") { s -> s.copy(backgroundPaddingV = it) }) },
                                onBackgroundCornerRadiusChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("backgroundCornerRadius") { s -> s.copy(backgroundCornerRadius = it) }) }
                            )
                        }
                        StyleTab.ANIMATION -> {
                                AnimationTab(
                                    style = style,
                                    onDisplayModeChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("displayMode") { s -> s.copy(displayMode = it) }) },
                                    onWordEnterChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("wordEnter") { s -> s.copy(wordEnterAnimation = it) }) },
                                    onKaraokeHighlightChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("karaokeHighlight") { s -> s.copy(karaokeHighlightMode = it) }) },
                                    onAnimationDurationChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateStyle("animationDuration") { s -> s.copy(animationDurationMs = it) }) }
                                )
                        }
                    }
                } ?: run {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
