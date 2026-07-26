package com.dipdev.aiautocaptioner.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

// ─────────────────────────────────────────────────────────────────────────────
// DialogArchShape — traced directly from the reference SVG (ref.svg), not
// hand-tuned. The dome is two cubic Beziers per side (not one) — that's what
// the single-cubic version was missing: with two curves you get a control
// point that can sit level with the peak (horizontal tangent at the top) AND
// a separate control point that sits level with the flat top edge
// (horizontal tangent at the bottom), so both joins are seamless. A single
// cubic can only guarantee one of those two tangencies, which is exactly
// where the old kink came from.
//
// All numbers below are the literal control points from the SVG's path data
// (peak-relative, in the SVG's own units), scaled by `sx` so the whole shape
// resizes proportionally with the card's actual width — the shape you get
// is the same shape at any dialog width. Corner radius scales the same way,
// since the reference uses one consistent radius throughout.
// ─────────────────────────────────────────────────────────────────────────────

internal class DialogArchShape : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val w = size.width
        val h = size.height
        val cx = w / 2f

        // Reference SVG's card width (wall to wall) — everything below is
        // scaled relative to this so the traced shape reproduces exactly,
        // regardless of the dialog's actual rendered width.
        val sx = w / 348.93945f

        val cr = 27.323349f * sx   // corner radius (all four corners)
        val ah = 54.822262f * sx   // flat top edge y — how far the dome rises above it

        // Dome control points, relative to the peak (0,0), straight from the SVG.
        // L6 is the point where the dome meets the flat top edge.
        val l1x = -25.82779f;  val l1y = 0.000723f
        val l2x = -48.8123f;   val l2y = 15.129802f
        val l3x = -60.10045f;  val l3y = 38.021972f
        val l4x = -64.44129f;  val l4y = 46.825112f
        val l5x = -73.87413f;  val l5y = 54.822262f
        val l6x = -83.71507f   // l6y == l5y == the flat-edge height

        val aw = -l6x * sx   // dome half-width at the flat edge

        val path = Path().apply {
            moveTo(cr, ah)
            lineTo(cx - aw, ah)   // top-left flat segment → dome's left base

            // Left half of the dome, base → peak (SVG curve run in reverse —
            // reversing a Bezier's control points retraces the identical curve).
            cubicTo(
                cx + l5x * sx, ah,
                cx + l4x * sx, l4y * sx,
                cx + l3x * sx, l3y * sx
            )
            cubicTo(
                cx + l2x * sx, l2y * sx,
                cx + l1x * sx, l1y * sx,
                cx, 0f
            )

            // Right half of the dome, peak → base (mirror image of the left).
            cubicTo(
                cx - l1x * sx, l1y * sx,
                cx - l2x * sx, l2y * sx,
                cx - l3x * sx, l3y * sx
            )
            cubicTo(
                cx - l4x * sx, l4y * sx,
                cx - l5x * sx, ah,
                cx + aw, ah
            )

            lineTo(w - cr, ah)

            // ── Corners + sides ──
            arcTo(Rect(w - 2 * cr, ah, w, ah + 2 * cr), 270f, 90f, false)
            lineTo(w, h - cr)
            arcTo(Rect(w - 2 * cr, h - 2 * cr, w, h), 0f, 90f, false)
            lineTo(cr, h)
            arcTo(Rect(0f, h - 2 * cr, 2 * cr, h), 90f, 90f, false)
            lineTo(0f, ah + cr)
            arcTo(Rect(0f, ah, 2 * cr, ah + 2 * cr), 180f, 90f, false)

            close()
        }

        return Outline.Generic(path)
    }
}
