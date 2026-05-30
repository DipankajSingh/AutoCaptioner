package com.dipdev.aiautocaptioner.data.model

data class WhisperModel(
    val id: String,
    val displayName: String,
    val description: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val minRamMb: Int,
    val accuracy: Int,        // 1-5
    val speed: Int,           // 1-5
    val languages: List<String>,
    val isMultilingual: Boolean,
    val isDownloaded: Boolean = false,
    val localPath: String? = null
) {
    val sizeMb: Int get() = (sizeBytes / 1_000_000).toInt()
}
