package com.dipdev.aiautocaptioner.ui.recorder.components

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView
import com.dipdev.aiautocaptioner.core.gl.RendererThread

/**
 * Hosts a bare [TextureView] and forwards its [SurfaceTexture] lifecycle to [rendererThread].
 * This view shows exactly what will be recorded — the crop/rotate transform is baked into
 * every frame by the GL renderer before it ever reaches this surface, so no separate
 * crop-guide overlay is needed on top of it.
 *
 * Gesture handling:
 *  - Pinch-to-zoom: [onZoomChange] is called with the new absolute zoom ratio on each update.
 *  - Tap-to-focus: [onTapToFocus] is called with normalized (0..1, 0..1) view-space coordinates.
 */
@Composable
fun CameraPreviewView(
    rendererThread: RendererThread,
    onZoomChange: ((Float) -> Unit)? = null,
    onTapToFocus: ((Float, Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Track accumulated zoom so each new pinch gesture starts from the current level.
    var currentZoom by remember { mutableFloatStateOf(1f) }
    // Track view size for tap normalization.
    var viewWidthPx by remember { mutableFloatStateOf(1f) }
    var viewHeightPx by remember { mutableFloatStateOf(1f) }

    AndroidView(
        modifier = modifier
            .onSizeChanged { size ->
                viewWidthPx = size.width.toFloat().coerceAtLeast(1f)
                viewHeightPx = size.height.toFloat().coerceAtLeast(1f)
            }
            .pointerInput(onZoomChange) {
                if (onZoomChange == null) return@pointerInput
                detectTransformGestures { _, _, zoomDelta, _ ->
                    // Multiply current level by the per-frame delta from the pinch gesture.
                    val newZoom = (currentZoom * zoomDelta).coerceIn(1f, 10f)
                    if (newZoom != currentZoom) {
                        currentZoom = newZoom
                        onZoomChange(newZoom)
                    }
                }
            }
            .pointerInput(onTapToFocus) {
                if (onTapToFocus == null) return@pointerInput
                detectTapGestures { offset ->
                    val normX = (offset.x / viewWidthPx).coerceIn(0f, 1f)
                    val normY = (offset.y / viewHeightPx).coerceIn(0f, 1f)
                    onTapToFocus(normX, normY)
                }
            },
        factory = { context ->
            TextureView(context).apply {
                // TextureView must be opaque to render correctly inside Compose on
                // many devices — without this, the compositor may not composite it.
                isOpaque = true
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                        rendererThread.setPreviewViewportSize(width, height)
                        rendererThread.setPreviewSurface(Surface(st))
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                        rendererThread.setPreviewViewportSize(width, height)
                    }

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        rendererThread.setPreviewSurface(null)
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose { rendererThread.setPreviewSurface(null) }
    }
}
