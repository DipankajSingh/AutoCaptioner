package com.dipdev.aiautocaptioner.ui.navigation

import kotlinx.serialization.Serializable

sealed class Screen {
    @Serializable
    data object Onboarding : Screen()

    @Serializable
    data object DeviceCheck : Screen()

    @Serializable
    data object ModelManager : Screen()

    @Serializable
    data class ModelDownload(val modelId: String) : Screen()

    @Serializable
    data object Home : Screen()

    @Serializable
    data object Settings : Screen()

    @Serializable
    data class ExportHistory(val projectId: String) : Screen()

    @Serializable
    data class ProjectEditorGraph(val projectId: String) : Screen()

    @Serializable
    data class VideoEditor(val projectId: String) : Screen()

    @Serializable
    data class Processing(val projectId: String, val forceModelPicker: Boolean = false, val isRegenerating: Boolean = false) : Screen()

    @Serializable
    data class CaptionEditor(val projectId: String, val fromEditor: Boolean = false) : Screen()

    @Serializable
    data class Export(val projectId: String) : Screen()

    @Serializable
    data class SmartRecorder(val mode: String) : Screen()
}