package com.dipdev.aiautocaptioner.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A consistent section label used across all style editor tabs and settings screens.
 * Renders a small caps-style header above a group of controls.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text     = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color    = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
        modifier = modifier.padding(top = 20.dp, bottom = 6.dp)
    )
}
