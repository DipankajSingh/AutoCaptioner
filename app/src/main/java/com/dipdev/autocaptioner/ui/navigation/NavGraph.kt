package com.dipdev.autocaptioner.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dipdev.autocaptioner.ui.captioneditor.CaptionEditorScreen
import com.dipdev.autocaptioner.ui.devicecheck.DeviceCheckScreen
import com.dipdev.autocaptioner.ui.home.HomeScreen
import com.dipdev.autocaptioner.ui.modeldownload.ModelDownloadScreen
import com.dipdev.autocaptioner.ui.onboarding.OnboardingScreen
import com.dipdev.autocaptioner.ui.processing.ProcessingScreen
import com.dipdev.autocaptioner.ui.styleeditor.StyleEditorScreen

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
                    navController.navigate(Screen.DeviceCheck.route) {
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
                    navController.navigate(Screen.Home.route) {
                        // Clear entire back stack — user shouldn't go back to download flow
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToProcessing = { projectId ->
                    navController.navigate(Screen.Processing.createRoute(projectId))
                },
                onNavigateToEditor = { projectId ->
                    navController.navigate(Screen.StyleEditor.createRoute(projectId))
                }
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
                onDone = {
                    navController.navigate(Screen.StyleEditor.createRoute(projectId)) {
                        popUpTo(Screen.Processing.route) { inclusive = true }
                    }
                }
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
            // TODO: ExportScreen(projectId = projectId)
        }
    }
}