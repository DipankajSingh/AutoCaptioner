package com.dipdev.aiautocaptioner.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey


@Entity(
    tableName = "caption_segments",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],

    indices = [Index("projectId")]
)
data class CaptionSegmentEntity(

    @PrimaryKey
    val id: String,

    val projectId: String,

    val index: Int,

    val startTimeMs: Long,
    val endTimeMs: Long,

    val text: String,

    val isEdited: Boolean = false
)