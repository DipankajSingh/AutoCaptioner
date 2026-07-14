package com.dipdev.aiautocaptioner.ui.navigation

// Sealed class = a closed set of types
// Each object inside represents one screen/destination in the app
// Using sealed class instead of plain strings means:
// → typos are caught at compile time, not runtime
// → IDE autocomplete works
// → refactoring is safe

sealed class Screen(val route: String) {

    // ---- Onboarding flow ----
    // These screens only appear on first launch


    // 3-page welcome/intro screen
    data object Onboarding : Screen("onboarding")

    // Checks device RAM/CPU and recommends a model
    data object DeviceCheck : Screen("device_check")

    // Dedicated screen to download, switch, and delete models
    data object ModelManager : Screen("model_manager")

    // Downloads the selected Whisper model with progress
    data object ModelDownload : Screen("model_download/{modelId}") {
        // Helper function to build the route with actual modelId
        // Usage: Screen.ModelDownload.createRoute("base.en")
        // Result: "model_download/base.en"
        fun createRoute(modelId: String) = "model_download/$modelId"
    }

    // ---- Main app flow ----

    // Home screen — shows recent projects + import button
    data object Home : Screen("home")


    
    // Settings screen — app appearance
    data object Settings : Screen("settings")
    
    // Export History — view previous exports for a project
    data object ExportHistory : Screen("export_history/{projectId}") {
        fun createRoute(projectId: String) = "export_history/$projectId"
    }

    /**
     * Nested navigation graph that scopes [SharedPlayerViewModel] across VideoEditor and StyleEditor.
     * Navigate to the graph by navigating to VideoEditor.createRoute(projectId); the nested graph
     * is entered automatically.
     */
    data object ProjectEditorGraph : Screen("project_editor_graph/{projectId}") {
        fun createRoute(projectId: String) = "project_editor_graph/$projectId"
    }

    // Video Editor — Trim and middle cuts before processing
    data object VideoEditor : Screen("video_editor/{projectId}") {
        fun createRoute(projectId: String) = "video_editor/$projectId"
    }

    // Processing screen — extract audio + transcribe
    // Takes projectId as argument so it knows which project to process
    data object Processing : Screen("processing/{projectId}?forceModelPicker={forceModelPicker}") {
        fun createRoute(projectId: String, forceModelPicker: Boolean = false) = "processing/$projectId?forceModelPicker=$forceModelPicker"
    }

    // Caption editor — fix words, adjust timing, mark emphasis
    data object CaptionEditor : Screen("caption_editor/{projectId}?fromEditor={fromEditor}") {
        fun createRoute(projectId: String, fromEditor: Boolean = false) =
            "caption_editor/$projectId?fromEditor=$fromEditor"
    }

    // Style editor — font, color, animation settings
    data object StyleEditor : Screen("style_editor/{projectId}?fromProcessing={fromProcessing}") {
        fun createRoute(projectId: String, fromProcessing: Boolean = false) =
            "style_editor/$projectId?fromProcessing=$fromProcessing"
    }

    // Export — FFmpeg / Media3 burns captions into video
    data object Export : Screen("export/{projectId}") {
        fun createRoute(projectId: String): String {
            return "export/$projectId"
        }
    }

    // Smart Recorder
    data object SmartRecorder : Screen("smart_recorder/{mode}") {
        fun createRoute(mode: String) = "smart_recorder/$mode"
    }
}