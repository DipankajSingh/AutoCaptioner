package com.dipdev.aiautocaptioner.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A [Button] that swaps its label for a [CircularProgressIndicator] while [isLoading] is true.
 * Disables itself during loading to prevent double-taps.
 */
@Composable
fun ProgressButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    cornerRadius: Dp = 12.dp
) {
    Button(
        onClick  = onClick,
        modifier = modifier,
        enabled  = enabled && !isLoading,
        shape    = RoundedCornerShape(4.dp), // Sharper corners for flat style
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier  = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color     = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(text)
            }
        }
    }
}

/** Outlined variant of [ProgressButton]. */
@Composable
fun OutlinedProgressButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    cornerRadius: Dp = 12.dp
) {
    OutlinedButton(
        onClick  = onClick,
        modifier = modifier,
        enabled  = enabled && !isLoading,
        shape    = RoundedCornerShape(4.dp), // Sharper corners for flat style
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier  = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(text)
            }
        }
    }
}
