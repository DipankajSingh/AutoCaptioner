package com.dipdev.aiautocaptioner.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionWordEntity

@UnstableApi
class CaptionOverlayEffect @OptIn(UnstableApi::class) constructor
    (
    private val segments: List<CaptionSegmentEntity>,
    private val wordsMap: Map<String, List<CaptionWordEntity>>,
    private val style: CaptionStyleEntity,
    private val videoWidth: Int,
    private val videoHeight: Int
) : BitmapOverlay() {

    // Create a recycled bitmap matching exactly the physical boundaries of the video encode frame
    private var recycledBitmap: Bitmap = Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888)
    private var overlayCanvas: Canvas = Canvas(recycledBitmap)

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        // Obliterate the previous frame
        overlayCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val currentPositionMs = presentationTimeUs / 1000

        com.dipdev.aiautocaptioner.engine.CaptionRenderer.draw(
            canvas = overlayCanvas,
            currentPositionMs = currentPositionMs,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            style = style,
            segments = segments,
            wordsMap = wordsMap
        )

        return recycledBitmap
    }
}
