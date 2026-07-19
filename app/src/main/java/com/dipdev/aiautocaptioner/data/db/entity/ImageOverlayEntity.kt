package com.dipdev.aiautocaptioner.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "image_overlays",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("projectId")
    ]
)
data class ImageOverlayEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val imageUri: String,
    val positionX: Float = 0.5f,
    val positionY: Float = 0.5f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = Long.MAX_VALUE,
    val zOrder: Int = 0,
    val createdAt: Long,
    val naturalWidth: Int = 0,
    val naturalHeight: Int = 0
)
