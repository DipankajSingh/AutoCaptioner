package com.dipdev.aiautocaptioner.core.whisper

/**
 * Single source of truth for the language list shown in the UI.
 *
 * "hinglish" is a synthetic UI language: whisper.cpp (g_lang table) has no
 * Hinglish code, so it is mapped to the real whisper code "hi" (Hindi).
 * "zh-TW" is likewise not a whisper.cpp code — traditional Chinese is
 * transcribed with the "zh" code.
 */
object WhisperLanguages {

    /** UI language codes, in the default (unranked) display order. */
    val UI_CODES: List<String> = listOf(
        "auto", "en", "hi", "hinglish", "es", "fr", "de",
        "zh", "zh-TW", "yue", "ja", "ko",
        "it", "ar", "ru", "pt", "ta", "te",
        "nl", "tr", "pl", "vi", "th", "id", "ms"
    )

    /** Real whisper.cpp language code for a UI code. */
    fun whisperCode(uiCode: String): String = when (uiCode) {
        "hinglish" -> "hi"
        "zh-TW" -> "zh"
        else -> uiCode
    }

    /**
     * Regional language rankings keyed by ISO-3166 country code.
     * The first entries are shown first in the language list for users in
     * that country.
     */
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

    /**
     * Orders [UI_CODES] for a device: "auto" first, then the regional
     * languages for [countryCode] (falling back to [deviceLanguage] when the
     * country is unknown), then the remaining languages in default order.
     */
    fun orderedCodes(countryCode: String?, deviceLanguage: String?): List<String> {
        val ranked = countryCode?.let { countryLanguages[it.uppercase()] }
            ?: deviceLanguage?.let { lang ->
                countryLanguages.values.firstOrNull { ranked -> ranked.contains(lang) }
                    ?: listOf(lang)
            }
            ?: emptyList()

        return buildList {
            add("auto")
            ranked.forEach { code ->
                if (code != "auto" && code in UI_CODES && code !in this) add(code)
            }
            UI_CODES.forEach { code ->
                if (code != "auto" && code !in this) add(code)
            }
        }
    }
}
