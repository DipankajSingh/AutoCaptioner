package com.dipdev.aiautocaptioner.ui.videoeditor.overlay

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import com.dipdev.aiautocaptioner.ui.theme.AccentCyan
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val AUTO_FIT_SCALE = 0.35f
private const val MIN_SCALE = 0.05f
private const val MAX_SCALE = 5.0f
private const val DEBOUNCE_MS = 300L

private fun clampScale(v: Float) = v.coerceIn(MIN_SCALE, MAX_SCALE)

private fun computeVideoDisplayRect(
    containerW: Float, containerH: Float,
    videoW: Int, videoH: Int
): Triple<Float, Float, Pair<Float, Float>> {
    if (videoW <= 0 || videoH <= 0) return Triple(0f, 0f, containerW to containerH)
    val videoAspect = videoW.toFloat() / videoH.toFloat()
    val containerAspect = containerW / containerH
    return if (videoAspect > containerAspect) {
        val dw = containerW
        val dh = containerW / videoAspect
        Triple(0f, (containerH - dh) / 2f, dw to dh)
    } else {
        val dh = containerH
        val dw = containerH * videoAspect
        Triple((containerW - dw) / 2f, 0f, dw to dh)
    }
}

@Composable
fun OverlayRenderer(
    overlays: List<ImageOverlayEntity>,
    currentTimelineMs: () -> Long,
    selectedOverlayId: String?,
    onUpdateOverlay: (ImageOverlayEntity) -> Unit,
    onSelectOverlay: (String) -> Unit,
    videoWidth: Int = 0,
    videoHeight: Int = 0,
    player: Player? = null,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()

        val (offsetX, offsetY, videoRect) = remember(containerW, containerH, videoWidth, videoHeight) {
            computeVideoDisplayRect(containerW, containerH, videoWidth, videoHeight)
        }
        val canvasWidth = videoRect.first
        val canvasHeight = videoRect.second

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .width(with(LocalDensity.current) { canvasWidth.toDp() })
                .height(with(LocalDensity.current) { canvasHeight.toDp() })
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTapGestures {
                        onSelectOverlay("")
                        player?.let { p ->
                            if (p.isPlaying) p.pause() else p.play()
                        }
                    }
                }
        ) {
            overlays.forEach { overlay ->
                OverlayItem(
                    overlay = overlay,
                    canvasWidth = canvasWidth,
                    canvasHeight = canvasHeight,
                    isSelected = overlay.id == selectedOverlayId,
                    currentTimelineMs = currentTimelineMs,
                    onUpdateOverlay = onUpdateOverlay,
                    onSelectOverlay = onSelectOverlay,
                    player = player
                )
            }
        }
    }
}

@Composable
private fun BoxScope.OverlayItem(
    overlay: ImageOverlayEntity,
    canvasWidth: Float,
    canvasHeight: Float,
    isSelected: Boolean,
    currentTimelineMs: () -> Long,
    onUpdateOverlay: (ImageOverlayEntity) -> Unit,
    onSelectOverlay: (String) -> Unit,
    player: Player?
) {
    val isVisible = currentTimelineMs() in overlay.startTimeMs..overlay.endTimeMs
    if (!isVisible) return

    var localScaleX by remember(overlay.id) { mutableFloatStateOf(overlay.scaleX) }
    var localScaleY by remember(overlay.id) { mutableFloatStateOf(overlay.scaleY) }
    var localPosX by remember(overlay.id) { mutableFloatStateOf(overlay.positionX) }
    var localPosY by remember(overlay.id) { mutableFloatStateOf(overlay.positionY) }
    var lastTransformTime by remember(overlay.id) { mutableLongStateOf(0L) }
    var hasPendingTransform by remember(overlay.id) { mutableStateOf(false) }
    var wasPlaying by remember(overlay.id) { mutableStateOf(false) }

    var imgAspectRatio by remember { mutableFloatStateOf(1f) }
    var isLoaded by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val boxWidthPx = if (isLoaded) {
        val canvasAspect = canvasWidth / canvasHeight
        if (imgAspectRatio > canvasAspect) canvasWidth
        else canvasHeight * imgAspectRatio
    } else 0f
    val boxHeightPx = if (isLoaded) {
        val canvasAspect = canvasWidth / canvasHeight
        if (imgAspectRatio > canvasAspect) canvasWidth / imgAspectRatio
        else canvasHeight
    } else 0f

    LaunchedEffect(overlay.scaleX, overlay.scaleY, overlay.positionX, overlay.positionY) {
        if (System.currentTimeMillis() - lastTransformTime > 500) {
            localScaleX = overlay.scaleX
            localScaleY = overlay.scaleY
            localPosX = overlay.positionX
            localPosY = overlay.positionY
        }
    }

    LaunchedEffect(overlay.naturalWidth, overlay.naturalHeight, isLoaded) {
        if (isLoaded && overlay.naturalWidth > 0 && overlay.scaleX == 1f && overlay.scaleY == 1f) {
            localScaleX = AUTO_FIT_SCALE
            localScaleY = AUTO_FIT_SCALE
            onUpdateOverlay(
                overlay.copy(
                    scaleX = AUTO_FIT_SCALE,
                    scaleY = AUTO_FIT_SCALE
                )
            )
        }
    }

    LaunchedEffect(lastTransformTime) {
        if (lastTransformTime > 0) {
            delay(DEBOUNCE_MS)
            onUpdateOverlay(
                overlay.copy(
                    scaleX = localScaleX,
                    scaleY = localScaleY,
                    positionX = localPosX,
                    positionY = localPosY
                )
            )
            hasPendingTransform = false
        }
    }

    DisposableEffect(overlay.id) {
        onDispose {
            if (hasPendingTransform) {
                onUpdateOverlay(
                    overlay.copy(
                        scaleX = localScaleX,
                        scaleY = localScaleY,
                        positionX = localPosX,
                        positionY = localPosY
                    )
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    translationX = (localPosX - 0.5f) * canvasWidth
                    translationY = (localPosY - 0.5f) * canvasHeight
                    scaleX = localScaleX
                    scaleY = localScaleY
                }
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = if (isSelected) AccentCyan else Color.Transparent
                )
                .pointerInput(overlay.id + "_drag") {
                    detectDragGestures(
                        onDragStart = {
                            wasPlaying = player?.isPlaying == true
                            player?.pause()
                            onSelectOverlay(overlay.id)
                        },
                        onDragEnd = {
                            if (wasPlaying) player?.play()
                            wasPlaying = false
                        },
                        onDragCancel = {
                            if (wasPlaying) player?.play()
                            wasPlaying = false
                        },
                        onDrag = { change, pan ->
                            change.consume()
                            localPosX += pan.x / canvasWidth
                            localPosY += pan.y / canvasHeight
                            lastTransformTime = System.currentTimeMillis()
                            hasPendingTransform = true
                        }
                    )
                }
                .pointerInput(overlay.id + "_tap") {
                    detectTapGestures { onSelectOverlay(overlay.id) }
                }
        ) {
            Box(
                modifier = Modifier
                    .width(
                        if (isLoaded) {
                            val canvasAspect = canvasWidth / canvasHeight
                            if (imgAspectRatio > canvasAspect) with(density) { canvasWidth.toDp() }
                            else with(density) { (canvasHeight * imgAspectRatio).toDp() }
                        } else 100.dp
                    )
                    .height(
                        if (isLoaded) {
                            val canvasAspect = canvasWidth / canvasHeight
                            if (imgAspectRatio > canvasAspect) with(density) { (canvasWidth / imgAspectRatio).toDp() }
                            else with(density) { canvasHeight.toDp() }
                        } else 100.dp
                    )
            ) {
                AsyncImage(
                    model = overlay.imageUri,
                    contentDescription = "Image Overlay",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    onSuccess = { state ->
                        val image = state.result.image
                        val w = image.width.toFloat()
                        val h = image.height.toFloat()
                        if (h > 0) imgAspectRatio = w / h
                        isLoaded = true
                    }
                )
            }
        }

        if (isSelected && isLoaded && boxWidthPx > 0f && boxHeightPx > 0f) {
            OverlayResizeHandle(
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                posX = localPosX,
                posY = localPosY,
                boxWidthPx = boxWidthPx,
                boxHeightPx = boxHeightPx,
                scaleX = localScaleX,
                scaleY = localScaleY,
                onScaleChange = { sx, sy ->
                    localScaleX = clampScale(sx)
                    localScaleY = clampScale(sy)
                    lastTransformTime = System.currentTimeMillis()
                    hasPendingTransform = true
                }
            )
        }
    }
}
