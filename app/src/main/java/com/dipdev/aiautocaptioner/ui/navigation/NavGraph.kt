package com.dipdev.aiautocaptioner.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dipdev.aiautocaptioner.ui.captioneditor.CaptionEditorScreen
import com.dipdev.aiautocaptioner.ui.devicecheck.DeviceCheckScreen
import com.dipdev.aiautocaptioner.ui.home.HomeScreen
import com.dipdev.aiautocaptioner.ui.modeldownload.ModelDownloadScreen
import com.dipdev.aiautocaptioner.ui.onboarding.OnboardingScreen
import com.dipdev.aiautocaptioner.ui.processing.ProcessingScreen
import com.dipdev.aiautocaptioner.ui.settings.SettingsScreen
import com.dipdev.aiautocaptioner.ui.styleeditor.StyleEditorScreen

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

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToProcessing = { projectId ->
                    navController.navigate(Screen.VideoEditor.createRoute(projectId))
                },
                onNavigateToEditor = { projectId ->
                    navController.navigate(Screen.StyleEditor.createRoute(projectId))
                },
                onNavigateToModelManager = {
                    navController.navigate(Screen.ModelManager.route)
                },

                onNavigateToSettings = { // Pass to settings if HomeScreen supports it, or handle it via a top bar
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToHistory = { projectId ->
                    navController.navigate(Screen.ExportHistory.createRoute(projectId))
                }
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
                navArgument("projectId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            ProcessingScreen(
                projectId = projectId,
                onNavigateToStyleEditor = {
                    navController.navigate(Screen.StyleEditor.createRoute(projectId)) {
                        popUpTo(Screen.Processing.route) { inclusive = true }
                    }
                },
                onNavigateToCaptionEditor = {
                    navController.navigate(Screen.CaptionEditor.createRoute(projectId)) {
                        popUpTo(Screen.Processing.route) { inclusive = true }
                    }
                },
                onNavigateToVideoEditor = {
                    navController.navigate(Screen.VideoEditor.createRoute(projectId))
                },
                onNavigateToDeviceCheck = {
                    navController.navigate(Screen.DeviceCheck.route)
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.CaptionEditor.route,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            CaptionEditorScreen(
                projectId = projectId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToStyleEditor = {
                    navController.navigate(Screen.StyleEditor.createRoute(projectId)) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                onNavigateToProcessing = { pid ->
                    navController.navigate(Screen.Processing.createRoute(pid)) {
                        popUpTo(Screen.CaptionEditor.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.StyleEditor.route,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            StyleEditorScreen(
                projectId = projectId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCaptionEditor = {
                    navController.navigate(Screen.CaptionEditor.createRoute(projectId))
                },
                onNavigateToExport = {
                    navController.navigate(Screen.Export.createRoute(projectId))
                },
                onSaved = { navController.popBackStack() }
            )
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
            route = Screen.VideoEditor.route,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            com.dipdev.aiautocaptioner.ui.videoeditor.VideoEditorScreen(
                projectId = projectId,
                onNavigateToProcessing = {
                    navController.navigate(Screen.Processing.createRoute(projectId)) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
