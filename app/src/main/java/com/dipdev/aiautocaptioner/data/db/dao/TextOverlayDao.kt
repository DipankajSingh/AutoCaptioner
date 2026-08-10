package com.dipdev.aiautocaptioner.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dipdev.aiautocaptioner.data.db.entity.TextOverlayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TextOverlayDao {
    @Query("SELECT * FROM text_overlays WHERE projectId = :projectId ORDER BY zOrder ASC")
    fun getOverlaysForProject(projectId: String): Flow<List<TextOverlayEntity>>

    @Query("SELECT * FROM text_overlays WHERE projectId = :projectId ORDER BY zOrder ASC")
    suspend fun getOverlaysForProjectSync(projectId: String): List<TextOverlayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(overlay: TextOverlayEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(overlays: List<TextOverlayEntity>)

    @Update
    suspend fun update(overlay: TextOverlayEntity)

    @Query("DELETE FROM text_overlays WHERE id = :id AND projectId = :projectId")
    suspend fun delete(id: String, projectId: String)

    @Query("DELETE FROM text_overlays WHERE projectId = :projectId")
    suspend fun deleteByProjectId(projectId: String)
}
