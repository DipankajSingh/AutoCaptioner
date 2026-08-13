package com.dipdev.aiautocaptioner.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

import androidx.room.ForeignKey

@Entity(
    tableName = "projects",
    foreignKeys = [
        ForeignKey(
            entity = CaptionStyleEntity::class,
            parentColumns = ["id"],
            childColumns = ["activeStyleId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [androidx.room.Index(value = ["activeStyleId"])]
)
data class ProjectEntity(

    @PrimaryKey
    val id: String,

    val title: String,

    val originalVideoUri: String,

    val workingVideoPath: String,

    val audioPath: String? = null,

    val thumbnailPath: String? = null,

    val videoDurationMs: Long,

    val videoWidth: Int,
    val videoHeight: Int,

    val videoRotation: Int = 0,

    val videoFps: Float,

    val status: ProjectStatus = ProjectStatus.IMPORTED,

    val activeStyleId: String? = null,

    val hasVisitedCaptionEditor: Boolean = false,

    val createdAt: Long,
    val updatedAt: Long,

    val exportedVideoPath: String? = null,

    val transcriptionLanguage: String? = "en",

    val transcribedWithModelId: String? = null,

    val facelessBackgroundType: String? = null,
    val facelessBackgroundValue: String? = null,
    
    val initialPrompt: String? = null,
    
    val creationMode: CreationMode = CreationMode.ADVANCED
)

enum class CreationMode {
    QUICK_CAPTION,
    ADVANCED
}


enum class ProjectStatus {
    IMPORTED,
    READY_FOR_PROCESSING,
    EXTRACTING_AUDIO,
    TRANSCRIBING,
    TRANSCRIBED,
    EXPORTED
}

data class ProjectWithExportedFiles(
    @androidx.room.Embedded
    val project: ProjectEntity,
    
    @androidx.room.Relation(
        parentColumn = "id",
        entityColumn = "projectId"
    )
    val exportedFiles: List<ExportedFileEntity>
)