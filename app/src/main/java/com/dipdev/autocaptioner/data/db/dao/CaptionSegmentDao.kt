package com.dipdev.autocaptioner.data.db.dao

import androidx.room.*
import com.dipdev.autocaptioner.data.db.entity.CaptionSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptionSegmentDao {

    // Get all segments for a project, ordered by their position in the video
    // Flow = auto-updates the UI when segments are edited
    @Query("SELECT * FROM caption_segments WHERE projectId = :projectId ORDER BY `index` ASC")
    fun getSegmentsForProject(projectId: String): Flow<List<CaptionSegmentEntity>>

    // Non-Flow version — used when we need a one-time read (not observing)
    // Example: when generating the ASS subtitle file for export
    @Query("SELECT * FROM caption_segments WHERE projectId = :projectId ORDER BY `index` ASC")
    suspend fun getSegmentsForProjectOnce(projectId: String): List<CaptionSegmentEntity>

    // Insert all segments from Whisper transcription result at once
    // insertAll is more efficient than calling insert() in a loop
    // because Room wraps it in a single database transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(segments: List<CaptionSegmentEntity>)

    // Update a segment after the user edits it in the caption editor
    @Update
    suspend fun updateSegment(segment: CaptionSegmentEntity)

    // Delete all segments for a project
    // Used when re-transcribing (user wants to redo transcription)
    @Query("DELETE FROM caption_segments WHERE projectId = :projectId")
    suspend fun deleteSegmentsForProject(projectId: String)

    // Count how many segments a project has
    // Used to show progress: "Transcribed X segments"
    @Query("SELECT COUNT(*) FROM caption_segments WHERE projectId = :projectId")
    suspend fun getSegmentCount(projectId: String): Int
}