package com.dipdev.aiautocaptioner.ui.processing.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.ui.processing.StreamedSegment
import com.dipdev.aiautocaptioner.ui.components.AiProcessingAnimation
import com.dipdev.aiautocaptioner.ui.components.AppOutlinedButton
import com.dipdev.aiautocaptioner.ui.components.GlassmorphicCard
import com.dipdev.aiautocaptioner.ui.components.FullScreenStateContainer
import com.dipdev.aiautocaptioner.ui.processing.ProcessingStep
import com.dipdev.aiautocaptioner.ui.theme.AccentCyan

@Composable
fun TranscribingStateView(
    step: ProcessingStep.Transcribing,
    streamedSegments: List<StreamedSegment>,
    onCancel: () -> Unit
) {
    val rawProgress = step.progress
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "transcriptionProgress"
    )
    val percent = (rawProgress * 100).toInt()

    FullScreenStateContainer(
        graphicContent = {
            GlassmorphicCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AiProcessingAnimation(
                        modifier = Modifier.fillMaxSize(),
                        progress = animatedProgress
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$percent%",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Generating...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        if (step.estimatedSecondsRemaining != null) {
                            val secs = step.estimatedSecondsRemaining
                            Text(
                                text = if (secs >= 60) "~${secs / 60}m left" else "~${secs}s left",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        },
        textContent = {
            GlassmorphicCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (streamedSegments.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                strokeCap = StrokeCap.Round,
                                color = AccentCyan
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Waiting for first caption...",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                } else {
                    val scrollState = rememberScrollState()

                    LaunchedEffect(scrollState.maxValue) {
                        if (scrollState.value < scrollState.maxValue) {
                            scrollState.animateScrollTo(
                                scrollState.maxValue,
                                animationSpec = tween(300, easing = LinearOutSlowInEasing)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val total = streamedSegments.size
                            val displaySegments = remember(streamedSegments) { streamedSegments.takeLast(60) }
                            val startIndex = total - displaySegments.size

                            displaySegments.forEachIndexed { index, segment ->
                                val globalIndex = startIndex + index
                                val isRecent = globalIndex >= total - 3

                                val targetColor = if (isRecent) {
                                    AccentCyan
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                }
                                val animatedColor by animateColorAsState(
                                    targetValue = targetColor,
                                    animationSpec = tween(400),
                                    label = "textColor"
                                )

                                key("${segment.startMs}_${segment.endMs}_${segment.text}") {
                                    val entranceAnim = remember { Animatable(0f) }
                                    LaunchedEffect(Unit) {
                                        entranceAnim.animateTo(
                                            targetValue = 1f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessLow
                                            )
                                        )
                                    }

                                    val alphaVal = entranceAnim.value.coerceIn(0f, 1f)
                                    val scaleVal = 0.8f + (0.2f * entranceAnim.value)
                                    val offsetYVal = (10f * (1f - entranceAnim.value)).dp

                                    Text(
                                        text = segment.text,
                                        fontSize = 16.sp,
                                        color = animatedColor.copy(alpha = alphaVal * animatedColor.alpha),
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.graphicsLayer {
                                            scaleX = scaleVal
                                            scaleY = scaleVal
                                            translationY = offsetYVal.toPx()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        actionContent = {
            Spacer(modifier = Modifier.height(8.dp))

            AppOutlinedButton(onClick = onCancel) {
                Text("Cancel", maxLines = 1)
            }
        }
    )
}
