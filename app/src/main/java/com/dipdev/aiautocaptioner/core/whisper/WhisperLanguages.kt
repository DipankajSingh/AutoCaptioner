package com.dipdev.aiautocaptioner.core.whisper

import java.util.Locale

object WhisperLanguages {

    data class Language(
        val code: String,
        val whisperCode: String = code,
        val displayNameOverride: String? = null
    )

    private val SUPPORTED_LANGUAGES = listOf(
        Language("auto", displayNameOverride = "Auto"),
        Language("en"),
        Language("hi"),
        Language("hinglish", whisperCode = "hi", displayNameOverride = "Hinglish"),
        Language("es"),
        Language("fr"),
        Language("de"),
        Language("zh", displayNameOverride = "Chinese (Simplified)"),
        Language("zh-TW", whisperCode = "zh", displayNameOverride = "Chinese (Traditional)"),
        Language("yue", displayNameOverride = "Cantonese"),
        Language("ja"),
        Language("ko"),
        Language("it"),
        Language("ar"),
        Language("ru"),
        Language("pt"),
        Language("ta"),
        Language("te"),
        Language("nl"),
        Language("tr"),
        Language("pl"),
        Language("vi"),
        Language("th"),
        Language("id"),
        Language("ms")
    )

    val UI_CODES: List<String> = SUPPORTED_LANGUAGES.map { it.code }

    private val whisperMapping = SUPPORTED_LANGUAGES.associate { it.code to it.whisperCode }
    private val displayNameOverrides = SUPPORTED_LANGUAGES.associate { it.code to it.displayNameOverride }

    fun whisperCode(uiCode: String): String = whisperMapping[uiCode] ?: uiCode

    fun getDisplayName(code: String): String {
        displayNameOverrides[code]?.let { return it }
        val locale = Locale.forLanguageTag(code)
        return locale.getDisplayName(Locale.getDefault()).replaceFirstChar { it.uppercase() }
    }

    private val countryLanguages: Map<String, List<String>> = mapOf(
        // South Asia
        "IN" to listOf("hi", "hinglish", "en", "ta", "te", "ur", "bn", "pa", "mr", "gu", "kn", "ml"),
        "PK" to listOf("ur", "en", "hi"),
        "BD" to listOf("bn", "en", "hi"),
        "NP" to listOf("hi", "en"),
        "LK" to listOf("ta", "en", "hi"),
        "MV" to listOf("hi", "ta", "en"),
        // English-speaking
        "US" to listOf("en", "es"),
        "GB" to listOf("en"),
        "CA" to listOf("en", "fr"),
        "AU" to listOf("en"),
        "NZ" to listOf("en"),
        "SG" to listOf("en", "zh", "ms", "ta"),
        // Europe
        "DE" to listOf("de", "en", "tr"),
        "FR" to listOf("fr", "en", "ar"),
        "ES" to listOf("es", "en"),
        "IT" to listOf("it", "en"),
        "NL" to listOf("nl", "en"),
        "PT" to listOf("pt", "en"),
        "TR" to listOf("tr", "en"),
        "PL" to listOf("pl", "en"),
        "RU" to listOf("ru", "en"),
        "SE" to listOf("sv", "en"),
        // Latin America
        "MX" to listOf("es", "en"),
        "AR" to listOf("es", "en"),
        "CO" to listOf("es", "en"),
        "CL" to listOf("es", "en"),
        "PE" to listOf("es", "en"),
        "BR" to listOf("pt", "es", "en"),
        // East Asia
        "CN" to listOf("zh", "zh-TW", "yue", "en"),
        "TW" to listOf("zh-TW", "en"),
        "HK" to listOf("zh-TW", "yue", "en"),
        "JP" to listOf("ja", "en"),
        "KR" to listOf("ko", "en"),
        // South-East Asia
        "VN" to listOf("vi", "en"),
        "TH" to listOf("th", "en"),
        "ID" to listOf("id", "en"),
        "MY" to listOf("ms", "en", "zh"),
        "PH" to listOf("en", "tl"),
        // Middle East
        "SA" to listOf("ar", "en"),
        "AE" to listOf("ar", "en", "hi"),
        "EG" to listOf("ar", "en"),
        "IQ" to listOf("ar", "en"),
        "IL" to listOf("he", "en", "ar"),
        // Africa
        "NG" to listOf("en", "ha", "yo"),
        "ZA" to listOf("en", "af"),
        "KE" to listOf("en", "sw")
    )

    fun orderedCodes(countryCode: String?, deviceLanguage: String?): List<String> {
        val uiCodeSet = UI_CODES.toSet()
        val result = LinkedHashSet<String>()
        
        result.add("auto")

        // 1. Languages for the specific country
        countryCode?.uppercase()?.let { country ->
            countryLanguages[country]?.forEach { code ->
                if (code in uiCodeSet) result.add(code)
            }
        }

        // 2. Device language
        deviceLanguage?.let { lang ->
            if (lang in uiCodeSet) result.add(lang)
            // If device language is not in UI_CODES, try to find a country that uses it
            else {
                countryLanguages.values.firstOrNull { it.contains(lang) }?.forEach { code ->
                   if (code in uiCodeSet) result.add(code)
                }
            }
        }

        // 3. All other supported languages
        UI_CODES.forEach { result.add(it) }

        return result.toList()
    }
}
