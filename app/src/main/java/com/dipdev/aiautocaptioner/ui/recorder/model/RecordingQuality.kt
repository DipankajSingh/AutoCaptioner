package com.dipdev.aiautocaptioner.ui.recorder.model

enum class RecordingQuality(
    val label: String,
    val videoBitrate: Int,
    val audioBitrate: Int,
    val fps: Int
) {
    LOW("Low", 2_000_000, 96_000, 24),
    MEDIUM("Med", 4_000_000, 128_000, 30),
    HIGH("High", 8_000_000, 192_000, 30);

    companion object {
        fun cycle(current: RecordingQuality): RecordingQuality {
            val values = entries
            val nextIndex = (values.indexOf(current) + 1) % values.size
            return values[nextIndex]
        }

        fun fromName(name: String): RecordingQuality {
            return entries.find { it.name == name } ?: MEDIUM
        }
    }
}
