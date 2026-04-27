package com.dipdev.autocaptioner.di

import com.dipdev.autocaptioner.data.model.WhisperModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WhisperModule {

    @Provides
    @Singleton
    fun provideAvailableModels(): List<WhisperModel> = listOf(
        WhisperModel(
            id = "tiny.en",
            displayName = "Tiny — English only",
            description = "Fastest model. Great for short clips on any device.",
            sizeBytes = 75_000_000L,
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin",
            minRamMb = 512,
            accuracy = 2,
            speed = 5,
            languages = listOf("en"),
            isMultilingual = false
        ),
        WhisperModel(
            id = "base.en",
            displayName = "Base — English only",
            description = "Best balance of speed and accuracy for English content.",
            sizeBytes = 142_000_000L,
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin",
            minRamMb = 1024,
            accuracy = 3,
            speed = 4,
            languages = listOf("en"),
            isMultilingual = false
        ),
        WhisperModel(
            id = "small.en",
            displayName = "Small — English only",
            description = "Higher accuracy for longer or complex English videos.",
            sizeBytes = 466_000_000L,
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.en.bin",
            minRamMb = 2048,
            accuracy = 4,
            speed = 3,
            languages = listOf("en"),
            isMultilingual = false
        ),
        WhisperModel(
            id = "base",
            displayName = "Base — Multilingual",
            description = "Supports 99 languages. Slightly slower than base.en.",
            sizeBytes = 142_000_000L,
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
            minRamMb = 1024,
            accuracy = 3,
            speed = 4,
            languages = listOf("multilingual"),
            isMultilingual = true
        ),
        WhisperModel(
            id = "small",
            displayName = "Small — Multilingual",
            description = "Best accuracy for non-English content.",
            sizeBytes = 466_000_000L,
            downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin",
            minRamMb = 2048,
            accuracy = 4,
            speed = 3,
            languages = listOf("multilingual"),
            isMultilingual = true
        )
    )
}