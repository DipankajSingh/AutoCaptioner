package com.dipdev.aiautocaptioner.data.db.dao

import androidx.room.*
import com.dipdev.aiautocaptioner.data.db.entity.ProjectEntity
import com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus
import kotlinx.coroutines.flow.Flow

// @Dao tells Room: "this interface contains database query definitions"
// Room generates the actual SQL implementation at compile time
// You never write SQL manually — Room handles it from the annotations
@Dao
interface ProjectDao {

    // Flow<List<ProjectEntity>> means:
    // → returns a stream that automatically emits new values when data changes
    // → the UI observes this Flow and recomposes automatically
    // → ORDER BY updatedAt DESC = newest projects first
    // This is what powers the home screen project list
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Transaction
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getProjectsWithExportedFiles(): Flow<List<com.dipdev.aiautocaptioner.data.db.entity.ProjectWithExportedFiles>>

    // Suspend = runs on a coroutine (background thread)
    // Never run database queries on the main thread — it blocks the UI
    // Returns null if no project with that id exists
    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: String): ProjectEntity?

    // Insert a new project into the database
    // OnConflictStrategy.REPLACE means:
    // if a project with the same id already exists, replace it entirely
    // This lets us use the same function for both insert and update
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    // Update specific fields without replacing the whole row
    // @Update matches by primary key (id) automatically
    @Update
    suspend fun updateProject(project: ProjectEntity)

    // Delete a project — Room will also cascade-delete all its
    // segments, words automatically (because of our ForeignKey cascade)
    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    // Update just the status field — faster than updating the whole row
    // Used frequently during processing pipeline steps
    @Query("UPDATE projects SET status = :status, updatedAt = :updatedAt WHERE id = :projectId")
    suspend fun updateStatus(
        projectId: String,
        status: ProjectStatus,
        updatedAt: Long = System.currentTimeMillis()
    )

    // Update the audio path once extraction is complete
    @Query("UPDATE projects SET audioPath = :audioPath WHERE id = :projectId")
    suspend fun updateAudioPath(projectId: String, audioPath: String)

    // Update thumbnail path once we've extracted the first frame
    @Query("UPDATE projects SET thumbnailPath = :thumbnailPath WHERE id = :projectId")
    suspend fun updateThumbnailPath(projectId: String, thumbnailPath: String)

    // Update the hasVisitedCaptionEditor flag
    @Query("UPDATE projects SET hasVisitedCaptionEditor = :hasVisited WHERE id = :projectId")
    suspend fun updateVisitedCaptionEditor(projectId: String, hasVisited: Boolean)

    // Delete all projects — used in settings "clear all data"
    @Query("DELETE FROM projects")
    suspend fun deleteAllProjects()
}