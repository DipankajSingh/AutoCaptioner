package com.dipdev.aiautocaptioner.data.repository

import com.dipdev.aiautocaptioner.data.db.dao.ImageOverlayDao
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverlayRepository @Inject constructor(
    private val dao: ImageOverlayDao
) {

    fun getOverlaysForProject(projectId: String): Flow<List<ImageOverlayEntity>> {
        return dao.getOverlaysForProject(projectId)
    }

    suspend fun getOverlaysOnce(projectId: String): List<ImageOverlayEntity> {
        return dao.getOverlaysForProjectOnce(projectId)
    }

    suspend fun addOverlay(overlay: ImageOverlayEntity) {
        dao.insert(overlay)
    }

    suspend fun updateOverlay(overlay: ImageOverlayEntity) {
        dao.update(overlay)
    }

    suspend fun deleteOverlay(overlayId: String) {
        dao.deleteById(overlayId)
    }
}
