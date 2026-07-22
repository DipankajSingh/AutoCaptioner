package com.dipdev.aiautocaptioner.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import compose.icons.FeatherIcons
import compose.icons.feathericons.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.R

@Composable
private fun languages() = listOf(
    "auto" to stringResource(R.string.lang_auto_detect_label),
    "en"   to stringResource(R.string.lang_english),
    "hi"   to stringResource(R.string.lang_hindi),
    "es"   to stringResource(R.string.lang_spanish),
    "fr"   to stringResource(R.string.lang_french),
    "de"   to stringResource(R.string.lang_german),
    "zh"   to stringResource(R.string.lang_chinese_simplified),
    "zh-TW" to stringResource(R.string.lang_chinese_traditional),
    "yue"  to stringResource(R.string.lang_cantonese),
    "ja"   to stringResource(R.string.lang_japanese),
    "ko"   to stringResource(R.string.lang_korean),
    "it"   to stringResource(R.string.lang_italian),
    "ar"   to stringResource(R.string.lang_arabic),
    "ru"   to stringResource(R.string.lang_russian),
    "pt"   to stringResource(R.string.lang_portuguese),
    "ta"   to stringResource(R.string.lang_tamil),
    "te"   to stringResource(R.string.lang_telugu),
    "nl"   to stringResource(R.string.lang_dutch),
    "tr"   to stringResource(R.string.lang_turkish),
    "pl"   to stringResource(R.string.lang_polish),
    "vi"   to stringResource(R.string.lang_vietnamese),
    "th"   to stringResource(R.string.lang_thai),
    "id"   to stringResource(R.string.lang_indonesian),
    "ms"   to stringResource(R.string.lang_malay)
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

    val LANGUAGES = languages()

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
            text       = stringResource(R.string.lang_select_video),
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
            val modelLangName = visibleLanguages.firstOrNull { it.first != "auto" }?.second ?: stringResource(R.string.lang_specific)
            Text(
                text     = "$modelLangName-only model active",
                fontSize = 11.sp,
                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}
