package com.dipdev.aiautocaptioner.core.video

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

enum class VideoWarnLevel {
    NONE,
    WARN,         // 10 - 30 minutes
    STRONG_WARN   // > 30 minutes
}

data class VideoGuardResult(
    val warnLevel: VideoWarnLevel,
    val estimatedTimeSeconds: Int,
    val recommendationMessage: String,
    val canProceed: Boolean = true
)

@Singleton
class VideoLengthGuard @Inject constructor() {

    companion object {
        private const val TEN_MIN_MS = 10 * 60 * 1000L
        private const val THIRTY_MIN_MS = 30 * 60 * 1000L
    }

    fun evaluate(videoDurationMs: Long, modelId: String = "base.en", totalRamMb: Int = 4000): VideoGuardResult {
        if (videoDurationMs <= 0) {
            return VideoGuardResult(
                warnLevel = VideoWarnLevel.NONE,
                estimatedTimeSeconds = 0,
                recommendationMessage = ""
            )
        }

        // Estimate seconds: ~3.5s per 30-s window for base models on mid-range hardware
        val windowMultiplier = when {
            modelId.startsWith("tiny") -> 2.0
            modelId.startsWith("base") -> 3.5
            modelId.startsWith("small") -> 7.0
            else -> 4.0
        }

        val windowCount = ceil(videoDurationMs / 30000.0).toInt().coerceAtLeast(1)
        val estimatedSecs = (windowCount * windowMultiplier).toInt()

        return when {
            videoDurationMs >= THIRTY_MIN_MS -> {
                VideoGuardResult(
                    warnLevel = VideoWarnLevel.STRONG_WARN,
                    estimatedTimeSeconds = estimatedSecs,
                    recommendationMessage = "This video is over 30 minutes long. Transcription will take significant time and battery. Consider trimming the clip or choosing a faster model.",
                    canProceed = true
                )
            }
            videoDurationMs >= TEN_MIN_MS -> {
                VideoGuardResult(
                    warnLevel = VideoWarnLevel.WARN,
                    estimatedTimeSeconds = estimatedSecs,
                    recommendationMessage = "This video is over 10 minutes long (estimated processing time: ~${estimatedSecs / 60} minutes). AutoCaptioner is optimized for short videos.",
                    canProceed = true
                )
            }
            else -> {
                VideoGuardResult(
                    warnLevel = VideoWarnLevel.NONE,
                    estimatedTimeSeconds = estimatedSecs,
                    recommendationMessage = ""
                )
            }
        }
    }
}
