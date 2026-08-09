package com.dipdev.aiautocaptioner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.dipdev.aiautocaptioner.ui.MainUiEffect
import com.dipdev.aiautocaptioner.ui.MainViewModel
import com.dipdev.aiautocaptioner.ui.components.ShareVideoBottomSheet
import com.dipdev.aiautocaptioner.ui.navigation.NavGraph
import com.dipdev.aiautocaptioner.ui.navigation.Screen
import com.dipdev.aiautocaptioner.ui.theme.AutoCaptionerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        // Handle incoming intent if launched fresh
        mainViewModel.onIntentReceived(intent)

        splashScreen.setKeepOnScreenCondition {
            mainViewModel.uiState.value.startDestination == null
        }

        enableEdgeToEdge()
        setContent {
            val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
            val glassmorphismEnabled = uiState.glassmorphismEnabled
            val navController = rememberNavController()

            LaunchedEffect(mainViewModel.uiEffect) {
                mainViewModel.uiEffect.collectLatest { effect ->
                    when (effect) {
                        is MainUiEffect.NavigateToProcessing -> {
                            navController.navigate(Screen.Processing(effect.projectId, forceModelPicker = true, isRegenerating = true))
                        }
                        is MainUiEffect.NavigateToVideoEditor -> {
                            navController.navigate(Screen.VideoEditor(effect.projectId))
                        }
                    }
                }
            }

            AutoCaptionerTheme(
                glassmorphismEnabled = glassmorphismEnabled
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val startDestination = uiState.startDestination

                    startDestination?.let { dest ->
                        NavGraph(
                            navController = navController,
                            startDestination = dest
                        )
                    }

                    if (uiState.sharedVideoUri != null) {
                        ShareVideoBottomSheet(
                            onAutoCaption = { mainViewModel.importSharedVideo(true) },
                            onEditVideo = { mainViewModel.importSharedVideo(false) },
                            onDismissRequest = { mainViewModel.clearSharedUri() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Ensure the intent is handled when the app is already in memory
        mainViewModel.onIntentReceived(intent)
    }
}