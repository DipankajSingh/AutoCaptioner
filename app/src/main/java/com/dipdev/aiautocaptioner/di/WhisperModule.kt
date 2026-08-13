package com.dipdev.aiautocaptioner.di

import com.dipdev.aiautocaptioner.data.model.WhisperModel
import com.dipdev.aiautocaptioner.data.source.WhisperModelRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object WhisperModule {

    @Provides
    fun provideAvailableModels(registry: WhisperModelRegistry): List<WhisperModel> = registry.getModels()
}
