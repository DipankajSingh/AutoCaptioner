package com.dipdev.aiautocaptioner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.dipdev.aiautocaptioner.ui.MainViewModel
import com.dipdev.aiautocaptioner.ui.navigation.NavGraph
import com.dipdev.aiautocaptioner.ui.theme.AutoCaptionerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the splash screen BEFORE super.onCreate and setContent.
        // keepOnScreenCondition returns true while startDestination is null,
        // which holds the OS-drawn splash screen in place.
        // When startDestination becomes non-null the condition returns false
        // and the splash screen exits with a fade animation into the app.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Keep the splash screen visible until we know where to navigate
        splashScreen.setKeepOnScreenCondition {
            mainViewModel.startDestination.value == null
        }

        enableEdgeToEdge()
        setContent {
            val appTheme by mainViewModel.appTheme.collectAsState()
            val glassmorphismEnabled by mainViewModel.isGlassmorphismEnabled.collectAsState()

            AutoCaptionerTheme(
                appTheme = appTheme,
                glassmorphismEnabled = glassmorphismEnabled
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val startDestination by mainViewModel.startDestination.collectAsState()

                    // startDestination is guaranteed non-null here because
                    // the splash screen holds until it resolves.
                    // We still guard with ?: to be safe.
                    startDestination?.let { dest ->
                        NavGraph(startDestination = dest)
                    }
                }
            }
        }
    }
}