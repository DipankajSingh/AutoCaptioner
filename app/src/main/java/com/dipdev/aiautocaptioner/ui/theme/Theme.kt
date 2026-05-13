package com.dipdev.aiautocaptioner.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val FlatColorScheme = darkColorScheme(
    primary = LightGreenAccent,
    onPrimary = FlatBlack,
    secondary = FlatGrayLight,
    onSecondary = FlatWhite,
    tertiary = LightGreenAccent,
    onTertiary = FlatBlack,
    background = FlatBlack,
    onBackground = FlatWhite,
    surface = FlatGray,
    onSurface = FlatWhite,
    surfaceVariant = FlatGrayLight,
    onSurfaceVariant = FlatWhite
)

@Composable
fun AutoCaptionerTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = FlatColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}