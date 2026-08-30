package com.dipdev.aiautocaptioner.ui.recorder.components

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.dipdev.aiautocaptioner.core.gl.RendererThread

/**
 * Hosts a bare [TextureView] and forwards its [SurfaceTexture] lifecycle to [rendererThread].
 * This view shows exactly what will be recorded — the crop/rotate transform is baked into
 * every frame by the GL renderer before it ever reaches this surface, so no separate
 * crop-guide overlay is needed on top of it.
 */
@Composable
fun CameraPreviewView(
    rendererThread: RendererThread,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            TextureView(context).apply {
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
