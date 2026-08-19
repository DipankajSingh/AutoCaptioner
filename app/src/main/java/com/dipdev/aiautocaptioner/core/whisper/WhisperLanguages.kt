package com.dipdev.aiautocaptioner.core.whisper

import java.util.Locale

object WhisperLanguages {

    data class Language(
        val code: String,
        val whisperCode: String = code,
        val displayNameOverride: String? = null,
        val aliases: List<String> = emptyList()
    )

    private val SUPPORTED_LANGUAGES = listOf(
        Language("auto", displayNameOverride = "Auto"),
        Language("en", aliases = listOf("American", "British")),
        Language("hi", aliases = listOf("Hindu", "Hindustani")),
        Language("hinglish", whisperCode = "hi", displayNameOverride = "Hinglish"),
        Language("es", aliases = listOf("Castilian")),
        Language("fr"),
        Language("de"),
        Language("zh", displayNameOverride = "中文 (简体)", aliases = listOf("Mandarin", "Chinese")),
        Language("zh-TW", whisperCode = "zh", displayNameOverride = "中文 (繁體)", aliases = listOf("Mandarin", "Taiwanese")),
        Language("yue", displayNameOverride = "粵語", aliases = listOf("Cantonese")),
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
        Language("ms"),
        Language("af"),
        Language("am"),
        Language("as"),
        Language("az"),
        Language("ba"),
        Language("be"),
        Language("bg"),
        Language("bn"),
        Language("bo"),
        Language("br"),
        Language("bs"),
        Language("ca"),
        Language("cs"),
        Language("cy"),
        Language("da"),
        Language("el"),
        Language("et"),
        Language("eu"),
        Language("fa"),
        Language("fi"),
        Language("fo"),
        Language("gl"),
        Language("gu"),
        Language("ha"),
        Language("haw"),
        Language("he"),
        Language("hr"),
        Language("ht"),
        Language("hu"),
        Language("hy"),
        Language("is"),
        Language("jw"),
        Language("ka"),
        Language("kk"),
        Language("km"),
        Language("kn"),
        Language("la"),
        Language("lb"),
        Language("ln"),
        Language("lo"),
        Language("lt"),
        Language("lv"),
        Language("mg"),
        Language("mi"),
        Language("mk"),
        Language("ml"),
        Language("mn"),
        Language("mr"),
        Language("mt"),
        Language("my"),
        Language("ne"),
        Language("nn"),
        Language("no"),
        Language("oc"),
        Language("pa"),
        Language("ps"),
        Language("ro"),
        Language("sa"),
        Language("sd"),
        Language("si"),
        Language("sk"),
        Language("sl"),
        Language("sn"),
        Language("so"),
        Language("sq"),
        Language("sr"),
        Language("su"),
        Language("sv"),
        Language("sw"),
        Language("tg"),
        Language("tk"),
        Language("tl"),
        Language("tt"),
        Language("uk"),
        Language("ur"),
        Language("uz"),
        Language("yi"),
        Language("yo")
    )

    val UI_CODES: List<String> = SUPPORTED_LANGUAGES.map { it.code }

    private val whisperMapping = SUPPORTED_LANGUAGES.associate { it.code to it.whisperCode }
    private val displayNameOverrides = SUPPORTED_LANGUAGES.associate { it.code to it.displayNameOverride }
    private val aliasesMapping = SUPPORTED_LANGUAGES.associate { it.code to it.aliases }

    fun matchesSearchQuery(code: String, query: String): Boolean {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return true

        val nativeName = getDisplayName(code).lowercase()
        val englishName = if (code == "auto" || code == "hinglish") code else Locale.forLanguageTag(code).getDisplayName(Locale.ENGLISH).lowercase()
        val aliases = aliasesMapping[code]?.map { it.lowercase() } ?: emptyList()

        if (nativeName.contains(q) || englishName.contains(q) || aliases.any { it.contains(q) }) {
            return true
        }

        if (q.length > 3) {
            val maxDistance = if (q.length > 5) 2 else 1
            if (levenshtein(q, nativeName) <= maxDistance ||
                levenshtein(q, englishName) <= maxDistance ||
                aliases.any { levenshtein(q, it) <= maxDistance }
            ) {
                return true
            }
        }
        return false
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        if (lhs == rhs) return 0
        if (lhs.isEmpty()) return rhs.length
        if (rhs.isEmpty()) return lhs.length

        val lhsLength = lhs.length + 1
        val rhsLength = rhs.length + 1
        var cost = IntArray(lhsLength) { it }
        var newCost = IntArray(lhsLength) { 0 }

        for (i in 1 until rhsLength) {
            newCost[0] = i
            for (j in 1 until lhsLength) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = minOf(costInsert, costDelete, costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLength - 1]
    }

    fun whisperCode(uiCode: String): String = whisperMapping[uiCode] ?: uiCode

    private val textPaint by lazy { android.graphics.Paint() }

    private fun isRenderable(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            val charCount = Character.charCount(codePoint)
            val charStr = text.substring(i, i + charCount)
            // If the font cannot render this character, return false
            if (!Character.isWhitespace(codePoint) && !textPaint.hasGlyph(charStr)) {
                return false
            }
            i += charCount
        }
        return true
    }

    fun getDisplayName(code: String): String {
        val locale = Locale.forLanguageTag(code)
        val defaultName = locale.getDisplayName(Locale.getDefault()).replaceFirstChar { it.uppercase() }
        
        val nativeName = displayNameOverrides[code] 
            ?: locale.getDisplayName(locale).replaceFirstChar { it.uppercase() }

        // If the native script contains unsupported characters (tofu boxes), fallback to default English name
        return if (isRenderable(nativeName)) nativeName else defaultName
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
