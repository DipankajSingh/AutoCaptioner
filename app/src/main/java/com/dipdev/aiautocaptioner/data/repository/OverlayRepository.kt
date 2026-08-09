package com.dipdev.aiautocaptioner.data.repository

import com.dipdev.aiautocaptioner.data.db.dao.ImageOverlayDao
import com.dipdev.aiautocaptioner.data.db.dao.TextOverlayDao
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import com.dipdev.aiautocaptioner.data.db.entity.TextOverlayEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverlayRepository @Inject constructor(
    private val imageDao: ImageOverlayDao,
    private val textDao: TextOverlayDao
) {

    fun getOverlaysForProject(projectId: String): Flow<List<ImageOverlayEntity>> {
        return imageDao.getOverlaysForProject(projectId)
    }

    suspend fun getOverlaysOnce(projectId: String): List<ImageOverlayEntity> {
        return imageDao.getOverlaysForProjectOnce(projectId)
    }

    suspend fun addOverlay(overlay: ImageOverlayEntity) {
        imageDao.insert(overlay)
    }

    suspend fun updateOverlay(overlay: ImageOverlayEntity) {
        imageDao.update(overlay)
    }

    suspend fun deleteOverlay(overlayId: String) {
        imageDao.deleteById(overlayId)
    }

    // Text Overlays
    fun getTextOverlaysForProject(projectId: String): Flow<List<TextOverlayEntity>> {
        return textDao.getOverlaysForProject(projectId)
    }

    suspend fun getTextOverlaysForProjectSync(projectId: String): List<TextOverlayEntity> {
        return textDao.getOverlaysForProjectSync(projectId)
    }

    suspend fun addTextOverlay(overlay: TextOverlayEntity) {
        textDao.insert(overlay)
    }

    suspend fun updateTextOverlay(overlay: TextOverlayEntity) {
        textDao.update(overlay)
    }

    suspend fun deleteTextOverlay(id: String, projectId: String) {
        textDao.delete(id, projectId)
    }
}
