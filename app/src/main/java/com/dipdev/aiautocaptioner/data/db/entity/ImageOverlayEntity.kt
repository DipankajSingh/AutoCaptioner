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
    @PrimaryKey override val id: String,
    override val projectId: String,
    val imageUri: String,
    override val positionX: Float = 0.5f,
    override val positionY: Float = 0.5f,
    override val scaleX: Float = 1f,
    override val scaleY: Float = 1f,
    override val startTimeMs: Long = 0L,
    override val endTimeMs: Long = Long.MAX_VALUE,
    override var zOrder: Int = 0,
    override val createdAt: Long,
    val naturalWidth: Int = 0,
    val naturalHeight: Int = 0,
    val opacity: Float = 1f,
    val filterName: String? = null,
    val isFlippedX: Boolean = false
) : OverlayEntity {
    @androidx.room.Ignore override val rotation: Float = 0f
}
