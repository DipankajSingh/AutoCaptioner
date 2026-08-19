package com.dipdev.aiautocaptioner.core.device

import android.content.Context
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.data.model.WhisperModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class OnboardingModelTier(
    val tierName: String,
    val model: WhisperModel,
    val isRecommended: Boolean,
    val recommendedReasonResId: Int?
)

@Singleton
class OnboardingModelTierMapper @Inject constructor(
    private val modelRecommendationUseCase: ModelRecommendationUseCase,
    @ApplicationContext private val context: Context
) {
    fun mapToTiers(
        compatibleModels: List<WhisperModel>,
        language: String
    ): List<OnboardingModelTier> {
        if (compatibleModels.isEmpty()) {
            return emptyList()
        }

        val recommendation = modelRecommendationUseCase.getRecommendation(compatibleModels, language)
        val recModelId = recommendation.modelId

        if (compatibleModels.size == 1) {
            val model = compatibleModels[0]
            return listOf(
                OnboardingModelTier(
                    tierName = context.getString(R.string.model_tier_standard),
                    model = model,
                    isRecommended = model.id == recModelId,
                    recommendedReasonResId = if (model.id == recModelId) recommendation.reasonResId else null
                )
            )
        }

        if (compatibleModels.size == 2) {
            return compatibleModels.mapIndexed { index, model ->
                val tierName = if (index == 0) context.getString(R.string.model_tier_fast) else context.getString(R.string.model_tier_pro)
                OnboardingModelTier(
                    tierName = tierName,
                    model = model,
                    isRecommended = model.id == recModelId,
                    recommendedReasonResId = if (model.id == recModelId) recommendation.reasonResId else null
                )
            }
        }

        // size >= 3
        val selectedModels = mutableSetOf<WhisperModel>()
        selectedModels.add(compatibleModels.first())
        selectedModels.add(compatibleModels.last())
        
        val recModel = compatibleModels.find { it.id == recModelId }
        if (recModel != null) {
            selectedModels.add(recModel)
        }
        
        // If we still need more to make it 3, add from the middle
        if (selectedModels.size < 3) {
            selectedModels.add(compatibleModels[compatibleModels.size / 2])
        }
        if (selectedModels.size < 3) {
            selectedModels.add(compatibleModels[1])
        }
        
        // sort by size so Fast -> Standard -> Pro
        val sortedSelected = selectedModels.sortedBy { it.sizeMb }
        val tierNames = listOf(
            context.getString(R.string.model_tier_fast),
            context.getString(R.string.model_tier_standard),
            context.getString(R.string.model_tier_pro)
        )

        return sortedSelected.mapIndexed { index, model ->
            OnboardingModelTier(
                tierName = tierNames[index],
                model = model,
                isRecommended = model.id == recModelId,
                recommendedReasonResId = if (model.id == recModelId) recommendation.reasonResId else null
            )
        }
    }
}
