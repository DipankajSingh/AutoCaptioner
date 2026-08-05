package androidx.camera.view

import android.util.Rational
import android.view.Surface
import androidx.camera.core.Preview
import androidx.camera.core.ViewPort

/**
 * Bridges LifecycleCameraController to native declarative Compose [androidx.camera.compose.CameraXViewfinder].
 * 
 * Accesses package-private attachPreviewSurface within androidx.camera.view to supply a direct
 * [androidx.camera.core.SurfaceRequest] to Compose without legacy AndroidView or PreviewView instantiation.
 */
fun LifecycleCameraController.attachToComposeSurface(
    surfaceProvider: Preview.SurfaceProvider,
    aspectRatio: Rational = Rational(9, 16),
    rotation: Int = Surface.ROTATION_0
) {
    val viewPort = ViewPort.Builder(aspectRatio, rotation).build()
    attachPreviewSurface(surfaceProvider, viewPort)
}
