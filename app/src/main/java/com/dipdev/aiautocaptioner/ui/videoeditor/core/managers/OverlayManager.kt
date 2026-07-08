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
                
                val maxZ = getOverlays().maxOfOrNull { it.zOrder } ?: -1
                val overlay = ImageOverlayEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    imageUri = destFile.absolutePath,
                    zOrder = maxZ + 1,
                    createdAt = System.currentTimeMillis()
                )
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
}
