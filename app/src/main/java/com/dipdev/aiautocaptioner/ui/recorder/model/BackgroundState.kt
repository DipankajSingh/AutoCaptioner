package com.dipdev.aiautocaptioner.ui.recorder.model

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Immutable

@Immutable
sealed class BackgroundState {
    data class SolidColor(val color: Color) : BackgroundState()
    data class Gradient(val colors: List<Color>) : BackgroundState()

    companion object {
        val Default = Gradient(
            listOf(
                Color(0xFF4158D0.toInt()),
                Color(0xFFC850C0.toInt()),
                Color(0xFFFFCC70.toInt())
            )
        )
    }
}
