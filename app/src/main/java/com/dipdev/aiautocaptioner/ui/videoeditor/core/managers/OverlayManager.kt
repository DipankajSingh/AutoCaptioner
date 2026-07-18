package com.dipdev.aiautocaptioner.ui.videoeditor.core.managers

import android.content.Context
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import com.dipdev.aiautocaptioner.data.repository.OverlayRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class OverlayManager(
    private val context: Context,
    private val overlayRepository: OverlayRepository,
    private val getOverlays: () -> List<ImageOverlayEntity>,
    private val getProjectId: () -> String?,
    private val onOverlaySelected: (String?) -> Unit,
    private val isSelectedOverlay: (String) -> Boolean
) {
    private val zOrderLock = Any()
    fun addOverlay(uri: String, scope: CoroutineScope) {
        val projectId = getProjectId() ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val overlayDir = File(context.filesDir, "projects/$projectId/overlays")
                if (!overlayDir.exists()) overlayDir.mkdirs()
                
                val destFile = File(overlayDir, "${UUID.randomUUID()}.jpg")
                val inputStream = context.contentResolver.openInputStream(android.net.Uri.parse(uri))
                val outputStream = FileOutputStream(destFile)
                inputStream?.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                
                val overlay = synchronized(zOrderLock) {
                    val maxZ = getOverlays().maxOfOrNull { it.zOrder } ?: -1
                    ImageOverlayEntity(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        imageUri = destFile.absolutePath,
                        startTimeMs = 0L,
                        endTimeMs = 5000L,
                        zOrder = maxZ + 1,
                        createdAt = System.currentTimeMillis()
                    )
                }
                overlayRepository.addOverlay(overlay)
            } catch (e: Exception) {
                e.printStackTrace()
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
                    e.printStackTrace()
                }
            }
            
            if (isSelectedOverlay(overlayId)) {
                onOverlaySelected(null)
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
                    e.printStackTrace()
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
            onOverlaySelected(duplicate.id) // Auto select the new duplicate
        }
    }
}
