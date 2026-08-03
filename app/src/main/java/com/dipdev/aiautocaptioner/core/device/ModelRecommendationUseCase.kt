package com.dipdev.aiautocaptioner.core.device

import com.dipdev.aiautocaptioner.data.model.WhisperModel
import javax.inject.Inject
import javax.inject.Singleton

data class ModelRecommendation(
    val modelId: String,
    val reasonResId: Int?
)

@Singleton
class ModelRecommendationUseCase @Inject constructor(
    private val deviceCapabilityUseCase: DeviceCapabilityUseCase
) {

    fun filterCompatibleModels(
        allModels: List<WhisperModel>,
        language: String
    ): List<WhisperModel> {
        return WhisperModel.filterAndSortForLanguage(allModels, language)
            .filter { model -> deviceCapabilityUseCase.isModelRamCompatible(model.minRamMb) }
    }

    fun getRecommendation(
        compatibleModels: List<WhisperModel>,
        language: String
    ): ModelRecommendation {
        if (compatibleModels.isEmpty()) return ModelRecommendation("", null)

        val ramRecommendation = deviceCapabilityUseCase.getRecommendedModelWithReason(language)
        val ramBasedId = ramRecommendation.modelId

        val isSpecificLanguage = language != "en" && language != "auto"
        val languageSpecificModel = if (isSpecificLanguage) {
            compatibleModels.firstOrNull { it.languages.contains(language) && !it.isMultilingual }
        } else null

        return when {
            languageSpecificModel != null -> ModelRecommendation(
                modelId = languageSpecificModel.id,
                reasonResId = ramRecommendation.reasonResId
            )
            compatibleModels.any { it.id == ramBasedId } -> ModelRecommendation(
                modelId = ramBasedId,
                reasonResId = ramRecommendation.reasonResId
            )
            else -> ModelRecommendation(
                modelId = compatibleModels.first().id,
                reasonResId = null
            )
        }
    }
}
