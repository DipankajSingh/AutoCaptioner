package com.dipdev.autocaptioner.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// ForeignKey links this table to the projects table
// This means: every segment must belong to a project
// onDelete = CASCADE means: if a project is deleted,
// all its segments are automatically deleted too
// This prevents orphaned data in the database
@Entity(
    tableName = "caption_segments",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,  // parent table
            parentColumns = ["id"],          // parent's primary key
            childColumns = ["projectId"],    // our column that references it
            onDelete = ForeignKey.CASCADE    // delete segments when project deleted
        )
    ],
    // Index speeds up queries that filter by projectId
    // Without this, Room would scan the entire table to find segments for a project
    // With this, it jumps directly to the right rows — much faster
    indices = [Index("projectId")]
)
data class CaptionSegmentEntity(

    @PrimaryKey
    val id: String,

    // Which project this segment belongs to
    val projectId: String,

    // Order of this segment in the video (0, 1, 2, 3...)
    // Used to display segments in correct order in the editor
    val index: Int,

    // When this segment starts and ends in the video
    // In milliseconds from the start of the video
    // Example: startTimeMs=1000, endTimeMs=3500 means
    // this caption appears from 1.0s to 3.5s
    val startTimeMs: Long,
    val endTimeMs: Long,

    // The full text of this segment
    // Example: "Hello this is a test video"
    // This is the joined text of all words in this segment
    val text: String,

    // Tracks whether the user manually edited this segment's text
    // Useful for analytics and for deciding whether to re-transcribe
    val isEdited: Boolean = false
)