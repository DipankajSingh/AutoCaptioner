package com.dipdev.aiautocaptioner.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LANGUAGES = listOf(
    "en"   to "English",
    "hi"   to "Hindi",
    "es"   to "Spanish",
    "fr"   to "French",
    "de"   to "German",
    "zh"   to "Chinese",
    "ja"   to "Japanese",
    "ar"   to "Arabic",
    "pt"   to "Portuguese",
    "ru"   to "Russian",
    "auto" to "Auto-detect"
)

/**
 * A labelled dropdown for selecting a Whisper language code.
 *
 * @param selectedLanguage  Currently selected language code (e.g. "en").
 * @param onLanguageSelected Called with the new code when the user picks one.
 * @param modifier          Applied to the outer [Column].
 */
@Composable
fun LanguageDropdown(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val displayName = LANGUAGES.find { it.first == selectedLanguage }?.second ?: selectedLanguage

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text       = "Language",
            fontSize   = 13.sp,
            color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier   = Modifier.padding(bottom = 6.dp)
        )
        Box {
            OutlinedButton(
                onClick  = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp)
            ) {
                Text(displayName, modifier = Modifier.weight(1f))
                Text("▾", fontSize = 14.sp)
            }
            DropdownMenu(
                expanded          = expanded,
                onDismissRequest  = { expanded = false }
            ) {
                LANGUAGES.forEach { (code, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = { onLanguageSelected(code); expanded = false },
                        trailingIcon = {
                            if (code == selectedLanguage) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint     = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
