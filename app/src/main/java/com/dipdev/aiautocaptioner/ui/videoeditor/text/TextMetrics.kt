package com.dipdev.aiautocaptioner.ui.videoeditor.text

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType

const val TEXT_BASE_WIDTH = 1080f

const val DEFAULT_TEXT_WIDTH_FRACTION = 0.8f
const val DEFAULT_FONT_SIZE = 48f

fun textPxScale(canvasWidth: Float): Float = canvasWidth / TEXT_BASE_WIDTH

fun textFontPx(fontSize: Float, canvasWidth: Float): Float =
    fontSize * textPxScale(canvasWidth)

fun textBoxWidthPx(textWidthFraction: Float, canvasWidth: Float): Float =
    textWidthFraction * canvasWidth

fun textHPaddingPx(fontSize: Float, canvasWidth: Float): Float =
    fontSize * 0.75f * textPxScale(canvasWidth)

fun textVPaddingPx(fontSize: Float, canvasWidth: Float): Float =
    fontSize * 0.5f * textPxScale(canvasWidth)

fun textCornerRadiusPx(fontSize: Float, canvasWidth: Float): Float =
    fontSize * 0.5f * textPxScale(canvasWidth)


fun Density.pxToTextUnit(px: Float): TextUnit {
    val spValue = px / (density * fontScale)
    return TextUnit(spValue, TextUnitType.Sp)
}
