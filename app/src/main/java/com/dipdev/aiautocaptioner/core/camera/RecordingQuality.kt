package com.dipdev.aiautocaptioner.core.camera

/**
 * A recording quality tier, expressed as a *short-edge* pixel target (the smaller of
 * width/height once the target aspect ratio is applied — see [RecordingAspectRatio.resolve]).
 *
 * This is deliberately an open, ordered list rather than a fixed enum with behavior baked
 * in elsewhere. To add a new tier later (e.g. 4K), add one entry here — nothing else in
 * this feature needs to change. [CameraCapabilities.supportedQualities] automatically
 * filters the list down to whatever the connected sensor can actually deliver, so an
 * unsupported tier just silently won't appear in the UI on devices that can't do it.
 */
sealed class RecordingQuality(
    val id: String,
    val label: String,
    val shortEdgePx: Int,
    val targetBitrateBps: Int
) {
    data object SD_480 : RecordingQuality(
        id = "480p",
        label = "480p",
        shortEdgePx = 480,
        targetBitrateBps = 2_500_000
    )

    data object HD_720 : RecordingQuality(
        id = "720p",
        label = "720p HD",
        shortEdgePx = 720,
        targetBitrateBps = 5_000_000
    )

    data object FHD_1080 : RecordingQuality(
        id = "1080p",
        label = "1080p Full HD",
        shortEdgePx = 1080,
        targetBitrateBps = 9_000_000
    )

    // To add a future tier, add a case here — e.g.:
    // data object UHD_4K : RecordingQuality("4k", "4K Ultra HD", 2160, 35_000_000)
    // CameraCapabilities will filter it in/out per-device automatically; no other file
    // in this feature needs to change.

    companion object {
        /** Ordered from lowest to highest — also the default fallback preference order. */
        val ALL: List<RecordingQuality> = listOf(SD_480, HD_720, FHD_1080)

        val DEFAULT: RecordingQuality = HD_720

        fun fromId(id: String): RecordingQuality = ALL.firstOrNull { it.id == id } ?: DEFAULT
    }
}
