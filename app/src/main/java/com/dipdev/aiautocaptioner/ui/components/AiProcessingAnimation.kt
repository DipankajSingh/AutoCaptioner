package com.dipdev.aiautocaptioner.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Animated processing indicator utilizing the TV-Robot Mascot.
 * Replaces the old pulse animation while keeping CPU/GPU footprint minimal during Whisper processing.
 */
@Composable
fun AiProcessingAnimation(
    modifier: Modifier = Modifier,
    progress: Float = 0f // 0..1
) {
    val dialogType = if (progress >= 1f) DialogType.SUCCESS else DialogType.INFO
    MascotRobot(
        type = dialogType,
        modifier = modifier
    )
}
