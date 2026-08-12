package com.dipdev.aiautocaptioner.ui.videoeditor.text

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType

/**
 * Text overlay metrics are defined proportionally to the width of the video
 * display (the "canvas"): the editor preview canvas and the exported video
 * frame both apply the same formulas with their own width, so the result is
 * pixel-identical in proportion on every device and in the exported file.
 *
 * `fontSize` on [com.dipdev.aiautocaptioner.data.db.entity.TextOverlayEntity]
 * is expressed in "units per [TEXT_BASE_WIDTH]px of video width". E.g. 48 at
 * 1080px width renders 48px tall.
 */
const val TEXT_BASE_WIDTH = 1080f

const val DEFAULT_TEXT_WIDTH_FRACTION = 0.8f
const val DEFAULT_FONT_SIZE = 48f

/** Scale factor that maps a "1080-base" metric into this canvas' pixel space. */
fun textPxScale(canvasWidth: Float): Float = canvasWidth / TEXT_BASE_WIDTH

/** Rendered font height in px for this canvas. */
fun textFontPx(fontSize: Float, canvasWidth: Float): Float =
    fontSize * textPxScale(canvasWidth)

/** Rendered text box width in px for this canvas. */
fun textBoxWidthPx(textWidthFraction: Float, canvasWidth: Float): Float =
    textWidthFraction * canvasWidth

/** Horizontal box padding in px, proportional to the font size. */
fun textHPaddingPx(fontSize: Float, canvasWidth: Float): Float =
    fontSize * 0.75f * textPxScale(canvasWidth)

/** Vertical box padding in px, proportional to the font size. */
fun textVPaddingPx(fontSize: Float, canvasWidth: Float): Float =
    fontSize * 0.5f * textPxScale(canvasWidth)

/** Background corner radius in px, proportional to the font size. */
fun textCornerRadiusPx(fontSize: Float, canvasWidth: Float): Float =
    fontSize * 0.5f * textPxScale(canvasWidth)

/**
 * Converts an exact pixel font size into the TextUnit sp value that Compose
 * renders as that many px (accounting for density and system font scale).
 */
fun Density.pxToTextUnit(px: Float): TextUnit {
    val spValue = px / (density * fontScale)
    return TextUnit(spValue, TextUnitType.Sp)
}
