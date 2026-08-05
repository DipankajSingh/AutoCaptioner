package com.dipdev.aiautocaptioner.engine.effects

import androidx.compose.runtime.Immutable

/**
 * Curated color grading profiles designed specifically for 2026 short-form creator content
 * (Reels, TikToks, and Shorts).
 *
 * Each aesthetic profile is executed via real-time floating-point mathematical GPU shaders,
 * preserving 10-bit HDR sensor dynamic range without highlight clipping or color banding.
 *
 * Annotated with [@Immutable] to guarantee Jetpack Compose skips redundant layout recompositions
 * when evaluating UI filter chip components.
 *
 * @param displayName Human-readable title displayed on selection badges and carousel chips.
 * @param subtitle Short aesthetic description highlighting the mood or ideal lighting condition.
 * @param shaderIndex Integer uniform identifier routed directly to [LutGlEffect] shader registers.
 * @param accentColorHex Primary hex color code utilized for UI glow effects and active badges.
 */
@Immutable
enum class CreatorFilter(
    val displayName: String,
    val subtitle: String,
    val shaderIndex: Int,
    val accentColorHex: Long
) {
    /**
     * Calibrated baseline sensor true-tone pass-through without color modification.
     */
    NATURAL(
        displayName = "Natural",
        subtitle = "True-Tone Sensor",
        shaderIndex = 0,
        accentColorHex = 0xFFFFFFFFL
    ),

    /**
     * Crisp contrast, clean whites, and elevated saturation designed to make speaking commentary pop in social feeds.
     */
    VIBRANT(
        displayName = "Clarity",
        subtitle = "Vibrant Pop",
        shaderIndex = 1,
        accentColorHex = 0xFF00E5FFL
    ),

    /**
     * Sun-kissed golden hour tone with warm midtones, ideal for cozy ambient talking-head sessions.
     */
    WARM_GLOW(
        displayName = "Golden Hour",
        subtitle = "Warm Glow",
        shaderIndex = 2,
        accentColorHex = 0xFFFFAB00L
    ),

    /**
     * Elevated midtone exposure with softened harsh highlights to combat artificial indoor LED lighting glare.
     */
    STUDIO_BRIGHT(
        displayName = "Clean",
        subtitle = "Studio Glam",
        shaderIndex = 3,
        accentColorHex = 0xFFFFD700L
    ),

    /**
     * Classic Hollywood color separation driving shadows toward rich teal and skin midtones toward cinematic amber.
     */
    CINEMATIC(
        displayName = "Teal & Orange",
        subtitle = "Cinematic Film",
        shaderIndex = 4,
        accentColorHex = 0xFFFF6E40L
    ),

    /**
     * Dreamy aesthetic curve featuring slightly lifted shadows and diffused pastel midtones for lifestyle storytelling.
     */
    SOFT_PASTEL(
        displayName = "Aesthetic",
        subtitle = "Soft Pastel",
        shaderIndex = 5,
        accentColorHex = 0xFFF48FB1L
    );

    companion object {
        /**
         * Resolves a filter by name safely with fallback to [NATURAL] for DataStore persistence decoding.
         */
        fun fromName(name: String?): CreatorFilter {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: NATURAL
        }
    }
}
