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
                                onFontSizeChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateFontSize(it)) },
                                onFontWeightChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateFontWeight(it)) },
                                onMaxWordsChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateMaxWordsPerLine(it)) },
                                onMaxLinesChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateMaxLines(it)) },
                                onRemovePunctuationChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateRemovePunctuation(it)) },
                                onAlignmentChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateAlignment(it)) },
                                onLetterSpacingChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateLetterSpacing(it)) },
                                onIsItalicChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateIsItalic(it)) }
                            )
                        }
                        StyleTab.COLOR -> {
                            ColorTab(
                                style = style,
                                onTextColorChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateTextColor(it)) },
                                onHighlightColorChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateHighlightColor(it)) },
                                onOutlineColorChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateOutlineColor(it)) },
                                onOutlineWidthChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateOutlineWidth(it)) },
                                onBackgroundTypeChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateBackgroundType(it)) },
                                onBackgroundColorChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateBackgroundColor(it)) },
                                onBackgroundOpacityChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateBackgroundOpacity(it)) },
                                onBackgroundPaddingHChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateBackgroundPaddingH(it)) },
                                onBackgroundPaddingVChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateBackgroundPaddingV(it)) },
                                onBackgroundCornerRadiusChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateBackgroundCornerRadius(it)) }
                            )
                        }
                        StyleTab.ANIMATION -> {
                                AnimationTab(
                                    style = style,
                                    onDisplayModeChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateDisplayMode(it)) },
                                    onWordEnterChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateWordEnterAnimation(it)) },
                                    onKaraokeHighlightChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateKaraokeHighlightMode(it)) },
                                    onAnimationDurationChange = { viewModel.setEvent(StyleEditorUiEvent.UpdateAnimationDurationMs(it)) }
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
