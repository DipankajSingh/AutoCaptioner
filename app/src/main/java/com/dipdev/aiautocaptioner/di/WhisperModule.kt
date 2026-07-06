package com.dipdev.aiautocaptioner.di

import com.dipdev.aiautocaptioner.data.model.WhisperModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WhisperModule {

    @Provides
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
        ),
        WhisperModel(
            id = "es.tiny",
            displayName = "Tiny — Spanish",
            description = "Fast model fine-tuned for Spanish.",
            sizeBytes = 77_691_730L,
            downloadUrl = "https://huggingface.co/fffffx/whisper-es-tiny-ggml-format/resolve/main/ggml-tiny-es.bin",
            minRamMb = 512,
            accuracy = 2,
            speed = 5,
            languages = listOf("es"),
            isMultilingual = false
        ),
        WhisperModel(
            id = "de.tiny.q8",
            displayName = "Tiny — German (Q8)",
            description = "Extremely fast German model.",
            sizeBytes = 43_537_433L,
            downloadUrl = "https://huggingface.co/Pomni/whisper-tiny-german-1224-ggml-allquants/resolve/main/ggml-tiny-german-1224-q8_0.bin",
            minRamMb = 512,
            accuracy = 2,
            speed = 5,
            languages = listOf("de"),
            isMultilingual = false
        ),
        WhisperModel(
            id = "zh-TW.base.q5",
            displayName = "Base — Traditional Chinese (Q5)",
            description = "Fast model for Traditional Chinese / Taiwan.",
            sizeBytes = 59_707_642L,
            downloadUrl = "https://huggingface.co/Qwe1325/whisper-base-tw-ggml/resolve/main/ggml-model-whisper-base.tw-q5_1.bin",
            minRamMb = 1024,
            accuracy = 3,
            speed = 4,
            languages = listOf("zh-TW", "zh"),
            isMultilingual = false
        ),
        WhisperModel(
            id = "hi.base",
            displayName = "Base — Hindi",
            description = "High accuracy Hindi model.",
            sizeBytes = 147_951_482L,
            downloadUrl = "https://huggingface.co/khidrew/whisper-base-hindi-ggml/resolve/main/ggml-base-hi.bin",
            minRamMb = 1024,
            accuracy = 3,
            speed = 4,
            languages = listOf("hi"),
            isMultilingual = false
        ),
        WhisperModel(
            id = "yue.base",
            displayName = "Base — Cantonese",
            description = "High accuracy Cantonese model.",
            sizeBytes = 147_951_482L,
            downloadUrl = "https://huggingface.co/wabisabisocial/whisper-base-cantonese-ggml/resolve/main/ggml-base-yue.bin",
            minRamMb = 1024,
            accuracy = 3,
            speed = 4,
            languages = listOf("yue"),
            isMultilingual = false
        ),
        WhisperModel(
            id = "fr.tiny",
            displayName = "Tiny — French",
            description = "Fast French model.",
            sizeBytes = 77_691_713L,
            downloadUrl = "https://huggingface.co/wabisabisocial/whisper-tiny-french-ggml/resolve/main/ggml-tiny-fr.bin",
            minRamMb = 512,
            accuracy = 2,
            speed = 5,
            languages = listOf("fr"),
            isMultilingual = false
        ),
        WhisperModel(
            id = "it.tiny",
            displayName = "Tiny — Italian",
            description = "Fast Italian model.",
            sizeBytes = 77_691_713L,
            downloadUrl = "https://huggingface.co/wabisabisocial/whisper-tiny-italian-ggml/resolve/main/ggml-tiny-it.bin",
            minRamMb = 512,
            accuracy = 2,
            speed = 5,
            languages = listOf("it"),
            isMultilingual = false
        ),
        WhisperModel(
            id = "ko.tiny",
            displayName = "Tiny — Korean",
            description = "Fast Korean model.",
            sizeBytes = 77_691_730L,
            downloadUrl = "https://huggingface.co/wabisabisocial/whisper-tiny-korean-ggml/resolve/main/ggml-tiny-ko.bin",
            minRamMb = 512,
            accuracy = 2,
            speed = 5,
            languages = listOf("ko"),
            isMultilingual = false
        ),
        WhisperModel(
            id = "ru.tiny",
            displayName = "Tiny — Russian",
            description = "Fast Russian model.",
            sizeBytes = 77_691_730L,
            downloadUrl = "https://huggingface.co/wabisabisocial/whisper-tiny-russian-ggml/resolve/main/ggml-tiny-ru.bin",
            minRamMb = 512,
            accuracy = 2,
            speed = 5,
            languages = listOf("ru"),
            isMultilingual = false
        ),
        WhisperModel(
            id = "ar.base",
            displayName = "Base — Arabic",
            description = "High accuracy Arabic model.",
            sizeBytes = 147_951_465L,
            downloadUrl = "https://huggingface.co/B1uqa/whisper-base-ar-quran-ggml/resolve/main/ggml-model.bin",
            minRamMb = 1024,
            accuracy = 3,
            speed = 4,
            languages = listOf("ar"),
            isMultilingual = false
        ),
        WhisperModel(
            id = "fr.base",
            displayName = "Base — French",
            description = "High accuracy French model.",
            sizeBytes = 147_951_465L,
            downloadUrl = "https://huggingface.co/wabisabisocial/whisper-base-french-ggml/resolve/main/ggml-base-fr.bin",
            minRamMb = 1024,
            accuracy = 3,
            speed = 4,
            languages = listOf("fr"),
            isMultilingual = false
        ),
        WhisperModel(
            id = "it.base.q5",
            displayName = "Base — Italian (Q5)",
            description = "Highly compressed Base Italian model.",
            sizeBytes = 55_295_450L,
            downloadUrl = "https://huggingface.co/LocalAI-io/whisper-base-it-ggml/resolve/main/ggml-model-q5_0.bin",
            minRamMb = 1024,
            accuracy = 3,
            speed = 4,
            languages = listOf("it"),
            isMultilingual = false
        ),
        WhisperModel(
            id = "ko.base",
            displayName = "Base — Korean",
            description = "High accuracy Korean model.",
            sizeBytes = 147_951_482L,
            downloadUrl = "https://huggingface.co/wabisabisocial/whisper-base-korean-ggml/resolve/main/ggml-base-ko.bin",
            minRamMb = 1024,
            accuracy = 3,
            speed = 4,
            languages = listOf("ko"),
            isMultilingual = false
        ),
        WhisperModel(
            id = "ru.base",
            displayName = "Base — Russian",
            description = "High accuracy Russian model.",
            sizeBytes = 147_951_482L,
            downloadUrl = "https://huggingface.co/wabisabisocial/whisper-base-russian-ggml/resolve/main/ggml-base-ru.bin",
            minRamMb = 1024,
            accuracy = 3,
            speed = 4,
            languages = listOf("ru"),
            isMultilingual = false
        )
    )
}