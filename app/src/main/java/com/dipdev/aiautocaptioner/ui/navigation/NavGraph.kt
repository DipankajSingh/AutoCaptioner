package com.dipdev.aiautocaptioner.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
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
    startDestination: Screen = Screen.Onboarding
) {
    val safePopBackStack: () -> Unit = {
        navController.popBackStack()
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {

        composable<Screen.Onboarding> {
            OnboardingScreen(
                onFinish = {
                    navController.navigate(Screen.SetupLanguage) {
                        popUpTo<Screen.Onboarding> { inclusive = true }
                    }
                }
            )
        }

        composable<Screen.SetupLanguage> {
            com.dipdev.aiautocaptioner.ui.onboarding.SetupLanguageScreen(
                onNavigateBack = { safePopBackStack() },
                onContinue = { langCode ->
                    navController.navigate(Screen.SetupQuality(langCode)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<Screen.SetupQuality> {
            com.dipdev.aiautocaptioner.ui.onboarding.SetupQualityScreen(
                onNavigateBack = { safePopBackStack() },
                onDownloadComplete = {
                    navController.navigate(Screen.Home) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable<Screen.DeviceCheck> {
            DeviceCheckScreen(
                onModelSelected = { modelId ->
                    navController.navigate(Screen.ModelDownload(modelId))
                }
            )
        }

        composable<Screen.ModelDownload> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.ModelDownload>()
            ModelDownloadScreen(
                modelId = args.modelId,
                onDownloadComplete = {
                    val poppedToProcessing = navController.popBackStack<Screen.Processing>(inclusive = false)
                    if (!poppedToProcessing) {
                        navController.navigate(Screen.Home) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable<Screen.Home> {
            val homeViewModel: com.dipdev.aiautocaptioner.ui.home.HomeViewModel = hiltViewModel()

            HomeScreen(
                onNavigateToSmartRecorder = { mode -> navController.navigate(Screen.SmartRecorder(mode)) },
                onNavigateToVideoEditor = { projectId ->
                    navController.navigate(Screen.VideoEditor(projectId))
                },
                onNavigateToProcessing = { projectId ->
                    navController.navigate(Screen.Processing(projectId))
                },
                onNavigateToCaptionEditor = { projectId ->
                    navController.navigate(Screen.CaptionEditor(projectId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings) {
                        launchSingleTop = true
                    }
                },
                onNavigateToHistory = { projectId ->
                    navController.navigate(Screen.ExportHistory(projectId))
                },
                viewModel = homeViewModel
            )
        }

        composable<Screen.Settings> {
            SettingsScreen(
                onNavigateBack = { safePopBackStack() },
                onNavigateToModelManager = { 
                    navController.navigate(Screen.ModelManager) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable<Screen.ModelManager> {
            com.dipdev.aiautocaptioner.ui.modelmanager.ModelManagerScreen(
                onNavigateBack = { safePopBackStack() }
            )
        }
        
        composable<Screen.ExportHistory> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.ExportHistory>()
            com.dipdev.aiautocaptioner.ui.exporthistory.ExportHistoryScreen(
                projectId = args.projectId,
                onNavigateBack = { safePopBackStack() }
            )
        }

        composable<Screen.Processing> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.Processing>()
            ProcessingScreen(
                projectId = args.projectId,
                forceModelPicker = args.forceModelPicker,
                isRegenerating = args.isRegenerating,
                onNavigateToCaptionEditor = {
                    navController.navigate(Screen.CaptionEditor(args.projectId)) {
                        popUpTo<Screen.Home> { inclusive = false }
                    }
                },
                onNavigateToVideoEditor = {
                    navController.navigate(Screen.VideoEditor(args.projectId)) {
                        popUpTo<Screen.Home> { inclusive = false }
                    }
                },
                onCancel = { safePopBackStack() }
            )
        }


        // ── Project Editor Graph ───────────────────────────────────────────────────
        // VideoEditor and StyleEditor share a single ExoPlayer via SharedPlayerViewModel,
        // which is scoped to this nested navigation graph.
        navigation<Screen.ProjectEditorGraph>(
            startDestination = Screen.VideoEditor::class
        ) {
            composable<Screen.VideoEditor> { backStackEntry ->
                val args = backStackEntry.toRoute<Screen.VideoEditor>()
                val projectId = args.projectId

                // Fix A: SharedPlayerViewModel scoped to the parent graph entry
                val graphEntry = remember(backStackEntry) {
                    // We need to look up by class when using typed navigation
                    navController.getBackStackEntry<Screen.ProjectEditorGraph>()
                }
                val sharedPlayerViewModel: SharedPlayerViewModel = hiltViewModel(graphEntry)

                EditorScreen(
                    projectId = projectId,
                    sharedPlayerViewModel = sharedPlayerViewModel,
                    onNavigateToProcessing = {
                        navController.navigate(Screen.Processing(projectId, forceModelPicker = true, isRegenerating = true))
                    },
                    onNavigateToCaptionEditor = {
                        navController.navigate(Screen.CaptionEditor(projectId, fromEditor = true)) {
                            popUpTo<Screen.VideoEditor> { inclusive = false }
                        }
                    },
                    onNavigateBack = { safePopBackStack() },
                    onNavigateToExport = {
                        navController.navigate(Screen.Export(projectId))
                    }
                )
            }

            composable<Screen.CaptionEditor> { backStackEntry ->
                val args = backStackEntry.toRoute<Screen.CaptionEditor>()
                val projectId = args.projectId
                val fromEditor = args.fromEditor
                
                val graphEntry = remember(backStackEntry) {
                    navController.getBackStackEntry<Screen.ProjectEditorGraph>()
                }
                val sharedPlayerViewModel: SharedPlayerViewModel = hiltViewModel(graphEntry)

                CaptionEditorScreen(
                    projectId = projectId,
                    fromEditor = fromEditor,
                    sharedPlayerViewModel = sharedPlayerViewModel,
                    onNavigateBack = { safePopBackStack() },
                    onNavigateToExport = { pid ->
                        navController.navigate(Screen.Export(pid))
                    }
                )
            }
        }

        composable<Screen.Export> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.Export>()
            
            com.dipdev.aiautocaptioner.ui.export.ExportScreen(
                projectId = args.projectId,
                onNavigateBack = { safePopBackStack() }
            )
        }

        composable<Screen.SmartRecorder> { _ ->
            SmartRecorderScreen(
                onNavigateBack = { safePopBackStack() },
                onVideoReady = { projectId ->
                    navController.navigate(Screen.VideoEditor(projectId)) {
                        popUpTo<Screen.SmartRecorder> { inclusive = true }
                    }
                }
            )
        }
    }
}
