package com.dipdev.aiautocaptioner.ui.videoeditor.overlay

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import androidx.compose.ui.platform.LocalContext
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import com.dipdev.aiautocaptioner.data.db.entity.TextOverlayEntity
import com.dipdev.aiautocaptioner.ui.theme.AccentRose
import com.dipdev.aiautocaptioner.ui.videoeditor.text.DEFAULT_TEXT_WIDTH_FRACTION
import com.dipdev.aiautocaptioner.ui.videoeditor.text.TextOverlayContent
import androidx.compose.foundation.gestures.detectTransformGestures
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
    textOverlays: List<TextOverlayEntity> = emptyList(),
    currentTimelineMs: () -> Long,
    selectedOverlayId: String?,
    onUpdateOverlay: (ImageOverlayEntity) -> Unit,
    onSelectOverlay: (String?) -> Unit,
    onUpdateTextOverlay: (TextOverlayEntity) -> Unit = {},
    editingTextOverlayId: String? = null,
    onStopEditingTextOverlay: () -> Unit = {},
    onStartEditingTextOverlay: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    videoWidth: Int = 0,
    videoHeight: Int = 0,
    player: Player? = null,
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
                .pointerInput(editingTextOverlayId) {
                    detectTapGestures {
                        if (editingTextOverlayId != null) {
                            // Tap outside while editing = commit (don't toggle playback)
                            onStopEditingTextOverlay()
                        } else {
                            onSelectOverlay(null)
                            player?.let { p ->
                                if (p.isPlaying) p.pause() else p.play()
                            }
                        }
                    }
                }
        ) {
            val allOverlays = remember(overlays, textOverlays) {
                (overlays.map { Pair(it.zOrder, it) } + textOverlays.map { Pair(it.zOrder, it) })
                    .sortedBy { it.first }
                    .map { it.second }
            }

            allOverlays.forEach { overlayItem ->
                val overlayId = if (overlayItem is ImageOverlayEntity) overlayItem.id else (overlayItem as TextOverlayEntity).id
                key(overlayId) {
                    when (overlayItem) {
                        is ImageOverlayEntity -> {
                            OverlayItem(
                                overlay = overlayItem,
                                canvasWidth = canvasWidth,
                                canvasHeight = canvasHeight,
                                isSelected = overlayItem.id == selectedOverlayId,
                                currentTimelineMs = currentTimelineMs,
                                onUpdateOverlay = onUpdateOverlay,
                                onSelectOverlay = onSelectOverlay,
                                player = player
                            )
                        }
                        is TextOverlayEntity -> {
                            TextOverlayItem(
                                overlay = overlayItem,
                                canvasWidth = canvasWidth,
                                canvasHeight = canvasHeight,
                                isSelected = selectedOverlayId == overlayItem.id,
                                isEditing = editingTextOverlayId == overlayItem.id,
                                onStopEditing = onStopEditingTextOverlay,
                                onStartEditing = { onStartEditingTextOverlay(overlayItem.id) },
                                currentTimelineMs = currentTimelineMs,
                                onUpdateOverlay = onUpdateTextOverlay,
                                onSelectOverlay = onSelectOverlay,
                                player = player
                            )
                        }
                    }
                }
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
    onSelectOverlay: (String?) -> Unit,
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
                .pointerInput(overlay.id + "_drag") {
                    detectDragGestures(
                        onDragStart = {
                            player?.pause()
                            onSelectOverlay(overlay.id)
                        },
                        onDragEnd = {},
                        onDragCancel = {},
                        onDrag = { change, pan ->
                            change.consume()
                            localPosX += (pan.x * localScaleX) / canvasWidth
                            localPosY += (pan.y * localScaleY) / canvasHeight
                            lastTransformTime = System.currentTimeMillis()
                            hasPendingTransform = true
                        }
                    )
                }
                .pointerInput(overlay.id + "_tap") {
                    detectTapGestures { 
                        player?.pause()
                        onSelectOverlay(overlay.id) 
                    }
                }
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = if (isSelected) AccentRose else Color.Transparent
                )
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
                val context = LocalContext.current
                val colorFilter = remember(overlay.filterName) {
                    when (overlay.filterName) {
                        "Grayscale" -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) })
                        "Sepia" -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                            0.393f, 0.769f, 0.189f, 0f, 0f,
                            0.349f, 0.686f, 0.168f, 0f, 0f,
                            0.272f, 0.534f, 0.131f, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f
                        )))
                        "Invert" -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                            -1f, 0f, 0f, 0f, 255f,
                            0f, -1f, 0f, 0f, 255f,
                            0f, 0f, -1f, 0f, 255f,
                            0f, 0f, 0f, 1f, 0f
                        )))
                        else -> null
                    }
                }
                
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(overlay.imageUri)
                        .size(Size.ORIGINAL)
                        .build(),
                    contentDescription = stringResource(R.string.side_image_overlay),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = if (overlay.isFlippedX) -1f else 1f
                        },
                    contentScale = ContentScale.Fit,
                    alpha = overlay.opacity,
                    colorFilter = colorFilter,
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

@Composable
private fun BoxScope.TextOverlayItem(
    overlay: TextOverlayEntity,
    canvasWidth: Float,
    canvasHeight: Float,
    isSelected: Boolean,
    isEditing: Boolean,
    onStopEditing: () -> Unit,
    onStartEditing: () -> Unit,
    currentTimelineMs: () -> Long,
    onUpdateOverlay: (TextOverlayEntity) -> Unit,
    onSelectOverlay: (String?) -> Unit,
    player: Player?
) {
    val isVisible = currentTimelineMs() in overlay.startTimeMs..overlay.endTimeMs
    if (!isVisible) return

    var localScaleX by remember(overlay.id) { mutableFloatStateOf(overlay.scaleX) }
    var localScaleY by remember(overlay.id) { mutableFloatStateOf(overlay.scaleY) }
    var localPosX by remember(overlay.id) { mutableFloatStateOf(overlay.positionX) }
    var localPosY by remember(overlay.id) { mutableFloatStateOf(overlay.positionY) }
    var localRotation by remember(overlay.id) { mutableFloatStateOf(overlay.rotation) }
    var localTextWidth by remember(overlay.id) { mutableFloatStateOf(overlay.textWidth ?: DEFAULT_TEXT_WIDTH_FRACTION) }
    var lastTransformTime by remember(overlay.id) { mutableLongStateOf(0L) }
    var hasPendingTransform by remember(overlay.id) { mutableStateOf(false) }
    
    var boxWidthPx by remember { mutableFloatStateOf(0f) }
    var boxHeightPx by remember { mutableFloatStateOf(0f) }

    // The resize handle deliberately updates localTextWidth immediately and
    // persists it after a short debounce. Render from that same local value so
    // the box tracks the user's finger rather than jumping every debounce.
    val renderedOverlay = overlay.copy(textWidth = localTextWidth)

    LaunchedEffect(overlay.scaleX, overlay.scaleY, overlay.positionX, overlay.positionY, overlay.rotation, overlay.textWidth) {
        if (System.currentTimeMillis() - lastTransformTime > 500) {
            localScaleX = overlay.scaleX
            localScaleY = overlay.scaleY
            localPosX = overlay.positionX
            localPosY = overlay.positionY
            localRotation = overlay.rotation
            localTextWidth = overlay.textWidth ?: DEFAULT_TEXT_WIDTH_FRACTION
        }
    }

    LaunchedEffect(lastTransformTime) {
        if (lastTransformTime > 0) {
            kotlinx.coroutines.delay(DEBOUNCE_MS)
            onUpdateOverlay(
                overlay.copy(
                    scaleX = localScaleX,
                    scaleY = localScaleY,
                    positionX = localPosX,
                    positionY = localPosY,
                    rotation = localRotation,
                    textWidth = localTextWidth
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
                        positionY = localPosY,
                        rotation = localRotation,
                        textWidth = localTextWidth
                    )
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // While editing, the text field needs clean touches (cursor placement,
        // selection, IME), so move/transform/tap gestures are disabled.
        val interactionModifier = if (isEditing) {
            Modifier
        } else {
            Modifier
                .pointerInput(overlay.id + "_tap") {
                    detectTapGestures {
                        player?.pause()
                        onSelectOverlay(overlay.id)
                        onStartEditing()
                    }
                }
                .pointerInput(overlay.id + "_transform") {
                    detectTransformGestures { _, pan, zoom, rotation ->
                        val angleRad = localRotation * Math.PI / 180.0
                        val parentPanX = (pan.x * localScaleX) * Math.cos(angleRad) - (pan.y * localScaleY) * Math.sin(angleRad)
                        val parentPanY = (pan.x * localScaleX) * Math.sin(angleRad) + (pan.y * localScaleY) * Math.cos(angleRad)

                        localPosX += parentPanX.toFloat() / canvasWidth
                        localPosY += parentPanY.toFloat() / canvasHeight
                        localScaleX = clampScale(localScaleX * zoom)
                        localScaleY = clampScale(localScaleY * zoom)
                        localRotation += rotation
                        lastTransformTime = System.currentTimeMillis()
                        hasPendingTransform = true
                    }
                }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .onSizeChanged { size ->
                    boxWidthPx = size.width.toFloat()
                    boxHeightPx = size.height.toFloat()
                }
                .graphicsLayer {
                    translationX = (localPosX - 0.5f) * canvasWidth
                    translationY = (localPosY - 0.5f) * canvasHeight
                    scaleX = localScaleX
                    scaleY = localScaleY
                    rotationZ = localRotation
                }
                .then(interactionModifier)
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = if (isSelected) AccentRose else Color.Transparent
                )
        ) {
            if (isEditing) {
                com.dipdev.aiautocaptioner.ui.videoeditor.text.InlineTextEditor(
                    overlay = overlay,
                    containerSize = androidx.compose.ui.unit.IntSize(canvasWidth.toInt(), canvasHeight.toInt()),
                    onTextChange = {
                        onUpdateOverlay(overlay.copy(text = it))
                    },
                    onFontChange = { fontAssetPath ->
                        onUpdateOverlay(overlay.copy(fontAssetPath = fontAssetPath))
                    }
                )
            } else {
                TextOverlayContent(
                    overlay = renderedOverlay,
                    canvasWidth = canvasWidth
                )
            }
        }
        
        if (isSelected && !isEditing && boxWidthPx > 0f && boxHeightPx > 0f) {
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
                },
                textWidthFraction = localTextWidth,
                onTextWidthChange = { newFraction ->
                    localTextWidth = newFraction
                    lastTransformTime = System.currentTimeMillis()
                    hasPendingTransform = true
                }
            )
        }
    }
}
