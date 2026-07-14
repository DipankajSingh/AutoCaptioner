package com.dipdev.aiautocaptioner.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dipdev.aiautocaptioner.ui.captioneditor.CaptionEditorScreen
import com.dipdev.aiautocaptioner.ui.devicecheck.DeviceCheckScreen
import com.dipdev.aiautocaptioner.ui.home.HomeScreen
import com.dipdev.aiautocaptioner.ui.modeldownload.ModelDownloadScreen
import com.dipdev.aiautocaptioner.ui.onboarding.OnboardingScreen
import com.dipdev.aiautocaptioner.ui.processing.ProcessingScreen
import com.dipdev.aiautocaptioner.ui.settings.SettingsScreen
import com.dipdev.aiautocaptioner.ui.videoeditor.core.EditorScreen
import com.dipdev.aiautocaptioner.ui.videoeditor.core.player.SharedPlayerViewModel
import com.dipdev.aiautocaptioner.ui.recorder.SmartRecorderScreen

@Composable
fun NavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    // startDestination is now passed in from MainActivity
    // instead of hardcoded to splash
    startDestination: String = Screen.Onboarding.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinish = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.DeviceCheck.route) {
            DeviceCheckScreen(
                onModelSelected = { modelId ->
                    navController.navigate(Screen.ModelDownload.createRoute(modelId))
                }
            )
        }

        composable(
            route = Screen.ModelDownload.route,
            arguments = listOf(
                navArgument("modelId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val modelId = backStackEntry.arguments?.getString("modelId") ?: ""
            ModelDownloadScreen(
                modelId = modelId,
                onDownloadComplete = {
                    val poppedToProcessing = navController.popBackStack(Screen.Processing.route, inclusive = false)
                    if (!poppedToProcessing) {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Screen.Home.route) { backStackEntry ->
            val homeViewModel: com.dipdev.aiautocaptioner.ui.home.HomeViewModel = hiltViewModel()

            HomeScreen(
                onNavigateToSmartRecorder = { mode -> navController.navigate(Screen.SmartRecorder.createRoute(mode)) },
                onNavigateToVideoEditor = { projectId ->
                    navController.navigate(Screen.VideoEditor.createRoute(projectId))
                },
                onNavigateToProcessing = { projectId ->
                    navController.navigate(Screen.Processing.createRoute(projectId))
                },
                onNavigateToCaptionEditor = { projectId ->
                    navController.navigate(Screen.CaptionEditor.createRoute(projectId))
                },
                onNavigateToModelManager = {
                    navController.navigate(Screen.ModelManager.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToHistory = { projectId ->
                    navController.navigate(Screen.ExportHistory.createRoute(projectId))
                },
                viewModel = homeViewModel
            )
        }


        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ModelManager.route) {
            com.dipdev.aiautocaptioner.ui.modelmanager.ModelManagerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.ExportHistory.route,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            com.dipdev.aiautocaptioner.ui.exporthistory.ExportHistoryScreen(
                projectId = projectId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Processing.route,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType },
                navArgument("forceModelPicker") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            val forceModelPicker = backStackEntry.arguments?.getBoolean("forceModelPicker") ?: false
            ProcessingScreen(
                projectId = projectId,
                forceModelPicker = forceModelPicker,
                onNavigateToCaptionEditor = {
                    navController.navigate(Screen.CaptionEditor.createRoute(projectId)) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                onNavigateToVideoEditor = {
                    navController.navigate(Screen.VideoEditor.createRoute(projectId)) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.CaptionEditor.route,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType },
                navArgument("fromEditor") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            val fromEditor = backStackEntry.arguments?.getBoolean("fromEditor") ?: false
            CaptionEditorScreen(
                projectId = projectId,
                fromEditor = fromEditor,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProcessing = { pid ->
                    navController.navigate(Screen.Processing.createRoute(pid, forceModelPicker = true))
                },
                onNavigateToExport = { pid ->
                    navController.navigate(Screen.Export.createRoute(pid))
                }
            )
        }

        // ── Project Editor Graph ───────────────────────────────────────────────────
        // VideoEditor and StyleEditor share a single ExoPlayer via SharedPlayerViewModel,
        // which is scoped to this nested navigation graph.
        navigation(
            startDestination = Screen.VideoEditor.route,
            route = Screen.ProjectEditorGraph.route,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType }
            )
        ) {
            composable(
                route = Screen.VideoEditor.route,
                arguments = listOf(
                    navArgument("projectId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId") ?: ""

                // Fix A: SharedPlayerViewModel scoped to the parent graph entry
                val graphEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Screen.ProjectEditorGraph.createRoute(projectId))
                }
                val sharedPlayerViewModel: SharedPlayerViewModel = hiltViewModel(graphEntry)

                EditorScreen(
                    projectId = projectId,
                    sharedPlayerViewModel = sharedPlayerViewModel,
                    onNavigateToProcessing = {
                        navController.navigate(Screen.Processing.createRoute(projectId)) {
                            popUpTo(Screen.Home.route) { inclusive = false }
                        }
                    },
                    onNavigateToCaptionEditor = {
                        navController.navigate(Screen.CaptionEditor.createRoute(projectId, fromEditor = true)) {
                            popUpTo(Screen.VideoEditor.createRoute(projectId)) { inclusive = false }
                        }
                    },
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToExport = {
                        navController.navigate(Screen.Export.createRoute(projectId))
                    }
                )
            }
        }

        composable(
            route = Screen.Export.route,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            
            com.dipdev.aiautocaptioner.ui.export.ExportScreen(
                projectId = projectId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.SmartRecorder.route,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType }
            )
        ) {
            SmartRecorderScreen(
                onNavigateBack = { navController.popBackStack() },
                onVideoReady = { projectId ->
                    navController.navigate(Screen.VideoEditor.createRoute(projectId)) {
                        popUpTo(Screen.SmartRecorder.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
