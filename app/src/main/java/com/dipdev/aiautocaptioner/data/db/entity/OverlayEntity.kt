package com.dipdev.aiautocaptioner.data.db.entity

interface OverlayEntity {
    val id: String
    val projectId: String
    val positionX: Float
    val positionY: Float
    val scaleX: Float
    val scaleY: Float
    val rotation: Float
    val startTimeMs: Long
    val endTimeMs: Long
    var zOrder: Int
    val createdAt: Long
}
