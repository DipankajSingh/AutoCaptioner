package com.dipdev.aiautocaptioner.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Processing animation driven by the TV-Robot Mascot.
 * Maps processing progress to a contextually appropriate MascotMode.
 *
 * Callers pass [progress] (0..1); mode is inferred:
 *   0.0        → Listening (actively transcribing / processing)
 *   1.0        → Celebrating (all done)
 *
 * For fine-grained control per processing step, call MascotRobot directly
 * with the appropriate MascotMode.
 */
@Composable
fun AiProcessingAnimation(
    modifier: Modifier = Modifier,
    progress: Float = 0f  // 0..1
) {
    val mode = if (progress >= 1f) MascotMode.Celebrating else MascotMode.Listening
    MascotRobot(mode = mode, modifier = modifier)
}
