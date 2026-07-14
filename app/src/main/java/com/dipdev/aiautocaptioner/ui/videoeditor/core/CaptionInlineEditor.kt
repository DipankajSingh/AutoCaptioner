package com.dipdev.aiautocaptioner.ui.videoeditor.core

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.aiautocaptioner.ui.components.AppOutlinedButton
import com.dipdev.aiautocaptioner.ui.components.AppPrimaryButton
import com.dipdev.aiautocaptioner.ui.components.GlassmorphicCard
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import compose.icons.FeatherIcons
import compose.icons.feathericons.Edit2
import compose.icons.feathericons.X

/**
 * Inline caption quick-edit card shown above the timeline in EditorScreen.
 *
 * Visible when a caption segment is selected. Allows the user to edit the segment's
 * text without leaving EditorScreen. `imePadding` ensures the keyboard does not
 * cover the card.
 *
 * @param segment           Currently selected segment, or null to hide the card.
 * @param editText          The current text being edited (controlled externally).
 * @param onEditTextChange  Called whenever the user types.
 * @param onSave            Called when the user taps "Save" with the edited text.
 * @param onDismiss         Called when the user dismisses without saving.
 * @param onOpenFullEditor  Called when the user taps the full-editor (Edit2) icon.
 */
@Composable
fun CaptionInlineEditor(
    segment: CaptionSegmentEntity?,
    editText: String,
    onEditTextChange: (String) -> Unit,
    onSave: (segmentId: String, text: String) -> Unit,
    onDismiss: () -> Unit,
    onOpenFullEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .imePadding()   // Fix 8: shifts card above soft keyboard
    ) {
        AnimatedVisibility(
            visible = segment != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            segment?.let { seg ->
                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Header row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Edit Caption",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Open full caption editor
                                IconButton(onClick = onOpenFullEditor) {
                                    Icon(
                                        FeatherIcons.Edit2,
                                        contentDescription = "Full Editor",
                                        tint = AccentAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                // Dismiss
                                IconButton(onClick = onDismiss) {
                                    Icon(
                                        FeatherIcons.X,
                                        contentDescription = "Close",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Inline text input
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            BasicTextField(
                                value = editText,
                                onValueChange = onEditTextChange,
                                textStyle = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp
                                ),
                                cursorBrush = SolidColor(AccentAmber),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppOutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f)
                            ) { Text("Cancel") }
                            AppPrimaryButton(
                                onClick = { onSave(seg.id, editText) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Save") }
                        }
                    }
                }
            }
        }
    }
}
