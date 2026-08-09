package com.dipdev.aiautocaptioner.ui.videoeditor.core.managers

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.net.toUri
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import com.dipdev.aiautocaptioner.data.db.entity.TextOverlayEntity
import com.dipdev.aiautocaptioner.data.repository.OverlayRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class OverlayManager(
    private val context: Context,
    private val overlayRepository: OverlayRepository,
    private val getOverlays: () -> List<ImageOverlayEntity>,
    private val getProjectId: () -> String?,
    private val onOverlaySelected: (String?) -> Unit,
    private val isSelectedOverlay: (String) -> Boolean,
    private val getTextOverlays: () -> List<TextOverlayEntity> = { emptyList() },
    private val onTextOverlaySelected: (String?) -> Unit = {},
    private val isSelectedTextOverlay: (String) -> Boolean = { false }
) {
    private val zOrderLock = Any()
    fun addOverlay(uri: String, scope: CoroutineScope) {
        val projectId = getProjectId() ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val overlayDir = File(context.filesDir, "projects/$projectId/overlays")
                if (!overlayDir.exists()) overlayDir.mkdirs()
                
                val destFile = File(overlayDir, "${UUID.randomUUID()}.jpg")
                val inputStream = context.contentResolver.openInputStream(uri.toUri())
                val outputStream = FileOutputStream(destFile)
                inputStream?.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }

                val decodeOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(destFile.absolutePath, decodeOpts)

                val overlay = synchronized(zOrderLock) {
                    val maxZ = getOverlays().maxOfOrNull { it.zOrder } ?: -1
                    ImageOverlayEntity(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        imageUri = destFile.absolutePath,
                        startTimeMs = 0L,
                        endTimeMs = 5000L,
                        zOrder = maxZ + 1,
                        createdAt = System.currentTimeMillis(),
                        naturalWidth = decodeOpts.outWidth,
                        naturalHeight = decodeOpts.outHeight
                    )
                }
                overlayRepository.addOverlay(overlay)
            } catch (e: Exception) {
                android.util.Log.e("OverlayManager", "Error adding overlay", e)
            }
        }
    }

    fun updateOverlay(overlay: ImageOverlayEntity, scope: CoroutineScope) {
        scope.launch {
            overlayRepository.updateOverlay(overlay)
        }
    }

    fun deleteOverlay(overlayId: String, scope: CoroutineScope) {
        val overlay = getOverlays().find { it.id == overlayId }
        scope.launch(Dispatchers.IO) {
            overlayRepository.deleteOverlay(overlayId)
            
            if (overlay != null && !overlay.imageUri.startsWith("content://")) {
                try {
                    val file = File(overlay.imageUri)
                    if (file.exists()) file.delete()
                } catch (e: Exception) {
                    android.util.Log.e("OverlayManager", "Error deleting file", e)
                }
            }
            
            if (isSelectedOverlay(overlayId)) {
                withContext(Dispatchers.Main) {
                    onOverlaySelected(null)
                }
            }
        }
    }

    fun selectOverlay(overlayId: String?) {
        onOverlaySelected(overlayId)
    }

    fun moveOverlayZ(overlayId: String, bringToFront: Boolean, scope: CoroutineScope) {
        scope.launch {
            val currentList = getOverlays().sortedBy { it.zOrder }
            val index = currentList.indexOfFirst { it.id == overlayId }
            if (index == -1) return@launch
            
            val overlay = currentList[index]
            if (bringToFront && index < currentList.size - 1) {
                val next = currentList[index + 1]
                val currentZ = overlay.zOrder
                overlayRepository.updateOverlay(overlay.copy(zOrder = next.zOrder))
                overlayRepository.updateOverlay(next.copy(zOrder = currentZ))
            } else if (!bringToFront && index > 0) {
                val prev = currentList[index - 1]
                val currentZ = overlay.zOrder
                overlayRepository.updateOverlay(overlay.copy(zOrder = prev.zOrder))
                overlayRepository.updateOverlay(prev.copy(zOrder = currentZ))
            }
        }
    }

    fun duplicateOverlay(overlayId: String, scope: CoroutineScope) {
        val overlay = getOverlays().find { it.id == overlayId } ?: return
        val projectId = getProjectId() ?: return
        scope.launch(Dispatchers.IO) {
            var newImageUri = overlay.imageUri
            if (!overlay.imageUri.startsWith("content://")) {
                try {
                    val originalFile = File(overlay.imageUri)
                    if (originalFile.exists()) {
                        val overlayDir = File(context.filesDir, "projects/$projectId/overlays")
                        if (!overlayDir.exists()) overlayDir.mkdirs()
                        
                        val destFile = File(overlayDir, "${UUID.randomUUID()}.jpg")
                        originalFile.copyTo(destFile)
                        newImageUri = destFile.absolutePath
                    }
                } catch (e: Exception) {
                    android.util.Log.e("OverlayManager", "Error duplicating overlay", e)
                }
            }
            
            val duplicate = synchronized(zOrderLock) {
                val maxZ = getOverlays().maxOfOrNull { it.zOrder } ?: -1
                overlay.copy(
                    id = UUID.randomUUID().toString(),
                    imageUri = newImageUri,
                    zOrder = maxZ + 1,
                    // offset position slightly so it's visible as a copy
                    positionX = overlay.positionX + 0.05f,
                    positionY = overlay.positionY + 0.05f,
                    createdAt = System.currentTimeMillis()
                )
            }
            overlayRepository.addOverlay(duplicate)
            withContext(Dispatchers.Main) {
                onOverlaySelected(duplicate.id) // Auto select the new duplicate
            }
        }
    }

    // --- Text Overlays ---
    fun addTextOverlay(
        text: String,
        fontAssetPath: String,
        textColorArgb: Int,
        backgroundColorArgb: Int,
        backgroundOpacity: Float,
        textAlignment: String,
        fontSize: Float,
        currentPlayheadMs: Long,
        scope: CoroutineScope
    ) {
        val projectId = getProjectId() ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val overlay = synchronized(zOrderLock) {
                    val maxImageZ = getOverlays().maxOfOrNull { it.zOrder } ?: -1
                    val maxTextZ = getTextOverlays().maxOfOrNull { it.zOrder } ?: -1
                    val maxZ = maxOf(maxImageZ, maxTextZ)
                    
                    TextOverlayEntity(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        text = text,
                        fontAssetPath = fontAssetPath,
                        textColorArgb = textColorArgb,
                        backgroundColorArgb = backgroundColorArgb,
                        backgroundOpacity = backgroundOpacity,
                        textAlignment = textAlignment,
                        fontSize = fontSize,
                        positionX = 0.5f,
                        positionY = 0.5f,
                        scaleX = 1f,
                        scaleY = 1f,
                        rotation = 0f,
                        startTimeMs = currentPlayheadMs,
                        endTimeMs = currentPlayheadMs + 5000L,
                        zOrder = maxZ + 1,
                        createdAt = System.currentTimeMillis()
                    )
                }
                overlayRepository.addTextOverlay(overlay)
                withContext(Dispatchers.Main) {
                    onTextOverlaySelected(overlay.id)
                }
            } catch (e: Exception) {
                android.util.Log.e("OverlayManager", "Error adding text overlay", e)
            }
        }
    }

    fun updateTextOverlay(overlay: TextOverlayEntity, scope: CoroutineScope) {
        scope.launch {
            overlayRepository.updateTextOverlay(overlay)
        }
    }

    fun deleteTextOverlay(overlayId: String, scope: CoroutineScope) {
        val projectId = getProjectId() ?: return
        scope.launch(Dispatchers.IO) {
            overlayRepository.deleteTextOverlay(overlayId, projectId)
            if (isSelectedTextOverlay(overlayId)) {
                withContext(Dispatchers.Main) {
                    onTextOverlaySelected(null)
                }
            }
        }
    }

    fun selectTextOverlay(overlayId: String?) {
        onTextOverlaySelected(overlayId)
    }

    fun duplicateTextOverlay(overlayId: String, scope: CoroutineScope) {
        val overlay = getTextOverlays().find { it.id == overlayId } ?: return
        scope.launch(Dispatchers.IO) {
            val duplicate = synchronized(zOrderLock) {
                val maxImageZ = getOverlays().maxOfOrNull { it.zOrder } ?: -1
                val maxTextZ = getTextOverlays().maxOfOrNull { it.zOrder } ?: -1
                val maxZ = maxOf(maxImageZ, maxTextZ)
                
                overlay.copy(
                    id = UUID.randomUUID().toString(),
                    zOrder = maxZ + 1,
                    positionX = overlay.positionX + 0.05f,
                    positionY = overlay.positionY + 0.05f,
                    createdAt = System.currentTimeMillis()
                )
            }
            overlayRepository.addTextOverlay(duplicate)
            withContext(Dispatchers.Main) {
                onTextOverlaySelected(duplicate.id)
            }
        }
    }

    fun moveTextOverlayZ(overlayId: String, bringToFront: Boolean, scope: CoroutineScope) {
        scope.launch {
            val currentList = getTextOverlays().sortedBy { it.zOrder }
            val index = currentList.indexOfFirst { it.id == overlayId }
            if (index == -1) return@launch
            
            val overlay = currentList[index]
            if (bringToFront && index < currentList.size - 1) {
                val next = currentList[index + 1]
                val currentZ = overlay.zOrder
                overlayRepository.updateTextOverlay(overlay.copy(zOrder = next.zOrder))
                overlayRepository.updateTextOverlay(next.copy(zOrder = currentZ))
            } else if (!bringToFront && index > 0) {
                val prev = currentList[index - 1]
                val currentZ = overlay.zOrder
                overlayRepository.updateTextOverlay(overlay.copy(zOrder = prev.zOrder))
                overlayRepository.updateTextOverlay(prev.copy(zOrder = currentZ))
            }
        }
    }
}
