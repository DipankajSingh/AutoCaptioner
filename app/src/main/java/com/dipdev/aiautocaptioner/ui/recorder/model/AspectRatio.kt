package com.dipdev.aiautocaptioner.ui.recorder.model

enum class AspectRatio(
    val label: String,
    val width: Int,
    val height: Int,
    val displayLabel: String
) {
    PORTRAIT_9_16("9:16", 1080, 1920, "9:16"),
    SQUARE_1_1("1:1", 1080, 1080, "1:1"),
    LANDSCAPE_16_9("16:9", 1920, 1080, "16:9"),
    PORTRAIT_4_5("4:5", 1080, 1350, "4:5");

    companion object {
        fun cycle(current: AspectRatio): AspectRatio {
            val values = entries
            val nextIndex = (values.indexOf(current) + 1) % values.size
            return values[nextIndex]
        }

        fun fromName(name: String): AspectRatio {
            return entries.find { it.name == name } ?: PORTRAIT_9_16
        }
    }
}
