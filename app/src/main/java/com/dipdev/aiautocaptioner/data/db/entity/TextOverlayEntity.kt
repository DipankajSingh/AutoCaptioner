package com.dipdev.aiautocaptioner.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "text_overlays",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("projectId"),
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class TextOverlayEntity(
    @PrimaryKey override val id: String,
    override val projectId: String,
    val text: String,
    val fontAssetPath: String,
    val textColorArgb: Int,
    val backgroundColorArgb: Int,
    val backgroundOpacity: Float,
    val backgroundStyle: String,
    val textAlignment: String,
    val fontSize: Float,
    val textWidth: Float?,
    override val positionX: Float,
    override val positionY: Float,
    override val scaleX: Float,
    override val scaleY: Float,
    override val rotation: Float,
    override val startTimeMs: Long,
    override val endTimeMs: Long,
    override var zOrder: Int,
    override val createdAt: Long
) : OverlayEntity
