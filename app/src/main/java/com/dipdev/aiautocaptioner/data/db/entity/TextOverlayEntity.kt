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
    @PrimaryKey val id: String,
    val projectId: String,
    val text: String,
    val fontAssetPath: String,
    val textColorArgb: Int,
    val backgroundColorArgb: Int,
    val backgroundOpacity: Float,
    val textAlignment: String,
    val fontSize: Float,
    val positionX: Float,
    val positionY: Float,
    val scaleX: Float,
    val scaleY: Float,
    val rotation: Float,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val zOrder: Int,
    val createdAt: Long
)
