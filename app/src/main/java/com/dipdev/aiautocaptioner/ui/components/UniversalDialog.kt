package com.dipdev.aiautocaptioner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import com.dipdev.aiautocaptioner.ui.theme.AccentRose
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

// ─────────────────────────────────────────────────────────────────────────────
// DialogType — drives mascot expression and accent colour
// ─────────────────────────────────────────────────────────────────────────────

enum class DialogType {
    /** Neutral/informational — permissions, general notices */
    INFO,
    /** Caution — "are you sure", cellular warnings */
    WARNING,
    /** Destructive — deletes, errors */
    ERROR,
    /** Positive — future confirmations */
    SUCCESS
}

// ─────────────────────────────────────────────────────────────────────────────
// Public API
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The single universal dialog for the entire app.
 *
 * Swap the Material AlertDialog for this everywhere. The TV-robot mascot animates
 * its expression based on [type], giving the user immediate emotional context
 * before they read a word of text.
 *
 * @param modifier          Applied to the dialog card.
 * @param type              Drives mascot expression and accent colour.
 * @param title             Short, bold headline.
 * @param body              Optional body text. Ignored when [content] is set.
 * @param content           Optional arbitrary composable (e.g. a TextField). When
 *                          provided, [body] is not rendered.
 * @param confirmText       Primary action button label.
 * @param onConfirm         Primary action callback.
 * @param isConfirmEnabled  Set false to disable the primary button (e.g. blank input).
 * @param dismissText       Optional secondary/cancel button label.
 * @param onDismiss         Secondary action callback; falls back to [onDismissRequest].
 * @param onDismissRequest  Called on outside tap or back press.
 */
@Composable
fun UniversalDialog(
    modifier: Modifier = Modifier,
    type: DialogType = DialogType.INFO,
    title: String,
    body: String? = null,
    content: @Composable (() -> Unit)? = null,
    confirmText: String,
    onConfirm: () -> Unit,
    isConfirmEnabled: Boolean = true,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    onDismissRequest: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(40.milliseconds); visible = true }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 6 }
        ) {
            DialogCard(
                modifier         = modifier,
                type             = type,
                title            = title,
                body             = body,
                content          = content,
                confirmText      = confirmText,
                onConfirm        = onConfirm,
                isConfirmEnabled = isConfirmEnabled,
                dismissText      = dismissText,
                onDismiss        = onDismiss ?: onDismissRequest
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DialogCard — card whose top edge bulges up into a smooth arch housing the mascot
// ─────────────────────────────────────────────────────────────────────────────

private val MASCOT_SIZE_DP = 104.dp

@Composable
private fun DialogCard(
    modifier: Modifier,
    type: DialogType,
    title: String,
    body: String?,
    content: (@Composable () -> Unit)?,
    confirmText: String,
    onConfirm: () -> Unit,
    isConfirmEnabled: Boolean,
    dismissText: String?,
    onDismiss: () -> Unit
) {
    val accent    = type.accentColor()
    val surface   = MaterialTheme.colorScheme.surface
    val archShape = remember { DialogArchShape() }

    Box(
        modifier         = modifier.fillMaxWidth(0.92f),
        contentAlignment = Alignment.TopCenter
    ) {
        // ── Main card surface, clipped to the arch shape ───────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.45f),
                            accent.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        )
                    ),
                    shape = archShape
                )
                .clip(archShape)
                .background(surface)
                .drawBehind {
                    val headerH = size.height * 0.38f
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.22f),
                                accent.copy(alpha = 0.00f)
                            ),
                            startY = 0f,
                            endY   = headerH
                        ),
                        size = Size(size.width, headerH)
                    )
                }
                .padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Mascot lives inside the arch dome ──────────────────────────────
            Spacer(Modifier.height(12.dp))
            MascotRobot(
                type     = type,
                modifier = Modifier.size(MASCOT_SIZE_DP)
            )
            Spacer(Modifier.height(20.dp))

            Text(
                text      = title,
                style     = MaterialTheme.typography.titleLarge,
                color     = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )

            if (body != null || content != null) {
                Spacer(Modifier.height(8.dp))
                if (content != null) {
                    content()
                } else {
                    Text(
                        text      = body!!,
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Action buttons ─────────────────────────────────────────────
            if (dismissText != null) {
                Column(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick  = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                        border   = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                        )
                    ) {
                        Text(
                            text  = dismissText,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick  = onConfirm,
                        enabled  = isConfirmEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = primaryButtonColors(accent)
                    ) {
                        Text(confirmText, style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                Button(
                    onClick  = onConfirm,
                    enabled  = isConfirmEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = primaryButtonColors(accent)
                ) {
                    Text(confirmText, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun DialogType.accentColor(): Color = when (this) {
    DialogType.INFO    -> AccentAmber
    DialogType.WARNING -> AccentAmber
    DialogType.ERROR   -> AccentRose
    DialogType.SUCCESS -> AccentAmber
}

@Composable
private fun primaryButtonColors(accent: Color) = ButtonDefaults.buttonColors(
    containerColor         = accent,
    contentColor           = Color.White,
    disabledContainerColor = accent.copy(alpha = 0.30f),
    disabledContentColor   = Color.White.copy(alpha = 0.38f)
)
