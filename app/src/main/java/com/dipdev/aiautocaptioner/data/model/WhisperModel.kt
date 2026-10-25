package com.dipdev.aiautocaptioner.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WhisperModel(
    val id: String,
    val displayName: String,
    val description: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val minRamMb: Int,
    val accuracy: Int,        // 1-5
    val speed: Int,           // 1-5
    val languages: List<String>,
    val isMultilingual: Boolean,
    val isDownloaded: Boolean = false,
    val localPath: String? = null
) {
    val sizeMb: Int get() = (sizeBytes / 1_000_000).toInt()

    /**
     * Human-readable speed estimate shown on model selection cards so users
     * understand the real-world wait time before committing to a download.
     * Based on the model's [speed] rating (1–5) and typical mobile CPU throughput.
     */
    val speedHint: String get() = when (speed) {
        5    -> "~3–8 sec / 30s clip"
        4    -> "~10–20 sec / 30s clip"
        3    -> "~30–60 sec / 30s clip"
        2    -> "~2–4 min / 30s clip"
        else -> "~5+ min / 30s clip"
    }

    companion object {
        /**
         * Filters and sorts available models based on the selected language:
         * - For specific languages (e.g. Hindi "hi", English "en"): returns ONLY language-specific models plus multilingual models.
         *   Sorted with language-specific models first (by size/speed), followed by multilingual models (by size/speed).
         * - For "auto": returns multilingual models first, followed by all other models, sorted by size/performance.
         */
        fun filterAndSortForLanguage(allModels: List<WhisperModel>, language: String): List<WhisperModel> {
            return if (language == "auto" || language == "multilingual") {
                allModels.sortedWith(
                    compareByDescending<WhisperModel> { it.isMultilingual }
                        .thenBy { it.sizeBytes }
                )
            } else {
                val matchingModels = allModels.filter { model ->
                    model.isMultilingual || model.languages.contains(language)
                }
                matchingModels.sortedWith(
                    compareBy<WhisperModel> { it.isMultilingual }
                        .thenBy { it.sizeBytes }
                )
            }
        }
    }
}
