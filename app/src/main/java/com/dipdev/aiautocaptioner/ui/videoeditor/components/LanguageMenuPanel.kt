package com.dipdev.aiautocaptioner.ui.videoeditor.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dipdev.aiautocaptioner.ui.components.LanguageDropdown

@Composable
fun LanguageMenuPanel(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    selectedLanguage: String,
    translateToEnglish: Boolean,
    onLanguageSelected: (String, Boolean) -> Unit
) {
    AnimatedContent(
        targetState = expanded,
        label = "langPanel"
    ) { isExpanded ->
        if (isExpanded) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 8.dp,
                modifier = Modifier.width(220.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Language",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        IconButton(
                            onClick = { onExpandedChange(false) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Outlined.Close, null, modifier = Modifier.size(16.dp))
                        }
                    }
                    LanguageDropdown(
                        selectedLanguage = selectedLanguage,
                        onLanguageSelected = { lang ->
                            onLanguageSelected(lang, if (lang == "en") false else translateToEnglish)
                        },
                        allowedLanguages = listOf("multilingual")
                    )
                    if (selectedLanguage != "en") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Translate to EN", style = MaterialTheme.typography.labelSmall)
                            Switch(
                                checked = translateToEnglish,
                                onCheckedChange = { v ->
                                    onLanguageSelected(selectedLanguage, v)
                                }
                            )
                        }
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shadowElevation = 4.dp,
                onClick = { onExpandedChange(true) }
            ) {
                Text(
                    text = selectedLanguage.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
