package com.dipdev.aiautocaptioner.ui.videoeditor.timeline

import android.graphics.Bitmap
import androidx.media3.common.Player
import com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity
import com.dipdev.aiautocaptioner.data.db.entity.TextOverlayEntity
import com.dipdev.aiautocaptioner.data.model.Clip
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class TimelineData(
    val clips: ImmutableList<Clip> = persistentListOf(),
    val overlays: ImmutableList<ImageOverlayEntity> = persistentListOf(),
    val textOverlays: ImmutableList<TextOverlayEntity> = persistentListOf(),
    val segments: List<CaptionSegmentEntity> = emptyList(),
    val thumbnails: Map<Long, Bitmap> = emptyMap(),
    val originalDurationMs: Long = 0L,
    val zoomLevel: Float = 1f,
    val player: Player,
    val currentTimelineMs: () -> Long
)

data class TimelineSelection(
    val selectedClipId: String? = null,
    val selectedOverlayId: String? = null,
    val isTextOverlaySelected: Boolean = false,
    val selectedCaptionSegmentId: String? = null
)

interface TimelineCallbacks {
    fun onClipSelected(id: String?)
    fun onMoveClip(from: Int, to: Int)
    fun onTrimClip(id: String, startMs: Long, endMs: Long)
    
    fun onOverlaySelected(id: String?)
    fun onUpdateImageOverlay(overlay: ImageOverlayEntity)
    fun onUpdateTextOverlay(overlay: TextOverlayEntity)
    fun onMoveOverlayZ(id: String, bringToFront: Boolean)
    fun onDeleteImageOverlay(id: String)
    fun onDeleteTextOverlay(id: String)
    
    fun onCaptionSegmentTap(segment: CaptionSegmentEntity)
    
    fun onRequestThumbnails(timestamps: List<Long>)
    fun onDragStateChange(isDragging: Boolean)
    
    fun onSplit()
    fun onDelete(id: String)
    fun onDuplicateClip(id: String)
    fun onDuplicateImageOverlay(id: String)
    fun onDuplicateTextOverlay(id: String)
    
    fun onZoomIn()
    fun onZoomOut()
    fun onPinchZoom(scale: Float)
}
