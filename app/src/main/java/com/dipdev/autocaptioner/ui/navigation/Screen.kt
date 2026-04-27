package com.dipdev.autocaptioner.ui.navigation

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

    // Processing screen — extract audio + transcribe
    // Takes projectId as argument so it knows which project to process
    data object Processing : Screen("processing/{projectId}") {
        fun createRoute(projectId: String) = "processing/$projectId"
    }

    // Caption editor — fix words, adjust timing, mark emphasis
    data object CaptionEditor : Screen("caption_editor/{projectId}") {
        fun createRoute(projectId: String) = "caption_editor/$projectId"
    }

    // Style editor — font, color, animation settings
    data object StyleEditor : Screen("style_editor/{projectId}") {
        fun createRoute(projectId: String) = "style_editor/$projectId"
    }

    // Export — FFmpeg burns captions into video
    data object Export : Screen("export/{projectId}") {
        fun createRoute(projectId: String) = "export/$projectId"
    }
}