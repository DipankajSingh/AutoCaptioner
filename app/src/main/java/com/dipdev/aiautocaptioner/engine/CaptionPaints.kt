package com.dipdev.aiautocaptioner.engine

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Typeface
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity

/** All fonts bundled in assets/fonts/ — kept in sync with actual filenames. */
object BundledFonts {
    data class FontEntry(
        val displayName: String,
        val assetPath: String,
        val category: FontCategory,
    )

    enum class FontCategory { SANS_SERIF, SERIF, DISPLAY, HANDWRITTEN, MONOSPACE }

    val all: List<FontEntry> = listOf(
        FontEntry("System", "", FontCategory.SANS_SERIF),
        FontEntry("Montserrat", "fonts/montserrat.ttf", FontCategory.SANS_SERIF),
        FontEntry("DMSans", "fonts/dm_sans.ttf", FontCategory.SANS_SERIF),
        FontEntry("Rubik", "fonts/rubik.ttf", FontCategory.SANS_SERIF),
        FontEntry("Bebas Neue", "fonts/bebas_neue.ttf", FontCategory.DISPLAY),
        FontEntry("Oswald", "fonts/oswald.ttf", FontCategory.DISPLAY),
        FontEntry("Bungee", "fonts/bungee.ttf", FontCategory.DISPLAY),
        FontEntry("Pacifico", "fonts/pacifico.ttf", FontCategory.HANDWRITTEN),
        FontEntry("Permanent Marker", "fonts/permanent_marker.ttf", FontCategory.HANDWRITTEN),
        FontEntry("Roboto", "", FontCategory.SANS_SERIF),
    )

    /** Display names for the font picker — in UI order. */
    val displayNames: List<String> = all.map { it.displayName }
}

// ─────────────────────────────────────────────────────────────────────────────
// CaptionPaints
//
// Owns and configures the four shared Paint objects used across every draw call.
// Allocated once per process — never recreated — eliminating GC pressure at 60fps.
//
// Call [configure] at the start of each frame to synchronise paint state with
// the current style. All other engine code reads the exposed paint references.
// ─────────────────────────────────────────────────────────────────────────────
object CaptionPaints {

    /** Main word fill paint */
    val text    = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Stroke outline drawn behind [text] */
    val outline = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Background rect/pill fill */
    val bg      = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Karaoke highlight fill (FILL_LEFT_RIGHT, BACKGROUND_HIGHLIGHT) */
    val highlight = Paint(Paint.ANTI_ALIAS_FLAG)

    // Typeface cache — avoids allocating new Typeface objects on every draw().
    // At 60fps with a complex style this saves ~120 allocations/sec and
    // eliminates the GC pressure that causes jank in the style editor preview.
    private var cachedTypeface: Typeface? = null
    private var cachedFontFamily: String = ""
    private var cachedFontWeight: Int = -1
    private var cachedItalic: Boolean = false

    /**
     * Synchronise all paints to [style].
     * Must be called once at the top of every [CaptionRenderer.draw] invocation,
     * before any paint is read or drawn with.
     *
     * @param context   Application context — used to load bundled fonts from assets.
     * @param style     The active caption style.
     * @param baseScale videoHeight / 1920f — used to scale dp-like values to px.
     */
    fun configure(context: Context, style: CaptionStyleEntity, baseScale: Float) {
        val textSizePx = style.fontSize * baseScale
        val tf = resolveTypeface(context, style)

        text.apply {
            textSize      = textSizePx
            typeface      = tf
            color         = style.textColor.toInt()
            textAlign     = Paint.Align.LEFT
            letterSpacing = style.letterSpacing
            this.style    = if (style.outlineOnly) Paint.Style.STROKE else Paint.Style.FILL
            strokeWidth   = if (style.outlineOnly) style.outlineWidth * baseScale else 0f
            strokeJoin    = Paint.Join.ROUND
            flags         = flags or Paint.SUBPIXEL_TEXT_FLAG
            textLocale    = java.util.Locale.ROOT
            clearShadowLayer()
            // Glow mask — applied during background pass, cleared for fill
            maskFilter = null
        }

        outline.apply {
            textSize      = textSizePx
            typeface      = tf
            color         = style.outlineColor.toInt()
            this.style    = Paint.Style.STROKE
            strokeWidth   = style.outlineWidth * baseScale
            strokeJoin    = Paint.Join.ROUND
            textAlign     = Paint.Align.LEFT
            letterSpacing = style.letterSpacing
            flags         = flags or Paint.SUBPIXEL_TEXT_FLAG
            textLocale    = java.util.Locale.ROOT
            clearShadowLayer()
        }

        bg.apply {
            color         = style.backgroundColor.toInt()
            alpha         = (style.backgroundOpacity * 255).toInt()
            this.style    = Paint.Style.FILL
        }

        highlight.apply {
            textSize      = textSizePx
            typeface      = tf
            color         = style.highlightColor.toInt()
            this.style    = Paint.Style.FILL
            textAlign     = Paint.Align.LEFT
            letterSpacing = style.letterSpacing
            flags         = flags or Paint.SUBPIXEL_TEXT_FLAG
            textLocale    = java.util.Locale.ROOT
            clearShadowLayer()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun resolveTypeface(context: Context, style: CaptionStyleEntity): Typeface {
        // Only recompute if style fields affecting typeface have changed.
        if (cachedTypeface != null &&
            cachedFontFamily == style.fontFamily &&
            cachedFontWeight == style.fontWeight &&
            cachedItalic == style.isItalic) {
            return cachedTypeface!!
        }

        val tf = loadTypeface(context, style.fontFamily, style.fontWeight, style.isItalic)

        cachedTypeface  = tf
        cachedFontFamily = style.fontFamily
        cachedFontWeight = style.fontWeight
        cachedItalic    = style.isItalic
        return tf
    }

    /**
     * Resolve a Typeface from the font family name.
     * Tries bundled assets first, then falls back to system fonts.
     */
    fun loadTypeface(context: Context, fontFamily: String, fontWeight: Int, isItalic: Boolean): Typeface {
        val tsStyle = when {
            fontWeight > 600 && isItalic -> Typeface.BOLD_ITALIC
            fontWeight > 600             -> Typeface.BOLD
            isItalic                     -> Typeface.ITALIC
            else                         -> Typeface.NORMAL
        }

        // 1. Try bundled asset font
        val entry = BundledFonts.all.find { it.displayName == fontFamily }
        if (entry != null && entry.assetPath.isNotEmpty()) {
            try {
                val assetTf = Typeface.createFromAsset(context.assets, entry.assetPath)
                return Typeface.create(assetTf, tsStyle)
            } catch (_: Exception) {
                // Fall through to system font
            }
        }

        // 2. System font fallback
        val base = when (fontFamily) {
            "System" -> Typeface.DEFAULT
            else     -> Typeface.create(fontFamily, Typeface.NORMAL)
        }
        return Typeface.create(base, tsStyle)
    }
}
