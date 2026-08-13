package com.dipdev.aiautocaptioner.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "caption_words",
    foreignKeys = [
        ForeignKey(
            entity = CaptionSegmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["segmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("segmentId"),
        Index("projectId")
    ]
)
data class CaptionWordEntity(

    @PrimaryKey
    val id: String,

    val segmentId: String,

    val projectId: String,

    val index: Int,

    val word: String,


    val startTimeMs: Long,
    val endTimeMs: Long,


    val confidence: Float = 1.0f,

    val isEmphasized: Boolean = false,

    val emphasisType: EmphasisType = EmphasisType.NONE
)

enum class EmphasisType {
    NONE,
    BOUNCE,
    SCALE,
    SHAKE,
    COLOR_POP
}