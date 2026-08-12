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
        val images = getOverlays().map { Pair(it.id, it.zOrder) }
        val texts = getTextOverlays().map { Pair(it.id, it.zOrder) }
        val all = (images + texts).sortedBy { it.second }.toMutableList()

        val index = all.indexOfFirst { it.first == overlayId }
        if (index == -1) return
        
        var swapWithIndex = -1
        if (bringToFront && index < all.size - 1) {
            swapWithIndex = index + 1
        } else if (!bringToFront && index > 0) {
            swapWithIndex = index - 1
        }
        
        if (swapWithIndex != -1) {
            val current = all[index]
            val swapWith = all[swapWithIndex]
            
            val newCurrentZ = swapWith.second
            val newSwapWithZ = current.second
            
            var newImages = getOverlays()
            var newTexts = getTextOverlays()
            
            val updateEntity = { id: String, newZ: Int ->
                if (newImages.any { it.id == id }) {
                    newImages = newImages.map { if (it.id == id) it.copy(zOrder = newZ) else it }
                    scope.launch(Dispatchers.IO) {
                        newImages.find { it.id == id }?.let { overlayRepository.updateOverlay(it) }
                    }
                } else if (newTexts.any { it.id == id }) {
                    newTexts = newTexts.map { if (it.id == id) it.copy(zOrder = newZ) else it }
                    scope.launch(Dispatchers.IO) {
                        newTexts.find { it.id == id }?.let { overlayRepository.updateTextOverlay(it) }
                    }
                }
            }
            
            updateEntity(current.first, newCurrentZ)
            updateEntity(swapWith.first, newSwapWithZ)
            
            setOverlays(newImages)
            setTextOverlays(newTexts)
            onStateUpdated()
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

    /**
     * Adds a text overlay to the UI state only. No DB write and no history
     * entry — used for the instant draft box shown when the user taps T.
     * The overlay is persisted on commit (see [commitTextOverlay]).
     */
    fun addTextOverlayDraft(overlay: TextOverlayEntity) {
        setTextOverlays(getTextOverlays() + overlay)
    }

    /**
     * Replaces a text overlay in the UI state only (draft edits while the
     * overlay is being edited). No DB write, no history entry.
     */
    fun updateTextOverlayDraft(overlay: TextOverlayEntity) {
        val currentList = getTextOverlays()
        setTextOverlays(currentList.map { if (it.id == overlay.id) overlay else it })
    }

    /**
     * Persists the final state of a text overlay: records one history step and
     * writes to the DB (upsert). Called once when an edit session ends.
     */
    fun commitTextOverlay(overlay: TextOverlayEntity, scope: CoroutineScope) {
        val currentList = getTextOverlays()
        setTextOverlays(
            if (currentList.any { it.id == overlay.id }) {
                currentList.map { if (it.id == overlay.id) overlay else it }
            } else {
                currentList + overlay
            }
        )
        onStateUpdated()
        scope.launch(Dispatchers.IO) {
            overlayRepository.addTextOverlay(overlay)
        }
    }

    fun updateTextOverlay(overlay: TextOverlayEntity, scope: CoroutineScope) {
        val currentList = getTextOverlays()
        val exists = currentList.any { it.id == overlay.id }
        
        if (exists) {
            val newList = currentList.map { if (it.id == overlay.id) overlay else it }
            setTextOverlays(newList)
            onStateUpdated()
            scope.launch(Dispatchers.IO) {
                overlayRepository.updateTextOverlay(overlay)
            }
        } else {
            setTextOverlays(currentList + overlay)
            onStateUpdated()
            scope.launch(Dispatchers.IO) {
                overlayRepository.addTextOverlay(overlay)
            }
        }
    }

    fun deleteTextOverlay(overlayId: String, scope: CoroutineScope, recordHistory: Boolean = true) {
        val projectId = getProjectId() ?: return
        val newList = getTextOverlays().filter { it.id != overlayId }
        setTextOverlays(newList)
        if (recordHistory) {
            onStateUpdated()
        }
        if (isSelectedOverlay(overlayId)) {
            onOverlaySelected(null)
        }
        
        scope.launch(Dispatchers.IO) {
            overlayRepository.deleteTextOverlay(overlayId, projectId)
        }
    }

    fun selectTextOverlay(overlayId: String?) {
        onOverlaySelected(overlayId)
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
                onOverlaySelected(duplicate.id)
                onStateUpdated()
            }
            overlayRepository.addTextOverlay(duplicate)
        }
    }


}
