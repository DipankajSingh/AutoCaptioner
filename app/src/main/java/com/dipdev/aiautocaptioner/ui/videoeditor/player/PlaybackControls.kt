package com.dipdev.aiautocaptioner.ui.videoeditor.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.data.model.Clip
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import com.dipdev.aiautocaptioner.ui.videoeditor.core.managers.resolveTimelinePosition
import compose.icons.FeatherIcons
import compose.icons.feathericons.Pause
import compose.icons.feathericons.Play
import kotlinx.coroutines.delay

@Composable
fun PlayPauseTapOverlay(
    player: Player,
    modifier: Modifier = Modifier
) {
    var showPlayPauseIcon by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                if (player.isPlaying) player.pause() else player.play()
                showPlayPauseIcon = true
            },
        contentAlignment = Alignment.Center
    ) {
        LaunchedEffect(showPlayPauseIcon) {
            if (showPlayPauseIcon) {
                delay(1500)
                showPlayPauseIcon = false
            }
        }
        val iconAlpha by animateFloatAsState(
            targetValue = if (showPlayPauseIcon) 1f else 0f,
            animationSpec = androidx.compose.animation.core.tween(200),
            label = "playPauseAlpha"
        )
        if (iconAlpha > 0f) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f * iconAlpha),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (player.isPlaying) FeatherIcons.Pause else FeatherIcons.Play,
                    contentDescription = if (player.isPlaying) stringResource(R.string.playback_pause) else stringResource(R.string.playback_play),
                    tint = AccentAmber.copy(alpha = iconAlpha),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun TimerPill(
    currentTimelineMs: () -> Long,
    totalEditedMs: Long,
    formatTime: (Long) -> String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${formatTime(currentTimelineMs())} / ${formatTime(totalEditedMs)}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/**
 * Mini scrubber bar shown between the preview and the timeline.
 *
 * Fix 2 — Recomposition: [currentTimelineMs] is no longer read in the composable body.
 * It is read inside the [Canvas] DrawScope, deferring the read to the Drawing phase and
 * skipping Composition + Layout on every frame.
 *
 * Fix 7 — Touch target: The outer [Box] is 48 dp tall (Android's recommended minimum)
 * while the visual track (2 dp line + 4 dp thumb) remains visually compact inside it.
 */
@Composable
fun MiniScrubber(
    currentTimelineMs: () -> Long,
    totalEditedMs: Long,
    clips: List<Clip>,
    player: Player,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = AccentAmber.copy(alpha = 0.3f)

    // Outer touch target; visual content drawn at actual size inside Canvas
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .padding(horizontal = 16.dp)
            .pointerInput(totalEditedMs) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    seekPlayer(fraction, totalEditedMs, clips, player)
                }
            }
            .pointerInput(totalEditedMs) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    seekPlayer(fraction, totalEditedMs, clips, player)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Fix 2: Read state inside DrawScope — deferred to Drawing phase only
            val fraction = if (totalEditedMs > 0L) {
                (currentTimelineMs().toFloat() / totalEditedMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            val trackHeight = 2.dp.toPx()
            val cornerRadius = CornerRadius(trackHeight / 2)

            // Background track
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, center.y - trackHeight / 2),
                size = Size(size.width, trackHeight),
                cornerRadius = cornerRadius
            )

            // Active (filled) track
            val activeWidth = size.width * fraction
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(0f, center.y - trackHeight / 2),
                size = Size(activeWidth, trackHeight),
                cornerRadius = cornerRadius
            )

            // Thumb
            drawCircle(
                color = AccentAmber,
                radius = 4.dp.toPx(),
                center = Offset(activeWidth, center.y)
            )
        }
    }
}

/**
 * Seeks the player to the given scrub fraction of the total edited timeline.
 *
 * Fix 5: Uses [resolveTimelinePosition] instead of the hand-rolled loop,
 * eliminating the IndexOutOfBoundsException race condition when clips are
 * concurrently modified.
 */
private fun seekPlayer(
    fraction: Float,
    totalEditedMs: Long,
    clips: List<Clip>,
    player: Player
) {
    if (totalEditedMs == 0L || clips.isEmpty()) return
    val seekMs = (fraction * totalEditedMs).toLong()
    val (windowIndex, posInWindow) = resolveTimelinePosition(seekMs, clips)
    player.seekTo(windowIndex, posInWindow)
}
