package com.dipdev.aiautocaptioner.ui.videoeditor.overlay

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import com.dipdev.aiautocaptioner.ui.theme.AccentCyan
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Renders all currently-visible image overlays on top of the video preview.
 *
 * Fix 1 — Recomposition: Each overlay is isolated in its own [OverlayItem] composable.
 * The parent [BoxWithConstraints] no longer reads [currentTimelineMs] at all — only each
 * child reads it within its own scope, so a visibility change for one overlay only
 * recomposes that one child, not the entire overlay surface.
 *
 * Fix 3 — State loss: A [DisposableEffect] inside [OverlayItem] fires [onUpdateOverlay]
 * synchronously in onDispose, ensuring the last transform is persisted even if the
 * user navigates away before the 300 ms debounce fires.
 */
@Composable
fun OverlayRenderer(
    overlays: List<ImageOverlayEntity>,
    currentTimelineMs: () -> Long,
    selectedOverlayId: String?,
    onUpdateOverlay: (ImageOverlayEntity) -> Unit,
    onSelectOverlay: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val canvasWidth = constraints.maxWidth.toFloat()
        val canvasHeight = constraints.maxHeight.toFloat()

        // Fix 1: iterate all overlays; each child decides its own visibility
        overlays.forEach { overlay ->
            OverlayItem(
                overlay = overlay,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                isSelected = overlay.id == selectedOverlayId,
                currentTimelineMs = currentTimelineMs,
                onUpdateOverlay = onUpdateOverlay,
                onSelectOverlay = onSelectOverlay
            )
        }
    }
}

/**
 * Single overlay item. Reads [currentTimelineMs] in its own isolated scope (Fix 1).
 * Persists transform state on disposal (Fix 3).
 */
@Composable
private fun BoxScope.OverlayItem(
    overlay: ImageOverlayEntity,
    canvasWidth: Float,
    canvasHeight: Float,
    isSelected: Boolean,
    currentTimelineMs: () -> Long,
    onUpdateOverlay: (ImageOverlayEntity) -> Unit,
    onSelectOverlay: (String) -> Unit
) {
    // Fix 1: visibility read is isolated to this composable
    val isVisible = currentTimelineMs() in overlay.startTimeMs..overlay.endTimeMs
    if (!isVisible) return

    var localScale by remember(overlay.id) { mutableFloatStateOf(overlay.scaleX) }
    var localPosX by remember(overlay.id) { mutableFloatStateOf(overlay.positionX) }
    var localPosY by remember(overlay.id) { mutableFloatStateOf(overlay.positionY) }
    var lastTransformTime by remember(overlay.id) { mutableLongStateOf(0L) }
    // Fix 3: track whether there is a pending unsaved transform
    var hasPendingTransform by remember(overlay.id) { mutableStateOf(false) }

    // Sync external changes back to local state (only when no local edits are in flight)
    LaunchedEffect(overlay.scaleX, overlay.positionX, overlay.positionY) {
        if (System.currentTimeMillis() - lastTransformTime > 500) {
            localScale = overlay.scaleX
            localPosX = overlay.positionX
            localPosY = overlay.positionY
        }
    }

    // Debounced persist — fires 300 ms after the last gesture update
    LaunchedEffect(lastTransformTime) {
        if (lastTransformTime > 0) {
            delay(300.milliseconds)
            onUpdateOverlay(
                overlay.copy(
                    scaleX = localScale,
                    scaleY = localScale,
                    positionX = localPosX,
                    positionY = localPosY
                )
            )
            hasPendingTransform = false
        }
    }

    // Fix 3: Flush any pending transform synchronously when this composable leaves composition
    // (e.g. user navigates away within the 300 ms debounce window)
    DisposableEffect(overlay.id) {
        onDispose {
            if (hasPendingTransform) {
                onUpdateOverlay(
                    overlay.copy(
                        scaleX = localScale,
                        scaleY = localScale,
                        positionX = localPosX,
                        positionY = localPosY
                    )
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .graphicsLayer {
                translationX = (localPosX - 0.5f) * canvasWidth
                translationY = (localPosY - 0.5f) * canvasHeight
                scaleX = localScale
                scaleY = localScale
            }
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) AccentCyan else Color.Transparent
            )
            .pointerInput(overlay.id) {
                detectTransformGestures { _, pan, zoom, _ ->
                    localScale *= zoom
                    localPosX += (pan.x / canvasWidth)
                    localPosY += (pan.y / canvasHeight)
                    lastTransformTime = System.currentTimeMillis()
                    hasPendingTransform = true
                }
            }
            .pointerInput(overlay.id + "_tap") {
                detectTapGestures {
                    onSelectOverlay(overlay.id)
                }
            }
    ) {
        AsyncImage(
            model = overlay.imageUri,
            contentDescription = "Image Overlay",
            modifier = Modifier
                .widthIn(max = 200.dp)
                .heightIn(max = 200.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )
    }
}
