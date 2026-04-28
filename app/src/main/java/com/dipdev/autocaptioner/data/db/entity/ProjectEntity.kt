package com.dipdev.autocaptioner.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// @Entity tells Room: "create a table for this data class"
// tableName is what the actual SQL table will be called
@Entity(tableName = "projects")
data class ProjectEntity(

    // @PrimaryKey = unique identifier for each row
    // We use a String UUID instead of auto-increment Int because:
    // 1. UUIDs are globally unique — safe if we ever add cloud sync later
    // 2. We can generate the ID before saving, which simplifies our code
    @PrimaryKey
    val id: String,

    // User-visible name of the project
    // Defaults to the video filename, user can rename it
    val title: String,

    // The original URI the user picked from their gallery
    // Stored as String because Room can't store Uri objects directly
    // We use this to show "open original" or re-import if needed
    val originalVideoUri: String,

    // Path to our working copy of the video in app's internal storage
    // We copy the video here so we're not dependent on the user's gallery
    // Path example: /data/data/com.dipdev.autocaptioner/files/projects/uuid/original.mp4
    val workingVideoPath: String,

    // Path to the extracted audio file (16kHz mono WAV)
    // This is what we feed to Whisper for transcription
    // Null until audio extraction is complete
    val audioPath: String? = null,

    // Path to the video thumbnail (first frame as JPEG)
    // Used in the home screen project list
    // Null until thumbnail is generated
    val thumbnailPath: String? = null,

    // Total length of the video in milliseconds
    // Used to calculate progress during transcription
    val videoDurationMs: Long,

    // Video dimensions — needed for:
    // 1. Caption positioning (relative 0.0-1.0 coords need absolute px)
    // 2. Export resolution
    val videoWidth: Int,
    val videoHeight: Int,

    // Rotation in degrees (0, 90, 180, 270)
    // Phone videos are often recorded at 90° rotation
    // We need this to render captions correctly
    val videoRotation: Int = 0,

    // Frames per second — needed for frame-accurate caption timing
    val videoFps: Float,

    // Current state of this project in the processing pipeline
    // Used to resume where the user left off if they close the app
    val status: ProjectStatus = ProjectStatus.IMPORTED,

    // ID of the CaptionStyle the user last applied to this project
    // Null = list default style
    val activeStyleId: String? = null,

    // Indicates if the user has explicitly reviewed and edited their captions
    val hasVisitedCaptionEditor: Boolean = false,

    // Unix timestamps in milliseconds
    // Used for sorting projects in home screen ("2 hours ago")
    val createdAt: Long,
    val updatedAt: Long,

    // Absolute path of the last successfully exported video file
    // Null until the user has exported at least once
    val exportedVideoPath: String? = null
)

// Represents where the project is in the processing pipeline
// Stored as String in the database (Room converts enum to String automatically)
enum class ProjectStatus {
    IMPORTED,           // video copied to app storage, nothing processed yet
    EXTRACTING_AUDIO,   // FFmpeg/Media3 is currently extracting audio
    TRANSCRIBING,       // Whisper is currently running
    TRANSCRIBED,        // captions are ready — user can edit, preview, export
    EXPORTED            // final video with burned-in captions has been saved
}