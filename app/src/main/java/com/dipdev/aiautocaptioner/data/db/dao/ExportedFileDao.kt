package com.dipdev.aiautocaptioner.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dipdev.aiautocaptioner.data.db.entity.ExportedFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExportedFileDao {

    @Query("SELECT * FROM exported_files WHERE projectId = :projectId ORDER BY exportedAt DESC")
    fun getExportsForProject(projectId: String): Flow<List<ExportedFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExportedFile(exportedFile: ExportedFileEntity)

    @Delete
    suspend fun deleteExportedFile(exportedFile: ExportedFileEntity)
    
    @Query("DELETE FROM exported_files WHERE id = :id")
    suspend fun deleteById(id: String)
}
