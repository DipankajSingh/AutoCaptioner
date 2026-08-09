package com.dipdev.aiautocaptioner.core.utils

import android.content.Context
import java.io.File

object FileUtils {
    private const val TAG = "FileUtils"

    fun createTempVideoFile(context: Context): File {
        return File(context.cacheDir, "edited_video_${System.currentTimeMillis()}.mp4")
    }
}
