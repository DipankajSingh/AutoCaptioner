package com.dipdev.aiautocaptioner.core.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object FileUtils {
    private const val TAG = "FileUtils"

    /**
     * Copies a model from the assets folder to internal storage.
     * Call only during first launch / model download fallback.
     */
    suspend fun copyModelFromAssets(context: Context, assetFileName: String): File {
        return withContext(Dispatchers.IO) {
            val outputFile = File(context.filesDir, assetFileName)
            if (!outputFile.exists()) {
                Log.i(TAG, "Copying model from assets to internal storage...")
                context.assets.open(assetFileName).use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Model copied to: ${outputFile.absolutePath}")
            } else {
                Log.i(TAG, "Model already exists at: ${outputFile.absolutePath}")
            }
            outputFile
        }
    }
}
