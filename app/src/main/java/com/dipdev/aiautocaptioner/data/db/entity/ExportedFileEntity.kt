package com.dipdev.aiautocaptioner.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exported_files",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("projectId"),
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["projectId"])
    ]
)
data class ExportedFileEntity(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val videoFilePath: String?,
    val srtFilePath: String?,
    val exportedAt: Long,
    val quality: String? = null
)
