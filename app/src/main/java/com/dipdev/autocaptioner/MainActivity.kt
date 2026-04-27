package com.dipdev.autocaptioner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dipdev.autocaptioner.ui.MainViewModel
import com.dipdev.autocaptioner.ui.navigation.NavGraph
import com.dipdev.autocaptioner.ui.theme.AutoCaptionerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // viewModels() is the Activity-level ViewModel delegate
    // Hilt wires the dependencies automatically
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutoCaptionerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val startDestination by mainViewModel.startDestination.collectAsState()

                    if (startDestination == null) {
                        // Still deciding — show a minimal centered spinner
                        // This typically takes < 100ms so user barely sees it
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        // Start destination is known — launch nav graph directly
                        NavGraph(startDestination = startDestination!!)
                    }
                }
            }
        }
    }
}