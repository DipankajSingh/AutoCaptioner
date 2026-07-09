package com.dipdev.aiautocaptioner.ui.videoeditor.overlay

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
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
        
        overlays.filter { currentTimelineMs() in it.startTimeMs..it.endTimeMs }.forEach { overlay ->
            val isSelected = overlay.id == selectedOverlayId
            
            var localScale by remember(overlay.id) { mutableFloatStateOf(overlay.scaleX) }
            var localPosX by remember(overlay.id) { mutableFloatStateOf(overlay.positionX) }
            var localPosY by remember(overlay.id) { mutableFloatStateOf(overlay.positionY) }
            var lastTransformTime by remember(overlay.id) { mutableLongStateOf(0L) }
            
            LaunchedEffect(overlay.scaleX, overlay.positionX, overlay.positionY) {
                if (System.currentTimeMillis() - lastTransformTime > 500) {
                    localScale = overlay.scaleX
                    localPosX = overlay.positionX
                    localPosY = overlay.positionY
                }
            }
            
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
                    modifier = Modifier.widthIn(max = 200.dp).heightIn(max = 200.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            }
        }
    }
}
