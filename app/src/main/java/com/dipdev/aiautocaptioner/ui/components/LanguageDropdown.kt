package com.dipdev.aiautocaptioner.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import compose.icons.FeatherIcons
import compose.icons.feathericons.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LANGUAGES = listOf(
    "auto" to "Auto-detect",
    "en"   to "English",
    "hi"   to "Hindi",
    "es"   to "Spanish",
    "fr"   to "French",
    "de"   to "German",
    "zh"   to "Chinese (Simplified)",
    "zh-TW" to "Chinese (Traditional)",
    "yue"  to "Cantonese",
    "ja"   to "Japanese",
    "ko"   to "Korean",
    "it"   to "Italian",
    "ar"   to "Arabic",
    "ru"   to "Russian",
    "pt"   to "Portuguese",
    "ta"   to "Tamil",
    "te"   to "Telugu",
    "nl"   to "Dutch",
    "tr"   to "Turkish",
    "pl"   to "Polish",
    "vi"   to "Vietnamese",
    "th"   to "Thai",
    "id"   to "Indonesian",
    "ms"   to "Malay"
)

/**
 * A labelled dropdown for selecting a Whisper language code.
 *
 * @param selectedLanguage   Currently selected language code (e.g. "en").
 * @param onLanguageSelected Called with the new code when the user picks one.
 * @param allowedLanguages   List of language codes supported by the active model.
 *                           If the list contains "multilingual", all languages are shown.
 *                           Otherwise, only the explicitly allowed languages are shown.
 * @param modifier           Applied to the outer [Column].
 */
@Composable
fun LanguageDropdown(
    modifier: Modifier = Modifier,
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    allowedLanguages: List<String> = listOf("multilingual")
) {
    val isMultilingual = allowedLanguages.contains("multilingual")

    // Force selection to the model's supported language if current selection is invalid
    LaunchedEffect(allowedLanguages) {
        if (!isMultilingual && !allowedLanguages.contains(selectedLanguage)) {
            val fallback = allowedLanguages.firstOrNull { it != "auto" } ?: "en"
            onLanguageSelected(fallback)
        }
    }

    val visibleLanguages = if (isMultilingual) {
        LANGUAGES
    } else {
        LANGUAGES.filter { allowedLanguages.contains(it.first) || it.first == "auto" }
    }

    var expanded by remember { mutableStateOf(false) }
    val displayName = visibleLanguages.find { it.first == selectedLanguage }?.second
        ?: LANGUAGES.find { it.first == selectedLanguage }?.second
        ?: selectedLanguage

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text       = "Select video language",
            fontSize   = 13.sp,
            color      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier   = Modifier.padding(bottom = 6.dp)
        )
        Box {
            OutlinedButton(
                onClick  = { if (visibleLanguages.size > 1) expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp),
                enabled  = visibleLanguages.size > 1
            ) {
                Text(displayName, modifier = Modifier.weight(1f))
                if (visibleLanguages.size > 1) {
                    Text("▾", fontSize = 14.sp)
                }
            }
            DropdownMenu(
                expanded          = expanded,
                onDismissRequest  = { expanded = false }
            ) {
                visibleLanguages.forEach { (code, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = { onLanguageSelected(code); expanded = false },
                        trailingIcon = {
                            if (code == selectedLanguage) {
                                Icon(
                                    FeatherIcons.CheckCircle,
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

        // Helper text shown only for single-language models
        if (!isMultilingual) {
            Spacer(modifier = Modifier.height(4.dp))
            val modelLangName = visibleLanguages.firstOrNull { it.first != "auto" }?.second ?: "Specific"
            Text(
                text     = "$modelLangName-only model active",
                fontSize = 11.sp,
                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}
