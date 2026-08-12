package com.dipdev.aiautocaptioner.ui.videoeditor.image.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MIN_CROP_FRACTION = 0.12f

/** A non-destructive crop editor: applying creates a new overlay asset. */
@Composable
fun ImageCropOverlay(
    overlay: ImageOverlayEntity,
    onApply: (ImageOverlayEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var bitmap by remember(overlay.imageUri) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember(overlay.imageUri) { mutableStateOf(false) }
    var isApplying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(overlay.imageUri) {
        bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(overlay.imageUri) }
        loadFailed = bitmap == null
    }

    Dialog(
        onDismissRequest = { if (!isApplying) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF101114))
        ) {
            if (bitmap == null) {
                if (loadFailed) {
                    Text("Could not load this image", color = Color.White, modifier = Modifier.align(Alignment.Center))
                } else {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            } else {
                CropWorkspace(bitmap = bitmap!!, onApply = { cropRect ->
                    isApplying = true
                    scope.launch {
                        val croppedOverlay = withContext(Dispatchers.IO) { cropOverlayAsset(overlay, cropRect) }
                        if (croppedOverlay != null) onApply(croppedOverlay)
                        isApplying = false
                        if (croppedOverlay != null) onDismiss()
                    }
                })
            }

            Row(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Close, "Cancel crop", tint = Color.White,
                    modifier = Modifier.size(32.dp).clickable(enabled = !isApplying) { onDismiss() })
                Spacer(Modifier.width(16.dp))
                Text("Crop image", color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (isApplying) CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun CropWorkspace(bitmap: Bitmap, onApply: (RectF) -> Unit) {
    var left by remember { mutableStateOf(0.1f) }
    var top by remember { mutableStateOf(0.1f) }
    var right by remember { mutableStateOf(0.9f) }
    var bottom by remember { mutableStateOf(0.9f) }
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(top = 76.dp, bottom = 84.dp, start = 16.dp, end = 16.dp)) {
        val maxWidthPx = constraints.maxWidth.toFloat()
        val maxHeightPx = constraints.maxHeight.toFloat()
        val bitmapAspect = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
        val displayWidthPx = min(maxWidthPx, maxHeightPx * bitmapAspect)
        val displayHeightPx = displayWidthPx / bitmapAspect
        val displayWidth = with(density) { displayWidthPx.toDp() }
        val displayHeight = with(density) { displayHeightPx.toDp() }

        Box(
            modifier = Modifier.align(Alignment.Center).size(displayWidth, displayHeight)
        ) {
            Image(bitmap.asImageBitmap(), "Image to crop", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)

            // Dim the pixels that will be discarded, leaving the crop area clear.
            val shade = Color.Black.copy(alpha = 0.58f)
            Box(Modifier.fillMaxWidth().height(with(density) { (top * displayHeightPx).toDp() }).background(shade))
            Box(Modifier.fillMaxWidth().height(with(density) { ((1f - bottom) * displayHeightPx).toDp() })
                .align(Alignment.BottomCenter).background(shade))
            Box(Modifier.width(with(density) { (left * displayWidthPx).toDp() })
                .height(with(density) { ((bottom - top) * displayHeightPx).toDp() })
                .offset { IntOffset(0, (top * displayHeightPx).roundToInt()) }.background(shade))
            Box(Modifier.width(with(density) { ((1f - right) * displayWidthPx).toDp() })
                .height(with(density) { ((bottom - top) * displayHeightPx).toDp() })
                .align(Alignment.TopEnd).offset { IntOffset(0, (top * displayHeightPx).roundToInt()) }.background(shade))

            val cropWidthPx = (right - left) * displayWidthPx
            val cropHeightPx = (bottom - top) * displayHeightPx
            Box(
                Modifier
                    .offset { IntOffset((left * displayWidthPx).roundToInt(), (top * displayHeightPx).roundToInt()) }
                    .size(with(density) { cropWidthPx.toDp() }, with(density) { cropHeightPx.toDp() })
                    .border(2.dp, Color.White)
                    .pointerInput(displayWidthPx, displayHeightPx) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val dx = drag.x / displayWidthPx
                            val dy = drag.y / displayHeightPx
                            val width = right - left
                            val height = bottom - top
                            left = (left + dx).coerceIn(0f, 1f - width)
                            right = left + width
                            top = (top + dy).coerceIn(0f, 1f - height)
                            bottom = top + height
                        }
                    }
            )

            CropHandle(left, top, displayWidthPx, displayHeightPx) { dx, dy ->
                left = (left + dx).coerceIn(0f, right - MIN_CROP_FRACTION)
                top = (top + dy).coerceIn(0f, bottom - MIN_CROP_FRACTION)
            }
            CropHandle(right, bottom, displayWidthPx, displayHeightPx) { dx, dy ->
                right = (right + dx).coerceIn(left + MIN_CROP_FRACTION, 1f)
                bottom = (bottom + dy).coerceIn(top + MIN_CROP_FRACTION, 1f)
            }
        }

        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
            Text("Drag the frame or its corners", color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(12.dp))
            Icon(Icons.Rounded.Check, "Apply crop", tint = Color.White,
                modifier = Modifier.size(42.dp).clickable { onApply(RectF(left, top, right, bottom)) })
        }
    }
}

@Composable
private fun CropHandle(
    x: Float, y: Float, widthPx: Float, heightPx: Float, onDrag: (Float, Float) -> Unit
) {
    val density = LocalDensity.current
    Box(
        Modifier.offset { IntOffset((x * widthPx - 14.dp.toPx(density)).roundToInt(), (y * heightPx - 14.dp.toPx(density)).roundToInt()) }
            .size(28.dp).background(Color.White)
            .pointerInput(widthPx, heightPx) {
                detectDragGestures { change, drag ->
                    change.consume()
                    onDrag(drag.x / widthPx, drag.y / heightPx)
                }
            }
    )
}

private fun androidx.compose.ui.unit.Dp.toPx(density: androidx.compose.ui.unit.Density): Float = with(density) { toPx() }

private fun cropOverlayAsset(overlay: ImageOverlayEntity, normalizedCrop: RectF): ImageOverlayEntity? {
    val source = BitmapFactory.decodeFile(overlay.imageUri) ?: return null
    val left = (normalizedCrop.left * source.width).roundToInt().coerceIn(0, source.width - 1)
    val top = (normalizedCrop.top * source.height).roundToInt().coerceIn(0, source.height - 1)
    val width = max(1, ((normalizedCrop.right - normalizedCrop.left) * source.width).roundToInt())
        .coerceAtMost(source.width - left)
    val height = max(1, ((normalizedCrop.bottom - normalizedCrop.top) * source.height).roundToInt())
        .coerceAtMost(source.height - top)
    val cropped = Bitmap.createBitmap(source, left, top, width, height)
    val destination = File(File(overlay.imageUri).parentFile, "${UUID.randomUUID()}_crop.jpg")
    FileOutputStream(destination).use { cropped.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    if (cropped !== source) cropped.recycle()
    source.recycle()
    return overlay.copy(imageUri = destination.absolutePath, naturalWidth = width, naturalHeight = height)
}
