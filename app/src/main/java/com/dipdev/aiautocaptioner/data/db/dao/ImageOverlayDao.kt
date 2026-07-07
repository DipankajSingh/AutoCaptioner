package com.dipdev.aiautocaptioner.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageOverlayDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(overlay: ImageOverlayEntity)

    @Update
    suspend fun update(overlay: ImageOverlayEntity)

    @Query("DELETE FROM image_overlays WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM image_overlays WHERE projectId = :projectId ORDER BY zOrder ASC")
    fun getOverlaysForProject(projectId: String): Flow<List<ImageOverlayEntity>>

    @Query("SELECT * FROM image_overlays WHERE projectId = :projectId ORDER BY zOrder ASC")
    suspend fun getOverlaysForProjectOnce(projectId: String): List<ImageOverlayEntity>
}
