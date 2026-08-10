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
    private val setOverlays: (List<ImageOverlayEntity>) -> Unit = {},
    private val getProjectId: () -> String?,
    private val onOverlaySelected: (String?) -> Unit,
    private val isSelectedOverlay: (String) -> Boolean,
    private val getTextOverlays: () -> List<TextOverlayEntity> = { emptyList() },
    private val setTextOverlays: (List<TextOverlayEntity>) -> Unit = {},
    private val onTextOverlaySelected: (String?) -> Unit = {},
    private val isSelectedTextOverlay: (String) -> Boolean = { false },
    private val onStateUpdated: () -> Unit = {}
) {
    private val zOrderLock = Any()
    
    fun addOverlay(uri: String, currentPlayheadMs: Long, scope: CoroutineScope) {
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
                        startTimeMs = currentPlayheadMs,
                        endTimeMs = currentPlayheadMs + 5000L,
                        zOrder = maxZ + 1,
                        createdAt = System.currentTimeMillis(),
                        naturalWidth = decodeOpts.outWidth,
                        naturalHeight = decodeOpts.outHeight
                    )
                }
                
                withContext(Dispatchers.Main) {
                    setOverlays(getOverlays() + overlay)
                    onStateUpdated()
                }
                overlayRepository.addOverlay(overlay)
            } catch (e: Exception) {
                android.util.Log.e("OverlayManager", "Error adding overlay", e)
            }
        }
    }

    fun updateOverlay(overlay: ImageOverlayEntity, scope: CoroutineScope) {
        val newList = getOverlays().map { if (it.id == overlay.id) overlay else it }
        setOverlays(newList)
        onStateUpdated()
        scope.launch(Dispatchers.IO) {
            overlayRepository.updateOverlay(overlay)
        }
    }

    fun deleteOverlay(overlayId: String, scope: CoroutineScope) {
        val overlay = getOverlays().find { it.id == overlayId }
        val newList = getOverlays().filter { it.id != overlayId }
        setOverlays(newList)
        onStateUpdated()
        if (isSelectedOverlay(overlayId)) {
            onOverlaySelected(null)
        }
        
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
        }
    }

    fun selectOverlay(overlayId: String?) {
        onOverlaySelected(overlayId)
    }

    fun moveOverlayZ(overlayId: String, bringToFront: Boolean, scope: CoroutineScope) {
        val currentList = getOverlays().sortedBy { it.zOrder }.toMutableList()
        val index = currentList.indexOfFirst { it.id == overlayId }
        if (index == -1) return
        
        val overlay = currentList[index]
        if (bringToFront && index < currentList.size - 1) {
            val next = currentList[index + 1]
            val currentZ = overlay.zOrder
            currentList[index] = overlay.copy(zOrder = next.zOrder)
            currentList[index + 1] = next.copy(zOrder = currentZ)
            setOverlays(currentList)
            onStateUpdated()
            scope.launch(Dispatchers.IO) {
                overlayRepository.updateOverlay(currentList[index])
                overlayRepository.updateOverlay(currentList[index + 1])
            }
        } else if (!bringToFront && index > 0) {
            val prev = currentList[index - 1]
            val currentZ = overlay.zOrder
            currentList[index] = overlay.copy(zOrder = prev.zOrder)
            currentList[index - 1] = prev.copy(zOrder = currentZ)
            setOverlays(currentList)
            onStateUpdated()
            scope.launch(Dispatchers.IO) {
                overlayRepository.updateOverlay(currentList[index])
                overlayRepository.updateOverlay(currentList[index - 1])
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
            
            withContext(Dispatchers.Main) {
                setOverlays(getOverlays() + duplicate)
                onOverlaySelected(duplicate.id)
                onStateUpdated() // Auto select the new duplicate
            }
            overlayRepository.addOverlay(duplicate)
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
                
                withContext(Dispatchers.Main) {
                    setTextOverlays(getTextOverlays() + overlay)
                    onTextOverlaySelected(overlay.id)
                    onStateUpdated()
                }
                overlayRepository.addTextOverlay(overlay)
            } catch (e: Exception) {
                android.util.Log.e("OverlayManager", "Error adding text overlay", e)
            }
        }
    }

    fun updateTextOverlay(overlay: TextOverlayEntity, scope: CoroutineScope) {
        val newList = getTextOverlays().map { if (it.id == overlay.id) overlay else it }
        setTextOverlays(newList)
        onStateUpdated()
        scope.launch(Dispatchers.IO) {
            overlayRepository.updateTextOverlay(overlay)
        }
    }

    fun deleteTextOverlay(overlayId: String, scope: CoroutineScope) {
        val projectId = getProjectId() ?: return
        val newList = getTextOverlays().filter { it.id != overlayId }
        setTextOverlays(newList)
        onStateUpdated()
        if (isSelectedTextOverlay(overlayId)) {
            onTextOverlaySelected(null)
        }
        
        scope.launch(Dispatchers.IO) {
            overlayRepository.deleteTextOverlay(overlayId, projectId)
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
            
            withContext(Dispatchers.Main) {
                setTextOverlays(getTextOverlays() + duplicate)
                onTextOverlaySelected(duplicate.id)
                onStateUpdated()
            }
            overlayRepository.addTextOverlay(duplicate)
        }
    }

    fun moveTextOverlayZ(overlayId: String, bringToFront: Boolean, scope: CoroutineScope) {
        val currentList = getTextOverlays().sortedBy { it.zOrder }.toMutableList()
        val index = currentList.indexOfFirst { it.id == overlayId }
        if (index == -1) return
        
        val overlay = currentList[index]
        if (bringToFront && index < currentList.size - 1) {
            val next = currentList[index + 1]
            val currentZ = overlay.zOrder
            currentList[index] = overlay.copy(zOrder = next.zOrder)
            currentList[index + 1] = next.copy(zOrder = currentZ)
            setTextOverlays(currentList)
            onStateUpdated()
            scope.launch(Dispatchers.IO) {
                overlayRepository.updateTextOverlay(currentList[index])
                overlayRepository.updateTextOverlay(currentList[index + 1])
            }
        } else if (!bringToFront && index > 0) {
            val prev = currentList[index - 1]
            val currentZ = overlay.zOrder
            currentList[index] = overlay.copy(zOrder = prev.zOrder)
            currentList[index - 1] = prev.copy(zOrder = currentZ)
            setTextOverlays(currentList)
            onStateUpdated()
            scope.launch(Dispatchers.IO) {
                overlayRepository.updateTextOverlay(currentList[index])
                overlayRepository.updateTextOverlay(currentList[index - 1])
            }
        }
    }
}
