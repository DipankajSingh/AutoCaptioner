package com.dipdev.aiautocaptioner.engine.animation

import com.dipdev.aiautocaptioner.engine.timing.WordLifecycle


data class WordTransform(
    val alpha: Float = 1f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val translateX: Float = 0f,
    val translateY: Float = 0f,
    val clipFraction: Float = 1f,
    val colorOverride: Int? = null
)


interface WordAnimator {
    val id: String

    fun computeTransform(
        progress: Float,
        lifecycle: WordLifecycle,
        baseScale: Float
    ): WordTransform
}
