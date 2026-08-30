package com.dipdev.aiautocaptioner.core.camera

/**
 * Output aspect ratio choices, matching the short-form-video convention (TikTok / Reels /
 * Shorts): portrait 9:16 as the default, plus 1:1 and 3:4. Recording is portrait-locked, so
 * [resolve] always returns a size where height >= width (or height == width for the square
 * ratio) — there is no landscape case to handle.
 */
enum class RecordingAspectRatio(
    val label: String,
    val widthRatio: Int,
    val heightRatio: Int
) {
    RATIO_9_16("9:16", 9, 16),
    RATIO_1_1("1:1", 1, 1),
    RATIO_3_4("3:4", 3, 4);

    /**
     * Resolves a concrete, even-numbered (width, height) pixel size for the given
     * [quality]'s short edge. Width is always the short edge for portrait ratios; for the
     * square ratio, width == height == shortEdge.
     *
     * Dimensions are rounded down to the nearest even number, since AVC encoders require
     * even width/height for both the encoder surface and the muxer track format.
     */
    fun resolve(quality: RecordingQuality): Size {
        val shortEdge = quality.shortEdgePx
        val longEdge = if (widthRatio == heightRatio) {
            shortEdge
        } else {
            (shortEdge.toLong() * heightRatio / widthRatio).toInt()
        }
        return Size(width = evenify(shortEdge), height = evenify(longEdge))
    }

    private fun evenify(px: Int): Int = if (px % 2 == 0) px else px - 1

    data class Size(val width: Int, val height: Int) {
        val aspect: Float get() = width.toFloat() / height.toFloat()
    }

    companion object {
        val DEFAULT = RATIO_9_16

        fun fromLabel(label: String): RecordingAspectRatio =
            entries.firstOrNull { it.label == label } ?: DEFAULT
    }
}
