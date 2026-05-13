package com.dipdev.aiautocaptioner.engine

import android.graphics.Paint
import android.graphics.Typeface
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity

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

    /**
     * Synchronise all paints to [style].
     * Must be called once at the top of every [CaptionRenderer.draw] invocation,
     * before any paint is read or drawn with.
     *
     * @param style     The active caption style.
     * @param baseScale videoHeight / 1920f — used to scale dp-like values to px.
     */
    fun configure(style: CaptionStyleEntity, baseScale: Float) {
        val textSizePx = style.fontSize * baseScale
        val tf = resolveTypeface(style)

        text.apply {
            textSize      = textSizePx
            typeface      = tf
            color         = style.textColor.toInt()
            textAlign     = Paint.Align.LEFT
            letterSpacing = style.letterSpacing
            this.style    = Paint.Style.FILL
            clearShadowLayer()
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
            clearShadowLayer()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun resolveTypeface(style: CaptionStyleEntity): Typeface {
        val base = if (style.fontFamily == "System") Typeface.DEFAULT
                   else Typeface.create(style.fontFamily, Typeface.NORMAL)
        val tsStyle = when {
            style.fontWeight > 600 && style.isItalic -> Typeface.BOLD_ITALIC
            style.fontWeight > 600                   -> Typeface.BOLD
            style.isItalic                           -> Typeface.ITALIC
            else                                     -> Typeface.NORMAL
        }
        return Typeface.create(base, tsStyle)
    }
}
