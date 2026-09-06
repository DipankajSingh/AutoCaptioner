package com.dipdev.aiautocaptioner.ui.recorder.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.recorder.RecordingPhase
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import com.dipdev.aiautocaptioner.ui.theme.AccentRose
import com.dipdev.aiautocaptioner.ui.theme.GlassSurface

/**
 * Bottom overlay bar containing recording controls.
 *
 * Shows different content per [phase]:
 *  - Idle/Previewing: large red record button only.
 *  - Recording: pause + stop buttons + live timer.
 *  - Paused: resume + stop buttons + blinking timer.
 *  - Finalizing: progress indicator.
 */
@Composable
fun RecordingControlsBar(
    phase: RecordingPhase,
    elapsedMs: Long,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(GlassSurface)
            .padding(vertical = 20.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        when (phase) {
            is RecordingPhase.Idle, is RecordingPhase.Previewing -> {
                RecordButton(onClick = onStart)
            }

            is RecordingPhase.Recording -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ControlIconButton(
                        icon = { Icon(Icons.Default.Pause, contentDescription = stringResource(R.string.recorder_pause)) },
                        onClick = onPause
                    )
                    TimerText(elapsedMs = elapsedMs, blinking = false)
                    ControlIconButton(
                        icon = { Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.recorder_stop)) },
                        onClick = onStop,
                        tint = AccentRose
                    )
                }
            }

            is RecordingPhase.Paused -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ControlIconButton(
                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.recorder_resume)) },
                        onClick = onResume
                    )
                    TimerText(elapsedMs = elapsedMs, blinking = true)
                    ControlIconButton(
                        icon = { Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.recorder_stop)) },
                        onClick = onStop,
                        tint = AccentRose
                    )
                }
            }

            is RecordingPhase.Finalizing -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = AccentAmber,
                    strokeWidth = 3.dp
                )
            }

            else -> Unit
        }
    }
}

@Composable
private fun RecordButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(AccentRose)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(64.dp)) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

@Composable
private fun ControlIconButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    tint: Color = Color.White
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick) {
            Box(contentAlignment = Alignment.Center) {
                icon()
            }
        }
    }
}

@Composable
private fun TimerText(elapsedMs: Long, blinking: Boolean) {
    val totalSeconds = (elapsedMs / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val formatted = "%02d:%02d".format(minutes, seconds)

    val alpha = if (blinking) {
        val transition = rememberInfiniteTransition(label = "timer_blink")
        val anim by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "blink_alpha"
        )
        anim
    } else 1f

    Text(
        text = formatted,
        color = if (blinking) AccentAmber else AccentRose,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .width(80.dp)
            .alpha(alpha)
    )
}
