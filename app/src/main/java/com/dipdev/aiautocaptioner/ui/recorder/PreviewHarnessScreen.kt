package com.dipdev.aiautocaptioner.ui.recorder

import android.Manifest
import android.content.pm.PackageManager
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.dipdev.aiautocaptioner.core.camera.Camera2Session
import com.dipdev.aiautocaptioner.core.camera.CameraCapabilities
import com.dipdev.aiautocaptioner.core.camera.CameraFacing
import com.dipdev.aiautocaptioner.core.camera.RecordingAspectRatio
import com.dipdev.aiautocaptioner.core.camera.RecordingQuality
import com.dipdev.aiautocaptioner.core.gl.RendererThread
import com.dipdev.aiautocaptioner.ui.recorder.components.CameraPreviewView
import kotlinx.coroutines.CompletableDeferred

/**
 * TEMPORARY stage-1 harness. Its only job is to prove the Camera2 -> GL -> TextureView
 * preview pipeline works correctly on a real device — correct upright orientation, correct
 * crop to the selected aspect ratio, no stalls — before MediaCodec encoding is layered in.
 *
 * This is NOT wired into NavGraph and is NOT the real recorder screen. It has no controls,
 * no quality/ratio picker, no recording button. It will be deleted once the real
 * `RecorderViewModel` / `SmartRecorderScreen` lands (stage 3), which will use the exact same
 * `RendererThread` + `Camera2Session` + `CameraPreviewView` this harness exercises.
 *
 * TO TEST: temporarily point a debug NavGraph destination (or MainActivity's setContent) at
 * this composable, run on a physical device, and confirm:
 *  1. the preview appears upright (not sideways/upside-down) for the back camera
 *  2. the preview fills a 9:16 portrait frame (default aspect), correctly center-cropped
 *  3. no visible stutter/frozen frames
 * Switching CameraFacing.BACK to CameraFacing.FRONT below lets you repeat the check for the
 * front lens, including verifying the mirrored preview looks natural (not flipped text, etc).
 */
@Composable
fun PreviewHarnessScreen() {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasCameraPermission) return

    val cameraSurfaceDeferred = remember { CompletableDeferred<Surface>() }
    val rendererThread = remember {
        RendererThread(onCameraSurfaceReady = { cameraSurfaceDeferred.complete(it) }).also { it.start() }
    }
    var cameraSession by remember { mutableStateOf<Camera2Session?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            cameraSession?.close()
            rendererThread.release()
        }
    }

    LaunchedEffect(rendererThread) {
        val capabilities = CameraCapabilities(context)
        val cameraId = capabilities.findCameraId(CameraFacing.BACK) ?: return@LaunchedEffect

        val ratio = RecordingAspectRatio.DEFAULT
        val quality = RecordingQuality.DEFAULT
        // NOTE: this is the real sensor-native (landscape) stream size, NOT
        // ratio.resolve(quality) — that method returns the portrait, already-cropped
        // *final output* size, which is not a valid size to request from the camera itself.
        val cameraStreamSize = capabilities.bestCameraStreamSize(cameraId, quality, ratio)

        val cameraSurface = cameraSurfaceDeferred.await()
        rendererThread.setCameraFrameSize(cameraStreamSize.width, cameraStreamSize.height)
        rendererThread.setAspectRatio(ratio)

        val session = Camera2Session(context, cameraId, cameraSurface)
        rendererThread.setTransformParams(session.sensorOrientation, mirrorPreviewForFrontCamera = false)
        session.open()
        cameraSession = session
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreviewView(rendererThread = rendererThread)
    }
}
