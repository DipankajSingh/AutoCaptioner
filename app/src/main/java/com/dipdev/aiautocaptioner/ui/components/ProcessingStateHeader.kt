package com.dipdev.aiautocaptioner.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProcessingStateHeader(
    title: String,
    subtitle: String? = null
) {
    Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    if (subtitle != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}
